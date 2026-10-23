package dev.linakbed.app.log

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.log.LogLevel
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LogLineFormatterTest {
    private val time = Instant.parse("2026-10-22T22:00:01.234Z")
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `formats timestamp with offset and millis, level symbol and area`() {
        val line = LogLineFormatter.format(LogRecord(time, LogLevel.INFO, "Remote", "preset 2 executed"), seoul)

        assertThat(line).isEqualTo("2026-10-23T07:00:01.234+09:00 I/Remote: preset 2 executed")
    }

    @Test
    fun `appends stack trace on following lines when an error is given`() {
        val error = IllegalStateException("boom")

        val line = LogLineFormatter.format(LogRecord(time, LogLevel.ERROR, "App", "crash", error), seoul)

        assertThat(line).startsWith("2026-10-23T07:00:01.234+09:00 E/App: crash\njava.lang.IllegalStateException: boom")
        assertThat(line).doesNotMatch("(?s).*\\s$")
    }
}
