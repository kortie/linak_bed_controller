package dev.linakbed.app.feature.pairing

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.ui.theme.LinakBedTheme
import dev.linakbed.domain.model.PairedDevice

/** 시스템 기기 선택 창 띄우기와 결과 메시지 (연결 안내·설정 화면 공용). */
@Composable
fun PairingEffects(
    state: PairingUiState,
    onAction: (PairingAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val selection =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onAction(PairingAction.SelectionResult(confirmed = result.resultCode == Activity.RESULT_OK))
        }
    LaunchedEffect(state.pendingSelection) {
        state.pendingSelection?.let { sender ->
            selection.launch(IntentSenderRequest.Builder(sender).build())
            onAction(PairingAction.SelectionLaunched)
        }
    }
    val message = state.message?.let { pairingMessage(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onAction(PairingAction.MessageShown)
        }
    }
}

/** 설정 > 침대 관리. */
@Composable
fun PairingSection(
    snackbar: SnackbarHostState,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PairingEffects(state, viewModel::onAction, snackbar)
    PairingContent(state = state, onAction = viewModel::onAction)
}

@Composable
fun PairingContent(
    state: PairingUiState,
    onAction: (PairingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmUnpair by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.titleLarge)
        PairedDeviceCard(state.paired, state.loaded)
        Button(onClick = { onAction(PairingAction.Pair) }, enabled = !state.inProgress, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.paired == null) R.string.pairing_pair else R.string.pairing_pair_again))
        }
        if (state.paired != null) {
            OutlinedButton(onClick = { confirmUnpair = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pairing_unpair))
            }
        }
        NotFoundHelp(state, onAction)
    }
    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text(stringResource(R.string.pairing_unpair_confirm_title)) },
            text = { Text(stringResource(R.string.pairing_unpair_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    onAction(PairingAction.Unpair)
                }) { Text(stringResource(R.string.pairing_unpair)) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** "침대가 목록에 보이지 않나요?" — 펼치면 이름으로 찾기 (광고에 제어 서비스가 없는 침대용). */
@Composable
fun NotFoundHelp(
    state: PairingUiState,
    onAction: (PairingAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(state.filterByName) }
    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.pairing_not_found)) }
    if (!expanded) return
    Text(stringResource(R.string.pairing_not_found_desc), style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = state.filterByName, onCheckedChange = { onAction(PairingAction.FilterByName(it)) })
        Text(stringResource(R.string.pairing_filter_by_name), style = MaterialTheme.typography.bodyMedium)
    }
    if (state.filterByName) {
        OutlinedTextField(
            value = state.namePattern,
            onValueChange = { onAction(PairingAction.NamePatternChanged(it)) },
            label = { Text(stringResource(R.string.pairing_name_pattern)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PairedDeviceCard(
    device: PairedDevice?,
    loaded: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                !loaded -> Text(stringResource(R.string.common_loading))
                device == null -> Text(stringResource(R.string.pairing_none))
                else -> Text(device.name ?: stringResource(R.string.pairing_unnamed), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun pairingMessage(message: PairingMessage): String =
    when (message) {
        is PairingMessage.Paired -> stringResource(R.string.pairing_done, message.name ?: stringResource(R.string.pairing_unnamed))
        PairingMessage.Cancelled -> stringResource(R.string.pairing_cancelled)
        is PairingMessage.Failed -> stringResource(R.string.pairing_failed, message.reason ?: "-")
        PairingMessage.InvalidPattern -> stringResource(R.string.pairing_invalid_pattern)
        PairingMessage.Unpaired -> stringResource(R.string.pairing_unpaired)
    }

@Preview(showBackground = true)
@Composable
private fun PairingContentPreview() {
    LinakBedTheme {
        PairingContent(
            state = PairingUiState(loaded = true, paired = PairedDevice("bed-1", "AA:BB:CC:DD:EE:FF", "DPG1M", 3)),
            onAction = {},
        )
    }
}
