package dev.linakbed.app.feature.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.ui.theme.LinakBedTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DiagnosticsScreen(
    onOpenPairing: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.diag_export)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(state.shareIntent) {
        state.shareIntent?.let { intent ->
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            viewModel.onShareHandled()
        }
    }
    val fix = rememberCheckFixer(onOpenPairing = onOpenPairing, onResult = viewModel::refresh)
    DiagnosticsContent(state = state, onFix = fix, onExport = viewModel::export)
}

@Composable
fun DiagnosticsContent(
    state: DiagnosticsUiState,
    onFix: (SystemCheck) -> Unit,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.lastFallback?.let { at ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    stringResource(R.string.diag_fallback_warning, TIME.format(at.atZone(ZoneId.systemDefault()))),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        SystemCheck.entries.forEach { SystemCheckRow(it, state.checks[it], onFix) }
        Text(stringResource(R.string.diag_smartthings_battery), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(stringResource(R.string.diag_logs), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.diag_log_dir, state.logDirectory),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.diag_export)) }
        if (state.exportEmpty) Text(stringResource(R.string.diag_export_empty), color = MaterialTheme.colorScheme.error)
    }
}

private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Preview(showBackground = true)
@Composable
private fun DiagnosticsPreview() {
    LinakBedTheme {
        DiagnosticsContent(
            DiagnosticsUiState(
                checks = mapOf(SystemCheck.PAIRED to true),
                logDirectory = "Android/data/com.juanstudio.linakbed/files/logs",
            ),
            onFix = {},
            onExport = {},
        )
    }
}
