package dev.linakbed.app.feature.preset

import dev.linakbed.domain.BedError
import dev.linakbed.domain.preset.PresetName
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.PresetSlot

data class PresetUiState(
    val names: Map<PresetSlot, String> = emptyMap(),
    /** 프리셋별 마지막 자세 (S12, 버튼 그림). 모르는 슬롯은 없음. */
    val poses: Map<PresetSlot, PresetPose> = emptyMap(),
    /** 지금 이동 중인 프리셋 (출처 무관). */
    val running: PresetSlot? = null,
    /** 저장 확인 대기 중인 슬롯 (S6). */
    val pendingSave: PresetSlot? = null,
    /** 저장 2단계 확인: 체크해야 저장 버튼이 활성화된다. */
    val saveConfirmed: Boolean = false,
    val renaming: PresetSlot? = null,
    val renameText: String = "",
    val renameError: PresetName.Reason? = null,
    val message: PresetMessage? = null,
)

sealed interface PresetMessage {
    data class Arrived(
        val slot: PresetSlot,
    ) : PresetMessage

    data class Saved(
        val slot: PresetSlot,
    ) : PresetMessage

    data class Failed(
        val error: BedError,
    ) : PresetMessage
}

sealed interface PresetAction {
    data class Recall(
        val slot: PresetSlot,
    ) : PresetAction

    data object Stop : PresetAction

    data class RequestSave(
        val slot: PresetSlot,
    ) : PresetAction

    data class SaveConfirmedChanged(
        val confirmed: Boolean,
    ) : PresetAction

    data object ConfirmSave : PresetAction

    data object CancelSave : PresetAction

    data class StartRename(
        val slot: PresetSlot,
    ) : PresetAction

    data class RenameTextChanged(
        val text: String,
    ) : PresetAction

    data object ConfirmRename : PresetAction

    data object CancelRename : PresetAction

    data object MessageShown : PresetAction
}
