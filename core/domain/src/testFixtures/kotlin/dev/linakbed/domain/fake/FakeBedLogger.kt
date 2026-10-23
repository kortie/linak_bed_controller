package dev.linakbed.domain.fake

import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.LogLevel

/** 남겨진 로그를 기록해 테스트에서 검사한다. */
class FakeBedLogger : BedLogger {
    data class Entry(
        val level: LogLevel,
        val area: String,
        val message: String,
        val error: Throwable?,
    )

    private val recorded = mutableListOf<Entry>()
    val entries: List<Entry> get() = recorded.toList()

    override fun log(
        level: LogLevel,
        area: String,
        message: String,
        error: Throwable?,
    ) {
        recorded += Entry(level, area, message, error)
    }
}
