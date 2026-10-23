package dev.linakbed.app.feature.diagnostics

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageInfo
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakePairingStore
import dev.linakbed.domain.remote.notification.NotificationChannelSettings
import dev.linakbed.smartthings.BedNotificationListener
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class SystemChecksTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val pairing = FakePairingStore(device = null)
    private val checks = AndroidSystemChecks(app, pairing)

    private fun grantEverything() {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        pairing.device = FakePairingStore.DEFAULT
        shadowOf(app.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(app, BedNotificationListener::class.java), true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = NotificationChannelSettings.SMARTTHINGS_PACKAGE })
    }

    @Test
    fun `nothing granted - everything but full screen intent is missing`() =
        runTest {
            shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)

            val result = checks.evaluate()

            assertThat(result.filterValues { !it }.keys).containsAtLeast(
                SystemCheck.BLUETOOTH_PERMISSION,
                SystemCheck.PAIRED,
                SystemCheck.NOTIFICATION_ACCESS,
                SystemCheck.POST_NOTIFICATIONS,
                SystemCheck.BATTERY_OPTIMIZATION,
                SystemCheck.SMARTTHINGS_INSTALLED,
            )
            assertThat(result.keys).containsExactlyElementsIn(SystemCheck.entries)
        }

    @Test
    fun `everything granted - nothing missing for remote`() =
        runTest {
            grantEverything()

            val result = checks.evaluate()

            // Robolectric에는 canUseFullScreenIntent를 허용할 방법이 없어 항상 false — 그 항목만 남는다.
            assertThat(result.missingForRemote(10.seconds)).containsExactly(SystemCheck.FULL_SCREEN_INTENT)
            assertThat(result.missingForRemote(Duration.ZERO)).isEmpty()
        }

    @Test
    fun `full screen intent is required only when the pre-execution delay is on`() {
        val allButFullScreen = SystemCheck.entries.associateWith { it != SystemCheck.FULL_SCREEN_INTENT }

        assertThat(allButFullScreen.missingForRemote(10.seconds)).containsExactly(SystemCheck.FULL_SCREEN_INTENT)
        assertThat(allButFullScreen.missingForRemote(Duration.ZERO)).isEmpty()
    }

    @Test
    fun `missing keeps the guidance order and treats unknown as missing`() {
        assertThat(emptyMap<SystemCheck, Boolean>().missingForRemote(Duration.ZERO))
            .containsExactlyElementsIn(SystemCheck.entries - SystemCheck.FULL_SCREEN_INTENT)
            .inOrder()
    }

    @Test
    fun `fix intents open the matching system screens`() {
        assertThat(AndroidSystemChecks.fixIntent(app, SystemCheck.PAIRED)).isNull()
        assertThat(AndroidSystemChecks.fixIntent(app, SystemCheck.POST_NOTIFICATIONS)).isNull()
        val access = AndroidSystemChecks.fixIntent(app, SystemCheck.NOTIFICATION_ACCESS)!!
        assertThat(access.action).isEqualTo(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        assertThat(access.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME)).contains("BedNotificationListener")
        assertThat(AndroidSystemChecks.fixIntent(app, SystemCheck.BATTERY_OPTIMIZATION)!!.action)
            .isEqualTo(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        assertThat(AndroidSystemChecks.fixIntent(app, SystemCheck.FULL_SCREEN_INTENT)!!.action)
            .isEqualTo(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
    }
}
