package dev.linakbed.domain.remote.notification

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.fake.FakeNotificationChannelSettingsStore
import dev.linakbed.domain.fake.FakePresetNameStore
import dev.linakbed.domain.fake.FakeRemoteCommandHandler
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteResult
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

/** 02 문서 Part B §3 채널 검사 순서와 RemoteRequest 매핑. */
class NotificationChannelTest {
    private val settings = FakeNotificationChannelSettingsStore()
    private val handler = FakeRemoteCommandHandler()
    private val channel = NotificationChannel(settings, FakePresetNameStore(), handler)
    private val posted = Instant.parse("2026-10-23T07:00:00Z")

    private fun notification(
        vararg texts: String,
        packageName: String = NotificationChannelSettings.SMARTTHINGS_PACKAGE,
        key: String = "0|com.samsung.android.oneconnect|1|null|10123",
        postedAt: Instant = posted,
        sentAt: Instant? = posted.minusSeconds(2),
    ) = IncomingNotification(packageName, key, postedAt, sentAt, texts.toList())

    private fun usePin(pin: String = "4821") {
        settings.current.value = settings.current.value.copy(pin = pin)
    }

    @Test
    fun `a command becomes a RemoteRequest with the notification times`() =
        runTest {
            val n = notification("SmartThings", "#BED preset 2")

            val result = channel.translate(n) as ChannelResult.Request

            assertThat(result.request.command).isEqualTo(RemoteCommand.Preset(PresetSlot.P2))
            assertThat(result.request.channel).isEqualTo(RemoteChannel.SMARTTHINGS_NOTIFICATION)
            assertThat(result.request.receivedAt).isEqualTo(posted)
            assertThat(result.request.sentAt).isEqualTo(posted.minusSeconds(2))
            assertThat(result.request.requestId).isEqualTo(NotificationChannel.requestIdOf(n))
            assertThat(result.request.requestId).hasLength(16)
            assertThat(handler.rejected).isEmpty()
        }

    @Test
    fun `not a command is ignored and not recorded`() =
        runTest {
            assertThat(channel.translate(notification("현관문이 열렸습니다"))).isEqualTo(ChannelResult.Ignore)
            assertThat(handler.rejected).isEmpty()
        }

    @Test
    fun `S8 notifications from other packages are rejected first`() =
        runTest {
            val result = channel.translate(notification("#BED preset 1", packageName = "com.example.spoof"))

            assertThat(result).isEqualTo(ChannelResult.Rejected(RejectReason.UNTRUSTED_SOURCE))
            assertThat(handler.rejected.single().reason).isEqualTo(RejectReason.UNTRUSTED_SOURCE)
            assertThat(handler.rejected.single().detail).isNull()
        }

    @Test
    fun `S8 correct PIN is accepted`() =
        runTest {
            usePin()

            val result = channel.translate(notification("#BED 4821 preset 3")) as ChannelResult.Request

            assertThat(result.request.command).isEqualTo(RemoteCommand.Preset(PresetSlot.P3))
        }

    @Test
    fun `S8 missing PIN is AUTH_FAILED and the detail never contains the PIN`() =
        runTest {
            usePin()

            assertThat(channel.translate(notification("#BED preset 3"))).isEqualTo(ChannelResult.Rejected(RejectReason.AUTH_FAILED))
        }

    @Test
    fun `S8 wrong PIN wins over a malformed command`() =
        runTest {
            usePin()

            assertThat(channel.translate(notification("#BED 0000 move up"))).isEqualTo(ChannelResult.Rejected(RejectReason.AUTH_FAILED))
        }

    @Test
    fun `S8 PIN is masked in recorded details`() =
        runTest {
            usePin()

            channel.translate(notification("#BED 4821 move up"))

            val rejection = handler.rejected.single()
            assertThat(rejection.reason).isEqualTo(RejectReason.INVALID)
            assertThat(rejection.detail).contains("#BED **** move up")
            assertThat(rejection.detail).doesNotContain("4821")
        }

    @Test
    fun `malformed command is INVALID with the reason in the detail`() =
        runTest {
            assertThat(channel.translate(notification("#BED preset 7"))).isEqualTo(ChannelResult.Rejected(RejectReason.INVALID))
            assertThat(handler.rejected.single().detail).contains("unknown preset: 7")
        }

    @Test
    fun `without a PIN there is no PIN slot`() =
        runTest {
            assertThat(channel.translate(notification("#BED 4821 preset 3"))).isEqualTo(ChannelResult.Rejected(RejectReason.INVALID))
        }

    @Test
    fun `requestId is stable for the same notification and differs otherwise`() {
        val base = notification("#BED preset 2")

        assertThat(NotificationChannel.requestIdOf(notification("#BED  PRESET 2"))).isEqualTo(NotificationChannel.requestIdOf(base))
        assertThat(NotificationChannel.requestIdOf(notification("#BED preset 3"))).isNotEqualTo(NotificationChannel.requestIdOf(base))
        assertThat(NotificationChannel.requestIdOf(notification("#BED preset 2", key = "other")))
            .isNotEqualTo(NotificationChannel.requestIdOf(base))
        assertThat(NotificationChannel.requestIdOf(notification("#BED preset 2", postedAt = posted.plusSeconds(60))))
            .isNotEqualTo(NotificationChannel.requestIdOf(base))
    }

    @Test
    fun `dismiss source - executed, failed and user cancel only, and only when enabled`() {
        val on = NotificationChannelSettings()
        val off = NotificationChannelSettings(dismissHandledNotification = false)

        fun outcome(result: RemoteResult) = RemoteOutcome("id", result)

        assertThat(outcome(RemoteResult.Executed).shouldDismissSource(on)).isTrue()
        assertThat(outcome(RemoteResult.Failed(BedError.ConnectTimeout)).shouldDismissSource(on)).isTrue()
        assertThat(outcome(RemoteResult.Rejected(RejectReason.CANCELLED_BY_USER)).shouldDismissSource(on)).isTrue()
        assertThat(outcome(RemoteResult.Rejected(RejectReason.STALE)).shouldDismissSource(on)).isFalse()
        assertThat(outcome(RemoteResult.Rejected(RejectReason.SUPERSEDED)).shouldDismissSource(on)).isFalse()
        assertThat(outcome(RemoteResult.Executed).shouldDismissSource(off)).isFalse()
    }
}
