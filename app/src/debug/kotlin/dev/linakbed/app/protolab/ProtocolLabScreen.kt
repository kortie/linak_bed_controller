package dev.linakbed.app.protolab

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.app.ui.theme.LinakBedTheme
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.PresetSlot

@Composable
fun ProtocolLabScreen(viewModel: ProtocolLabViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val deniedMessage = stringResource(R.string.protolab_permissions_denied)
    val copiedMessage = stringResource(R.string.protolab_copied)
    var afterPermission by remember { mutableStateOf<ProtocolLabAction?>(null) }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val action = afterPermission
            afterPermission = null
            if (granted.values.all { it } && action != null) {
                viewModel.onAction(action)
            } else {
                Toast.makeText(context, deniedMessage, Toast.LENGTH_SHORT).show()
            }
        }

    ProtocolLabContent(
        state = state,
        onAction = { action ->
            if (action == ProtocolLabAction.StartScan || action == ProtocolLabAction.Connect) {
                afterPermission = action
                permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
            } else {
                viewModel.onAction(action)
            }
        },
        onCopy = {
            context
                .getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("protocol-lab", ProtocolLabReport.render(state)))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
fun ProtocolLabContent(
    state: ProtocolLabUiState,
    onAction: (ProtocolLabAction) -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        // STOP은 항상 화면 맨 위에 고정, 언제나 누를 수 있다.
        topBar = {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    onClick = { onAction(ProtocolLabAction.Stop) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.protolab_stop), style = MaterialTheme.typography.headlineMedium)
                }
                Text(stringResource(R.string.protolab_safety_notice), style = MaterialTheme.typography.bodySmall)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { DeviceSection(state, onAction) }
            item { SensorSection(state.head, state.feet) }
            item {
                HorizontalDivider()
                ControllerTab(state, onAction)
            }
            item { CommandSection(state, onAction) }
            item { RawSection(state.rawInput, onAction) }
            item {
                SectionTitle(stringResource(R.string.protolab_section_save))
                OutlinedButton(onClick = { showSaveMenu = true }) { Text(stringResource(R.string.protolab_save_menu)) }
            }
            item { ServicesSection(state) }
            item {
                SectionTitle(stringResource(R.string.protolab_section_log))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCopy) { Text(stringResource(R.string.protolab_copy)) }
                    OutlinedButton(onClick = { onAction(ProtocolLabAction.ClearLog) }) { Text(stringResource(R.string.protolab_clear)) }
                }
            }
            items(state.log.asReversed()) { line ->
                Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    SaveDialogs(
        showMenu = showSaveMenu,
        pendingSave = state.pendingSave,
        onAction = onAction,
        onCloseMenu = { showSaveMenu = false },
    )
}

/** 프리셋 저장 2단계: 경고가 있는 메뉴에서 슬롯 선택 → 덮어쓰기 확인. */
@Composable
private fun SaveDialogs(
    showMenu: Boolean,
    pendingSave: PresetSlot?,
    onAction: (ProtocolLabAction) -> Unit,
    onCloseMenu: () -> Unit,
) {
    if (showMenu) {
        SaveMenuDialog(
            onSelect = { slot ->
                onCloseMenu()
                onAction(ProtocolLabAction.RequestSave(slot))
            },
            onDismiss = onCloseMenu,
        )
    }
    pendingSave?.let { slot ->
        AlertDialog(
            onDismissRequest = { onAction(ProtocolLabAction.CancelSave) },
            title = { Text(stringResource(R.string.protolab_save_confirm_title, slot.number)) },
            text = { Text(stringResource(R.string.protolab_save_confirm_message, slot.number)) },
            confirmButton = {
                TextButton(onClick = { onAction(ProtocolLabAction.ConfirmSave) }) { Text(stringResource(R.string.protolab_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ProtocolLabAction.CancelSave) }) { Text(stringResource(R.string.protolab_cancel)) }
            },
        )
    }
}

@Composable
private fun DeviceSection(
    state: ProtocolLabUiState,
    onAction: (ProtocolLabAction) -> Unit,
) {
    SectionTitle(stringResource(R.string.protolab_section_device))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.scanning) {
            OutlinedButton(onClick = { onAction(ProtocolLabAction.StopScan) }) { Text(stringResource(R.string.protolab_scan_stop)) }
        } else {
            OutlinedButton(onClick = { onAction(ProtocolLabAction.StartScan) }) { Text(stringResource(R.string.protolab_scan)) }
        }
        Button(onClick = { onAction(ProtocolLabAction.Connect) }, enabled = state.selectedAddress != null) {
            Text(stringResource(R.string.protolab_connect))
        }
        OutlinedButton(onClick = { onAction(ProtocolLabAction.Disconnect) }) { Text(stringResource(R.string.protolab_disconnect)) }
    }
    Text(stringResource(R.string.protolab_link_state, state.link.toString()))
    state.devices.forEach { device ->
        val selected = device.address == state.selectedAddress
        Text(
            text =
                stringResource(
                    R.string.protolab_device_row,
                    device.name ?: stringResource(R.string.protolab_unnamed),
                    device.address,
                    device.rssi,
                    device.advertisesControlService.toString(),
                ),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onAction(ProtocolLabAction.SelectDevice(device.address)) }
                    .padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun SensorSection(
    head: HeightReading?,
    feet: HeightReading?,
) {
    SectionTitle(stringResource(R.string.protolab_section_sensors))
    listOf("HEAD" to head, "FEET" to feet).forEach { (label, reading) ->
        val value = reading?.let { "raw=${it.raw}  cm=${"%.2f".format(it.cm)}  speed=${it.speedCmPerMin ?: "-"}" } ?: "-"
        Text(stringResource(R.string.protolab_sensor_row, label, value), fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CommandSection(
    state: ProtocolLabUiState,
    onAction: (ProtocolLabAction) -> Unit,
) {
    SectionTitle(stringResource(R.string.protolab_section_commands))
    Text(stringResource(R.string.protolab_interval, state.repeatIntervalMs))
    Slider(
        value = state.repeatIntervalMs.toFloat(),
        onValueChange = { onAction(ProtocolLabAction.RepeatIntervalChanged(it.toInt())) },
        valueRange = ProtocolLabViewModel.MIN_REPEAT_INTERVAL_MS.toFloat()..ProtocolLabViewModel.MAX_REPEAT_INTERVAL_MS.toFloat(),
    )
    state.repeating?.let { Text(stringResource(R.string.protolab_repeating, it.toString()), color = MaterialTheme.colorScheme.error) }
    // 프리셋 저장은 여기 없다 — 저장 메뉴의 2단계 확인을 거친다.
    BedCommand.ALL.filterNot { it is BedCommand.SavePreset }.forEach { command ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(command.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { onAction(ProtocolLabAction.Send(command)) }) { Text(stringResource(R.string.protolab_send)) }
            if (command is BedCommand.Move || command is BedCommand.RecallPreset) {
                OutlinedButton(onClick = { onAction(ProtocolLabAction.StartRepeat(command)) }) {
                    Text(stringResource(R.string.protolab_repeat))
                }
            }
        }
    }
}

@Composable
private fun RawSection(
    rawInput: String,
    onAction: (ProtocolLabAction) -> Unit,
) {
    SectionTitle(stringResource(R.string.protolab_section_raw))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = rawInput,
            onValueChange = { onAction(ProtocolLabAction.RawInputChanged(it)) },
            placeholder = { Text(stringResource(R.string.protolab_raw_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onAction(ProtocolLabAction.SendRaw) }) { Text(stringResource(R.string.protolab_send_raw)) }
    }
}

@Composable
private fun ServicesSection(state: ProtocolLabUiState) {
    SectionTitle(stringResource(R.string.protolab_section_services))
    state.services.forEach { service ->
        Text(service.uuid.toString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        service.characteristics.forEach {
            Text("  ${it.uuid} ${it.properties.sorted()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SaveMenuDialog(
    onSelect: (PresetSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.protolab_save_menu_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.protolab_save_menu_warning))
                PresetSlot.entries.forEach { slot ->
                    OutlinedButton(onClick = { onSelect(slot) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.protolab_save_slot, slot.number))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.protolab_cancel)) } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider()
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Preview(showBackground = true)
@Composable
private fun ProtocolLabContentPreview() {
    LinakBedTheme {
        ProtocolLabContent(
            state =
                ProtocolLabUiState(
                    devices = listOf(ScannedDevice("AA:BB:CC:DD:EE:FF", "DPG1M", -60, advertisesControlService = true)),
                    selectedAddress = "AA:BB:CC:DD:EE:FF",
                    log = listOf("07:00:00.000 0e00 RecallPreset(slot=P1) → OK"),
                ),
            onAction = {},
            onCopy = {},
        )
    }
}
