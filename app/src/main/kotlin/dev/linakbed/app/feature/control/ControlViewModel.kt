package dev.linakbed.app.feature.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.BedController
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.controller.ConnectionLease
import dev.linakbed.domain.controller.MotionHandle
import dev.linakbed.domain.port.PairingStore
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 수동 제어 화면. 버튼을 누르고 있는 동안만 움직이고(S3), 손을 떼거나 화면이 안 보이게 되거나 ViewModel이 사라지면 멈춘다.
 * 모든 조작은 BedController를 거친다 (출처 MANUAL).
 */
@HiltViewModel
class ControlViewModel
    @Inject
    constructor(
        private val controller: BedController,
        private val pairingStore: PairingStore,
        private val presetNames: PresetNameStore,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ControlUiState())
        val uiState: StateFlow<ControlUiState> = mutableState.asStateFlow()

        private var lease: ConnectionLease? = null
        private var handle: MotionHandle? = null

        /** 누를 때마다 바뀌는 표식 — 시작이 끝나기 전에 손을 뗐거나 다른 버튼을 눌렀는지 판단한다. */
        private var pressToken = 0

        init {
            viewModelScope.launch { presetNames.names().collect { names -> mutableState.update { it.copy(presetNames = names) } } }
            viewModelScope.launch { pairingStore.observe().collect { device -> mutableState.update { it.copy(bedName = device?.name) } } }
            viewModelScope.launch {
                controller.status.collect { status ->
                    mutableState.update {
                        it.copy(
                            link = status.link,
                            headCm = status.headCm,
                            feetCm = status.feetCm,
                            light = status.light,
                            activeMotion = status.activeMotion,
                            lastError = status.lastError,
                        )
                    }
                }
            }
        }

        fun onAction(action: ControlAction) {
            when (action) {
                ControlAction.ScreenStarted -> onScreenStarted()
                ControlAction.ScreenStopped -> onScreenStopped()
                ControlAction.Reconnect -> reconnect()
                is ControlAction.Press -> press(action.move)
                ControlAction.Release -> releaseMove()
                ControlAction.Stop -> stop()
                is ControlAction.Light -> setLight(action.action)
                ControlAction.MessageShown -> mutableState.update { it.copy(userMessage = null) }
                is ControlAction.RecallPreset -> recallPreset(action.slot)
            }
        }

        override fun onCleared() {
            onScreenStopped()
        }

        private fun onScreenStarted() {
            if (lease == null) lease = controller.holdConnection()
        }

        /** 연결 유지 요청을 새로 하면 컨트롤러가 연결되지 않은 상태에서 다시 연결을 시도한다 (ADR-003). */
        private fun reconnect() {
            val previous = lease
            lease = controller.holdConnection()
            previous?.release()
        }

        private fun onScreenStopped() {
            releaseMove()
            lease?.release()
            lease = null
        }

        private fun press(move: ManualMove) {
            releaseMove()
            val token = ++pressToken
            mutableState.update { it.copy(pressed = move) }
            viewModelScope.launch {
                when (val result = controller.startManualMove(move.actuator, move.direction)) {
                    is BedResult.Ok ->
                        if (token == pressToken &&
                            uiState.value.pressed == move
                        ) {
                            handle = result.value
                        } else {
                            result.value.release()
                        }
                    is BedResult.Err -> {
                        if (token == pressToken) mutableState.update { it.copy(pressed = null) }
                        mutableState.update { it.copy(userMessage = result.error) }
                    }
                }
            }
        }

        private fun releaseMove() {
            pressToken++
            handle?.release()
            handle = null
            mutableState.update { it.copy(pressed = null) }
        }

        private fun stop() {
            releaseMove()
            viewModelScope.launch {
                val result = controller.stop(CommandSource.MANUAL)
                if (result is BedResult.Err) mutableState.update { it.copy(userMessage = result.error) }
            }
        }

        private fun recallPreset(slot: PresetSlot) {
            releaseMove()
            viewModelScope.launch {
                val result = controller.goToPreset(slot, CommandSource.MANUAL)
                if (result is BedResult.Err) mutableState.update { it.copy(userMessage = result.error) }
            }
        }

        private fun setLight(action: LightAction) {
            viewModelScope.launch {
                val result = controller.setLight(action, CommandSource.MANUAL)
                if (result is BedResult.Err) mutableState.update { it.copy(userMessage = result.error) }
            }
        }
    }
