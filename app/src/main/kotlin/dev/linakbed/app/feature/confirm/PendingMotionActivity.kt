package dev.linakbed.app.feature.confirm

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.linakbed.app.ui.theme.LinakBedTheme
import javax.inject.Inject

/** 잠금 화면 위에 잠금 해제 없이 뜨는 실행 전 확인 (S9). 볼륨 키로도 취소된다. */
@AndroidEntryPoint
class PendingMotionActivity : ComponentActivity() {
    private val viewModel: PendingMotionViewModel by viewModels()

    /** 앱 전체에 하나인 확인 상태. 볼륨 키 취소가 화면 상태와 상관없이 바로 닿도록 직접 잡는다. */
    @Inject
    lateinit var registry: PendingMotionRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.active) { if (!state.active) finish() }
            LinakBedTheme { PendingMotionScreen(state = state, onCancel = viewModel::cancel) }
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean =
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            registry.cancel(null)
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
}
