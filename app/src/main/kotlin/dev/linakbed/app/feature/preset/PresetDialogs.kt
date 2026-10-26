package dev.linakbed.app.feature.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.app.ui.message
import dev.linakbed.domain.preset.PresetName
import dev.linakbed.protocol.PresetSlot

/** 프리셋 저장 확인(S6)·이름 변경 다이얼로그. 홈 화면에서 쓴다. */
@Composable
fun PresetDialogs(
    state: PresetUiState,
    onAction: (PresetAction) -> Unit,
) {
    state.pendingSave?.let { slot ->
        SavePresetDialog(
            name = presetLabel(state, slot),
            confirmed = state.saveConfirmed,
            onConfirmedChange = { onAction(PresetAction.SaveConfirmedChanged(it)) },
            onSave = { onAction(PresetAction.ConfirmSave) },
            onCancel = { onAction(PresetAction.CancelSave) },
        )
    }
    state.renaming?.let { RenameDialog(state, onAction) }
}

@Composable
private fun RenameDialog(
    state: PresetUiState,
    onAction: (PresetAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(PresetAction.CancelRename) },
        title = { Text(stringResource(R.string.preset_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.renameText,
                    onValueChange = { onAction(PresetAction.RenameTextChanged(it)) },
                    singleLine = true,
                    isError = state.renameError != null,
                    supportingText = state.renameError?.let { reason -> { Text(renameErrorText(reason, state)) } },
                )
                // 이름은 SmartThings 루틴 문구(`#BED preset 이름`)에도 쓰인다.
                Text(stringResource(R.string.preset_rename_routine_warning), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(PresetAction.ConfirmRename) },
            ) { Text(stringResource(R.string.preset_rename_confirm)) }
        },
        dismissButton = { TextButton(onClick = { onAction(PresetAction.CancelRename) }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** 화면에 보이는 프리셋 이름. 4번은 항상 "Custom" (S12). */
@Composable
fun presetLabel(
    state: PresetUiState,
    slot: PresetSlot,
): String =
    if (slot == PresetSlot.P4) {
        stringResource(R.string.preset_custom)
    } else {
        state.names[slot] ?: stringResource(R.string.preset_fallback_name, slot.number)
    }

@Composable
private fun renameErrorText(
    reason: PresetName.Reason,
    state: PresetUiState,
): String =
    when (reason) {
        PresetName.Reason.Blank -> stringResource(R.string.preset_name_blank)
        PresetName.Reason.TooLong -> stringResource(R.string.preset_name_too_long, PresetName.MAX_LENGTH)
        PresetName.Reason.LineBreak -> stringResource(R.string.preset_name_line_break)
        PresetName.Reason.DigitsOnly -> stringResource(R.string.preset_name_digits_only)
        is PresetName.Reason.Duplicate -> stringResource(R.string.preset_name_duplicate, presetLabel(state, reason.slot))
    }

/** 프리셋 결과 메시지 (스낵바). */
@Composable
fun presetMessage(
    message: PresetMessage,
    names: Map<PresetSlot, String>,
): String {
    val custom = stringResource(R.string.preset_custom)

    fun label(slot: PresetSlot) = if (slot == PresetSlot.P4) custom else names[slot] ?: slot.name
    return when (message) {
        is PresetMessage.Arrived -> stringResource(R.string.preset_arrived, label(message.slot))
        is PresetMessage.Saved -> stringResource(R.string.preset_saved, label(message.slot))
        is PresetMessage.Failed -> message.error.message()
    }
}
