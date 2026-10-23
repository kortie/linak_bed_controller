package dev.linakbed.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.log.AndroidBedLogger
import dev.linakbed.domain.log.BedLogger
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** S00 완료 정의: 실제 Hilt 그래프로 앱이 뜨고, BedLogger가 주입되어 시작 로그를 남긴다. */
@RunWith(AndroidJUnit4::class)
class AppStartupTest {
    @Test
    fun `app injects the Android logger and writes a start line`() {
        val app = ApplicationProvider.getApplicationContext<LinakBedApp>()

        assertThat(app.logger).isInstanceOf(AndroidBedLogger::class.java)
        val startLines = ShadowLog.getLogsForTag("LinakBed/App").map { it.msg }
        assertThat(startLines).contains("started version=${BuildConfig.VERSION_NAME} debug=${BuildConfig.DEBUG}")
    }

    @Test
    fun `main activity launches`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity.isFinishing).isFalse()
            }
        }
    }

    @Test
    fun `logger is a singleton`() {
        val app = ApplicationProvider.getApplicationContext<LinakBedApp>()
        val logger: BedLogger = app.logger
        assertThat(logger).isSameInstanceAs(app.logger)
    }
}
