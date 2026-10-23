package dev.linakbed.app.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** 서비스는 시작 즉시 포그라운드(connectedDevice)로 오르고, 할 일이 없으면 스스로 멈춘다. */
@RunWith(AndroidJUnit4::class)
class BedForegroundServiceTest {
    private fun start(intent: Intent): BedForegroundService {
        val controller = Robolectric.buildService(BedForegroundService::class.java, intent).create()
        controller.startCommand(0, 1)
        return controller.get()
    }

    private fun awaitStopped(service: BedForegroundService) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!shadowOf(service).isStoppedBySelf && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
    }

    @Test
    fun `starts in the foreground with the service notification`() {
        val service = start(ServiceTask.runRemote(ApplicationProvider.getApplicationContext(), "unknown"))

        val shadow = shadowOf(service)
        assertThat(shadow.lastForegroundNotificationId).isEqualTo(BedNotifications.ID_SERVICE)
        assertThat(shadow.lastForegroundNotification.channelId).isEqualTo(BedNotifications.CHANNEL_SERVICE)
    }

    @Test
    fun `stops itself when the request is not pending`() {
        val service = start(ServiceTask.runRemote(ApplicationProvider.getApplicationContext(), "unknown"))

        awaitStopped(service)

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `stops itself when started without a request`() {
        val service = start(Intent(ApplicationProvider.getApplicationContext(), BedForegroundService::class.java))

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
