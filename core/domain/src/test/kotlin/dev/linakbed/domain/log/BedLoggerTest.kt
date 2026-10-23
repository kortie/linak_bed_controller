package dev.linakbed.domain.log

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeBedLogger
import org.junit.jupiter.api.Test

class BedLoggerTest {
    @Test
    fun `level shortcuts forward level, area, message and error`() {
        val logger = FakeBedLogger()
        val failure = IllegalStateException("boom")

        logger.debug("Ble", "d")
        logger.info("App", "i")
        logger.warn("Remote", "w")
        logger.error("Service", "e", failure)

        assertThat(logger.entries)
            .containsExactly(
                FakeBedLogger.Entry(LogLevel.DEBUG, "Ble", "d", null),
                FakeBedLogger.Entry(LogLevel.INFO, "App", "i", null),
                FakeBedLogger.Entry(LogLevel.WARN, "Remote", "w", null),
                FakeBedLogger.Entry(LogLevel.ERROR, "Service", "e", failure),
            ).inOrder()
    }

    @Test
    fun `warn and error shortcuts work with and without error`() {
        val logger = FakeBedLogger()
        val failure = IllegalArgumentException("bad")

        logger.warn("Remote", "w", failure)
        logger.error("Service", "e")

        assertThat(logger.entries)
            .containsExactly(
                FakeBedLogger.Entry(LogLevel.WARN, "Remote", "w", failure),
                FakeBedLogger.Entry(LogLevel.ERROR, "Service", "e", null),
            ).inOrder()
    }

    @Test
    fun `level symbols match log file format`() {
        assertThat(LogLevel.entries.map { it.symbol }).containsExactly('D', 'I', 'W', 'E').inOrder()
    }
}
