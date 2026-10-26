package dev.linakbed.app.feature.diagnostics

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** 조건 해결 동작을 돌려준다: 알림 표시 권한은 바로 요청, 페어링은 앱 안의 페어링 화면, 나머지는 해당 시스템 설정 화면. */
@Composable
fun rememberCheckFixer(
    onOpenPairing: () -> Unit,
    onResult: () -> Unit,
): (SystemCheck) -> Unit {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    return { check ->
        when (check) {
            SystemCheck.PAIRED -> onOpenPairing()
            SystemCheck.POST_NOTIFICATIONS -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> AndroidSystemChecks.fixIntent(context, check)?.let(context::startActivity)
        }
    }
}
