package dev.linakbed.protocol

import com.google.common.truth.Truth.assertThat
import dev.linakbed.protocol.BedCommand.Light
import dev.linakbed.protocol.BedCommand.Move
import dev.linakbed.protocol.BedCommand.RecallPreset
import dev.linakbed.protocol.BedCommand.SavePreset
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** 기대값은 저장소 루트 README.md의 명령 표를 그대로 옮긴 것 (벡터 파일과 독립적으로 교차 확인). */
class LinakCommandEncoderTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("readmeTable")
    fun `encodes every README command to the exact bytes`(
        command: BedCommand,
        expected: ByteArray,
    ) {
        assertThat(LinakCommandEncoder.encode(command)).isEqualTo(expected)
    }

    @Test
    fun `README table covers every command exactly once`() {
        val tableCommands = readmeTable().map { it.get()[0] as BedCommand }

        assertThat(tableCommands).hasSize(README_COMMAND_COUNT)
        assertThat(tableCommands).containsExactlyElementsIn(BedCommand.ALL)
    }

    @Test
    fun `every encoding is two bytes with a zero parameter byte and all opcodes differ`() {
        val encodings = BedCommand.ALL.map(LinakCommandEncoder::encode)

        encodings.forEach { bytes ->
            assertThat(bytes).hasLength(2)
            assertThat(bytes[1]).isEqualTo(0x00.toByte())
        }
        assertThat(encodings.map { it[0] }.toSet()).hasSize(BedCommand.ALL.size)
    }

    @Test
    fun `returns a fresh array so callers cannot corrupt the table`() {
        val first = LinakCommandEncoder.encode(BedCommand.Stop)
        first[0] = 0x00

        assertThat(LinakCommandEncoder.encode(BedCommand.Stop)).isEqualTo(bytes(0xff, 0x00))
        assertThat(LinakCommandEncoder.encode(BedCommand.Stop)).isNotSameInstanceAs(LinakCommandEncoder.encode(BedCommand.Stop))
    }

    @Test
    fun `preset slot numbers map back to slots`() {
        assertThat((1..4).map { PresetSlot.fromNumber(it) }).containsExactlyElementsIn(PresetSlot.entries).inOrder()
        assertThat(PresetSlot.fromNumber(0)).isNull()
        assertThat(PresetSlot.fromNumber(5)).isNull()
    }

    companion object {
        private const val README_COMMAND_COUNT = 18

        private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

        @JvmStatic
        fun readmeTable(): List<Arguments> =
            listOf(
                Move(Actuator.BOTH, Direction.DOWN) to bytes(0x00, 0x00),
                Move(Actuator.BOTH, Direction.UP) to bytes(0x01, 0x00),
                Move(Actuator.FEET, Direction.DOWN) to bytes(0x08, 0x00),
                Move(Actuator.FEET, Direction.UP) to bytes(0x09, 0x00),
                Move(Actuator.HEAD, Direction.DOWN) to bytes(0x0a, 0x00),
                Move(Actuator.HEAD, Direction.UP) to bytes(0x0b, 0x00),
                BedCommand.Stop to bytes(0xff, 0x00),
                RecallPreset(PresetSlot.P1) to bytes(0x0e, 0x00),
                RecallPreset(PresetSlot.P2) to bytes(0x0f, 0x00),
                RecallPreset(PresetSlot.P3) to bytes(0x0c, 0x00),
                RecallPreset(PresetSlot.P4) to bytes(0x44, 0x00),
                SavePreset(PresetSlot.P1) to bytes(0x38, 0x00),
                SavePreset(PresetSlot.P2) to bytes(0x39, 0x00),
                SavePreset(PresetSlot.P3) to bytes(0x3a, 0x00),
                SavePreset(PresetSlot.P4) to bytes(0x45, 0x00),
                Light(LightAction.ON) to bytes(0x92, 0x00),
                Light(LightAction.OFF) to bytes(0x93, 0x00),
                Light(LightAction.TOGGLE) to bytes(0x94, 0x00),
            ).map { (command, expected) -> Arguments.of(command, expected) }
    }
}
