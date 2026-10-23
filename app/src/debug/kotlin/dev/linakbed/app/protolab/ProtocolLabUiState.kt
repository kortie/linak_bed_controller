package dev.linakbed.app.protolab

import dev.linakbed.ble.GattServiceInfo
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.PresetSlot

data class ProtocolLabUiState(
    val scanning: Boolean = false,
    val devices: List<ScannedDevice> = emptyList(),
    val selectedAddress: String? = null,
    val link: LinkState = LinkState.Disconnected,
    val services: List<GattServiceInfo> = emptyList(),
    val head: HeightReading? = null,
    val feet: HeightReading? = null,
    val rawInput: String = "",
    val repeatIntervalMs: Int = ProtocolLabViewModel.DEFAULT_REPEAT_INTERVAL_MS,
    val repeating: BedCommand? = null,
    /** 저장 확인 대기 중인 슬롯 (S6과 같은 이유로 확인 없이 저장하지 않는다). */
    val pendingSave: PresetSlot? = null,
    val log: List<String> = emptyList(),
    /** Controller 탭: BedController(안전 감독 포함) 상태. */
    val controller: BedStatus = BedStatus(),
)

sealed interface ProtocolLabAction {
    data object StartScan : ProtocolLabAction

    data object StopScan : ProtocolLabAction

    data class SelectDevice(
        val address: String,
    ) : ProtocolLabAction

    data object Connect : ProtocolLabAction

    data object Disconnect : ProtocolLabAction

    /** 1회 전송. 프리셋 저장은 이 경로로 보낼 수 없다 ([RequestSave] 사용). */
    data class Send(
        val command: BedCommand,
    ) : ProtocolLabAction

    /** 이동·프리셋 호출을 [ProtocolLabUiState.repeatIntervalMs]마다 반복 전송 (ADR-004 실험용). */
    data class StartRepeat(
        val command: BedCommand,
    ) : ProtocolLabAction

    data object Stop : ProtocolLabAction

    data class RepeatIntervalChanged(
        val ms: Int,
    ) : ProtocolLabAction

    data class RawInputChanged(
        val text: String,
    ) : ProtocolLabAction

    data object SendRaw : ProtocolLabAction

    data class RequestSave(
        val slot: PresetSlot,
    ) : ProtocolLabAction

    data object ConfirmSave : ProtocolLabAction

    data object CancelSave : ProtocolLabAction

    data object ClearLog : ProtocolLabAction

    // ── Controller 탭: BedController 경유 (중재·안전 규칙 적용) ──

    /** 선택한 기기를 컨트롤러의 연결 대상(페어링 저장소)으로 지정. 정식 페어링(CDM)은 S04. */
    data object UseSelectedAsControllerTarget : ProtocolLabAction

    data class ControllerPreset(
        val slot: PresetSlot,
    ) : ProtocolLabAction

    /** 누르는 순간. 손을 떼면 [ControllerMoveRelease]. */
    data class ControllerMovePress(
        val actuator: Actuator,
        val direction: Direction,
    ) : ProtocolLabAction

    data object ControllerMoveRelease : ProtocolLabAction

    data object ControllerStop : ProtocolLabAction
}
