package dev.linakbed.app.feature.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R

/** S6: 하드웨어 프리셋을 덮어쓰는 2단계 확인. 체크박스를 켜야 저장 버튼이 활성화된다. */
@Composable
fun SavePresetDialog(
    name: String,
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.preset_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.preset_save_message, name), style = MaterialTheme.typography.bodyLarge)
                // 문구까지 포함한 행 전체가 토글 영역 (터치 영역을 넓힌다).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = confirmed, role = Role.Checkbox, onValueChange = onConfirmedChange),
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = null)
                    Text(stringResource(R.string.preset_save_check))
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = confirmed) { Text(stringResource(R.string.preset_save_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) } },
    )
}
