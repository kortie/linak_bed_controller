package dev.linakbed.domain.preset

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.preset.PresetName.Reason
import dev.linakbed.domain.preset.PresetName.Result
import dev.linakbed.protocol.PresetSlot
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PresetNameTest {
    private val defaults = PresetSlot.entries.associateWith(PresetName::defaultFor)

    private fun validate(
        input: String,
        slot: PresetSlot = PresetSlot.P1,
        current: Map<PresetSlot, String> = defaults,
    ) = PresetName.validate(input, slot, current)

    @Test
    fun `default names are valid and distinct`() {
        PresetSlot.entries.forEach { slot ->
            assertThat(validate(PresetName.defaultFor(slot), slot)).isInstanceOf(Result.Valid::class.java)
        }
        assertThat(defaults.values.toSet()).hasSize(4)
    }

    @Test
    fun `valid names are trimmed`() {
        assertThat(validate("  기상  ")).isEqualTo(Result.Valid("기상"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "　"])
    fun `blank names are rejected`(input: String) {
        assertThat(validate(input)).isEqualTo(Result.Invalid(Reason.Blank))
    }

    @Test
    fun `length limit is 20 characters after trimming`() {
        assertThat(validate("가".repeat(20))).isEqualTo(Result.Valid("가".repeat(20)))
        assertThat(validate("  " + "가".repeat(20) + "  ")).isInstanceOf(Result.Valid::class.java)
        assertThat(validate("가".repeat(21))).isEqualTo(Result.Invalid(Reason.TooLong))
    }

    @Test
    fun `line breaks are rejected`() {
        assertThat(validate("아침\n기상")).isEqualTo(Result.Invalid(Reason.LineBreak))
        assertThat(validate("아침\r기상")).isEqualTo(Result.Invalid(Reason.LineBreak))
    }

    @ParameterizedTest
    @ValueSource(strings = ["1", "4", "12", " 3 "])
    fun `digits-only names are rejected`(input: String) {
        assertThat(validate(input)).isEqualTo(Result.Invalid(Reason.DigitsOnly))
    }

    @Test
    fun `names containing digits and letters are fine`() {
        assertThat(validate("1번 자세")).isEqualTo(Result.Valid("1번 자세"))
    }

    @Test
    fun `duplicates are compared after normalization`() {
        val current = defaults + (PresetSlot.P2 to "Wake  Up")

        assertThat(validate("wake up", PresetSlot.P1, current)).isEqualTo(Result.Invalid(Reason.Duplicate(PresetSlot.P2)))
        assertThat(validate(" WAKE　UP ", PresetSlot.P1, current)).isEqualTo(Result.Invalid(Reason.Duplicate(PresetSlot.P2)))
        assertThat(validate("프리셋  3", PresetSlot.P1)).isEqualTo(Result.Invalid(Reason.Duplicate(PresetSlot.P3)))
    }

    @Test
    fun `keeping the slot's own name is not a duplicate`() {
        assertThat(validate("프리셋 1", PresetSlot.P1)).isEqualTo(Result.Valid("프리셋 1"))
    }

    @Test
    fun `normalize collapses whitespace including full-width and ignores case`() {
        assertThat(PresetName.normalize("  Wake　 Up  ")).isEqualTo("wake up")
    }
}
