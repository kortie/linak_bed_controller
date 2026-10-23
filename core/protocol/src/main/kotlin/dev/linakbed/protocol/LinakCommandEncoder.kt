package dev.linakbed.protocol

/**
 * [BedCommand] → 제어 특성에 쓸 2바이트 (`[opcode, 0x00]`).
 *
 * 명령 바이트 표는 **이 파일에만** 있다 (linak-ble-protocol §3). 프리셋 번호와 opcode는 순차적이지 않으므로
 * 계산하지 않고 표로 둔다. 값은 README 기준 잠정값이며, 근거와 실측 결과는 docs/spec/hw-findings.md,
 * 언어 독립 기준은 `linak-protocol-vectors.json`(테스트가 양방향 일치를 강제).
 */
object LinakCommandEncoder {
    private const val MOVE_BOTH_DOWN = 0x00
    private const val MOVE_BOTH_UP = 0x01
    private const val MOVE_FEET_DOWN = 0x08
    private const val MOVE_FEET_UP = 0x09
    private const val MOVE_HEAD_DOWN = 0x0a
    private const val MOVE_HEAD_UP = 0x0b
    private const val STOP = 0xff
    private const val RECALL_P1 = 0x0e
    private const val RECALL_P2 = 0x0f
    private const val RECALL_P3 = 0x0c
    private const val RECALL_P4 = 0x44
    private const val SAVE_P1 = 0x38
    private const val SAVE_P2 = 0x39
    private const val SAVE_P3 = 0x3a
    private const val SAVE_P4 = 0x45
    private const val LIGHT_ON = 0x92
    private const val LIGHT_OFF = 0x93
    private const val LIGHT_TOGGLE = 0x94
    private const val PARAMETER = 0x00

    /** 매 호출 새 배열을 돌려준다 (호출자가 바꿔도 표에 영향 없음). */
    fun encode(command: BedCommand): ByteArray = byteArrayOf(opcode(command).toByte(), PARAMETER.toByte())

    private fun opcode(command: BedCommand): Int =
        when (command) {
            is BedCommand.Move -> moveOpcode(command.actuator, command.direction)
            BedCommand.Stop -> STOP
            is BedCommand.RecallPreset -> recallOpcode(command.slot)
            is BedCommand.SavePreset -> saveOpcode(command.slot)
            is BedCommand.Light -> lightOpcode(command.action)
        }

    private fun moveOpcode(
        actuator: Actuator,
        direction: Direction,
    ): Int =
        when (actuator) {
            Actuator.BOTH ->
                when (direction) {
                    Direction.UP -> MOVE_BOTH_UP
                    Direction.DOWN -> MOVE_BOTH_DOWN
                }
            Actuator.FEET ->
                when (direction) {
                    Direction.UP -> MOVE_FEET_UP
                    Direction.DOWN -> MOVE_FEET_DOWN
                }
            Actuator.HEAD ->
                when (direction) {
                    Direction.UP -> MOVE_HEAD_UP
                    Direction.DOWN -> MOVE_HEAD_DOWN
                }
        }

    private fun recallOpcode(slot: PresetSlot): Int =
        when (slot) {
            PresetSlot.P1 -> RECALL_P1
            PresetSlot.P2 -> RECALL_P2
            PresetSlot.P3 -> RECALL_P3
            PresetSlot.P4 -> RECALL_P4
        }

    private fun saveOpcode(slot: PresetSlot): Int =
        when (slot) {
            PresetSlot.P1 -> SAVE_P1
            PresetSlot.P2 -> SAVE_P2
            PresetSlot.P3 -> SAVE_P3
            PresetSlot.P4 -> SAVE_P4
        }

    private fun lightOpcode(action: LightAction): Int =
        when (action) {
            LightAction.ON -> LIGHT_ON
            LightAction.OFF -> LIGHT_OFF
            LightAction.TOGGLE -> LIGHT_TOGGLE
        }
}
