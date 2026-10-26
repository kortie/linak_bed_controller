package dev.linakbed.domain.remote

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.protocol.PresetSlot
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** 02 문서 A3 공통 수용 정책 (S8). 알림 채널 타입을 쓰지 않는다 — 향후 채널도 같은 테스트로 보호된다. */
class RemoteCommandPolicyTest {
    private val clock = FakeClock()
    private var settings = RemoteSettings(enabled = true)
    private val policy = RemoteCommandPolicy({ settings }, clock)
    private var counter = 0

    private fun request(
        id: String = "req-${++counter}",
        receivedAt: Instant = clock.now(),
        sentAt: Instant? = null,
        command: RemoteCommand = RemoteCommand.Preset(PresetSlot.P1),
        channel: RemoteChannel = RemoteChannel.SMARTTHINGS_NOTIFICATION,
    ) = RemoteRequest(id, channel, command, receivedAt, sentAt)

    private fun Instant.minus(seconds: Long): Instant = minus(seconds.seconds.toJavaDuration())

    @Test
    fun `S8 accepts a fresh request when enabled`() {
        assertThat(policy.evaluate(request())).isEqualTo(PolicyDecision.Accept)
    }

    @Test
    fun `S8 disabled feature rejects first, before anything else`() {
        settings = settings.copy(enabled = false)

        assertThat(policy.evaluate(request(receivedAt = clock.now().minus(600)))).isEqualTo(PolicyDecision.Reject(RejectReason.DISABLED))
    }

    @Test
    fun `S8 each channel has its own switch`() {
        settings = RemoteSettings(enabled = false, appShortcutsEnabled = true)
        assertThat(policy.evaluate(request(channel = RemoteChannel.APP_SHORTCUT))).isEqualTo(PolicyDecision.Accept)
        assertThat(policy.evaluate(request())).isEqualTo(PolicyDecision.Reject(RejectReason.DISABLED))

        settings = RemoteSettings(enabled = true, appShortcutsEnabled = false)
        assertThat(policy.evaluate(request(channel = RemoteChannel.APP_SHORTCUT))).isEqualTo(PolicyDecision.Reject(RejectReason.DISABLED))
        assertThat(policy.evaluate(request())).isEqualTo(PolicyDecision.Accept)
    }

    @Test
    fun `S8 the rate limit is shared across channels`() {
        settings = RemoteSettings(enabled = true, appShortcutsEnabled = true)
        repeat(3) { policy.evaluate(request()) }
        repeat(3) { policy.evaluate(request(channel = RemoteChannel.APP_SHORTCUT)) }

        assertThat(
            policy.evaluate(request(channel = RemoteChannel.APP_SHORTCUT)),
        ).isEqualTo(PolicyDecision.Reject(RejectReason.RATE_LIMITED))
    }

    @Test
    fun `app shortcuts are on by default, the notification channel is off`() {
        assertThat(RemoteSettings().appShortcutsEnabled).isTrue()
        assertThat(RemoteSettings().enabled).isFalse()
    }

    @Test
    fun `S8 stale requests are rejected - exactly 120s is still fresh`() {
        assertThat(policy.evaluate(request(receivedAt = clock.now().minus(120)))).isEqualTo(PolicyDecision.Accept)
        assertThat(policy.evaluate(request(receivedAt = clock.now().minus(121)))).isEqualTo(PolicyDecision.Reject(RejectReason.STALE))
    }

    @Test
    fun `S8 freshness uses the sent time when known`() {
        val sent = clock.now().minus(300)

        assertThat(policy.evaluate(request(receivedAt = clock.now(), sentAt = sent))).isEqualTo(PolicyDecision.Reject(RejectReason.STALE))
    }

    @Test
    fun `S8 a sent time in the future falls back to the received time`() {
        val received = clock.now().minus(200)
        val future = clock.now().plusSeconds(3600)

        assertThat(policy.evaluate(request(receivedAt = received, sentAt = future))).isEqualTo(PolicyDecision.Reject(RejectReason.STALE))
        assertThat(policy.evaluate(request(receivedAt = clock.now(), sentAt = future))).isEqualTo(PolicyDecision.Accept)
    }

    @Test
    fun `S8 the same requestId is rejected for 10 minutes`() {
        assertThat(policy.evaluate(request(id = "same"))).isEqualTo(PolicyDecision.Accept)

        clock.advance(9.minutes)
        assertThat(policy.evaluate(request(id = "same"))).isEqualTo(PolicyDecision.Reject(RejectReason.DUPLICATE))

        clock.advance(1.minutes)
        assertThat(policy.evaluate(request(id = "same"))).isEqualTo(PolicyDecision.Accept)
    }

    @Test
    fun `S8 more than 6 accepted requests in a minute are rate limited, released after a minute`() {
        repeat(6) { assertThat(policy.evaluate(request())).isEqualTo(PolicyDecision.Accept) }

        assertThat(policy.evaluate(request())).isEqualTo(PolicyDecision.Reject(RejectReason.RATE_LIMITED))

        clock.advance(1.minutes)
        assertThat(policy.evaluate(request())).isEqualTo(PolicyDecision.Accept)
    }

    @Test
    fun `S8 rejected requests do not count toward duplicates or the rate limit`() {
        settings = settings.copy(enabled = false)
        repeat(10) { policy.evaluate(request(id = "x")) }
        settings = settings.copy(enabled = true)

        assertThat(policy.evaluate(request(id = "x"))).isEqualTo(PolicyDecision.Accept)
    }

    @Test
    fun `S8 check order - stale wins over duplicate`() {
        policy.evaluate(request(id = "same"))

        assertThat(policy.evaluate(request(id = "same", receivedAt = clock.now().minus(500))))
            .isEqualTo(PolicyDecision.Reject(RejectReason.STALE))
    }

    @Test
    fun `S8 settings changes apply to the next evaluation`() {
        settings = settings.copy(rateLimitPerMinute = 1)
        policy.evaluate(request())

        assertThat(policy.evaluate(request())).isEqualTo(PolicyDecision.Reject(RejectReason.RATE_LIMITED))
    }
}
