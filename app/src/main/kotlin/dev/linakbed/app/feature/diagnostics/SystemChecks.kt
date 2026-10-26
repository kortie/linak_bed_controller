package dev.linakbed.app.feature.diagnostics

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import dev.linakbed.domain.port.PairingStore

/**
 * 앱이 제대로 동작하는 데 필요한 시스템 조건 (진단 화면·모드 및 루틴 안내 공용).
 * S14에서 SmartThings 연동을 지우며 알림 접근·SmartThings 설치·절전 예외 조건도 없앴다 (ADR-013).
 */
enum class SystemCheck {
    BLUETOOTH_PERMISSION,
    PAIRED,
    POST_NOTIFICATIONS,
    FULL_SCREEN_INTENT,
}

fun interface SystemChecks {
    suspend fun evaluate(): Map<SystemCheck, Boolean>
}

class AndroidSystemChecks(
    private val context: Context,
    private val pairingStore: PairingStore,
) : SystemChecks {
    override suspend fun evaluate(): Map<SystemCheck, Boolean> {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return mapOf(
            SystemCheck.BLUETOOTH_PERMISSION to granted(Manifest.permission.BLUETOOTH_CONNECT),
            SystemCheck.PAIRED to pairingStore.all().isNotEmpty(),
            SystemCheck.POST_NOTIFICATIONS to granted(Manifest.permission.POST_NOTIFICATIONS),
            SystemCheck.FULL_SCREEN_INTENT to notifications.canUseFullScreenIntent(),
        )
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** 각 항목을 해결하는 시스템 설정 화면. null이면 앱 안에서 처리(페어링 화면·권한 요청). */
        fun fixIntent(
            context: Context,
            check: SystemCheck,
        ): Intent? {
            val packageUri = Uri.fromParts("package", context.packageName, null)
            return when (check) {
                SystemCheck.PAIRED, SystemCheck.POST_NOTIFICATIONS -> null
                SystemCheck.BLUETOOTH_PERMISSION -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                SystemCheck.FULL_SCREEN_INTENT -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
            }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
