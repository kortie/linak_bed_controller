package dev.linakbed.app.feature.diagnostics

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.linakbed.app.R

/**
 * 조건 해결 동작을 돌려준다: 알림 표시 권한은 바로 요청, 알림 접근은 설명 다이얼로그를 먼저 띄운 뒤 시스템 설정,
 * 페어링은 앱 안의 페어링 화면, 나머지는 해당 시스템 설정 화면.
 */
@Composable
fun rememberCheckFixer(
    onOpenPairing: () -> Unit,
    onResult: () -> Unit,
): (SystemCheck) -> Unit {
    val context = LocalContext.current
    var explainAccess by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    if (explainAccess) {
        AlertDialog(
            onDismissRequest = { explainAccess = false },
            title = { Text(stringResource(R.string.access_dialog_title)) },
            text = { Text(stringResource(R.string.access_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    explainAccess = false
                    AndroidSystemChecks.fixIntent(context, SystemCheck.NOTIFICATION_ACCESS)?.let(context::startActivity)
                }) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = { TextButton(onClick = { explainAccess = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    return { check ->
        when (check) {
            SystemCheck.PAIRED -> onOpenPairing()
            SystemCheck.POST_NOTIFICATIONS -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            SystemCheck.NOTIFICATION_ACCESS -> explainAccess = true
            else -> AndroidSystemChecks.fixIntent(context, check)?.let(context::startActivity)
        }
    }
}
