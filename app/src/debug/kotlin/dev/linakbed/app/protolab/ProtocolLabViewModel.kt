package dev.linakbed.app.protolab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.ble.DiagnosticBedLink
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.BedController
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.controller.MotionHandle
import dev.linakbed.domain.log.LogRedaction
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LinakCommandEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Protocol Lab: 실기기에서 README 프로토콜을 실측하기 위한 디버그 도구 (S02, backlog HW-S02).
 *
 * 정식 경로(BedController)를 거치지 않는 유일한 모터 경로이므로 자체 안전장치를 둔다:
 * 반복 전송은 [MAX_REPEAT_DURATION] 후 자동으로 멈추고, 반복이 끝나면 항상 Stop을 보낸다. 프리셋 저장은 확인을 거친다.
 */
@HiltViewModel
class ProtocolLabViewModel
    @Inject
    constructor(
        private val link: DiagnosticBedLink,
        private val scanner: DeviceScanner,
        private val clock: Clock,
        @param:Named(ProtocolLabModule.LAB) private val controller: BedController,
        private val target: LabTarget,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ProtocolLabUiState())
        val uiState: StateFlow<ProtocolLabUiState> = mutableState.asStateFlow()

        private var scanJob: Job? = null
        private var repeatJob: Job? = null

        /** 화면이 떠 있는 동안 컨트롤러의 유휴 연결 해제를 막는다 (Lab의 원시 연결과 같은 링크를 쓰므로). */
        private val lease = controller.holdConnection()
        private var movePressed = false
        private var moveHandle: MotionHandle? = null

        init {
            viewModelScope.launch {
                link.state.collect { linkState ->
                    mutableState.update {
                        it.copy(link = linkState, services = if (linkState == LinkState.Connected) link.services else it.services)
                    }
                }
            }
            viewModelScope.launch {
                controller.status.collect { status -> mutableState.update { it.copy(controller = status) } }
            }
            viewModelScope.launch {
                link.telemetry.collect { reading ->
                    mutableState.update { if (reading.actuator == Actuator.HEAD) it.copy(head = reading) else it.copy(feet = reading) }
                }
            }
        }

        fun onAction(action: ProtocolLabAction) {
            when (action) {
                ProtocolLabAction.StartScan -> startScan()
                ProtocolLabAction.StopScan -> scanJob?.cancel()
                is ProtocolLabAction.SelectDevice -> mutableState.update { it.copy(selectedAddress = action.address) }
                ProtocolLabAction.Connect -> connect()
                ProtocolLabAction.Disconnect -> disconnect()
                is ProtocolLabAction.Send -> sendOnce(action.command)
                is ProtocolLabAction.StartRepeat -> startRepeat(action.command)
                ProtocolLabAction.Stop -> stop()
                is ProtocolLabAction.RepeatIntervalChanged ->
                    mutableState.update { it.copy(repeatIntervalMs = action.ms.coerceIn(MIN_REPEAT_INTERVAL_MS, MAX_REPEAT_INTERVAL_MS)) }
                is ProtocolLabAction.RawInputChanged -> mutableState.update { it.copy(rawInput = action.text) }
                ProtocolLabAction.SendRaw -> sendRaw()
                is ProtocolLabAction.RequestSave -> mutableState.update { it.copy(pendingSave = action.slot) }
                ProtocolLabAction.ConfirmSave -> confirmSave()
                ProtocolLabAction.CancelSave -> mutableState.update { it.copy(pendingSave = null) }
                ProtocolLabAction.ClearLog -> mutableState.update { it.copy(log = emptyList()) }
                ProtocolLabAction.UseSelectedAsControllerTarget -> useSelectedAsControllerTarget()
                is ProtocolLabAction.ControllerPreset ->
                    viewModelScope.launch {
                        log("controller preset ${action.slot} → ${controller.goToPreset(action.slot, CommandSource.MANUAL).describe()}")
                    }
                is ProtocolLabAction.ControllerMovePress -> controllerMovePress(action.actuator, action.direction)
                ProtocolLabAction.ControllerMoveRelease -> controllerMoveRelease()
                ProtocolLabAction.ControllerStop ->
                    viewModelScope.launch { log("controller stop → ${controller.stop(CommandSource.MANUAL).describe()}") }
            }
        }

        override fun onCleared() {
            controllerMoveRelease()
            lease.release()
        }

        private fun useSelectedAsControllerTarget() {
            val device = uiState.value.devices.firstOrNull { it.address == uiState.value.selectedAddress }
            if (device == null) {
                log("controller target: select a device first")
                return
            }
            target.device.value =
                PairedDevice(
                    bedId = UUID.randomUUID().toString(),
                    address = device.address,
                    name = device.name,
                    cdmAssociationId = NO_CDM_ASSOCIATION,
                )
            log("controller target set to ${LogRedaction.mac(device.address)}")
        }

        /** 버튼을 누르고 있는 동안만 움직인다 (S3). 시작이 끝나기 전에 손을 떼도 바로 멈춘다. */
        private fun controllerMovePress(
            actuator: Actuator,
            direction: Direction,
        ) {
            movePressed = true
            viewModelScope.launch {
                when (val result = controller.startManualMove(actuator, direction)) {
                    is BedResult.Ok -> if (movePressed) moveHandle = result.value else result.value.release()
                    is BedResult.Err -> log("controller move $actuator $direction → ERR ${result.error}")
                }
            }
        }

        private fun controllerMoveRelease() {
            movePressed = false
            moveHandle?.release()
            moveHandle = null
        }

        private fun startScan() {
            scanJob?.cancel()
            mutableState.update { it.copy(scanning = true) }
            log("scan start")
            scanJob =
                viewModelScope.launch {
                    try {
                        withTimeoutOrNull(SCAN_DURATION) {
                            scanner
                                .scan()
                                .catch { failure -> log("scan error: ${failure.message}") }
                                .collect { device -> addDevice(device) }
                        }
                    } finally {
                        mutableState.update { it.copy(scanning = false) }
                        log("scan stop (${uiState.value.devices.size} devices)")
                    }
                }
        }

        private fun addDevice(device: ScannedDevice) {
            mutableState.update { state ->
                state.copy(devices = (state.devices.filterNot { it.address == device.address } + device).sortedByDescending { it.rssi })
            }
        }

        private fun connect() {
            val address = uiState.value.selectedAddress
            if (address == null) {
                log("connect: select a device first")
                return
            }
            viewModelScope.launch {
                log("connect ${LogRedaction.mac(address)}")
                log("connect → ${link.connect(address, CONNECT_TIMEOUT).describe()}")
            }
        }

        private fun disconnect() {
            repeatJob?.cancel()
            viewModelScope.launch {
                link.disconnect()
                log("disconnected")
            }
        }

        private fun sendOnce(command: BedCommand) {
            if (command is BedCommand.SavePreset) {
                log("save must be confirmed: use the save menu")
                return
            }
            viewModelScope.launch { send(command) }
        }

        private fun startRepeat(command: BedCommand) {
            if (command !is BedCommand.Move && command !is BedCommand.RecallPreset) {
                log("repeat is only for move and preset recall")
                return
            }
            repeatJob?.cancel()
            mutableState.update { it.copy(repeating = command) }
            val interval = uiState.value.repeatIntervalMs.milliseconds
            log("repeat $command every $interval")
            repeatJob =
                viewModelScope.launch {
                    try {
                        val finished =
                            withTimeoutOrNull(MAX_REPEAT_DURATION) {
                                while (true) {
                                    send(command)
                                    delay(interval)
                                }
                            }
                        if (finished == null) log("repeat auto-stopped after $MAX_REPEAT_DURATION")
                    } finally {
                        withContext(NonCancellable) { send(BedCommand.Stop) }
                        mutableState.update { it.copy(repeating = null) }
                    }
                }
        }

        /** 반복 중이면 반복을 끊는다 (반복이 끝나면서 Stop을 보냄). 아니면 바로 Stop. */
        private fun stop() {
            val repeat = repeatJob
            if (repeat != null && repeat.isActive) {
                repeat.cancel()
            } else {
                viewModelScope.launch { send(BedCommand.Stop) }
            }
        }

        private fun sendRaw() {
            val bytes = HexInput.parse(uiState.value.rawInput)
            if (bytes == null) {
                log("raw: enter exactly 2 bytes in hex, e.g. 0b00")
                return
            }
            viewModelScope.launch { log("${HexInput.format(bytes)} raw → ${link.writeRaw(bytes).describe()}") }
        }

        private fun confirmSave() {
            val slot = uiState.value.pendingSave ?: return
            mutableState.update { it.copy(pendingSave = null) }
            viewModelScope.launch { send(BedCommand.SavePreset(slot)) }
        }

        private suspend fun send(command: BedCommand) {
            val result = link.write(command)
            log("${HexInput.format(LinakCommandEncoder.encode(command))} $command → ${result.describe()}")
        }

        private fun log(message: String) {
            val time = TIME.format(clock.now().atZone(clock.zone()))
            mutableState.update { it.copy(log = (it.log + "$time $message").takeLast(MAX_LOG_LINES)) }
        }

        private fun BedResult<Unit>.describe(): String =
            when (this) {
                is BedResult.Ok -> "OK"
                is BedResult.Err -> "ERR $error"
            }

        companion object {
            const val DEFAULT_REPEAT_INTERVAL_MS = 200

            /** Lab에서 지정한 대상은 CDM 연결이 없다. */
            const val NO_CDM_ASSOCIATION = -1
            const val MIN_REPEAT_INTERVAL_MS = 100
            const val MAX_REPEAT_INTERVAL_MS = 1000
            const val MAX_LOG_LINES = 300
            val MAX_REPEAT_DURATION = 30.seconds
            val SCAN_DURATION = 15.seconds
            val CONNECT_TIMEOUT = 10.seconds
            private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        }
    }
