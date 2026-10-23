package dev.linakbed.domain.remote.notification

import dev.linakbed.domain.preset.PresetName
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot

sealed interface ParseResult {
    /** 접두어 없음 → 조용히 무시. */
    data object NotACommand : ParseResult

    data class Command(
        val command: RemoteCommand,
        val pinToken: String?,
    ) : ParseResult

    /** 접두어는 있으나 형식 오류 → 기록. */
    data class Invalid(
        val reason: String,
        val pinToken: String?,
    ) : ParseResult
}

/**
 * `<접두어> [<PIN>] <명령> [<인자>]` 해석 (02 문서 Part B §1). 순수 함수.
 * - 접두어는 대소문자 무시, 앞뒤가 공백·줄 경계여야 한다 (`#BEDROOM` 불일치). 처음 나온 접두어의 그 줄만 본다.
 * - PIN이 설정돼 있으면 접두어 다음 토큰을 **무조건** PIN 자리로 떼어 낸다 (검증은 채널이 한다).
 * - 프리셋 이름은 [PresetName.normalize] 후 완전 일치. 숫자 1~4는 슬롯 번호.
 */
object NotificationCommandParser {
    private val WHITESPACE = Regex("[\\s\\u3000]+")

    fun parse(
        text: String,
        settings: NotificationChannelSettings,
        presetNames: Map<PresetSlot, String>,
    ): ParseResult {
        val tokens = commandLineTokens(text, settings.prefix) ?: return ParseResult.NotACommand
        val pinToken = if (settings.pin != null) tokens.firstOrNull() else null
        val body = if (settings.pin != null) tokens.drop(1) else tokens
        val verb = body.firstOrNull()?.lowercase()
        val args = body.drop(1)
        val command =
            when (verb) {
                null -> null to "missing command"
                in PRESET_VERBS -> preset(args, presetNames)
                in STOP_VERBS -> if (args.isEmpty()) RemoteCommand.Stop to "" else null to "stop takes no argument"
                in LIGHT_VERBS -> light(args)
                else -> null to "unknown command: $verb"
            }
        return command.first?.let { ParseResult.Command(it, pinToken) } ?: ParseResult.Invalid(command.second, pinToken)
    }

    /** 처음 나온 접두어 뒤, 그 줄 끝까지의 토큰. 접두어가 없으면 null. */
    private fun commandLineTokens(
        text: String,
        prefix: String,
    ): List<String>? {
        val line =
            text.lineSequence().firstNotNullOfOrNull { line ->
                val words = line.split(WHITESPACE).filter { it.isNotEmpty() }
                val index = words.indexOfFirst { it.equals(prefix, ignoreCase = true) }
                if (index >= 0) words.drop(index + 1) else null
            }
        return line
    }

    private fun preset(
        args: List<String>,
        presetNames: Map<PresetSlot, String>,
    ): Pair<RemoteCommand?, String> {
        if (args.isEmpty()) return null to "preset needs a slot or name"
        val argument = args.joinToString(" ")
        val number = argument.toIntOrNull()
        val slot =
            if (number != null) {
                PresetSlot.fromNumber(number)
            } else {
                val normalized = PresetName.normalize(argument)
                presetNames.entries.firstOrNull { PresetName.normalize(it.value) == normalized }?.key
            }
        return if (slot == null) null to "unknown preset: $argument" else RemoteCommand.Preset(slot) to ""
    }

    private fun light(args: List<String>): Pair<RemoteCommand?, String> {
        val action = LIGHT_ACTIONS[args.singleOrNull()?.lowercase()]
        return if (action == null) null to "light needs on/off/toggle" else RemoteCommand.Light(action) to ""
    }

    private val PRESET_VERBS = setOf("preset", "프리셋")
    private val STOP_VERBS = setOf("stop", "정지")
    private val LIGHT_VERBS = setOf("light", "조명")
    private val LIGHT_ACTIONS =
        mapOf(
            "on" to LightAction.ON,
            "켜기" to LightAction.ON,
            "off" to LightAction.OFF,
            "끄기" to LightAction.OFF,
            "toggle" to LightAction.TOGGLE,
            "토글" to LightAction.TOGGLE,
        )
}
