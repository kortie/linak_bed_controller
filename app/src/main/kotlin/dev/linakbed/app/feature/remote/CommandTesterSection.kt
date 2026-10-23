package dev.linakbed.app.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteCommand

/** 루틴 문구를 넣어 해석 결과만 본다 — 침대를 움직이지 않고 기록도 남기지 않는다. */
@Composable
fun CommandTesterSection(
    state: RemoteSettingsUiState,
    onAction: (RemoteSettingsAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tester_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.testerInput,
            onValueChange = { onAction(RemoteSettingsAction.TesterInputChanged(it)) },
            placeholder = { Text(stringResource(R.string.tester_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onAction(RemoteSettingsAction.RunTester) }) { Text(stringResource(R.string.tester_run)) }
        state.testerResult?.let { Text(testerText(it), style = MaterialTheme.typography.bodyLarge) }
        Text(stringResource(R.string.tester_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun testerText(result: TesterResult): String =
    when (result) {
        TesterResult.NotACommand -> stringResource(R.string.tester_ignore)
        is TesterResult.Rejected -> stringResource(R.string.tester_rejected, rejectText(result.reason))
        is TesterResult.Command -> stringResource(R.string.tester_request, commandText(result.command))
    }

@Composable
private fun rejectText(reason: RejectReason): String =
    when (reason) {
        RejectReason.AUTH_FAILED -> stringResource(R.string.reject_auth_failed)
        RejectReason.INVALID -> stringResource(R.string.reject_invalid)
        else -> stringResource(R.string.reject_other, reason.name)
    }

@Composable
private fun commandText(command: RemoteCommand): String =
    when (command) {
        is RemoteCommand.Preset -> stringResource(R.string.preset_fallback_name, command.slot.number)
        RemoteCommand.Stop -> stringResource(R.string.control_stop)
        is RemoteCommand.Light -> "${stringResource(R.string.control_light)} ${command.action}"
    }
