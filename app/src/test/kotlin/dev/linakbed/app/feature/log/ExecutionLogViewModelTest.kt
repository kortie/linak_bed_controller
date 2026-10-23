package dev.linakbed.app.feature.log

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.fake.FakeExecutionLog
import dev.linakbed.domain.log.ExecutionEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExecutionLogViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val log = FakeExecutionLog()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun entry(n: Int) = ExecutionEntry(Instant.ofEpochSecond(n.toLong()), CommandSource.MANUAL, null, null, "a$n", "ok")

    @Test
    fun `shows the latest 200 entries newest first`() =
        runTest(dispatcher) {
            repeat(205) { log.record(entry(it)) }
            val vm = ExecutionLogViewModel(log)
            val job = launch { vm.entries.collect {} }

            assertThat(vm.entries.value).hasSize(ExecutionLogViewModel.LIMIT)
            assertThat(
                vm.entries.value
                    .first()
                    .action,
            ).isEqualTo("a204")

            log.record(entry(300))
            assertThat(
                vm.entries.value
                    .first()
                    .action,
            ).isEqualTo("a300")
            job.cancel()
        }
}
