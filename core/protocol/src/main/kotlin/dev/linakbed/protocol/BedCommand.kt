package dev.linakbed.protocol

enum class Actuator { HEAD, FEET, BOTH }

enum class Direction { UP, DOWN }

enum class PresetSlot(
    val number: Int,
) {
    P1(1),
    P2(2),
    P3(3),
    P4(4),
    ;

    companion object {
        fun fromNumber(number: Int): PresetSlot? = entries.firstOrNull { it.number == number }
    }
}

enum class LightAction { ON, OFF, TOGGLE }

/** 침대 컨트롤러에 보낼 수 있는 명령 전체. 바이트 표현은 [LinakCommandEncoder]에만 있다. */
sealed interface BedCommand {
    data class Move(
        val actuator: Actuator,
        val direction: Direction,
    ) : BedCommand

    data object Stop : BedCommand

    data class RecallPreset(
        val slot: PresetSlot,
    ) : BedCommand

    data class SavePreset(
        val slot: PresetSlot,
    ) : BedCommand

    data class Light(
        val action: LightAction,
    ) : BedCommand

    companion object {
        /** 가능한 모든 명령 (README 표의 18개). 벡터 파일·Protocol Lab이 "빠짐없이"를 확인하는 기준. */
        val ALL: List<BedCommand> =
            Actuator.entries.flatMap { actuator -> Direction.entries.map { Move(actuator, it) } } +
                Stop +
                PresetSlot.entries.map(::RecallPreset) +
                PresetSlot.entries.map(::SavePreset) +
                LightAction.entries.map(::Light)
    }
}
