package dev.linakbed.app.feature.diagnostics

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakePairingStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SystemChecksTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val pairing = FakePairingStore(device = null)
    private val checks = AndroidSystemChecks(app, pairing)

    @Test
    fun `nothing granted - permissions and pairing are missing`() =
        runTest {
            shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)

            val result = checks.evaluate()

            assertThat(result.keys).containsExactlyElementsIn(SystemCheck.entries)
            assertThat(result[SystemCheck.BLUETOOTH_PERMISSION]).isFalse()
            assertThat(result[SystemCheck.PAIRED]).isFalse()
            assertThat(result[SystemCheck.POST_NOTIFICATIONS]).isFalse()
        }

    @Test
    fun `granted permissions and a paired bed are reported`() =
        runTest {
            shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
            pairing.device = FakePairingStore.DEFAULT

            val result = checks.evaluate()

            assertThat(result[SystemCheck.BLUETOOTH_PERMISSION]).isTrue()
            assertThat(result[SystemCheck.PAIRED]).isTrue()
            assertThat(result[SystemCheck.POST_NOTIFICATIONS]).isTrue()
        }

    @Test
    fun `fix intents open the matching system screens`() {
        assertThat(AndroidSystemChecks.fixIntent(app, SystemCheck.PAIRED)).isNull()
        assertThat(AndroidSystemChecks.fixIntent(app, SystemCheck.POST_NOTIFICATIONS)).isNull()
        assertThat(AndroidSystemChecks.fixIntent(app, SystemCheck.BLUETOOTH_PERMISSION)!!.action)
            .isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(AndroidSystemChecks.fixIntent(app, SystemCheck.FULL_SCREEN_INTENT)!!.action)
            .isEqualTo(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
    }

    @Test
    fun `S14 the app has no notification listener, so sideloading is not blocked for notification access`() {
        @Suppress("DEPRECATION") // Robolectric은 PackageInfoFlags 판을 지원하지 않는다
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS)

        assertThat(info.services.orEmpty().map { it.permission }).doesNotContain("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
        assertThat(info.requestedPermissions.orEmpty().toList())
            .containsNoneOf("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "android.permission.BIND_ACCESSIBILITY_SERVICE")
    }
}
