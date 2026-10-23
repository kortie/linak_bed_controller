package dev.linakbed.app.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.feature.diagnostics.SystemCheckRow
import dev.linakbed.app.feature.diagnostics.rememberCheckFixer
import dev.linakbed.app.ui.theme.LinakBedTheme

@Composable
fun RemoteSettingsScreen(
    onOpenPairing: () -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: RemoteSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(RemoteSettingsAction.RefreshChecks) }
    val fix = rememberCheckFixer(onOpenPairing = onOpenPairing, onResult = { viewModel.onAction(RemoteSettingsAction.RefreshChecks) })
    RemoteSettingsContent(state = state, onAction = viewModel::onAction, onFix = fix, onOpenGuide = onOpenGuide)
}

@Composable
fun RemoteSettingsContent(
    state: RemoteSettingsUiState,
    onAction: (RemoteSettingsAction) -> Unit,
    onFix: (dev.linakbed.app.feature.diagnostics.SystemCheck) -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchRow(stringResource(R.string.remote_enabled), state.remote.enabled) { onAction(RemoteSettingsAction.SetEnabled(it)) }
        Text(stringResource(R.string.remote_enabled_desc), style = MaterialTheme.typography.bodySmall)
        if (state.missing.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.remote_missing_title), style = MaterialTheme.typography.titleMedium)
                    state.missing.forEach { SystemCheckRow(it, state.checks[it], onFix) }
                }
            }
        }
        HorizontalDivider()
        ChannelFields(state, onAction)
        DelayChips(
            state.remote.preExecutionDelay.inWholeSeconds
                .toInt(),
            onAction,
        )
        SwitchRow(stringResource(R.string.remote_notify_success), state.remote.notifyOnSuccess) {
            onAction(RemoteSettingsAction.NotifyOnSuccess(it))
        }
        SwitchRow(stringResource(R.string.remote_dismiss_handled), state.channel.dismissHandledNotification) {
            onAction(RemoteSettingsAction.DismissHandled(it))
        }
        HorizontalDivider()
        CommandTesterSection(state, onAction)
        OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_guide)) }
    }
}

@Composable
private fun ChannelFields(
    state: RemoteSettingsUiState,
    onAction: (RemoteSettingsAction) -> Unit,
) {
    OutlinedTextField(
        value = state.prefixInput,
        onValueChange = { onAction(RemoteSettingsAction.PrefixChanged(it)) },
        label = { Text(stringResource(R.string.remote_prefix)) },
        isError = state.prefixInvalid,
        supportingText = if (state.prefixInvalid) ({ Text(stringResource(R.string.remote_prefix_invalid)) }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.pinInput,
        onValueChange = { onAction(RemoteSettingsAction.PinChanged(it)) },
        label = { Text(stringResource(R.string.remote_pin)) },
        isError = state.pinInvalid,
        supportingText = if (state.pinInvalid) ({ Text(stringResource(R.string.remote_pin_invalid)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onAction(RemoteSettingsAction.SaveChannel) }) { Text(stringResource(R.string.common_save)) }
}

@Composable
private fun DelayChips(
    selectedSeconds: Int,
    onAction: (RemoteSettingsAction) -> Unit,
) {
    Text(stringResource(R.string.remote_delay), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DELAY_OPTIONS.forEach { seconds ->
            FilterChip(
                selected = seconds == selectedSeconds,
                onClick = { onAction(RemoteSettingsAction.DelaySelected(seconds)) },
                label = {
                    Text(
                        if (seconds ==
                            0
                        ) {
                            stringResource(R.string.remote_delay_off)
                        } else {
                            stringResource(R.string.remote_delay_seconds, seconds)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** S9 실행 전 대기 선택지 (0 = 끔), S07 스텝 문서 그대로. */
@Suppress("MagicNumber")
val DELAY_OPTIONS = listOf(0, 5, 10, 20, 30, 60)

@Preview(showBackground = true)
@Composable
private fun RemoteSettingsPreview() {
    LinakBedTheme { RemoteSettingsContent(RemoteSettingsUiState(), onAction = {}, onFix = {}, onOpenGuide = {}) }
}
