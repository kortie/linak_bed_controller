package dev.linakbed.app.feature.confirm

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakePresetNameStore
import dev.linakbed.domain.port.Clock
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PendingMotionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** 가상 시간을 따라가는 시계 — 카운트다운이 실제로 끝난다. */
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.EPOCH.plusMillis(dispatcher.scheduler.currentTime)

            override fun zone(): ZoneId = ZoneId.of("Asia/Seoul")
        }
    private val registry = PendingMotionRegistry()
    private val session = PendingMotionRegistry.Session("req-1", PresetSlot.P2, Instant.EPOCH.plusSeconds(10))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PendingMotionViewModel(registry, FakePresetNameStore(), clock)

    @Test
    fun `counts down the remaining seconds to zero and stops`() =
        runTest(dispatcher) {
            registry.begin(session)
            val vm = viewModel()
            runCurrent()
            assertThat(vm.uiState.value.remainingSeconds).isEqualTo(10)
            assertThat(vm.uiState.value.slot).isEqualTo(PresetSlot.P2)

            advanceTimeBy(3_500)
            assertThat(vm.uiState.value.remainingSeconds).isEqualTo(7)

            advanceUntilIdle()
            assertThat(vm.uiState.value.remainingSeconds).isEqualTo(0)
            assertThat(vm.uiState.value.active).isTrue()
        }

    @Test
    fun `cancel completes the current session`() =
        runTest(dispatcher) {
            registry.begin(session)
            val vm = viewModel()

            vm.cancel()

            assertThat(session.cancelled.isCompleted).isTrue()
        }

    @Test
    fun `screen becomes inactive when the confirmation ends`() =
        runTest(dispatcher) {
            registry.begin(session)
            val vm = viewModel()
            runCurrent()
            assertThat(vm.uiState.value.active).isTrue()

            registry.end("req-1")
            runCurrent()

            assertThat(vm.uiState.value.active).isFalse()
        }

    @Test
    fun `ending or cancelling another request leaves the current one`() {
        registry.begin(session)

        registry.end("other")
        registry.cancel("other")

        assertThat(registry.current.value).isSameInstanceAs(session)
        assertThat(session.cancelled.isCompleted).isFalse()
    }
}
