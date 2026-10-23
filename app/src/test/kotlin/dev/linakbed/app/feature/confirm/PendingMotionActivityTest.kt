package dev.linakbed.app.feature.confirm

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.protocol.PresetSlot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant

/** 잠금 화면 위에서 켜지고, 볼륨 키로 취소된다 (S9). */
@RunWith(AndroidJUnit4::class)
class PendingMotionActivityTest {
    private fun withActivity(block: (PendingMotionActivity, PendingMotionRegistry.Session) -> Unit) {
        ActivityScenario.launch(PendingMotionActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val session = PendingMotionRegistry.Session("req-1", PresetSlot.P1, Instant.now().plusSeconds(10))
                activity.registry.begin(session)
                block(activity, session)
                activity.registry.end("req-1")
            }
        }
    }

    @Test
    fun `shows over the lock screen and turns the screen on`() {
        withActivity { activity, _ ->
            assertThat(shadowOf(activity).showWhenLocked).isTrue()
            assertThat(shadowOf(activity).turnScreenOn).isTrue()
        }
    }

    @Test
    fun `volume up and down cancel and are consumed`() {
        listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN).forEach { key ->
            withActivity { activity, session ->
                val consumed = activity.onKeyDown(key, KeyEvent(KeyEvent.ACTION_DOWN, key))

                assertThat(consumed).isTrue()
                assertThat(session.cancelled.isCompleted).isTrue()
            }
        }
    }

    @Test
    fun `other keys do not cancel`() {
        withActivity { activity, session ->
            activity.onKeyDown(KeyEvent.KEYCODE_A, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))

            assertThat(session.cancelled.isCompleted).isFalse()
        }
    }
}
