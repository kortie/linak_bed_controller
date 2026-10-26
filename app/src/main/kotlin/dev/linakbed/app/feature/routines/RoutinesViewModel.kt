package dev.linakbed.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.app.feature.diagnostics.SystemCheck
import dev.linakbed.app.feature.diagnostics.SystemChecks
import dev.linakbed.domain.port.RemoteSettingsStore
import dev.linakbed.domain.remote.RemoteSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class RoutinesUiState(
    val settings: RemoteSettings = RemoteSettings(),
    /** 바로가기 실행이 잘 되려면 있으면 좋은 조건 중 모자란 것 (침대 연결·알림 표시·전체 화면 알림). */
    val recommended: List<SystemCheck> = emptyList(),
)

/** 설정 > 삼성 모드 및 루틴 (S11, ADR-011): 앱 바로가기 채널 스위치와 안내. */
@HiltViewModel
class RoutinesViewModel
    @Inject
    constructor(
        private val store: RemoteSettingsStore,
        private val systemChecks: SystemChecks,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(RoutinesUiState())
        val uiState: StateFlow<RoutinesUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch { store.settings().collect { settings -> mutableState.update { it.copy(settings = settings) } } }
            refresh()
        }

        fun setEnabled(enabled: Boolean) {
            viewModelScope.launch { store.update { it.copy(appShortcutsEnabled = enabled) } }
        }

        /** S9 실행 전 대기 (0 = 끔). 바뀌면 전체 화면 알림이 필요한지도 달라지므로 다시 확인한다. */
        fun setDelay(seconds: Int) {
            viewModelScope.launch {
                store.update { it.copy(preExecutionDelay = seconds.seconds) }
                refresh()
            }
        }

        fun setNotifyOnSuccess(enabled: Boolean) {
            viewModelScope.launch { store.update { it.copy(notifyOnSuccess = enabled) } }
        }

        fun refresh() {
            viewModelScope.launch {
                val checks = systemChecks.evaluate()
                val delayOn =
                    store
                        .settings()
                        .first()
                        .preExecutionDelay
                        .isPositive()
                val missing =
                    RECOMMENDED.filter { check ->
                        (check != SystemCheck.FULL_SCREEN_INTENT || delayOn) && checks[check] != true
                    }
                mutableState.update { it.copy(recommended = missing) }
            }
        }

        private companion object {
            /** 알림 접근·절전 예외·SmartThings 설치는 이 채널에 필요 없다. */
            val RECOMMENDED = listOf(SystemCheck.PAIRED, SystemCheck.POST_NOTIFICATIONS, SystemCheck.FULL_SCREEN_INTENT)
        }
    }
