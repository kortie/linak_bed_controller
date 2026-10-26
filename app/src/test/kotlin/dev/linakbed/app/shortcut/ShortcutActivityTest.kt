package dev.linakbed.app.shortcut

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** 중계 화면은 아무것도 보여 주지 않고 바로 닫힌다. */
@RunWith(AndroidJUnit4::class)
class ShortcutActivityTest {
    @Test
    fun `the relay finishes immediately`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java)
                .setData(Uri.parse("bedcontrol://shortcut/preset/9"))

        ActivityScenario.launch<ShortcutActivity>(intent).use { scenario ->
            assertThat(scenario.state).isEqualTo(Lifecycle.State.DESTROYED)
        }
    }
}
