package dev.linakbed.app.simbed

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakePairingStore
import dev.linakbed.protocol.ProtocolProfile
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

@OptIn(ExperimentalCoroutinesApi::class)
class SimBedViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pairing = FakePairingStore(device = FakePairingStore.DEFAULT)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() =
        SimBedViewModel(pairing, SimulatedBedLink(ProtocolProfile.DEFAULT, backgroundScope, FakeBedLogger())).also { vm ->
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
        }

    @Test
    fun `pairing the virtual bed replaces a real pairing, unpair removes only the virtual one`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertThat(vm.uiState.value.pairing).isEqualTo(SimPairing.REAL)

            vm.pairSimulated()
            runCurrent()
            assertThat(pairing.device).isEqualTo(SimBedViewModel.DEVICE)
            assertThat(vm.uiState.value.pairing).isEqualTo(SimPairing.SIMULATED)

            vm.unpairSimulated()
            runCurrent()
            assertThat(pairing.device).isNull()
            assertThat(vm.uiState.value.pairing).isEqualTo(SimPairing.NONE)
        }

    @Test
    fun `unpair leaves a real bed alone`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.unpairSimulated()
            runCurrent()

            assertThat(pairing.device).isEqualTo(FakePairingStore.DEFAULT)
        }
}
