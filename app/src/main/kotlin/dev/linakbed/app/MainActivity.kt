package dev.linakbed.app

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.linakbed.app.navigation.AppRoot
import dev.linakbed.app.navigation.RootState
import dev.linakbed.app.navigation.RootViewModel
import dev.linakbed.app.ui.theme.LinakBedTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // AppRoot의 hiltViewModel()과 같은 인스턴스 (같은 ViewModelStore, 같은 키)
    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱은 고정 다크 테마(S10): 단말이 라이트 모드여도 시스템 바를 투명 + 밝은 아이콘으로 (하단 바가 밝은 띠로 남지 않게)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LinakBedTheme {
                AppRoot(rootViewModel)
            }
        }
        // 언어 전환 등으로 다시 만들어질 땐(savedInstanceState 있음) 시스템 스플래시가 없으니 최소 시간도 두지 않는다
        val minSplashMs = if (savedInstanceState == null) SPLASH_MIN_MS else 0L
        keepSplash(findViewById(android.R.id.content), SystemClock.uptimeMillis(), minSplashMs)
    }

    /** 첫 화면이 정해지고 최소 표시 시간이 지날 때까지 시스템 스플래시 화면을 유지한다 (로딩 표시가 잠깐 번쩍이지 않게). */
    private fun keepSplash(
        content: View,
        startedAt: Long,
        minSplashMs: Long,
    ) {
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val elapsed = SystemClock.uptimeMillis() - startedAt
                    if (shouldKeepSplash(rootViewModel.state.value, elapsed, minSplashMs)) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    companion object {
        /** 스플래시 최소 표시 시간 (침대 그림이 눈에 들어올 만큼). */
        const val SPLASH_MIN_MS = 2_000L

        internal fun shouldKeepSplash(
            state: RootState,
            elapsedMs: Long,
            minSplashMs: Long,
        ): Boolean = state == RootState.LOADING || elapsedMs < minSplashMs
    }
}
