package dev.linakbed.app.feature.control

import dev.linakbed.domain.BedError
import dev.linakbed.domain.controller.ActiveMotion
import dev.linakbed.domain.controller.LightState
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot

data class ControlUiState(
    /** 연결(페어링)된 침대 이름. 이름이 없거나 아직 모르면 null. */
    val bedName: String? = null,
    val link: LinkState = LinkState.Disconnected,
    val headCm: Double? = null,
    val feetCm: Double? = null,
    val light: LightState = LightState.UNKNOWN,
    /** 지금 누르고 있는 버튼. */
    val pressed: ManualMove? = null,
    val activeMotion: ActiveMotion? = null,
    /** 컨트롤러가 마지막으로 보고한 오류 (상태 표시용). */
    val lastError: BedError? = null,
    /** 사용자 조작 결과 오류 (스낵바로 한 번 보여 주고 [ControlAction.MessageShown]으로 지운다). */
    val userMessage: BedError? = null,
    val presetNames: Map<PresetSlot, String> = emptyMap(),
) {
    /** 지금 이동 중인 프리셋 (출처 무관). */
    val runningPreset: PresetSlot? get() = activeMotion?.preset

    /** 조정·프리셋·조명은 연결돼 있을 때만. STOP은 이와 무관하게 항상 활성 (ADR-009). */
    val controlsEnabled: Boolean get() = link == LinkState.Connected
}

data class ManualMove(
    val actuator: Actuator,
    val direction: Direction,
)

sealed interface ControlAction {
    /** 화면이 보이기 시작 (ON_START): 연결 유지 요청. */
    data object ScreenStarted : ControlAction

    /** 화면이 안 보임 (ON_STOP): 움직이던 것을 멈추고 연결 유지를 놓는다 (S3). */
    data object ScreenStopped : ControlAction

    /** 연결 상태 칩: 끊겼을 때 다시 연결을 시도한다. */
    data object Reconnect : ControlAction

    data class Press(
        val move: ManualMove,
    ) : ControlAction

    data object Release : ControlAction

    data object Stop : ControlAction

    data class Light(
        val action: LightAction,
    ) : ControlAction

    data object MessageShown : ControlAction

    data class RecallPreset(
        val slot: PresetSlot,
    ) : ControlAction
}
