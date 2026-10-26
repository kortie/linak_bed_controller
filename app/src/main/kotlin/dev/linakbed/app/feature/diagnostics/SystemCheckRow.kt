package dev.linakbed.app.feature.diagnostics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R

@StringRes
fun SystemCheck.labelRes(): Int =
    when (this) {
        SystemCheck.BLUETOOTH_PERMISSION -> R.string.check_bluetooth
        SystemCheck.PAIRED -> R.string.check_paired
        SystemCheck.POST_NOTIFICATIONS -> R.string.check_post_notifications
        SystemCheck.FULL_SCREEN_INTENT -> R.string.check_full_screen
    }

/** 조건 하나: 이름 · 상태 · (모자라면) 해결 버튼. */
@Composable
fun SystemCheckRow(
    check: SystemCheck,
    ok: Boolean?,
    onFix: (SystemCheck) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(check.labelRes()), modifier = Modifier.weight(1f))
        if (ok == true) {
            Text(stringResource(R.string.diag_ok), color = MaterialTheme.colorScheme.primary)
        } else {
            Text(stringResource(R.string.diag_needed), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { onFix(check) }) { Text(stringResource(R.string.remote_fix)) }
        }
    }
}
