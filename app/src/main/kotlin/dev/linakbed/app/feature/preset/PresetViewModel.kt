package dev.linakbed.app.feature.preset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.app.feature.control.UnavailableBed
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.BedActions
import dev.linakbed.domain.controller.BedController
import dev.linakbed.domain.controller.BedFleet
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.port.PresetPoseStore
import dev.linakbed.domain.preset.PresetName
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 프리셋 호출·저장(2단계 확인, S6)·이름 변경. 모든 동작은 BedController를 거친다 (출처 MANUAL). */
@HiltViewModel(assistedFactory = PresetViewModel.Factory::class)
class PresetViewModel(
    private val controller: BedController,
    /** STOP은 모든 침대를 멈춘다 (ADR-012). */
    private val stopAll: BedActions,
    private val nameStore: PresetNameStore,
    poses: Flow<Map<PresetSlot, PresetPose>>,
) : ViewModel() {
    /** 카드 하나 = 침대 한 대 (S13). 프리셋 그림·Custom 저장은 침대별. */
    @AssistedInject
    constructor(
        @Assisted bedId: String,
        fleet: BedFleet,
        all: BedActions,
        nameStore: PresetNameStore,
        poseStore: PresetPoseStore,
    ) : this(fleet.controller(bedId) ?: UnavailableBed, all, nameStore, poseStore.poses(bedId))

    @AssistedFactory
    interface Factory {
        fun create(bedId: String): PresetViewModel
    }

    private val mutableState = MutableStateFlow(PresetUiState())
    val uiState: StateFlow<PresetUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { nameStore.names().collect { names -> mutableState.update { it.copy(names = names) } } }
        viewModelScope.launch { poses.collect { poses -> mutableState.update { it.copy(poses = poses) } } }
        viewModelScope.launch {
            controller.status.collect { status -> mutableState.update { it.copy(running = status.activeMotion?.preset) } }
        }
    }

    fun onAction(action: PresetAction) {
        when (action) {
            is PresetAction.Recall -> recall(action.slot)
            PresetAction.Stop -> stop()
            is PresetAction.RequestSave -> mutableState.update { it.copy(pendingSave = action.slot, saveConfirmed = false) }
            is PresetAction.SaveConfirmedChanged -> mutableState.update { it.copy(saveConfirmed = action.confirmed) }
            PresetAction.ConfirmSave -> confirmSave()
            PresetAction.CancelSave -> mutableState.update { it.copy(pendingSave = null, saveConfirmed = false) }
            is PresetAction.StartRename -> startRename(action.slot)
            is PresetAction.RenameTextChanged -> mutableState.update { it.copy(renameText = action.text, renameError = null) }
            PresetAction.ConfirmRename -> confirmRename()
            PresetAction.CancelRename -> mutableState.update { it.copy(renaming = null, renameError = null) }
            PresetAction.MessageShown -> mutableState.update { it.copy(message = null) }
        }
    }

    private fun recall(slot: PresetSlot) {
        viewModelScope.launch {
            val message =
                when (val result = controller.goToPreset(slot, CommandSource.MANUAL)) {
                    is BedResult.Ok -> PresetMessage.Arrived(slot)
                    is BedResult.Err -> PresetMessage.Failed(result.error)
                }
            mutableState.update { it.copy(message = message) }
        }
    }

    private fun stop() {
        viewModelScope.launch {
            val result = stopAll.stop(CommandSource.MANUAL)
            if (result is BedResult.Err) mutableState.update { it.copy(message = PresetMessage.Failed(result.error)) }
        }
    }

    /** S6: 확인 체크 없이는 하드웨어 프리셋을 덮어쓰지 않는다. */
    private fun confirmSave() {
        val state = uiState.value
        val slot = state.pendingSave ?: return
        if (!state.saveConfirmed) return
        mutableState.update { it.copy(pendingSave = null, saveConfirmed = false) }
        viewModelScope.launch {
            val message =
                when (val result = controller.savePreset(slot)) {
                    is BedResult.Ok -> PresetMessage.Saved(slot)
                    is BedResult.Err -> PresetMessage.Failed(result.error)
                }
            mutableState.update { it.copy(message = message) }
        }
    }

    private fun startRename(slot: PresetSlot) {
        mutableState.update { it.copy(renaming = slot, renameText = it.names[slot].orEmpty(), renameError = null) }
    }

    private fun confirmRename() {
        val state = uiState.value
        val slot = state.renaming ?: return
        when (val result = PresetName.validate(state.renameText, slot, state.names)) {
            is PresetName.Result.Invalid -> mutableState.update { it.copy(renameError = result.reason) }
            is PresetName.Result.Valid -> {
                mutableState.update { it.copy(renaming = null, renameError = null) }
                viewModelScope.launch { nameStore.rename(slot, result.name) }
            }
        }
    }
}
