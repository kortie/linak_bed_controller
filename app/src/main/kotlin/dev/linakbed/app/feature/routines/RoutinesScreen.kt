package dev.linakbed.app.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.feature.diagnostics.SystemCheck
import dev.linakbed.app.feature.diagnostics.SystemCheckRow
import dev.linakbed.app.feature.diagnostics.rememberCheckFixer
import dev.linakbed.app.ui.theme.LinakBedTheme

@Composable
fun RoutinesScreen(
    onOpenPairing: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val fix = rememberCheckFixer(onOpenPairing = onOpenPairing, onResult = viewModel::refresh)
    RoutinesContent(state = state, onEnabled = viewModel::setEnabled, onFix = fix)
}

@Composable
fun RoutinesContent(
    state: RoutinesUiState,
    onEnabled: (Boolean) -> Unit,
    onFix: (SystemCheck) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.routines_enabled), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = state.settings.appShortcutsEnabled, onCheckedChange = onEnabled)
        }
        Text(stringResource(R.string.routines_enabled_desc), style = MaterialTheme.typography.bodySmall)
        if (state.recommended.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.routines_recommended), style = MaterialTheme.typography.titleMedium)
                    state.recommended.forEach { SystemCheckRow(it, ok = false, onFix = onFix) }
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.routines_how_title), style = MaterialTheme.typography.titleMedium)
        listOf(R.string.routines_step1, R.string.routines_step2, R.string.routines_step3, R.string.routines_step4).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            stringResource(
                R.string.routines_delay_note,
                state.settings.preExecutionDelay.inWholeSeconds
                    .toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.routines_launcher_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1530)
@Composable
private fun RoutinesPreview() {
    LinakBedTheme { RoutinesContent(RoutinesUiState(recommended = listOf(SystemCheck.POST_NOTIFICATIONS)), onEnabled = {}, onFix = {}) }
}
