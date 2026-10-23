package dev.linakbed.domain.port

import java.time.Instant
import java.time.ZoneId

/** 현재 시각의 유일한 출처 (linak-conventions §2). 테스트에서는 `FakeClock`을 주입한다. */
interface Clock {
    fun now(): Instant

    fun zone(): ZoneId
}

/** 실제 시스템 시각. */
object SystemClock : Clock {
    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
