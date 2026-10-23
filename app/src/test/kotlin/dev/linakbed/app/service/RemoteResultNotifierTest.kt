package dev.linakbed.app.service

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.fake.FakeRemoteSettingsStore
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult
import dev.linakbed.domain.remote.RemoteSettings
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant

/** 02 문서 Part B §4: 실패는 항상, 성공은 설정에 따라, 거절은 알리지 않는다. */
@RunWith(AndroidJUnit4::class)
class RemoteResultNotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val settings = FakeRemoteSettingsStore(RemoteSettings(enabled = true, notifyOnSuccess = false))
    private val notifier = RemoteResultNotifier(BedNotifications(app), settings)
    private val request =
        RemoteRequest("req-1", RemoteChannel.SMARTTHINGS_NOTIFICATION, RemoteCommand.Preset(PresetSlot.P2), Instant.EPOCH, null)

    @Before
    fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted() = shadowOf(manager).allNotifications

    @Test
    fun `failure posts an alert naming the channel and reason`() =
        runTest {
            notifier.onOutcome(request, RemoteOutcome("req-1", RemoteResult.Failed(BedError.ConnectTimeout)))

            val text =
                posted()
                    .single()
                    .extras
                    .getCharSequence("android.text")
                    .toString()
            assertThat(text).contains("SmartThings")
            assertThat(posted().single().channelId).isEqualTo(BedNotifications.CHANNEL_ALERTS)
        }

    @Test
    fun `success is silent unless enabled`() =
        runTest {
            notifier.onOutcome(request, RemoteOutcome("req-1", RemoteResult.Executed))
            assertThat(posted()).isEmpty()

            settings.current.value = settings.current.value.copy(notifyOnSuccess = true)
            notifier.onOutcome(request, RemoteOutcome("req-1", RemoteResult.Executed))
            assertThat(posted()).hasSize(1)
        }

    @Test
    fun `rejections are never shown`() =
        runTest {
            notifier.onOutcome(request, RemoteOutcome("req-1", RemoteResult.Rejected(RejectReason.STALE)))
            assertThat(posted()).isEmpty()
        }

    @Test
    fun `without POST_NOTIFICATIONS nothing is posted and nothing crashes`() =
        runTest {
            shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            notifier.onOutcome(request, RemoteOutcome("req-1", RemoteResult.Failed(BedError.ConnectTimeout)))
            assertThat(posted()).isEmpty()
        }
}
