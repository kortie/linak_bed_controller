package dev.linakbed.domain.preset

import dev.linakbed.protocol.PresetSlot

/**
 * 프리셋 이름 규칙. 원격 명령의 이름 매칭(02 문서 Part B §1)이 모호해지지 않도록:
 * 앞뒤 공백 제거 후 1~[MAX_LENGTH]자, 줄바꿈 불가, 다른 슬롯과 중복 불가(정규화 후 비교), 숫자만으로 된 이름 불가.
 */
object PresetName {
    const val MAX_LENGTH = 20
    private val WHITESPACE = Regex("[\\s\\u3000]+")
    private val DIGITS_ONLY = Regex("^[0-9]+$")

    fun defaultFor(slot: PresetSlot): String = "프리셋 ${slot.number}"

    /** 비교·매칭용 정규화: 전각 공백 포함 연속 공백을 하나로, 앞뒤 공백 제거, 소문자. S06 파서도 같은 함수를 쓴다. */
    fun normalize(name: String): String = name.replace(WHITESPACE, " ").trim().lowercase()

    sealed interface Result {
        /** [name]은 앞뒤 공백을 지운 저장할 값. */
        data class Valid(
            val name: String,
        ) : Result

        data class Invalid(
            val reason: Reason,
        ) : Result
    }

    sealed interface Reason {
        data object Blank : Reason

        data object TooLong : Reason

        data object LineBreak : Reason

        data object DigitsOnly : Reason

        data class Duplicate(
            val slot: PresetSlot,
        ) : Reason
    }

    /** [current]는 지금 저장된 모든 슬롯 이름 ([slot] 자신의 이름은 중복 검사에서 제외). */
    fun validate(
        input: String,
        slot: PresetSlot,
        current: Map<PresetSlot, String>,
    ): Result {
        val trimmed = input.trim()
        val normalized = normalize(trimmed)
        val duplicate = current.entries.firstOrNull { (other, name) -> other != slot && normalize(name) == normalized }?.key
        val reason =
            when {
                trimmed.isEmpty() -> Reason.Blank
                trimmed.length > MAX_LENGTH -> Reason.TooLong
                trimmed.any { it == '\n' || it == '\r' } -> Reason.LineBreak
                DIGITS_ONLY.matches(normalized) -> Reason.DigitsOnly
                duplicate != null -> Reason.Duplicate(duplicate)
                else -> null
            }
        return if (reason == null) Result.Valid(trimmed) else Result.Invalid(reason)
    }
}
