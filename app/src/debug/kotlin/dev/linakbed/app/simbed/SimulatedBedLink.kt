package dev.linakbed.app.simbed

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.info
import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.port.LinkState
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.LinakSensorDecoder
import dev.linakbed.protocol.PresetSlot
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * 디버그 전용 [BedLink]: BLE 없이 [SimulatedBed]에 명령을 보낸다. 컨트롤러·안전 감독·원격 경로는 실제 코드 그대로 돈다.
 * 연결 실패와 동작 중 끊김(S5)을 흉내 낼 수 있다.
 */
class SimulatedBedLink(
    private val profile: ProtocolProfile,
    private val scope: CoroutineScope,
    private val logger: BedLogger,
) : BedLink {
    data class Received(
        val atMs: Long,
        val command: BedCommand,
    )

    private val bed = SimulatedBed(profile)
    private val calibration get() = profile.calibration
    private val lock = Mutex()
    private var nowMs = 0L

    private val mutableState = MutableStateFlow<LinkState>(LinkState.Disconnected)
    override val state: StateFlow<LinkState> = mutableState.asStateFlow()

    private val mutableTelemetry = MutableSharedFlow<HeightReading>(extraBufferCapacity = TELEMETRY_BUFFER)
    override val telemetry: Flow<HeightReading> = mutableTelemetry.asSharedFlow()

    private val mutableSnapshot = MutableStateFlow(bed.snapshot(0))
    val snapshot: StateFlow<SimulatedBed.Snapshot> = mutableSnapshot.asStateFlow()

    private val mutableReceived = MutableStateFlow<List<Received>>(emptyList())

    /** 최근 받은 명령 (최신이 앞). 의도치 않은 명령이 나갔는지 눈으로 확인하는 용도. */
    val received: StateFlow<List<Received>> = mutableReceived.asStateFlow()

    /** 켜면 다음 연결 시도가 [BedError.ConnectTimeout]으로 실패한다. */
    val failConnect = MutableStateFlow(false)

    init {
        scope.launch {
            while (true) {
                delay(SimulatedBed.TICK_MS)
                lock.withLock {
                    nowMs += SimulatedBed.TICK_MS
                    val moved = bed.tick(nowMs)
                    if (moved && state.value == LinkState.Connected) bed.readings().forEach(mutableTelemetry::tryEmit)
                    mutableSnapshot.value = bed.snapshot(nowMs)
                }
            }
        }
    }

    override suspend fun connect(
        address: String,
        timeout: Duration,
    ): BedResult<Unit> {
        if (state.value == LinkState.Connected) return BedResult.Ok(Unit)
        mutableState.value = LinkState.Connecting
        delay(CONNECT_DELAY_MS)
        val result: BedResult<Unit> = if (failConnect.value) BedResult.Err(BedError.ConnectTimeout) else BedResult.Ok(Unit)
        if (result is BedResult.Err) {
            mutableState.value = LinkState.Failed(result.error)
        } else {
            mutableState.value = LinkState.Connected
            logger.info(AREA, "virtual bed connected")
            // 실제 링크처럼 연결 직후 현재 높이를 한 번 알린다.
            lock.withLock { bed.readings().forEach(mutableTelemetry::tryEmit) }
        }
        return result
    }

    override suspend fun disconnect() {
        lock.withLock { bed.halt() }
        mutableState.value = LinkState.Disconnected
    }

    /** 동작 중 연결 끊김 흉내 (S5): 이후 쓰기는 [BedError.Disconnected]. */
    suspend fun dropLink() {
        logger.info(AREA, "virtual bed link dropped")
        disconnect()
    }

    override suspend fun write(command: BedCommand): BedResult<Unit> {
        if (state.value != LinkState.Connected) return BedResult.Err(BedError.Disconnected)
        lock.withLock {
            bed.accept(command, nowMs)
            mutableReceived.value = (listOf(Received(nowMs, command)) + mutableReceived.value).take(RECEIVED_LIMIT)
            mutableSnapshot.value = bed.snapshot(nowMs)
        }
        return BedResult.Ok(Unit)
    }

    /** 가상 침대에 설정된 프리셋 위치 — 실제 침대와 달리 읽어 줄 수 있다 (S13). */
    override suspend fun readPresetPoses(): Map<PresetSlot, PresetPose> =
        lock.withLock { bed.snapshot(nowMs).presets }.mapValues { (_, position) ->
            PresetPose(
                headCm = LinakSensorDecoder.fromRaw(Actuator.HEAD, position.head, null, calibration).cm,
                feetCm = LinakSensorDecoder.fromRaw(Actuator.FEET, position.feet, null, calibration).cm,
            )
        }

    companion object {
        /** 이 접두어로 시작하는 주소면 가상 침대로 연결한다. MAC 형식이 아니라 실제 기기와 겹치지 않는다. */
        const val ADDRESS_PREFIX = "SIMULATED-BED"

        /** 가상 침대 [index]번(1부터)의 주소. */
        fun addressOf(index: Int) = "$ADDRESS_PREFIX-$index"

        private const val AREA = "SimBed"
        private const val CONNECT_DELAY_MS = 300L
        private const val TELEMETRY_BUFFER = 64
        private const val RECEIVED_LIMIT = 50
    }
}
