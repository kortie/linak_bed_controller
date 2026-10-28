package dev.linakbed.app.feature.about

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** S16: Play 결제는 BILLING 권한만 더하고, 앱은 여전히 인터넷 권한이 없다 (ADR-014, 개인정보 안내). */
@RunWith(AndroidJUnit4::class)
class BillingPermissionsTest {
    @Test
    fun `the merged manifest asks for Play billing but not the internet`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val requested =
            app.packageManager
                .getPackageInfo(
                    app.packageName,
                    PackageManager.GET_PERMISSIONS,
                ).requestedPermissions
                .orEmpty()
                .toList()

        assertThat(requested).contains("com.android.vending.BILLING")
        assertThat(requested).doesNotContain(Manifest.permission.INTERNET)
    }
}
