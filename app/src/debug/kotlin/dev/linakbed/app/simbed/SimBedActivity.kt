package dev.linakbed.app.simbed

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.linakbed.app.MainActivity
import dev.linakbed.app.ui.theme.LinakBedTheme

/** 디버그 빌드 전용 런처: 침대 없이 앱 전체 흐름을 시험하기 위한 가상 침대. */
@AndroidEntryPoint
class SimBedActivity : ComponentActivity() {
    private val viewModel: SimBedViewModel by viewModels()

    /** 정식 앱(침대리모컨)으로 넘어간다. 페어링이 있으면 홈(기기 화면)이 열린다. */
    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LinakBedTheme {
                Scaffold { padding ->
                    SimBedScreen(
                        state = state,
                        onPair = { index -> viewModel.pair(index, onPaired = ::openApp) },
                        onOpenApp = ::openApp,
                        onUnpair = viewModel::unpair,
                        onFailConnect = viewModel::setFailConnect,
                        onDropLink = viewModel::dropLink,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}
