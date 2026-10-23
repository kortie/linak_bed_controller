package dev.linakbed.domain.port

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class SystemClockTest {
    @Test
    fun `now is close to the system time and zone is the system default`() {
        val before = Instant.now()
        val now = SystemClock.now()
        val after = Instant.now()

        assertThat(now).isAtLeast(before)
        assertThat(now).isAtMost(after)
        assertThat(SystemClock.zone()).isEqualTo(ZoneId.systemDefault())
    }
}
