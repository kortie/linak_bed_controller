package dev.linakbed.app.feature.preset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.BedController
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.preset.PresetName
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 프리셋 호출·저장(2단계 확인, S6)·이름 변경. 모든 동작은 BedController를 거친다 (출처 MANUAL). */
@HiltViewModel
class PresetViewModel
    @Inject
    constructor(
        private val controller: BedController,
        private val nameStore: PresetNameStore,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PresetUiState())
        val uiState: StateFlow<PresetUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch { nameStore.names().collect { names -> mutableState.update { it.copy(names = names) } } }
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
                val result = controller.stop(CommandSource.MANUAL)
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
