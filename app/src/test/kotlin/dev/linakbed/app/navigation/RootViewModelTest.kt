package dev.linakbed.app.navigation

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakePairingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** ADR-009: 페어링 없음 → 연결 안내, 있음 → 홈, 해제하면 다시 연결 안내. */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loading until the pairing is read`() {
        assertThat(RootViewModel(FakePairingStore()).state.value).isEqualTo(RootState.LOADING)
    }

    @Test
    fun `paired starts at home, unpairing returns to onboarding, pairing again goes home`() =
        runTest(dispatcher) {
            val pairing = FakePairingStore()
            val viewModel = RootViewModel(pairing)
            runCurrent()
            assertThat(viewModel.state.value).isEqualTo(RootState.READY)

            pairing.device = null
            runCurrent()
            assertThat(viewModel.state.value).isEqualTo(RootState.ONBOARDING)

            pairing.device = FakePairingStore.DEFAULT
            runCurrent()
            assertThat(viewModel.state.value).isEqualTo(RootState.READY)
        }

    @Test
    fun `not paired starts at onboarding`() =
        runTest(dispatcher) {
            val viewModel = RootViewModel(FakePairingStore(device = null))
            runCurrent()

            assertThat(viewModel.state.value).isEqualTo(RootState.ONBOARDING)
        }
}
