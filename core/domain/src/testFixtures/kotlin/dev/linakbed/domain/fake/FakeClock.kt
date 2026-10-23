package dev.linakbed.domain.fake

import dev.linakbed.domain.port.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class FakeClock(
    var current: Instant = Instant.parse("2026-10-23T07:00:00Z"),
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) : Clock {
    override fun now(): Instant = current

    override fun zone(): ZoneId = zoneId

    fun advance(duration: Duration) {
        current = current.plus(duration.toJavaDuration())
    }
}
