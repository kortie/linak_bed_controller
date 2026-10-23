package dev.linakbed.app.feature.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class OnboardingStep { INTRO, PERMISSION, CONNECT }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    /** 권한을 거부함 → 이유와 "앱 설정 열기"를 함께 보여 준다. */
    val permissionDenied: Boolean = false,
)

sealed interface OnboardingAction {
    /** 소개에서 "시작하기". 이미 권한이 있으면 연결 단계로 바로 간다. */
    data class Start(
        val permissionGranted: Boolean,
    ) : OnboardingAction

    data class PermissionResult(
        val granted: Boolean,
    ) : OnboardingAction

    /** 앱 설정에서 돌아왔을 때 등 권한 상태를 다시 확인. */
    data class PermissionChecked(
        val granted: Boolean,
    ) : OnboardingAction

    data object Back : OnboardingAction
}

/**
 * 연결 안내 (ADR-009): 소개 → 블루투스 권한 → 침대 연결. 연결이 끝나면 페어링 저장소가 바뀌어 앱이 홈으로 넘어간다
 * (이 ViewModel은 단계만 관리하고, 연결은 PairingViewModel이 한다).
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor() : ViewModel() {
        private val mutableState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

        fun onAction(action: OnboardingAction) {
            when (action) {
                is OnboardingAction.Start -> mutableState.update { it.copy(step = afterIntro(action.permissionGranted)) }
                is OnboardingAction.PermissionResult -> onPermission(action.granted, denyOnFailure = true)
                is OnboardingAction.PermissionChecked -> onPermission(action.granted, denyOnFailure = false)
                OnboardingAction.Back -> mutableState.value = OnboardingUiState()
            }
        }

        private fun onPermission(
            granted: Boolean,
            denyOnFailure: Boolean,
        ) {
            mutableState.update {
                when {
                    granted -> it.copy(step = OnboardingStep.CONNECT, permissionDenied = false)
                    it.step == OnboardingStep.CONNECT -> it.copy(step = OnboardingStep.PERMISSION)
                    denyOnFailure -> it.copy(permissionDenied = true)
                    else -> it
                }
            }
        }

        private fun afterIntro(permissionGranted: Boolean) = if (permissionGranted) OnboardingStep.CONNECT else OnboardingStep.PERMISSION
    }
