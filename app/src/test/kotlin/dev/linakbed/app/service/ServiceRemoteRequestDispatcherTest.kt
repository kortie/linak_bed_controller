package dev.linakbed.app.service

import android.app.ForegroundServiceStartNotAllowedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.fake.FakeExecutionLog
import dev.linakbed.domain.fake.FakeRemoteCommandHandler
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** S07 실행 경로: 서비스 경로 · 시작 거부 시 폴백(+기록) · 멈춤 방지 상한. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ServiceRemoteRequestDispatcherTest {
    private val handler = FakeRemoteCommandHandler()
    private val log = FakeExecutionLog()
    private val clock = FakeClock()
    private val notified = mutableListOf<RemoteOutcome>()
    private val started = mutableListOf<String>()
    private var deny = false
    private var dispatcher: ServiceRemoteRequestDispatcher? = null

    private val request =
        RemoteRequest("req-1", RemoteChannel.SMARTTHINGS_NOTIFICATION, RemoteCommand.Preset(PresetSlot.P2), Instant.EPOCH, null)

    private fun dispatcher(): ServiceRemoteRequestDispatcher =
        dispatcher ?: ServiceRemoteRequestDispatcher(
            handler = handler,
            starter =
                ServiceStarter { id ->
                    if (deny) throw ForegroundServiceStartNotAllowedException("background")
                    started += id
                },
            notifier = { _, outcome -> notified += outcome },
            executionLog = log,
            clock = clock,
            logger = FakeBedLogger(),
        ).also { dispatcher = it }

    @Test
    fun `service path - the service executes the pending request and dispatch returns its outcome`() =
        runTest {
            val pending = async { dispatcher().dispatch(request) }
            runCurrent()
            assertThat(started).containsExactly("req-1")
            assertThat(handler.handled).isEmpty()

            val executed = dispatcher().execute("req-1")

            assertThat(executed).isEqualTo(RemoteOutcome("req-1", RemoteResult.Executed))
            assertThat(pending.await()).isEqualTo(RemoteOutcome("req-1", RemoteResult.Executed))
            assertThat(notified).containsExactly(RemoteOutcome("req-1", RemoteResult.Executed))
            assertThat(log.entries).isEmpty()
            assertThat(dispatcher().lastFallback.value).isNull()
        }

    @Test
    fun `execute for an unknown request does nothing`() =
        runTest {
            assertThat(dispatcher().execute("nope")).isNull()
            assertThat(handler.handled).isEmpty()
        }

    @Test
    fun `fallback - start denied runs in place and records fallback`() =
        runTest {
            deny = true
            handler.result = RemoteResult.Failed(BedError.ConnectTimeout)

            val outcome = dispatcher().dispatch(request)

            assertThat(outcome.result).isEqualTo(RemoteResult.Failed(BedError.ConnectTimeout))
            assertThat(handler.handled).containsExactly(request)
            assertThat(notified).containsExactly(outcome)
            val entry = log.entries.single()
            assertThat(entry.requestId).isEqualTo("req-1")
            assertThat(entry.result).isEqualTo("fallback")
            assertThat(dispatcher().lastFallback.value).isEqualTo(clock.now())
            assertThat(dispatcher().execute("req-1")).isNull()
        }

    @Test
    fun `a service that never runs the request times out as interrupted`() =
        runTest {
            val pending = async { dispatcher().dispatch(request) }
            advanceTimeBy(ServiceRemoteRequestDispatcher.MAX_EXECUTION.inWholeMilliseconds + 1)

            assertThat(pending.await().result).isEqualTo(RemoteResult.Failed(BedError.Interrupted(CommandSource.REMOTE)))
            assertThat(dispatcher().execute("req-1")).isNull()
        }
}
