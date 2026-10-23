package dev.linakbed.app.feature.preset

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.ActiveMotion
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.controller.MotionKind
import dev.linakbed.domain.fake.FakeBedController
import dev.linakbed.domain.fake.FakePresetNameStore
import dev.linakbed.domain.preset.PresetName
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
class PresetViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val controller = FakeBedController()
    private val names = FakePresetNameStore()
    private lateinit var viewModel: PresetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = PresetViewModel(controller, names)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun act(action: PresetAction) = viewModel.onAction(action)

    private val state get() = viewModel.uiState.value

    @Test
    fun `recall goes through the controller as MANUAL and reports arrival`() =
        runTest(dispatcher) {
            act(PresetAction.Recall(PresetSlot.P1))
            runCurrent()

            assertThat(controller.calls).containsExactly("preset P1 MANUAL")
            assertThat(state.message).isEqualTo(PresetMessage.Arrived(PresetSlot.P1))
        }

    @Test
    fun `recall failures are reported - Busy and connection failure`() =
        runTest(dispatcher) {
            controller.result = BedResult.Err(BedError.Busy(CommandSource.MANUAL))
            act(PresetAction.Recall(PresetSlot.P2))
            runCurrent()
            assertThat(state.message).isEqualTo(PresetMessage.Failed(BedError.Busy(CommandSource.MANUAL)))

            act(PresetAction.MessageShown)
            controller.result = BedResult.Err(BedError.ConnectTimeout)
            act(PresetAction.Recall(PresetSlot.P2))
            runCurrent()
            assertThat(state.message).isEqualTo(PresetMessage.Failed(BedError.ConnectTimeout))
        }

    @Test
    fun `S6 save is not written before the overwrite is confirmed`() =
        runTest(dispatcher) {
            act(PresetAction.RequestSave(PresetSlot.P3))
            act(PresetAction.ConfirmSave)
            runCurrent()

            assertThat(controller.calls).isEmpty()
            assertThat(state.pendingSave).isEqualTo(PresetSlot.P3)

            act(PresetAction.SaveConfirmedChanged(true))
            act(PresetAction.ConfirmSave)
            runCurrent()

            assertThat(controller.calls).containsExactly("save P3")
            assertThat(state.pendingSave).isNull()
            assertThat(state.message).isEqualTo(PresetMessage.Saved(PresetSlot.P3))
        }

    @Test
    fun `S6 cancelling or reopening the save dialog resets the confirmation`() =
        runTest(dispatcher) {
            act(PresetAction.RequestSave(PresetSlot.P1))
            act(PresetAction.SaveConfirmedChanged(true))
            act(PresetAction.CancelSave)
            act(PresetAction.ConfirmSave)
            act(PresetAction.RequestSave(PresetSlot.P2))
            act(PresetAction.ConfirmSave)
            runCurrent()

            assertThat(controller.calls).isEmpty()
            assertThat(state.saveConfirmed).isFalse()
        }

    @Test
    fun `rename saves a valid trimmed name`() =
        runTest(dispatcher) {
            runCurrent()
            act(PresetAction.StartRename(PresetSlot.P2))
            assertThat(state.renameText).isEqualTo("프리셋 2")

            act(PresetAction.RenameTextChanged("  기상 "))
            act(PresetAction.ConfirmRename)
            runCurrent()

            assertThat(names.current.value[PresetSlot.P2]).isEqualTo("기상")
            assertThat(state.renaming).isNull()
            assertThat(state.names[PresetSlot.P2]).isEqualTo("기상")
        }

    @Test
    fun `invalid rename shows the reason and keeps the old name`() =
        runTest(dispatcher) {
            runCurrent()
            act(PresetAction.StartRename(PresetSlot.P1))
            act(PresetAction.RenameTextChanged("프리셋 2"))
            act(PresetAction.ConfirmRename)
            runCurrent()

            assertThat(state.renameError).isEqualTo(PresetName.Reason.Duplicate(PresetSlot.P2))
            assertThat(state.renaming).isEqualTo(PresetSlot.P1)
            assertThat(names.current.value[PresetSlot.P1]).isEqualTo("프리셋 1")

            act(PresetAction.RenameTextChanged("3"))
            assertThat(state.renameError).isNull()
            act(PresetAction.ConfirmRename)
            assertThat(state.renameError).isEqualTo(PresetName.Reason.DigitsOnly)
        }

    @Test
    fun `running preset follows the controller and stop is MANUAL`() =
        runTest(dispatcher) {
            runCurrent()
            controller.status.value = BedStatus(activeMotion = ActiveMotion(CommandSource.MANUAL, MotionKind.PRESET, PresetSlot.P4))
            runCurrent()
            assertThat(state.running).isEqualTo(PresetSlot.P4)

            act(PresetAction.Stop)
            runCurrent()
            assertThat(controller.calls).containsExactly("stop MANUAL")
        }
}
