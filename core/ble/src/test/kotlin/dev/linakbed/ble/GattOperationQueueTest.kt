package dev.linakbed.ble

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.GattStage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GattOperationQueueTest {
    private val queue = GattOperationQueue().apply { open() }
    private val timeoutError = BedError.GattFailure(GattStage.WRITE, null)

    private suspend fun discover(
        started: MutableList<String>,
        name: String,
    ) = queue.run(
        timeout = 5.seconds,
        timeoutError = timeoutError,
        start = {
            started += name
            null
        },
        matcher = { (it as? GattEvent.ServicesDiscovered)?.let { event -> BedResult.Ok(event.status) } },
    )

    @Test
    fun `runs one operation at a time`() =
        runTest {
            val started = mutableListOf<String>()
            val first = async { discover(started, "first") }
            val second = async { discover(started, "second") }
            runCurrent()

            assertThat(started).containsExactly("first")

            queue.onEvent(GattEvent.ServicesDiscovered(1))
            runCurrent()
            assertThat(started).containsExactly("first", "second").inOrder()

            queue.onEvent(GattEvent.ServicesDiscovered(2))
            assertThat(first.await()).isEqualTo(BedResult.Ok(1))
            assertThat(second.await()).isEqualTo(BedResult.Ok(2))
        }

    @Test
    fun `ignores events that do not match the current operation`() =
        runTest {
            val result = async { discover(mutableListOf(), "op") }
            runCurrent()

            queue.onEvent(GattEvent.CharacteristicWritten(java.util.UUID.randomUUID(), 0))
            runCurrent()
            assertThat(result.isCompleted).isFalse()

            queue.onEvent(GattEvent.ServicesDiscovered(0))
            assertThat(result.await()).isEqualTo(BedResult.Ok(0))
        }

    @Test
    fun `times out with the given error and lets the next operation run`() =
        runTest {
            val started = mutableListOf<String>()
            val first = async { discover(started, "first") }
            val second = async { discover(started, "second") }
            runCurrent()

            advanceTimeBy(5.seconds)
            runCurrent()

            assertThat(first.await()).isEqualTo(BedResult.Err(timeoutError))
            assertThat(started).containsExactly("first", "second").inOrder()
            queue.onEvent(GattEvent.ServicesDiscovered(0))
            assertThat(second.await()).isEqualTo(BedResult.Ok(0))
        }

    @Test
    fun `returns the start error without waiting`() =
        runTest {
            val result =
                queue.run<Unit>(
                    timeout = 5.seconds,
                    timeoutError = timeoutError,
                    start = { BedError.WriteFailed(201) },
                    matcher = { null },
                )

            assertThat(result).isEqualTo(BedResult.Err(BedError.WriteFailed(201)))
        }

    @Test
    fun `close fails the running and all waiting operations with Disconnected`() =
        runTest {
            val started = mutableListOf<String>()
            val running = async { discover(started, "running") }
            val waiting = async { discover(started, "waiting") }
            runCurrent()

            queue.close()
            runCurrent()

            assertThat(running.await()).isEqualTo(BedResult.Err(BedError.Disconnected))
            assertThat(waiting.await()).isEqualTo(BedResult.Err(BedError.Disconnected))
            assertThat(started).containsExactly("running")
        }

    @Test
    fun `rejects operations until reopened`() =
        runTest {
            queue.close()
            assertThat(discover(mutableListOf(), "x")).isEqualTo(BedResult.Err(BedError.Disconnected))

            queue.open()
            val result = async { discover(mutableListOf(), "y") }
            runCurrent()
            queue.onEvent(GattEvent.ServicesDiscovered(0))
            assertThat(result.await()).isEqualTo(BedResult.Ok(0))
        }
}
