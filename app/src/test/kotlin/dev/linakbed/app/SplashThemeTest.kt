package dev.linakbed.app

import android.app.Application
import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.navigation.RootState
import org.junit.Test
import org.junit.runner.RunWith

/** 첫 화면은 앱 아이콘과 같은 네이비 배경 + 침대 그림의 시스템 스플래시 화면으로 시작한다. */
@RunWith(AndroidJUnit4::class)
class SplashThemeTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun mainActivityTheme() =
        app.resources.newTheme().apply {
            val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
            applyStyle(info.themeResource, true)
        }

    @Test
    fun `main activity splash uses brand navy with the bed drawing`() {
        val attrs =
            mainActivityTheme().obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.windowSplashScreenBackground,
                    android.R.attr.windowSplashScreenAnimatedIcon,
                    android.R.attr.windowBackground,
                ),
            )
        try {
            val navy = app.getColor(R.color.brand_navy)
            assertThat(attrs.getColor(0, 0)).isEqualTo(navy)
            assertThat(attrs.getResourceId(1, 0)).isEqualTo(R.drawable.splash_icon)
            assertThat((attrs.getDrawable(2) as ColorDrawable).color).isEqualTo(navy)
        } finally {
            attrs.recycle()
        }
    }

    @Test
    fun `splash stays at least two seconds on fresh launch and until the first screen is known`() {
        val min = MainActivity.SPLASH_MIN_MS
        assertThat(min).isEqualTo(2_000L)
        assertThat(MainActivity.shouldKeepSplash(RootState.READY, 1_999, min)).isTrue()
        assertThat(MainActivity.shouldKeepSplash(RootState.READY, 2_000, min)).isFalse()
        assertThat(MainActivity.shouldKeepSplash(RootState.ONBOARDING, 2_500, min)).isFalse()
        assertThat(MainActivity.shouldKeepSplash(RootState.LOADING, 5_000, min)).isTrue()
        // 다시 만들어질 땐(최소 시간 0) 화면만 정해지면 바로 보인다
        assertThat(MainActivity.shouldKeepSplash(RootState.READY, 0, 0)).isFalse()
    }
}
