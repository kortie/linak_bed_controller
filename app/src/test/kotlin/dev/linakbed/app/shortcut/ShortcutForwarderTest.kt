package dev.linakbed.app.shortcut

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.fake.FakeRemoteCommandHandler
import dev.linakbed.domain.port.RemoteRequestDispatcher
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 02 문서 Part C2: 바로가기 → APP_SHORTCUT 원격 요청. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShortcutForwarderTest {
    private val clock = FakeClock()
    private val handler = FakeRemoteCommandHandler()
    private val dispatched = mutableListOf<RemoteRequest>()
    private val dispatcher =
        RemoteRequestDispatcher { request ->
            RemoteOutcome(request.requestId, RemoteResult.Executed).also {
                dispatched +=
                    request
            }
        }

    @Test
    fun `a preset shortcut becomes a fresh APP_SHORTCUT request, dispatched before forward returns`() =
        runTest {
            val forwarder = ShortcutForwarder(dispatcher, handler, clock, FakeBedLogger(), backgroundScope) { "abc123" }

            forwarder.forward(Uri.parse("bedcontrol://shortcut/preset/2"))

            // UNDISPATCHED: 서비스 시작(디스패치 호출)이 forward() 안에서 이미 일어났다.
            assertThat(dispatched).containsExactly(
                RemoteRequest("abc123", RemoteChannel.APP_SHORTCUT, RemoteCommand.Preset(PresetSlot.P2), clock.now(), null),
            )
            assertThat(handler.rejected).isEmpty()
        }

    @Test
    fun `stop shortcut is forwarded too`() =
        runTest {
            ShortcutForwarder(dispatcher, handler, clock, FakeBedLogger(), backgroundScope).forward(Uri.parse("bedcontrol://shortcut/stop"))

            assertThat(dispatched.single().command).isEqualTo(RemoteCommand.Stop)
            assertThat(dispatched.single().requestId).hasLength(16)
        }

    @Test
    fun `each run gets a new request id`() =
        runTest {
            val forwarder = ShortcutForwarder(dispatcher, handler, clock, FakeBedLogger(), backgroundScope)
            forwarder.forward(Uri.parse("bedcontrol://shortcut/stop"))
            forwarder.forward(Uri.parse("bedcontrol://shortcut/stop"))

            assertThat(dispatched.map { it.requestId }.toSet()).hasSize(2)
        }

    @Test
    fun `an unknown shortcut is recorded as INVALID and not dispatched`() =
        runTest {
            ShortcutForwarder(dispatcher, handler, clock, FakeBedLogger(), backgroundScope) { "bad1" }
                .forward(Uri.parse("bedcontrol://shortcut/preset/9"))
            runCurrent()

            assertThat(dispatched).isEmpty()
            val rejection = handler.rejected.single()
            assertThat(rejection.channel).isEqualTo(RemoteChannel.APP_SHORTCUT)
            assertThat(rejection.reason).isEqualTo(RejectReason.INVALID)
            assertThat(rejection.requestId).isEqualTo("bad1")
        }
}
