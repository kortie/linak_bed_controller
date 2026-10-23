package dev.linakbed.app.simbed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.linakbed.app.ui.theme.LinakBedTheme

/** 디버그 빌드 전용 런처: 침대 없이 앱 전체 흐름을 시험하기 위한 가상 침대. */
@AndroidEntryPoint
class SimBedActivity : ComponentActivity() {
    private val viewModel: SimBedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
            LinakBedTheme {
                Scaffold { padding ->
                    SimBedScreen(
                        state = state,
                        onPair = viewModel::pairSimulated,
                        onUnpair = viewModel::unpairSimulated,
                        onFailConnect = viewModel::setFailConnect,
                        onDropLink = viewModel::dropLink,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}
