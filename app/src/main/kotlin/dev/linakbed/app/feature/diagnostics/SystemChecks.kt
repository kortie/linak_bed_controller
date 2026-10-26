package dev.linakbed.app.feature.diagnostics

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import dev.linakbed.domain.port.PairingStore
import dev.linakbed.domain.remote.notification.NotificationChannelSettings
import dev.linakbed.smartthings.BedNotificationListener
import kotlin.time.Duration

/** 원격 연동에 필요한 시스템 조건 (S07). 진단 화면과 연동 스위치가 같은 판정을 쓴다. */
enum class SystemCheck {
    BLUETOOTH_PERMISSION,
    PAIRED,
    NOTIFICATION_ACCESS,
    POST_NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
    FULL_SCREEN_INTENT,
    SMARTTHINGS_INSTALLED,
}

fun interface SystemChecks {
    suspend fun evaluate(): Map<SystemCheck, Boolean>
}

/** 원격 연동을 켜기 전에 갖춰야 할 것. 전체 화면 알림은 실행 전 대기(S9)가 켜져 있을 때만 필수. */
fun Map<SystemCheck, Boolean>.missingForRemote(preExecutionDelay: Duration): List<SystemCheck> =
    SystemCheck.entries.filter { check ->
        val required = check != SystemCheck.FULL_SCREEN_INTENT || preExecutionDelay.isPositive()
        required && this[check] != true
    }

class AndroidSystemChecks(
    private val context: Context,
    private val pairingStore: PairingStore,
) : SystemChecks {
    override suspend fun evaluate(): Map<SystemCheck, Boolean> {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return mapOf(
            SystemCheck.BLUETOOTH_PERMISSION to granted(Manifest.permission.BLUETOOTH_CONNECT),
            SystemCheck.PAIRED to pairingStore.all().isNotEmpty(),
            SystemCheck.NOTIFICATION_ACCESS to notifications.isNotificationListenerAccessGranted(listener()),
            SystemCheck.POST_NOTIFICATIONS to granted(Manifest.permission.POST_NOTIFICATIONS),
            SystemCheck.BATTERY_OPTIMIZATION to power.isIgnoringBatteryOptimizations(context.packageName),
            SystemCheck.FULL_SCREEN_INTENT to notifications.canUseFullScreenIntent(),
            SystemCheck.SMARTTHINGS_INSTALLED to installed(NotificationChannelSettings.SMARTTHINGS_PACKAGE),
        )
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun installed(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (
            @Suppress("SwallowedException") missing: PackageManager.NameNotFoundException,
        ) {
            false
        }

    private fun listener() = ComponentName(context, BedNotificationListener::class.java)

    companion object {
        /**
         * 각 항목을 해결하는 시스템 설정 화면. null이면 앱 안에서 처리(페어링 화면·권한 요청).
         * 절전 예외는 Play 정책상 제한되는 직접 요청(`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) 대신 설정 목록을 연다.
         * CDM으로 연결된 앱은 `REQUEST_COMPANION_RUN_IN_BACKGROUND`로 이미 예외를 받는 경우가 많다 (ADR-005).
         */
        fun fixIntent(
            context: Context,
            check: SystemCheck,
        ): Intent? {
            val packageUri = Uri.fromParts("package", context.packageName, null)
            return when (check) {
                SystemCheck.PAIRED, SystemCheck.POST_NOTIFICATIONS -> null
                SystemCheck.BLUETOOTH_PERMISSION -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                SystemCheck.NOTIFICATION_ACCESS ->
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            ComponentName(context, BedNotificationListener::class.java).flattenToString(),
                        )
                SystemCheck.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                SystemCheck.FULL_SCREEN_INTENT -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
                SystemCheck.SMARTTHINGS_INSTALLED ->
                    Intent(Intent.ACTION_VIEW, "market://details?id=${NotificationChannelSettings.SMARTTHINGS_PACKAGE}".toUri())
            }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
