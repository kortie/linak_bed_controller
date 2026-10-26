package dev.linakbed.app.feature.routines

import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.feature.diagnostics.SystemCheck
import dev.linakbed.app.feature.diagnostics.SystemChecks
import dev.linakbed.domain.fake.FakeRemoteSettingsStore
import dev.linakbed.domain.remote.RemoteSettings
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
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeRemoteSettingsStore(RemoteSettings())
    private var checks = SystemCheck.entries.associateWith { false }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RoutinesViewModel(store, SystemChecks { checks })

    @Test
    fun `switch updates only the app shortcut channel`() =
        runTest(dispatcher) {
            val vm = viewModel()
            runCurrent()
            assertThat(vm.uiState.value.settings.appShortcutsEnabled).isTrue()

            vm.setEnabled(false)
            runCurrent()

            assertThat(store.current.value.appShortcutsEnabled).isFalse()
            assertThat(store.current.value.enabled).isFalse()
            assertThat(vm.uiState.value.settings.appShortcutsEnabled).isFalse()
        }

    @Test
    fun `recommends pairing and notifications, not notification access or battery`() =
        runTest(dispatcher) {
            val vm = viewModel()
            runCurrent()

            assertThat(vm.uiState.value.recommended)
                .containsExactly(SystemCheck.PAIRED, SystemCheck.POST_NOTIFICATIONS, SystemCheck.FULL_SCREEN_INTENT)
                .inOrder()
        }

    @Test
    fun `full screen permission is not needed without a pre-execution delay, nothing when all granted`() =
        runTest(dispatcher) {
            store.current.value = store.current.value.copy(preExecutionDelay = Duration.ZERO)
            val vm = viewModel()
            runCurrent()
            vm.refresh()
            runCurrent()
            assertThat(vm.uiState.value.recommended).doesNotContain(SystemCheck.FULL_SCREEN_INTENT)

            checks = SystemCheck.entries.associateWith { true }
            vm.refresh()
            runCurrent()
            assertThat(vm.uiState.value.recommended).isEmpty()
        }
}
