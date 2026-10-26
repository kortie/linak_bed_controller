package dev.linakbed.app.protolab

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.domain.fake.FakeBedController
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolLabViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val link = FakeDiagnosticBedLink()
    private val scanResults = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 8)
    private val scanner = DeviceScanner { scanResults as Flow<ScannedDevice> }
    private val controller = FakeBedController()
    private val target = LabTarget()
    private lateinit var viewModel: ProtocolLabViewModel

    private val headUp = BedCommand.Move(Actuator.HEAD, Direction.UP)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ProtocolLabViewModel(link, scanner, FakeClock(), controller, target)
    }

    @After
    fun tearDown() {
        viewModel.onAction(ProtocolLabAction.Stop)
        Dispatchers.resetMain()
    }

    private fun act(action: ProtocolLabAction) = viewModel.onAction(action)

    private val state get() = viewModel.uiState.value

    @Test
    fun `send writes once and logs bytes with the result`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.Send(BedCommand.RecallPreset(PresetSlot.P3)))
            runCurrent()

            assertThat(link.commands).containsExactly(BedCommand.RecallPreset(PresetSlot.P3))
            assertThat(state.log.last()).isEqualTo("16:00:00.000 0c00 RecallPreset(slot=P3) → OK")
        }

    @Test
    fun `failed write is logged with the error`() =
        runTest(dispatcher) {
            link.writeResult = BedResult.Err(BedError.Disconnected)

            act(ProtocolLabAction.Send(BedCommand.Stop))
            runCurrent()

            assertThat(state.log.last()).endsWith("ff00 Stop → ERR Disconnected")
        }

    @Test
    fun `preset save cannot be sent directly`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.Send(BedCommand.SavePreset(PresetSlot.P1)))
            runCurrent()

            assertThat(link.commands).isEmpty()
        }

    @Test
    fun `preset save is written only after confirmation`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.RequestSave(PresetSlot.P4))
            runCurrent()
            assertThat(state.pendingSave).isEqualTo(PresetSlot.P4)
            assertThat(link.commands).isEmpty()

            act(ProtocolLabAction.ConfirmSave)
            runCurrent()

            assertThat(link.commands).containsExactly(BedCommand.SavePreset(PresetSlot.P4))
            assertThat(state.pendingSave).isNull()
        }

    @Test
    fun `cancelling save writes nothing`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.RequestSave(PresetSlot.P2))
            act(ProtocolLabAction.CancelSave)
            act(ProtocolLabAction.ConfirmSave)
            runCurrent()

            assertThat(link.commands).isEmpty()
        }

    @Test
    fun `repeat writes at the chosen interval and STOP ends it with a Stop`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.RepeatIntervalChanged(300))
            act(ProtocolLabAction.StartRepeat(headUp))
            runCurrent()
            advanceTimeBy(900.milliseconds)
            runCurrent()

            assertThat(link.commands).containsExactly(headUp, headUp, headUp, headUp)
            assertThat(state.repeating).isEqualTo(headUp)

            act(ProtocolLabAction.Stop)
            runCurrent()

            assertThat(link.commands.last()).isEqualTo(BedCommand.Stop)
            assertThat(link.commands.count { it == BedCommand.Stop }).isEqualTo(1)
            assertThat(state.repeating).isNull()
        }

    @Test
    fun `repeat stops itself after 30 seconds and sends Stop`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.RepeatIntervalChanged(1000))
            act(ProtocolLabAction.StartRepeat(headUp))
            runCurrent()

            advanceTimeBy(ProtocolLabViewModel.MAX_REPEAT_DURATION + 1.seconds)
            runCurrent()

            assertThat(link.commands.last()).isEqualTo(BedCommand.Stop)
            assertThat(link.commands.count { it == headUp }).isEqualTo(30)
            assertThat(state.repeating).isNull()
            assertThat(state.log.any { it.contains("auto-stopped") }).isTrue()
        }

    @Test
    fun `repeat is only allowed for move and preset recall`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.StartRepeat(BedCommand.Light(dev.linakbed.protocol.LightAction.ON)))
            runCurrent()

            assertThat(link.commands).isEmpty()
            assertThat(state.repeating).isNull()
        }

    @Test
    fun `STOP without repeat sends Stop immediately`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.Stop)
            runCurrent()

            assertThat(link.commands).containsExactly(BedCommand.Stop)
        }

    @Test
    fun `repeat interval is clamped to 100 to 1000 ms`() {
        act(ProtocolLabAction.RepeatIntervalChanged(10))
        assertThat(state.repeatIntervalMs).isEqualTo(100)
        act(ProtocolLabAction.RepeatIntervalChanged(5000))
        assertThat(state.repeatIntervalMs).isEqualTo(1000)
    }

    @Test
    fun `raw input must be two hex bytes`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.RawInputChanged("0b"))
            act(ProtocolLabAction.SendRaw)
            runCurrent()
            assertThat(link.rawWrites).isEmpty()

            act(ProtocolLabAction.RawInputChanged("0b 00"))
            act(ProtocolLabAction.SendRaw)
            runCurrent()
            assertThat(link.rawWrites.single()).isEqualTo(byteArrayOf(0x0b, 0x00))
        }

    @Test
    fun `connect needs a selected device`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.Connect)
            runCurrent()
            assertThat(link.connects).isEmpty()

            act(ProtocolLabAction.SelectDevice("AA:BB:CC:DD:EE:FF"))
            act(ProtocolLabAction.Connect)
            runCurrent()
            assertThat(link.connects).containsExactly("AA:BB:CC:DD:EE:FF")
            assertThat(state.link).isEqualTo(LinkState.Connected)
            assertThat(state.log.any { it.contains("AA:BB:CC:DD:EE:FF") }).isFalse()
        }

    @Test
    fun `disconnect stops repeating and disconnects`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.StartRepeat(headUp))
            runCurrent()

            act(ProtocolLabAction.Disconnect)
            runCurrent()

            assertThat(link.disconnects).isEqualTo(1)
            assertThat(link.commands.last()).isEqualTo(BedCommand.Stop)
        }

    @Test
    fun `telemetry updates head and feet readings`() =
        runTest(dispatcher) {
            runCurrent()
            val head = HeightReading(Actuator.HEAD, 1000, 40.0, null)
            val feet = HeightReading(Actuator.FEET, 600, 20.0, 1.0)

            link.telemetry.emit(head)
            link.telemetry.emit(feet)
            runCurrent()

            assertThat(state.head).isEqualTo(head)
            assertThat(state.feet).isEqualTo(feet)
        }

    @Test
    fun `scan collects devices once per address, strongest first`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.StartScan)
            runCurrent()

            scanResults.emit(ScannedDevice("11:11:11:11:11:11", "a", -80, false))
            scanResults.emit(ScannedDevice("22:22:22:22:22:22", "bed", -50, true))
            scanResults.emit(ScannedDevice("11:11:11:11:11:11", "a", -70, false))
            runCurrent()

            assertThat(state.scanning).isTrue()
            assertThat(state.devices.map { it.address to it.rssi })
                .containsExactly("22:22:22:22:22:22" to -50, "11:11:11:11:11:11" to -70)
                .inOrder()

            act(ProtocolLabAction.StopScan)
            runCurrent()
            assertThat(state.scanning).isFalse()
        }

    @Test
    fun `scan stops by itself after the scan duration`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.StartScan)
            runCurrent()

            advanceTimeBy(ProtocolLabViewModel.SCAN_DURATION + 1.seconds)
            runCurrent()

            assertThat(state.scanning).isFalse()
        }

    @Test
    fun `controller target is saved from the selected scanned device`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.UseSelectedAsControllerTarget)
            runCurrent()
            assertThat(target.device.value).isNull()

            act(ProtocolLabAction.StartScan)
            runCurrent()
            scanResults.emit(ScannedDevice("22:22:22:22:22:22", "bed", -50, true))
            runCurrent()
            act(ProtocolLabAction.SelectDevice("22:22:22:22:22:22"))
            act(ProtocolLabAction.UseSelectedAsControllerTarget)
            runCurrent()

            assertThat(target.device.value?.address).isEqualTo("22:22:22:22:22:22")
            assertThat(target.device.value?.cdmAssociationId).isEqualTo(ProtocolLabViewModel.NO_CDM_ASSOCIATION)
            act(ProtocolLabAction.StopScan)
            runCurrent()
        }

    @Test
    fun `controller actions go through BedController as MANUAL`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.ControllerPreset(PresetSlot.P2))
            act(ProtocolLabAction.ControllerStop)
            runCurrent()

            assertThat(controller.calls).containsExactly("preset P2 MANUAL", "stop MANUAL").inOrder()
            assertThat(link.commands).isEmpty()
        }

    @Test
    fun `controller hold-to-move releases the motion when the finger lifts`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.ControllerMovePress(Actuator.HEAD, Direction.UP))
            runCurrent()
            assertThat(controller.calls).containsExactly("move HEAD UP")
            assertThat(controller.releases).isEqualTo(0)

            act(ProtocolLabAction.ControllerMoveRelease)
            act(ProtocolLabAction.ControllerMoveRelease)

            assertThat(controller.releases).isEqualTo(1)
        }

    @Test
    fun `releasing before the move has started still stops it`() =
        runTest(dispatcher) {
            act(ProtocolLabAction.ControllerMovePress(Actuator.FEET, Direction.DOWN))
            act(ProtocolLabAction.ControllerMoveRelease) // startManualMove는 아직 실행 전
            runCurrent()

            assertThat(controller.releases).isEqualTo(1)
        }

    @Test
    fun `controller errors are logged`() =
        runTest(dispatcher) {
            controller.result = BedResult.Err(BedError.NotPaired)

            act(ProtocolLabAction.ControllerMovePress(Actuator.HEAD, Direction.UP))
            runCurrent()

            assertThat(state.log.last()).endsWith("controller move HEAD UP → ERR NotPaired")
        }

    @Test
    fun `controller status is shown and a connection lease is held while the screen lives`() =
        runTest(dispatcher) {
            runCurrent()
            assertThat(controller.activeLeases).isEqualTo(1)

            controller.status.value = BedStatus(headCm = 12.5)
            runCurrent()
            assertThat(state.controller.headCm).isEqualTo(12.5)

            val store = ViewModelStore()
            store.put("lab", viewModel)
            store.clear()
            assertThat(controller.activeLeases).isEqualTo(0)
        }

    @Test
    fun `log keeps only the most recent lines and can be cleared`() =
        runTest(dispatcher) {
            repeat(ProtocolLabViewModel.MAX_LOG_LINES + 5) { act(ProtocolLabAction.Send(BedCommand.Stop)) }
            runCurrent()
            assertThat(state.log).hasSize(ProtocolLabViewModel.MAX_LOG_LINES)

            act(ProtocolLabAction.ClearLog)
            assertThat(state.log).isEmpty()
        }
}
