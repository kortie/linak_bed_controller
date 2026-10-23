package dev.linakbed.app.feature.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.ui.theme.LinakBedTheme

/** 02 문서 Part B §5와 같은 내용. 예시 문구는 지금 접두어·PIN을 반영한다. */
@Composable
fun RoutineGuideScreen(viewModel: RemoteSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copied = stringResource(R.string.guide_copied)
    RoutineGuideContent(example = state.exampleCommand) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("routine", state.exampleCommand))
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RoutineGuideContent(
    example: String,
    onCopy: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(R.string.guide_step1, R.string.guide_step2, R.string.guide_step3, R.string.guide_step4).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.guide_example), style = MaterialTheme.typography.titleSmall)
                Text(example, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge)
                Button(onClick = onCopy) { Text(stringResource(R.string.guide_copy)) }
            }
        }
        Text(stringResource(R.string.guide_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineGuidePreview() {
    LinakBedTheme { RoutineGuideContent(example = "#BED preset 2", onCopy = {}) }
}
