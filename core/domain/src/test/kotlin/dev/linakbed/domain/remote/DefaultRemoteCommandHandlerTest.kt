package dev.linakbed.domain.remote

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.ActiveMotion
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.controller.MotionKind
import dev.linakbed.domain.fake.FakeBedController
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.fake.FakeExecutionLog
import dev.linakbed.domain.fake.FakeMotionConfirmation
import dev.linakbed.domain.fake.FakeRemoteSettingsStore
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 채널 독립: RemoteRequest를 직접 만들어 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRemoteCommandHandlerTest {
    private val controller = FakeBedController()
    private val log = FakeExecutionLog()
    private val settings = FakeRemoteSettingsStore(RemoteSettings(enabled = true, preExecutionDelay = Duration.ZERO))
    private val confirmation = FakeMotionConfirmation()
    private val clock = FakeClock()
    private val handler = DefaultRemoteCommandHandler(controller, log, settings, confirmation, clock)
    private var counter = 0

    private fun request(command: RemoteCommand = RemoteCommand.Preset(PresetSlot.P2)) =
        RemoteRequest("req-${++counter}", RemoteChannel.SMARTTHINGS_NOTIFICATION, command, clock.now(), null)

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Nested
    inner class Execution {
        @Test
        fun `executed preset goes to the controller as REMOTE and is recorded with channel and requestId`() =
            runTest {
                val outcome = handler.handle(request())

                assertThat(outcome).isEqualTo(RemoteOutcome("req-1", RemoteResult.Executed))
                assertThat(controller.calls).containsExactly("preset P2 REMOTE")
                val entry = log.entries.single()
                assertThat(entry.source).isEqualTo(CommandSource.REMOTE)
                assertThat(entry.channel).isEqualTo(RemoteChannel.SMARTTHINGS_NOTIFICATION)
                assertThat(entry.requestId).isEqualTo("req-1")
                assertThat(entry.action).isEqualTo("preset P2")
                assertThat(entry.result).isEqualTo("executed")
            }

        @Test
        fun `stop and light are executed as REMOTE`() =
            runTest {
                handler.handle(request(RemoteCommand.Stop))
                handler.handle(request(RemoteCommand.Light(LightAction.ON)))

                assertThat(controller.calls).containsExactly("stop REMOTE", "light ON REMOTE").inOrder()
                assertThat(log.entries.map { it.action }).containsExactly("stop", "light ON").inOrder()
            }

        @Test
        fun `controller errors become Failed - Busy(MANUAL) included`() =
            runTest {
                controller.result = BedResult.Err(BedError.Busy(CommandSource.MANUAL))

                val outcome = handler.handle(request())

                assertThat(outcome.result).isEqualTo(RemoteResult.Failed(BedError.Busy(CommandSource.MANUAL)))
                assertThat(log.entries.single().result).isEqualTo("failed:Busy(activeSource=MANUAL)")
            }

        @Test
        fun `policy rejections are recorded and never reach the controller`() =
            runTest {
                settings.current.value = settings.current.value.copy(enabled = false)

                val outcome = handler.handle(request())

                assertThat(outcome.result).isEqualTo(RemoteResult.Rejected(RejectReason.DISABLED))
                assertThat(controller.calls).isEmpty()
                assertThat(log.entries.single().result).isEqualTo("rejected:DISABLED")
            }

        @Test
        fun `channel rejections are recorded with the masked detail`() =
            runTest {
                handler.reject(RemoteChannel.SMARTTHINGS_NOTIFICATION, "req-x", RejectReason.AUTH_FAILED, "#BED **** preset 3")

                val entry = log.entries.single()
                assertThat(entry.requestId).isEqualTo("req-x")
                assertThat(entry.result).isEqualTo("rejected:AUTH_FAILED")
                assertThat(entry.detail).isEqualTo("#BED **** preset 3")
                assertThat(entry.action).isEqualTo("-")
            }

        @Test
        fun `notify user - failures always, success by setting, rejections never`() {
            val quiet = RemoteSettings()
            val loud = RemoteSettings(notifyOnSuccess = true)

            assertThat(RemoteOutcome("a", RemoteResult.Failed(BedError.NotPaired)).shouldNotifyUser(quiet)).isTrue()
            assertThat(RemoteOutcome("a", RemoteResult.Executed).shouldNotifyUser(quiet)).isFalse()
            assertThat(RemoteOutcome("a", RemoteResult.Executed).shouldNotifyUser(loud)).isTrue()
            assertThat(RemoteOutcome("a", RemoteResult.Rejected(RejectReason.STALE)).shouldNotifyUser(loud)).isFalse()
        }
    }

    @Nested
    inner class PreExecutionDelay {
        private fun enableDelay(delay: Duration = 10.seconds) {
            settings.current.value = settings.current.value.copy(preExecutionDelay = delay)
        }

        @Test
        fun `S9 preset runs after the delay with a confirmation shown and dismissed`() =
            runTest {
                enableDelay()
                val outcome = async { handler.handle(request()) }
                runCurrent()

                val shown = confirmation.shown.single()
                assertThat(shown.delay).isEqualTo(10.seconds)
                advance(9.9.seconds)
                assertThat(controller.calls).isEmpty()
                advance(200.milliseconds)

                assertThat(outcome.await().result).isEqualTo(RemoteResult.Executed)
                assertThat(controller.calls).containsExactly("preset P2 REMOTE")
                assertThat(shown.dismissed).isTrue()
            }

        @Test
        fun `S9 user cancel during the wait never moves the bed`() =
            runTest {
                enableDelay()
                val outcome = async { handler.handle(request()) }
                advance(3.seconds)

                confirmation.shown.single().cancelByUser()
                runCurrent()

                assertThat(outcome.await().result).isEqualTo(RemoteResult.Rejected(RejectReason.CANCELLED_BY_USER))
                assertThat(controller.calls).isEmpty()
                assertThat(confirmation.shown.single().dismissed).isTrue()
                assertThat(log.entries.single().result).isEqualTo("rejected:CANCELLED_BY_USER")
            }

        @Test
        fun `S9 a manual move started in the app cancels the wait`() =
            runTest {
                enableDelay()
                val outcome = async { handler.handle(request()) }
                advance(2.seconds)

                controller.status.value = BedStatus(activeMotion = ActiveMotion(CommandSource.MANUAL, MotionKind.MANUAL_MOVE))
                runCurrent()

                assertThat(outcome.await().result).isEqualTo(RemoteResult.Rejected(RejectReason.CANCELLED_BY_USER))
                assertThat(controller.calls).isEmpty()
            }

        @Test
        fun `S9 a manual move already running when the wait starts does not cancel it`() =
            runTest {
                enableDelay()
                controller.status.value = BedStatus(activeMotion = ActiveMotion(CommandSource.MANUAL, MotionKind.MANUAL_MOVE))
                val outcome = async { handler.handle(request()) }

                advance(10.1.seconds)

                // 컨트롤러가 Busy(MANUAL)로 판단하도록 그대로 넘긴다 (FakeBedController는 Ok).
                assertThat(outcome.await().result).isEqualTo(RemoteResult.Executed)
            }

        @Test
        fun `S9 a newer remote preset supersedes the waiting one and waits again`() =
            runTest {
                enableDelay()
                val first = async { handler.handle(request(RemoteCommand.Preset(PresetSlot.P1))) }
                advance(5.seconds)

                val second = async { handler.handle(request(RemoteCommand.Preset(PresetSlot.P3))) }
                runCurrent()

                assertThat(first.await().result).isEqualTo(RemoteResult.Rejected(RejectReason.SUPERSEDED))
                assertThat(confirmation.shown[0].dismissed).isTrue()
                advance(9.9.seconds)
                assertThat(controller.calls).isEmpty()
                advance(200.milliseconds)
                assertThat(second.await().result).isEqualTo(RemoteResult.Executed)
                assertThat(controller.calls).containsExactly("preset P3 REMOTE")
            }

        @Test
        fun `S9 a remote stop supersedes the waiting preset and runs immediately`() =
            runTest {
                enableDelay()
                val preset = async { handler.handle(request()) }
                advance(5.seconds)

                val stop = handler.handle(request(RemoteCommand.Stop))
                runCurrent()

                assertThat(stop.result).isEqualTo(RemoteResult.Executed)
                assertThat(preset.await().result).isEqualTo(RemoteResult.Rejected(RejectReason.SUPERSEDED))
                assertThat(controller.calls).containsExactly("stop REMOTE")
            }

        @Test
        fun `S9 stop and light never wait`() =
            runTest {
                enableDelay()

                handler.handle(request(RemoteCommand.Stop))
                handler.handle(request(RemoteCommand.Light(LightAction.OFF)))

                assertThat(confirmation.shown).isEmpty()
                assertThat(controller.calls).containsExactly("stop REMOTE", "light OFF REMOTE").inOrder()
            }

        @Test
        fun `S9 zero delay runs the preset immediately without a confirmation`() =
            runTest {
                handler.handle(request())

                assertThat(confirmation.shown).isEmpty()
                assertThat(controller.calls).containsExactly("preset P2 REMOTE")
            }

        @Test
        fun `S9 delay above the maximum is capped at 60s`() =
            runTest {
                enableDelay(5.seconds * 60)
                val outcome = async { handler.handle(request()) }
                runCurrent()

                assertThat(confirmation.shown.single().delay).isEqualTo(60.seconds)
                advance(60.1.seconds)
                assertThat(outcome.await().result).isEqualTo(RemoteResult.Executed)
            }
    }
}
