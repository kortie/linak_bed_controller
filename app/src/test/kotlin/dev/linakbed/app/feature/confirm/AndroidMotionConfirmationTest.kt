package dev.linakbed.app.feature.confirm

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.service.BedNotifications
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** ADR-008: 전체 화면 알림 + 취소 액션 하나, 어느 경로의 취소든 awaitCancel이 돌아오고 dismiss는 알림을 지운다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidMotionConfirmationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    /** 수신기는 @AndroidEntryPoint라 onReceive에서 앱 그래프의 레지스트리를 주입받는다 — 같은 것을 쓴다. */
    private val receiver = CancelMotionReceiver().also { it.onReceive(app, Intent()) }
    private val registry = receiver.registry
    private val clock = FakeClock()
    private val confirmation = AndroidMotionConfirmation(app, registry, BedNotifications(app), clock)
    private val request =
        RemoteRequest("req-1", RemoteChannel.SMARTTHINGS_NOTIFICATION, RemoteCommand.Preset(PresetSlot.P2), Instant.EPOCH, null)

    @Before
    fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `posts a full-screen notification with a single cancel action`() {
        confirmation.show(request, 10.seconds)

        val notification = shadowOf(manager).getNotification(BedNotifications.ID_CONFIRM)
        assertThat(notification.fullScreenIntent).isNotNull()
        assertThat(notification.channelId).isEqualTo(BedNotifications.CHANNEL_CONFIRM)
        assertThat(notification.actions).hasLength(1)
        assertThat(
            notification.actions
                .single()
                .title
                .toString(),
        ).isEqualTo("취소")
        assertThat(shadowOf(notification.fullScreenIntent).savedIntent.component?.className)
            .isEqualTo(PendingMotionActivity::class.java.name)
    }

    @Test
    fun `registers the session with the deadline`() {
        confirmation.show(request, 10.seconds)

        val session = registry.current.value!!
        assertThat(session.requestId).isEqualTo("req-1")
        assertThat(session.slot).isEqualTo(PresetSlot.P2)
        assertThat(session.deadline).isEqualTo(clock.now().plusSeconds(10))
    }

    @Test
    fun `the notification cancel action makes awaitCancel return`() =
        runTest {
            val pending = confirmation.show(request, 10.seconds)
            val waiting = async { pending.awaitCancel() }
            runCurrent()
            assertThat(waiting.isCompleted).isFalse()

            val cancelIntent =
                shadowOf(
                    shadowOf(manager)
                        .getNotification(BedNotifications.ID_CONFIRM)
                        .actions
                        .single()
                        .actionIntent,
                ).savedIntent
            receiver.onReceive(app, cancelIntent)
            runCurrent()

            assertThat(waiting.isCompleted).isTrue()
        }

    @Test
    fun `dismiss removes the notification and ends the session`() {
        val pending = confirmation.show(request, 10.seconds)

        pending.dismiss()

        assertThat(shadowOf(manager).getNotification(BedNotifications.ID_CONFIRM)).isNull()
        assertThat(registry.current.value).isNull()
    }
}
