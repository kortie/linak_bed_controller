package dev.linakbed.app.simbed

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakePairingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** S13: 가상 침대 2대. */
@OptIn(ExperimentalCoroutinesApi::class)
class SimBedViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pairing = FakePairingStore(device = null)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() =
        SimBedViewModel(pairing, SimulatedBeds(backgroundScope, FakeBedLogger())).also { vm ->
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
        }

    @Test
    fun `two virtual beds can be added and removed one at a time`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertThat(
                vm.uiState.value.cards
                    .map { it.paired },
            ).containsExactly(false, false)

            vm.pair(1)
            vm.pair(2)
            runCurrent()
            assertThat(pairing.beds.value.map { it.bedId }).containsExactly("simulated-bed-1", "simulated-bed-2").inOrder()
            assertThat(
                vm.uiState.value.cards
                    .map { it.paired },
            ).containsExactly(true, true)
            assertThat(vm.uiState.value.canAdd).isFalse()

            vm.unpair(1)
            runCurrent()
            assertThat(pairing.beds.value.map { it.bedId }).containsExactly("simulated-bed-2")
        }

    @Test
    fun `a real bed counts toward the two-bed limit and is never removed here`() =
        runTest(dispatcher) {
            pairing.device = FakePairingStore.DEFAULT
            val vm = viewModel()

            vm.pair(1)
            runCurrent()
            assertThat(vm.uiState.value.canAdd).isFalse()

            vm.unpair(2)
            vm.unpair(1)
            runCurrent()
            assertThat(pairing.beds.value).containsExactly(FakePairingStore.DEFAULT)
        }

    @Test
    fun `after pairing the screen is told to open the app, only once the bed is saved`() =
        runTest(dispatcher) {
            val vm = viewModel()
            var savedWhenOpened: Int? = null

            vm.pair(1, onPaired = { savedWhenOpened = pairing.beds.value.size })
            runCurrent()

            assertThat(savedWhenOpened).isEqualTo(1)
        }
}
