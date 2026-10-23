package dev.linakbed.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.app.feature.onboarding.OnboardingScreen
import dev.linakbed.app.permissions.BlePermissionGate
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class RootState { LOADING, ONBOARDING, READY }

/** 페어링 여부로 연결 안내와 홈 중 무엇을 보일지 정한다 (ADR-009). */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        pairingStore: PairingStore,
    ) : ViewModel() {
        val state: StateFlow<RootState> =
            pairingStore
                .observe()
                .map { if (it == null) RootState.ONBOARDING else RootState.READY }
                .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.LOADING)
    }

/**
 * 앱의 최상위. 페어링이 없으면 연결 안내, 있으면 홈부터 시작하는 화면들.
 * 연결을 해제하면 홈 쪽 화면 스택이 통째로 사라지므로 뒤로 가기로 돌아갈 수 없다.
 */
@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state) {
        RootState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        RootState.ONBOARDING -> OnboardingScreen()
        RootState.READY -> BlePermissionGate { LinakNavHost() }
    }
}
