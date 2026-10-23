package dev.linakbed.app.simbed

import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.CommandTiming
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.LinakSensorDecoder
import dev.linakbed.protocol.PresetSlot
import dev.linakbed.protocol.ProtocolProfile

/**
 * 디버그 빌드 전용 가상 침대의 동작 모델 (시간은 호출자가 ms로 넘긴다).
 * 실제 Linak처럼 [ProtocolProfile]의 타이밍을 따른다: Repeat이면 명령이 계속 들어오는 동안만 움직이고,
 * OneShot이면 Stop·목표 도달·끝까지 움직인다. 센서 값은 실제 디코더와 같은 보정식으로 만든다.
 */
class SimulatedBed(
    private val profile: ProtocolProfile,
) {
    data class Position(
        val head: Int,
        val feet: Int,
    )

    data class Snapshot(
        val position: Position,
        val lightOn: Boolean,
        val presets: Map<PresetSlot, Position>,
        /** 지금 움직이고 있는 명령 (없으면 null). */
        val moving: BedCommand?,
    )

    private var position = Position(head = 0, feet = 0)
    private var lightOn = false
    private val presets = DEFAULT_PRESETS.toMutableMap()
    private var active: BedCommand? = null
    private var lastWriteMs = 0L

    fun snapshot(nowMs: Long): Snapshot = Snapshot(position, lightOn, presets.toMap(), active?.takeIf { holding(it, nowMs) })

    fun readings(): List<HeightReading> =
        listOf(
            LinakSensorDecoder.fromRaw(Actuator.HEAD, position.head, null, profile.calibration),
            LinakSensorDecoder.fromRaw(Actuator.FEET, position.feet, null, profile.calibration),
        )

    fun accept(
        command: BedCommand,
        nowMs: Long,
    ) {
        when (command) {
            is BedCommand.Move, is BedCommand.RecallPreset -> {
                active = command
                lastWriteMs = nowMs
            }
            BedCommand.Stop -> active = null
            is BedCommand.SavePreset -> presets[command.slot] = position
            is BedCommand.Light ->
                lightOn =
                    when (command.action) {
                        LightAction.ON -> true
                        LightAction.OFF -> false
                        LightAction.TOGGLE -> !lightOn
                    }
        }
    }

    /** 연결이 끊기면 컨트롤러가 명령을 보내지 못하므로 움직임도 멈춘다. */
    fun halt() {
        active = null
    }

    /** 한 틱 진행. 위치가 바뀌었으면 true. */
    fun tick(nowMs: Long): Boolean {
        val command = active?.takeIf { holding(it, nowMs) }
        val next = command?.let(::step) ?: position
        // 명령이 끊겼거나 끝·목표에 닿으면 실제 침대처럼 그 자리에 멈춘다 (센서가 더 변하지 않음).
        if (next == position) active = null
        val moved = next != position
        position = next
        return moved
    }

    private fun step(command: BedCommand): Position =
        when (command) {
            is BedCommand.Move -> move(command.actuator, command.direction)
            is BedCommand.RecallPreset -> toward(presets.getValue(command.slot))
            else -> position
        }

    private fun holding(
        command: BedCommand,
        nowMs: Long,
    ): Boolean {
        val timing = if (command is BedCommand.RecallPreset) profile.presetTiming else profile.moveTiming
        return when (timing) {
            CommandTiming.OneShot -> true
            is CommandTiming.Repeat -> nowMs - lastWriteMs <= timing.interval.inWholeMilliseconds * HOLD_FACTOR
        }
    }

    private fun move(
        actuator: Actuator,
        direction: Direction,
    ): Position {
        val sign = if (direction == Direction.UP) 1 else -1
        val head = if (actuator != Actuator.FEET) position.head + sign * HEAD_STEP else position.head
        val feet = if (actuator != Actuator.HEAD) position.feet + sign * FEET_STEP else position.feet
        return Position(head.coerceIn(0, HEAD_MAX), feet.coerceIn(0, FEET_MAX))
    }

    private fun toward(target: Position) =
        Position(
            head = approach(position.head, target.head, HEAD_STEP),
            feet = approach(position.feet, target.feet, FEET_STEP),
        )

    private fun approach(
        from: Int,
        to: Int,
        step: Int,
    ): Int = if (from < to) minOf(from + step, to) else maxOf(from - step, to)

    companion object {
        /** 한 틱 간격. 끝에서 끝까지 약 20초. */
        const val TICK_MS = 100L
        const val HEAD_MAX = 1500
        const val FEET_MAX = 1800
        private const val HEAD_STEP = 8
        private const val FEET_STEP = 9

        /** Repeat 주기의 몇 배 동안 명령이 없으면 멈추는가 (전송 지연 여유). */
        private const val HOLD_FACTOR = 2

        val DEFAULT_PRESETS =
            mapOf(
                PresetSlot.P1 to Position(head = 0, feet = 0),
                PresetSlot.P2 to Position(head = 750, feet = 300),
                PresetSlot.P3 to Position(head = 1500, feet = 0),
                PresetSlot.P4 to Position(head = 400, feet = 900),
            )
    }
}
