package dev.linakbed.app.log

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.log.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidBedLoggerTest {
    private val sink = FakeLogSink()
    private val clock = FakeClock()

    @Before
    fun clearLogcat() {
        ShadowLog.clear()
    }

    private fun logger(
        scope: CoroutineScope,
        minLevel: LogLevel = LogLevel.DEBUG,
    ) = AndroidBedLogger(sink, minLevel, clock, scope)

    private fun TestScope.logcat(tag: String) = ShadowLog.getLogsForTag(tag)

    @Test
    fun `writes formatted line to sink and logcat with area tag`() =
        runTest {
            val logger = logger(backgroundScope)

            logger.log(LogLevel.INFO, "Remote", "preset 2 executed")
            runCurrent()

            assertThat(sink.lines).containsExactly("2026-10-23T16:00:00.000+09:00 I/Remote: preset 2 executed")
            val logcat = logcat("LinakBed/Remote").single()
            assertThat(logcat.type).isEqualTo(Log.INFO)
            assertThat(logcat.msg).isEqualTo("preset 2 executed")
        }

    @Test
    fun `drops entries below the minimum level in both outputs`() =
        runTest {
            val logger = logger(backgroundScope, minLevel = LogLevel.INFO)

            logger.log(LogLevel.DEBUG, "Ble", "tx 0b00")
            logger.log(LogLevel.INFO, "Ble", "connected")
            runCurrent()

            assertThat(sink.lines.map { it.substringAfter(' ') }).containsExactly("I/Ble: connected")
            assertThat(logcat("LinakBed/Ble").map { it.msg }).containsExactly("connected")
        }

    @Test
    fun `flushes immediately for WARN and above`() =
        runTest {
            val logger = logger(backgroundScope)

            logger.log(LogLevel.WARN, "Remote", "rejected STALE")
            runCurrent()

            assertThat(sink.linesAtFlush).containsExactly(1)
        }

    @Test
    fun `flushes INFO lines only on the periodic tick`() =
        runTest {
            val logger = logger(backgroundScope)

            logger.log(LogLevel.INFO, "App", "started")
            runCurrent()
            assertThat(sink.flushCount).isEqualTo(0)

            advanceTimeBy(AndroidBedLogger.DEFAULT_FLUSH_INTERVAL)
            runCurrent()

            assertThat(sink.linesAtFlush).containsExactly(1)
        }

    @Test
    fun `logcat message includes stack trace when an error is given`() =
        runTest {
            val logger = logger(backgroundScope)

            logger.log(LogLevel.ERROR, "Service", "failed", IllegalStateException("boom"))
            runCurrent()

            assertThat(logcat("LinakBed/Service").single().msg).contains("java.lang.IllegalStateException: boom")
            assertThat(sink.lines.single()).contains("E/Service: failed\njava.lang.IllegalStateException: boom")
        }

    @Test
    fun `recordCrash writes and flushes synchronously without the queue`() =
        runTest {
            val logger = logger(backgroundScope)
            val thread = Thread({}, "worker-1")

            logger.recordCrash(thread, IllegalStateException("fatal"))

            // runCurrent() 없이도 이미 기록·flush 되어 있어야 한다.
            assertThat(sink.lines.single()).contains("E/App: Uncaught exception in thread worker-1")
            assertThat(sink.linesAtFlush).containsExactly(1)
            assertThat(logcat("LinakBed/App").single().type).isEqualTo(Log.ERROR)
        }
}
