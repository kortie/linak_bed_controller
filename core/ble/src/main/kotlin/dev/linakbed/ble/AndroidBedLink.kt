package dev.linakbed.ble

import android.Manifest
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.GattStage
import dev.linakbed.domain.andThen
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.LogRedaction
import dev.linakbed.domain.log.debug
import dev.linakbed.domain.log.info
import dev.linakbed.domain.log.warn
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.LinakCommandEncoder
import dev.linakbed.protocol.LinakGatt
import dev.linakbed.protocol.LinakSensorDecoder
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Duration

/**
 * [DiagnosticBedLink] 구현 (Android GATT).
 *
 * 연결 절차: connect → discoverServices → 제어 특성 확인 → 센서 notify(미지원이면 읽기 폴링).
 * 어느 단계든 실패하면 연결을 닫고 [BedResult.Err]. 연결된 뒤 끊기면 진행 중 작업을 모두
 * [BedError.Disconnected]로 끝내고 [LinkState.Disconnected]를 낸다 (S5의 기반). 동작 재개는 하지 않는다.
 */
class AndroidBedLink(
    private val connector: GattConnector,
    private val environment: BleEnvironment,
    private val profile: ProtocolProfile,
    private val logger: BedLogger,
    private val scope: CoroutineScope,
    private val config: BleConfig = BleConfig(),
) : DiagnosticBedLink {
    private val mutableState = MutableStateFlow<LinkState>(LinkState.Disconnected)
    override val state: StateFlow<LinkState> = mutableState.asStateFlow()

    private val mutableTelemetry =
        MutableSharedFlow<HeightReading>(extraBufferCapacity = TELEMETRY_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val telemetry: Flow<HeightReading> = mutableTelemetry.asSharedFlow()

    @Volatile
    override var services: List<GattServiceInfo> = emptyList()
        private set

    private val queue = GattOperationQueue()
    private val connectMutex = Mutex()

    @Volatile
    private var connection: GattConnection? = null

    @Volatile
    private var connectedAddress: String? = null
    private var pollJob: Job? = null

    private val listener = GattEventListener(::onGattEvent)

    override suspend fun connect(
        address: String,
        timeout: Duration,
    ): BedResult<Unit> =
        connectMutex.withLock {
            environmentError()?.let { error ->
                mutableState.value = LinkState.Failed(error)
                return@withLock BedResult.Err(error)
            }
            if (state.value == LinkState.Connected && connectedAddress == address) return@withLock BedResult.Ok(Unit)
            teardown()

            val masked = LogRedaction.mac(address)
            logger.info(AREA, "connecting $masked")
            mutableState.value = LinkState.Connecting
            queue.open()
            when (val result = establish(address, timeout)) {
                is BedResult.Ok -> {
                    connectedAddress = address
                    mutableState.value = LinkState.Connected
                    logger.info(AREA, "connected $masked")
                    result
                }
                is BedResult.Err -> {
                    teardown()
                    mutableState.value = LinkState.Failed(result.error)
                    logger.warn(AREA, "connect failed $masked: ${result.error}")
                    result
                }
            }
        }

    override suspend fun disconnect() {
        connectMutex.withLock {
            teardown()
            mutableState.value = LinkState.Disconnected
            logger.info(AREA, "disconnected by request")
        }
    }

    override suspend fun write(command: BedCommand): BedResult<Unit> = writeBytes(LinakCommandEncoder.encode(command), command.toString())

    override suspend fun writeRaw(bytes: ByteArray): BedResult<Unit> = writeBytes(bytes.copyOf(), "raw")

    private suspend fun establish(
        address: String,
        timeout: Duration,
    ): BedResult<Unit> =
        connectGatt(address, timeout)
            .andThen { gatt ->
                stage(GattStage.DISCOVER_SERVICES, start = { gatt.discoverServices() }) { event ->
                    (event as? GattEvent.ServicesDiscovered)?.status
                }.andThen { BedResult.Ok(gatt) }
            }.andThen { gatt ->
                services = gatt.services()
                logger.debug(AREA, "services: ${services.describe()}")
                if (services.characteristic(LinakGatt.SERVICE_CONTROL, LinakGatt.CHAR_CONTROL) == null) {
                    BedResult.Err(BedError.GattFailure(GattStage.MISSING_CONTROL_CHARACTERISTIC, null))
                } else {
                    setUpSensors(gatt)
                    BedResult.Ok(Unit)
                }
            }

    private suspend fun connectGatt(
        address: String,
        timeout: Duration,
    ): BedResult<GattConnection> =
        queue
            .run(
                timeout = timeout,
                timeoutError = BedError.ConnectTimeout,
                start = {
                    guard {
                        connection = connector.connect(address, listener)
                        if (connection == null) BedError.GattFailure(GattStage.CONNECT, null) else null
                    }
                },
                matcher = { event ->
                    (event as? GattEvent.ConnectionStateChanged)?.let {
                        if (it.connected && it.status == GATT_SUCCESS) {
                            BedResult.Ok(Unit)
                        } else {
                            BedResult.Err(GattStatusMapper.fromConnectFailure(it.status))
                        }
                    }
                },
            ).andThen { connection?.let { BedResult.Ok(it) } ?: BedResult.Err(BedError.Disconnected) }

    /** 센서는 없어도 연결은 성공으로 본다 (높이 표시·정체 감지만 못 함). */
    private suspend fun setUpSensors(gatt: GattConnection) {
        val pollTargets = mutableListOf<Pair<Actuator, UUID>>()
        for ((actuator, uuid) in SENSOR_CHARACTERISTICS) {
            val info = services.characteristic(LinakGatt.SERVICE_SENSOR, uuid)
            when {
                info == null -> logger.warn(AREA, "sensor $actuator not found")
                CharacteristicProperty.NOTIFY in info.properties -> {
                    val enabled =
                        stage(GattStage.ENABLE_NOTIFICATIONS, start = { gatt.enableNotifications(LinakGatt.SERVICE_SENSOR, uuid) }) {
                            (it as? GattEvent.DescriptorWritten)?.takeIf { written -> written.characteristic == uuid }?.status
                        }
                    if (enabled is BedResult.Err) logger.warn(AREA, "sensor $actuator notify failed: ${enabled.error}")
                }
                CharacteristicProperty.READ in info.properties -> pollTargets += actuator to uuid
                else -> logger.warn(AREA, "sensor $actuator has neither notify nor read")
            }
        }
        if (pollTargets.isNotEmpty()) {
            logger.info(AREA, "polling sensors ${pollTargets.map { it.first }} every ${config.sensorPollInterval}")
            pollJob = scope.launch { pollSensors(gatt, pollTargets) }
        }
    }

    private suspend fun pollSensors(
        gatt: GattConnection,
        targets: List<Pair<Actuator, UUID>>,
    ) {
        while (currentCoroutineContext().isActive) {
            for ((actuator, uuid) in targets) {
                val read =
                    queue.run(
                        timeout = config.operationTimeout,
                        timeoutError = BedError.GattFailure(GattStage.READ, null),
                        start = { guard { if (gatt.readCharacteristic(LinakGatt.SERVICE_SENSOR, uuid)) null else readFailure() } },
                        matcher = { event ->
                            (event as? GattEvent.CharacteristicRead)?.takeIf { it.characteristic == uuid }?.let {
                                GattStatusMapper.fromStageStatus(GattStage.READ, it.status).orOk(it.value)
                            }
                        },
                    )
                if (read is BedResult.Ok) publishReading(actuator, read.value)
            }
            delay(config.sensorPollInterval)
        }
    }

    private suspend fun writeBytes(
        bytes: ByteArray,
        label: String,
    ): BedResult<Unit> {
        val gatt = connection
        if (gatt == null || state.value != LinkState.Connected) return BedResult.Err(BedError.Disconnected)
        logger.debug(AREA, "tx ${bytes.toHex()} ($label)")
        val result =
            queue.run(
                timeout = config.operationTimeout,
                timeoutError = BedError.GattFailure(GattStage.WRITE, null),
                start = {
                    guard {
                        val code =
                            gatt.writeCharacteristic(LinakGatt.SERVICE_CONTROL, LinakGatt.CHAR_CONTROL, bytes, profile.writeWithResponse)
                        GattStatusMapper.fromWriteReturnCode(code)
                    }
                },
                matcher = { event ->
                    (event as? GattEvent.CharacteristicWritten)?.takeIf { it.characteristic == LinakGatt.CHAR_CONTROL }?.let {
                        GattStatusMapper.fromWriteStatus(it.status).orOk(Unit)
                    }
                },
            )
        if (result is BedResult.Err) logger.warn(AREA, "tx ${bytes.toHex()} failed: ${result.error}")
        return result
    }

    /** 연결 이후 단계: 시작 → status 이벤트 대기. [statusOf]는 이 단계의 이벤트면 status, 아니면 null. */
    private suspend fun stage(
        stage: GattStage,
        start: () -> Boolean,
        statusOf: (GattEvent) -> Int?,
    ): BedResult<Unit> =
        queue.run(
            timeout = config.operationTimeout,
            timeoutError = BedError.GattFailure(stage, null),
            start = { guard { if (start()) null else BedError.GattFailure(stage, null) } },
            matcher = { event ->
                statusOf(event)?.let { status -> GattStatusMapper.fromStageStatus(stage, status).orOk(Unit) }
            },
        )

    /** GATT 콜백 스레드에서 불린다. */
    private fun onGattEvent(event: GattEvent) {
        queue.onEvent(event)
        when (event) {
            is GattEvent.ConnectionStateChanged ->
                if (!event.connected && state.value == LinkState.Connected) onLinkLost(event.status)
            is GattEvent.CharacteristicChanged ->
                SENSOR_CHARACTERISTICS.firstOrNull { it.second == event.characteristic }?.let { (actuator, _) ->
                    publishReading(actuator, event.value)
                }
            else -> Unit
        }
    }

    private fun onLinkLost(status: Int) {
        queue.close(BedError.Disconnected)
        mutableState.value = LinkState.Disconnected
        logger.warn(AREA, "link lost (status $status)")
        scope.launch { connectMutex.withLock { if (state.value == LinkState.Disconnected) teardown() } }
    }

    private fun publishReading(
        actuator: Actuator,
        value: ByteArray,
    ) {
        logger.debug(AREA, "rx $actuator ${value.toHex()}")
        LinakSensorDecoder.decode(actuator, value, profile.calibration)?.let { mutableTelemetry.tryEmit(it) }
    }

    private fun teardown() {
        queue.close(BedError.Disconnected)
        pollJob?.cancel()
        pollJob = null
        connection?.let { gatt ->
            guard {
                gatt.disconnect()
                gatt.close()
                null
            }
        }
        connection = null
        connectedAddress = null
    }

    private fun environmentError(): BedError? =
        when {
            !environment.hasConnectPermission() -> BedError.PermissionMissing(Manifest.permission.BLUETOOTH_CONNECT)
            !environment.isBluetoothEnabled() -> BedError.BluetoothOff
            else -> null
        }

    /** 권한이 도중에 회수되면 SecurityException이 난다 — 밖으로 내보내지 않고 PermissionMissing으로 바꾼다. */
    private inline fun guard(block: () -> BedError?): BedError? =
        try {
            block()
        } catch (
            @Suppress("SwallowedException") denied: SecurityException,
        ) {
            BedError.PermissionMissing(Manifest.permission.BLUETOOTH_CONNECT)
        }

    private fun readFailure() = BedError.GattFailure(GattStage.READ, null)

    /** 실패 사유가 없으면 [value]로 성공. */
    private fun <T> BedError?.orOk(value: T): BedResult<T> = if (this == null) BedResult.Ok(value) else BedResult.Err(this)

    private fun List<GattServiceInfo>.describe(): String =
        joinToString("; ") { service ->
            "${service.uuid} [" + service.characteristics.joinToString { "${it.uuid} ${it.properties}" } + "]"
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val AREA = "Ble"
        const val GATT_SUCCESS = 0
        const val TELEMETRY_BUFFER = 64
        val SENSOR_CHARACTERISTICS = listOf(Actuator.HEAD to LinakGatt.CHAR_HEAD, Actuator.FEET to LinakGatt.CHAR_FEET)
    }
}
