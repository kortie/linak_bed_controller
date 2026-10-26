package dev.linakbed.domain.controller

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.fake.FakeBedController
import dev.linakbed.domain.fake.FakePresetPoseStore
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** S12: 프리셋 도착·저장 때의 높이를 기억한다. */
class PresetPoseRecorderTest {
    private val delegate = FakeBedController()
    private val store = FakePresetPoseStore()
    private val recorder = PresetPoseRecorder(delegate, store, "bed-1")

    private fun at(
        head: Double?,
        feet: Double?,
    ) {
        delegate.status.value = BedStatus(headCm = head, feetCm = feet)
    }

    @Test
    fun `a preset that arrives records the current heights`() =
        runTest {
            at(head = 30.0, feet = 12.0)

            assertThat(recorder.goToPreset(PresetSlot.P2, CommandSource.REMOTE)).isEqualTo(BedResult.Ok(Unit))

            assertThat(store.of("bed-1")).containsExactly(PresetSlot.P2, PresetPose(30.0, 12.0))
            assertThat(delegate.calls).containsExactly("preset P2 REMOTE")
        }

    @Test
    fun `a failed or interrupted preset records nothing`() =
        runTest {
            at(head = 30.0, feet = 12.0)
            delegate.result = BedResult.Err(BedError.Interrupted(CommandSource.MANUAL))

            recorder.goToPreset(PresetSlot.P1, CommandSource.MANUAL)

            assertThat(store.of("bed-1")).isEmpty()
        }

    @Test
    fun `without sensor values nothing is recorded`() =
        runTest {
            at(head = null, feet = 5.0)

            recorder.goToPreset(PresetSlot.P3, CommandSource.MANUAL)

            assertThat(store.of("bed-1")).isEmpty()
        }

    @Test
    fun `saving a preset records the position it was saved at, overwriting the old one`() =
        runTest {
            store.record("bed-1", PresetSlot.P4, PresetPose(1.0, 1.0))
            at(head = 44.0, feet = 20.0)

            recorder.savePreset(PresetSlot.P4)

            assertThat(store.of("bed-1")[PresetSlot.P4]).isEqualTo(PresetPose(44.0, 20.0))
        }

    @Test
    fun `everything else is delegated unchanged`() =
        runTest {
            recorder.startManualMove(Actuator.HEAD, Direction.UP)
            recorder.stop(CommandSource.MANUAL)

            assertThat(delegate.calls).containsExactly("move HEAD UP", "stop MANUAL").inOrder()
            assertThat(recorder.status).isSameInstanceAs(delegate.status)
        }
}
