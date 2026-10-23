package dev.linakbed.smartthings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakeNotificationChannelSettingsStore
import dev.linakbed.domain.fake.FakePresetNameStore
import dev.linakbed.domain.fake.FakeRemoteCommandHandler
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult
import dev.linakbed.domain.remote.notification.NotificationChannel
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NotificationBridgeTest {
    private val settings = FakeNotificationChannelSettingsStore()
    private val handler = FakeRemoteCommandHandler()
    private val channel = NotificationChannel(settings, FakePresetNameStore(), handler)
    private val dispatched = mutableListOf<RemoteRequest>()
    private var dispatchResult: RemoteResult = RemoteResult.Executed
    private val cancelled = mutableListOf<String>()
    private var extractCalls = 0

    private fun TestScope.bridge(): NotificationBridge =
        NotificationBridge(
            channel = channel,
            dispatcher = { request ->
                dispatched += request
                RemoteOutcome(request.requestId, dispatchResult)
            },
            settingsStore = settings,
            scope = backgroundScope,
            logger = FakeBedLogger(),
            extract = {
                extractCalls++
                NotificationTextExtractor.extract(it)
            },
        ).also {
            runCurrent()
            it.onListenerConnected(nowMillis = 5_000)
        }

    private fun TestScope.post(
        bridge: NotificationBridge,
        sbn: android.service.notification.StatusBarNotification,
    ) {
        bridge.onPosted(sbn) { cancelled += it }
        runCurrent()
    }

    @Test
    fun `a SmartThings command is dispatched and its notification removed once executed`() =
        runTest {
            val bridge = bridge()
            val sbn = statusBarNotification(text = "#BED preset 3")

            post(bridge, sbn)

            assertThat(dispatched.single().command).isEqualTo(RemoteCommand.Preset(PresetSlot.P3))
            assertThat(cancelled).containsExactly(sbn.key)
        }

    @Test
    fun `S8 other apps' notifications are dropped before reading any text`() =
        runTest {
            val bridge = bridge()

            post(bridge, statusBarNotification(packageName = "com.example.chat", text = "#BED preset 1"))

            assertThat(extractCalls).isEqualTo(0)
            assertThat(dispatched).isEmpty()
            assertThat(handler.rejected).isEmpty()
        }

    @Test
    fun `S8 notifications posted before the listener connected are ignored`() =
        runTest {
            val bridge = bridge()

            post(bridge, statusBarNotification(postTime = 4_999))

            assertThat(extractCalls).isEqualTo(0)
            assertThat(dispatched).isEmpty()
        }

    @Test
    fun `group summaries are ignored`() =
        runTest {
            post(bridge(), statusBarNotification(groupSummary = true))

            assertThat(dispatched).isEmpty()
        }

    @Test
    fun `non-commands and channel rejections never reach the dispatcher`() =
        runTest {
            val bridge = bridge()

            post(bridge, statusBarNotification(text = "현관문이 열렸습니다", id = 1))
            post(bridge, statusBarNotification(text = "#BED preset 9", id = 2))

            assertThat(dispatched).isEmpty()
            assertThat(handler.rejected.single().reason).isEqualTo(RejectReason.INVALID)
            assertThat(cancelled).isEmpty()
        }

    @Test
    fun `failed requests are removed too, but rejected ones stay visible`() =
        runTest {
            val bridge = bridge()
            dispatchResult = RemoteResult.Failed(BedError.ConnectTimeout)
            post(bridge, statusBarNotification(id = 1))
            assertThat(cancelled).hasSize(1)

            dispatchResult = RemoteResult.Rejected(RejectReason.STALE)
            post(bridge, statusBarNotification(id = 2))
            assertThat(cancelled).hasSize(1)
        }

    @Test
    fun `notifications are kept when auto-dismiss is off`() =
        runTest {
            settings.current.value = settings.current.value.copy(dismissHandledNotification = false)
            val bridge = bridge()

            post(bridge, statusBarNotification())

            assertThat(dispatched).hasSize(1)
            assertThat(cancelled).isEmpty()
        }
}
