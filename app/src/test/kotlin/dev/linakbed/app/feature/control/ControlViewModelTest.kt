package dev.linakbed.app.feature.control

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.ActiveMotion
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.controller.LightState
import dev.linakbed.domain.controller.MotionKind
import dev.linakbed.domain.fake.FakeBedController
import dev.linakbed.domain.fake.FakePairingStore
import dev.linakbed.domain.fake.FakePresetNameStore
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LightAction
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

@OptIn(ExperimentalCoroutinesApi::class)
class ControlViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val controller = FakeBedController()
    private val pairing = FakePairingStore()
    private val presetNames = FakePresetNameStore()
    private lateinit var viewModel: ControlViewModel

    private val headUp = ManualMove(Actuator.HEAD, Direction.UP)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ControlViewModel(controller, pairing, presetNames)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun act(action: ControlAction) = viewModel.onAction(action)

    private val state get() = viewModel.uiState.value

    @Test
    fun `S3 press starts a manual move and release stops it`() =
        runTest(dispatcher) {
            act(ControlAction.Press(headUp))
            runCurrent()

            assertThat(controller.calls).containsExactly("move HEAD UP")
            assertThat(state.pressed).isEqualTo(headUp)

            act(ControlAction.Release)
            assertThat(controller.releases).isEqualTo(1)
            assertThat(state.pressed).isNull()
        }

    @Test
    fun `S3 releasing before the move has started still stops it`() =
        runTest(dispatcher) {
            act(ControlAction.Press(headUp))
            act(ControlAction.Release)
            runCurrent()

            assertThat(controller.releases).isEqualTo(1)
        }

    @Test
    fun `S3 pressing another button releases the previous move`() =
        runTest(dispatcher) {
            act(ControlAction.Press(headUp))
            runCurrent()
            act(ControlAction.Press(ManualMove(Actuator.FEET, Direction.DOWN)))
            runCurrent()

            assertThat(controller.releases).isEqualTo(1)
            assertThat(controller.calls).containsExactly("move HEAD UP", "move FEET DOWN").inOrder()
        }

    @Test
    fun `S3 screen going to the background stops the move and drops the connection lease`() =
        runTest(dispatcher) {
            act(ControlAction.ScreenStarted)
            act(ControlAction.Press(headUp))
            runCurrent()
            assertThat(controller.activeLeases).isEqualTo(1)

            act(ControlAction.ScreenStopped)

            assertThat(controller.releases).isEqualTo(1)
            assertThat(controller.activeLeases).isEqualTo(0)
        }

    @Test
    fun `S3 clearing the ViewModel stops the move`() =
        runTest(dispatcher) {
            act(ControlAction.ScreenStarted)
            act(ControlAction.Press(headUp))
            runCurrent()

            ViewModelStore().apply { put("control", viewModel) }.clear()

            assertThat(controller.releases).isEqualTo(1)
            assertThat(controller.activeLeases).isEqualTo(0)
        }

    @Test
    fun `screen start holds the connection once and shows the bed name`() =
        runTest(dispatcher) {
            act(ControlAction.ScreenStarted)
            act(ControlAction.ScreenStarted)
            runCurrent()

            assertThat(controller.activeLeases).isEqualTo(1)
            assertThat(state.bedName).isEqualTo("Linak")
        }

    @Test
    fun `bed name follows pairing changes`() =
        runTest(dispatcher) {
            runCurrent()
            pairing.device = null
            runCurrent()

            assertThat(state.bedName).isNull()
        }

    @Test
    fun `reconnect asks for a fresh connection and keeps exactly one lease`() =
        runTest(dispatcher) {
            act(ControlAction.ScreenStarted)

            act(ControlAction.Reconnect)

            assertThat(controller.leaseRequests).isEqualTo(2)
            assertThat(controller.activeLeases).isEqualTo(1)
        }

    @Test
    fun `controls are enabled only while connected`() {
        assertThat(ControlUiState(link = LinkState.Connected).controlsEnabled).isTrue()
        assertThat(ControlUiState(link = LinkState.Connecting).controlsEnabled).isFalse()
        assertThat(ControlUiState(link = LinkState.Disconnected).controlsEnabled).isFalse()
    }

    @Test
    fun `move errors become a one-time message`() =
        runTest(dispatcher) {
            controller.result = BedResult.Err(BedError.NotPaired)

            act(ControlAction.Press(headUp))
            runCurrent()

            assertThat(state.userMessage).isEqualTo(BedError.NotPaired)
            assertThat(state.pressed).isNull()
            act(ControlAction.MessageShown)
            assertThat(state.userMessage).isNull()
        }

    @Test
    fun `stop and light go through the controller as MANUAL`() =
        runTest(dispatcher) {
            act(ControlAction.Stop)
            act(ControlAction.Light(LightAction.TOGGLE))
            runCurrent()

            assertThat(controller.calls).containsExactly("stop MANUAL", "light TOGGLE MANUAL").inOrder()
        }

    @Test
    fun `failed stop is reported`() =
        runTest(dispatcher) {
            controller.result = BedResult.Err(BedError.Disconnected)

            act(ControlAction.Stop)
            runCurrent()

            assertThat(state.userMessage).isEqualTo(BedError.Disconnected)
        }

    @Test
    fun `controller status is reflected`() =
        runTest(dispatcher) {
            runCurrent()
            controller.status.value =
                BedStatus(
                    link = LinkState.Connected,
                    headCm = 30.0,
                    feetCm = 10.0,
                    light = LightState.ON,
                    lastError = BedError.BluetoothOff,
                )
            runCurrent()

            assertThat(state.link).isEqualTo(LinkState.Connected)
            assertThat(state.headCm).isEqualTo(30.0)
            assertThat(state.feetCm).isEqualTo(10.0)
            assertThat(state.light).isEqualTo(LightState.ON)
            assertThat(state.lastError).isEqualTo(BedError.BluetoothOff)
        }

    @Test
    fun `preset quick row recalls as MANUAL, stops a held move first and shows names`() =
        runTest(dispatcher) {
            presetNames.rename(PresetSlot.P2, "기상")
            runCurrent()
            act(ControlAction.Press(headUp))
            runCurrent()

            act(ControlAction.RecallPreset(PresetSlot.P2))
            runCurrent()

            assertThat(controller.releases).isEqualTo(1)
            assertThat(controller.calls.last()).isEqualTo("preset P2 MANUAL")
            assertThat(state.presetNames[PresetSlot.P2]).isEqualTo("기상")
        }

    @Test
    fun `running preset comes from the controller status`() =
        runTest(dispatcher) {
            runCurrent()
            controller.status.value = BedStatus(activeMotion = ActiveMotion(CommandSource.REMOTE, MotionKind.PRESET, PresetSlot.P3))
            runCurrent()

            assertThat(state.runningPreset).isEqualTo(PresetSlot.P3)
        }
}
