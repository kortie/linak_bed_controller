package dev.linakbed.app.feature.remote

import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.feature.diagnostics.SystemCheck
import dev.linakbed.app.feature.diagnostics.SystemChecks
import dev.linakbed.domain.fake.FakeNotificationChannelSettingsStore
import dev.linakbed.domain.fake.FakePresetNameStore
import dev.linakbed.domain.fake.FakeRemoteSettingsStore
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteSettings
import dev.linakbed.protocol.PresetSlot
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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val remote = FakeRemoteSettingsStore(RemoteSettings(enabled = false))
    private val channel = FakeNotificationChannelSettingsStore()
    private var checks: Map<SystemCheck, Boolean> = SystemCheck.entries.associateWith { true }
    private lateinit var viewModel: RemoteSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = RemoteSettingsViewModel(remote, channel, FakePresetNameStore(), SystemChecks { checks })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value

    private fun act(action: RemoteSettingsAction) = viewModel.onAction(action)

    @Test
    fun `enabling with every check satisfied turns remote on`() =
        runTest(dispatcher) {
            act(RemoteSettingsAction.SetEnabled(true))
            runCurrent()

            assertThat(remote.current.value.enabled).isTrue()
            assertThat(state.missing).isEmpty()
        }

    @Test
    fun `enabling with missing checks stays off and lists what is missing`() =
        runTest(dispatcher) {
            checks = checks + (SystemCheck.NOTIFICATION_ACCESS to false) + (SystemCheck.PAIRED to false)

            act(RemoteSettingsAction.SetEnabled(true))
            runCurrent()

            assertThat(remote.current.value.enabled).isFalse()
            assertThat(state.missing).containsExactly(SystemCheck.PAIRED, SystemCheck.NOTIFICATION_ACCESS).inOrder()
        }

    @Test
    fun `returning from system settings with everything fixed enables automatically`() =
        runTest(dispatcher) {
            checks = checks + (SystemCheck.NOTIFICATION_ACCESS to false)
            act(RemoteSettingsAction.SetEnabled(true))
            runCurrent()
            assertThat(remote.current.value.enabled).isFalse()

            checks = checks + (SystemCheck.NOTIFICATION_ACCESS to true)
            act(RemoteSettingsAction.RefreshChecks)
            runCurrent()

            assertThat(remote.current.value.enabled).isTrue()
            assertThat(state.missing).isEmpty()
        }

    @Test
    fun `refresh without an enable request never turns remote on`() =
        runTest(dispatcher) {
            act(RemoteSettingsAction.RefreshChecks)
            runCurrent()

            assertThat(remote.current.value.enabled).isFalse()
        }

    @Test
    fun `full screen permission is only required with a pre-execution delay`() =
        runTest(dispatcher) {
            checks = checks + (SystemCheck.FULL_SCREEN_INTENT to false)
            act(RemoteSettingsAction.SetEnabled(true))
            runCurrent()
            assertThat(state.missing).containsExactly(SystemCheck.FULL_SCREEN_INTENT)

            act(RemoteSettingsAction.DelaySelected(0))
            runCurrent()
            act(RemoteSettingsAction.SetEnabled(true))
            runCurrent()

            assertThat(remote.current.value.preExecutionDelay).isEqualTo(Duration.ZERO)
            assertThat(remote.current.value.enabled).isTrue()
        }

    @Test
    fun `disabling clears the guidance`() =
        runTest(dispatcher) {
            remote.current.value = remote.current.value.copy(enabled = true)

            act(RemoteSettingsAction.SetEnabled(false))
            runCurrent()

            assertThat(remote.current.value.enabled).isFalse()
            assertThat(state.missing).isEmpty()
        }

    @Test
    fun `PIN must be 4 to 8 digits - invalid input is not saved`() =
        runTest(dispatcher) {
            runCurrent()
            act(RemoteSettingsAction.PinChanged("12a4"))
            act(RemoteSettingsAction.SaveChannel)
            runCurrent()
            assertThat(state.pinInvalid).isTrue()
            assertThat(channel.current.value.pin).isNull()

            act(RemoteSettingsAction.PinChanged("4821"))
            act(RemoteSettingsAction.SaveChannel)
            runCurrent()
            assertThat(state.pinInvalid).isFalse()
            assertThat(channel.current.value.pin).isEqualTo("4821")
            assertThat(state.exampleCommand).isEqualTo("#BED 4821 preset 2")
        }

    @Test
    fun `blank PIN removes it and a prefix with spaces is rejected`() =
        runTest(dispatcher) {
            channel.current.value = channel.current.value.copy(pin = "4821")
            runCurrent()

            act(RemoteSettingsAction.PrefixChanged("#MY BED"))
            act(RemoteSettingsAction.SaveChannel)
            runCurrent()
            assertThat(state.prefixInvalid).isTrue()
            assertThat(channel.current.value.pin).isEqualTo("4821")

            act(RemoteSettingsAction.PrefixChanged("#BED2"))
            act(RemoteSettingsAction.PinChanged(""))
            act(RemoteSettingsAction.SaveChannel)
            runCurrent()
            assertThat(channel.current.value.prefix).isEqualTo("#BED2")
            assertThat(channel.current.value.pin).isNull()
        }

    @Test
    fun `delay and toggles are stored`() =
        runTest(dispatcher) {
            act(RemoteSettingsAction.DelaySelected(30))
            act(RemoteSettingsAction.NotifyOnSuccess(true))
            act(RemoteSettingsAction.DismissHandled(false))
            runCurrent()

            assertThat(remote.current.value.preExecutionDelay).isEqualTo(30.seconds)
            assertThat(remote.current.value.notifyOnSuccess).isTrue()
            assertThat(channel.current.value.dismissHandledNotification).isFalse()
        }

    @Test
    fun `tester shows command, rejection and non-command`() =
        runTest(dispatcher) {
            runCurrent()

            fun test(text: String): TesterResult? {
                act(RemoteSettingsAction.TesterInputChanged(text))
                act(RemoteSettingsAction.RunTester)
                runCurrent()
                return state.testerResult
            }

            assertThat(test("#BED preset 2")).isEqualTo(TesterResult.Command(RemoteCommand.Preset(PresetSlot.P2)))
            assertThat(test("#BED move up")).isEqualTo(TesterResult.Rejected(RejectReason.INVALID))
            assertThat(test("문이 열렸습니다")).isEqualTo(TesterResult.NotACommand)

            channel.current.value = channel.current.value.copy(pin = "4821")
            runCurrent()
            assertThat(test("#BED preset 2")).isEqualTo(TesterResult.Rejected(RejectReason.AUTH_FAILED))
            assertThat(test("#BED 4821 preset 2")).isEqualTo(TesterResult.Command(RemoteCommand.Preset(PresetSlot.P2)))
        }

    @Test
    fun `editing the tester input clears the previous result`() =
        runTest(dispatcher) {
            act(RemoteSettingsAction.TesterInputChanged("#BED stop"))
            act(RemoteSettingsAction.RunTester)
            runCurrent()
            assertThat(state.testerResult).isEqualTo(TesterResult.Command(RemoteCommand.Stop))

            act(RemoteSettingsAction.TesterInputChanged("#BED sto"))

            assertThat(state.testerResult).isNull()
        }
}
