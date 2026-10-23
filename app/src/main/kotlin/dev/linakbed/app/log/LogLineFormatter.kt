package dev.linakbed.app.log

import dev.linakbed.domain.log.LogLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 로그 한 건. */
data class LogRecord(
    val time: Instant,
    val level: LogLevel,
    val area: String,
    val message: String,
    val error: Throwable? = null,
)

/** 로그 파일 한 줄: `2026-10-23T07:00:01.234+09:00 I/Remote: message` (+ 예외 스택). */
object LogLineFormatter {
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    fun format(
        record: LogRecord,
        zone: ZoneId,
    ): String =
        buildString {
            append(TIMESTAMP.format(record.time.atZone(zone)))
            append(' ')
            append(record.level.symbol)
            append('/')
            append(record.area)
            append(": ")
            append(record.message)
            if (record.error != null) {
                append('\n')
                append(record.error.stackTraceToString().trimEnd())
            }
        }
}
