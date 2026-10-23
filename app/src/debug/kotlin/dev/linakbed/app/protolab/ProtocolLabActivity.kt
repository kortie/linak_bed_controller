package dev.linakbed.app.protolab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.linakbed.app.ui.theme.LinakBedTheme

/** debug 빌드에만 있는 별도 런처 아이콘 "Protocol Lab". */
@AndroidEntryPoint
class ProtocolLabActivity : ComponentActivity() {
    private val viewModel: ProtocolLabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinakBedTheme {
                ProtocolLabScreen(viewModel)
            }
        }
    }
}
