package dev.linakbed.app.feature.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

data class PendingMotionUiState(
    /** false면 화면을 닫는다 (실행·취소·대체로 확인이 끝남). */
    val active: Boolean = true,
    val slot: PresetSlot? = null,
    val names: Map<PresetSlot, String> = emptyMap(),
    val remainingSeconds: Int = 0,
) {
    val presetName: String? get() = slot?.let(names::get)
}

@HiltViewModel
class PendingMotionViewModel
    @Inject
    constructor(
        private val registry: PendingMotionRegistry,
        presetNames: PresetNameStore,
        private val clock: Clock,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PendingMotionUiState())
        val uiState: StateFlow<PendingMotionUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch { presetNames.names().collect { names -> mutableState.update { it.copy(names = names) } } }
            viewModelScope.launch {
                registry.current.collectLatest { session ->
                    if (session == null) {
                        mutableState.update { it.copy(active = false) }
                        return@collectLatest
                    }
                    mutableState.update { it.copy(active = true, slot = session.slot) }
                    // 0초가 되면 멈춘다. 그 뒤로는 도메인이 실행하면서 확인을 닫는다.
                    do {
                        val left = Duration.between(clock.now(), session.deadline).toMillis()
                        val seconds = ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt().coerceAtLeast(0)
                        mutableState.update { it.copy(remainingSeconds = seconds) }
                        if (seconds > 0) delay(TICK_MS)
                    } while (seconds > 0)
                }
            }
        }

        /** 화면·볼륨 키에서 부른다. */
        fun cancel() {
            registry.cancel(null)
        }

        private companion object {
            const val MILLIS_PER_SECOND = 1000L
            const val TICK_MS = 250L
        }
    }
