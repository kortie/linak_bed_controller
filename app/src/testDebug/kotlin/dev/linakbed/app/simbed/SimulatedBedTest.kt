package dev.linakbed.app.simbed

import com.google.common.truth.Truth.assertThat
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.CommandTiming
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import dev.linakbed.protocol.ProtocolProfile
import org.junit.Test

class SimulatedBedTest {
    private val bed = SimulatedBed(ProtocolProfile.DEFAULT)
    private var now = 0L

    /** Repeat 타이밍처럼 200ms마다 같은 명령을 다시 쓰면서 [ticks]번 진행. */
    private fun hold(
        command: BedCommand,
        ticks: Int,
    ) {
        repeat(ticks) {
            if (it % 2 == 0) bed.accept(command, now)
            now += SimulatedBed.TICK_MS
            bed.tick(now)
        }
    }

    private fun idle(ticks: Int) =
        repeat(ticks) {
            now += SimulatedBed.TICK_MS
            bed.tick(now)
        }

    private val position get() = bed.snapshot(now).position

    @Test
    fun `move runs only while the command keeps coming`() {
        hold(BedCommand.Move(Actuator.HEAD, Direction.UP), ticks = 10)
        val moved = position.head
        assertThat(moved).isGreaterThan(0)
        assertThat(position.feet).isEqualTo(0)

        idle(10)

        assertThat(position.head).isAtMost(moved + 2 * 8)
        assertThat(bed.snapshot(now).moving).isNull()
    }

    @Test
    fun `stop halts immediately`() {
        hold(BedCommand.Move(Actuator.BOTH, Direction.UP), ticks = 4)
        val before = position
        bed.accept(BedCommand.Stop, now)

        idle(3)

        assertThat(position).isEqualTo(before)
    }

    @Test
    fun `positions are clamped at the ends`() {
        hold(BedCommand.Move(Actuator.BOTH, Direction.DOWN), ticks = 5)
        assertThat(position).isEqualTo(SimulatedBed.Position(0, 0))

        hold(BedCommand.Move(Actuator.BOTH, Direction.UP), ticks = 400)
        assertThat(position).isEqualTo(SimulatedBed.Position(SimulatedBed.HEAD_MAX, SimulatedBed.FEET_MAX))
    }

    @Test
    fun `preset recall moves to the stored position and stops there`() {
        hold(BedCommand.RecallPreset(PresetSlot.P4), ticks = 200)

        assertThat(position).isEqualTo(SimulatedBed.DEFAULT_PRESETS.getValue(PresetSlot.P4))
        assertThat(bed.snapshot(now).moving).isNull()
        assertThat(bed.tick(now + SimulatedBed.TICK_MS)).isFalse()
    }

    @Test
    fun `save preset stores the current position`() {
        hold(BedCommand.Move(Actuator.FEET, Direction.UP), ticks = 6)
        bed.accept(BedCommand.SavePreset(PresetSlot.P1), now)

        assertThat(bed.snapshot(now).presets[PresetSlot.P1]).isEqualTo(position)
    }

    @Test
    fun `one-shot timing keeps moving until the target without repeats`() {
        val oneShot = SimulatedBed(ProtocolProfile.DEFAULT.copy(presetTiming = CommandTiming.OneShot))
        oneShot.accept(BedCommand.RecallPreset(PresetSlot.P2), 0)

        var t = 0L
        repeat(200) {
            t += SimulatedBed.TICK_MS
            oneShot.tick(t)
        }

        assertThat(oneShot.snapshot(t).position).isEqualTo(SimulatedBed.DEFAULT_PRESETS.getValue(PresetSlot.P2))
    }

    @Test
    fun `light follows on, off and toggle`() {
        bed.accept(BedCommand.Light(LightAction.ON), now)
        assertThat(bed.snapshot(now).lightOn).isTrue()
        bed.accept(BedCommand.Light(LightAction.TOGGLE), now)
        assertThat(bed.snapshot(now).lightOn).isFalse()
    }

    @Test
    fun `readings use the protocol calibration`() {
        hold(BedCommand.RecallPreset(PresetSlot.P2), ticks = 200)

        val head = bed.readings().first { it.actuator == Actuator.HEAD }
        assertThat(head.raw).isEqualTo(750)
        assertThat(head.cm).isEqualTo(750 / 5 / 5.0)
    }
}
