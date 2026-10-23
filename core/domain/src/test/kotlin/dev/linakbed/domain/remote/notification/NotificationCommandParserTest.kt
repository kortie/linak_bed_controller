package dev.linakbed.domain.remote.notification

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** 02 문서 Part B §1의 문법·예시 표. */
class NotificationCommandParserTest {
    private val noPin = NotificationChannelSettings()
    private val withPin = NotificationChannelSettings(pin = "4821")
    private val names =
        mapOf(PresetSlot.P1 to "기상", PresetSlot.P2 to "프리셋 2", PresetSlot.P3 to "Wake Up", PresetSlot.P4 to "프리셋 4")

    private fun parse(
        text: String,
        settings: NotificationChannelSettings = noPin,
    ) = NotificationCommandParser.parse(text, settings, names)

    /** 예시 표에서 파서 결과로 판정되는 행 (PIN 검증 결과인 행은 NotificationChannelTest). */
    @ParameterizedTest(name = "{0}")
    @MethodSource("exampleTable")
    fun `Part B example table`(
        text: String,
        pin: Boolean,
        expected: ParseResult,
    ) {
        val result = parse(text, if (pin) withPin else noPin)
        if (expected is ParseResult.Invalid) {
            assertThat(result).isInstanceOf(ParseResult.Invalid::class.java)
            assertThat((result as ParseResult.Invalid).pinToken).isEqualTo(expected.pinToken)
        } else {
            assertThat(result).isEqualTo(expected)
        }
    }

    @Test
    fun `prefix is case-insensitive and full-width spaces separate tokens`() {
        assertThat(parse("#Bed　preset　2")).isEqualTo(ParseResult.Command(RemoteCommand.Preset(PresetSlot.P2), null))
    }

    @Test
    fun `command on a later line is found, only the first command is used`() {
        val text = "SmartThings\n알림: #BED preset 1\n#BED preset 4"
        assertThat(parse(text)).isEqualTo(ParseResult.Command(RemoteCommand.Preset(PresetSlot.P1), null))
    }

    @Test
    fun `only the line of the prefix is parsed`() {
        assertThat(parse("#BED light\non")).isInstanceOf(ParseResult.Invalid::class.java)
    }

    @Test
    fun `prefix must be a whole word`() {
        assertThat(parse("#BEDROOM preset 1")).isEqualTo(ParseResult.NotACommand)
        assertThat(parse("x#BED preset 1")).isEqualTo(ParseResult.NotACommand)
    }

    @Test
    fun `custom prefix`() {
        val settings = NotificationChannelSettings(prefix = "!침대")
        assertThat(parse("!침대 정지", settings)).isEqualTo(ParseResult.Command(RemoteCommand.Stop, null))
        assertThat(parse("#BED stop", settings)).isEqualTo(ParseResult.NotACommand)
    }

    @Test
    fun `preset names match after normalization, with spaces inside`() {
        assertThat(parse("#BED preset  wake   UP ")).isEqualTo(ParseResult.Command(RemoteCommand.Preset(PresetSlot.P3), null))
        assertThat(parse("#BED 프리셋 프리셋 4")).isEqualTo(ParseResult.Command(RemoteCommand.Preset(PresetSlot.P4), null))
    }

    @Test
    fun `korean aliases`() {
        assertThat(parse("#BED 정지")).isEqualTo(ParseResult.Command(RemoteCommand.Stop, null))
        assertThat(parse("#BED 조명 켜기")).isEqualTo(ParseResult.Command(RemoteCommand.Light(LightAction.ON), null))
        assertThat(parse("#BED 조명 끄기")).isEqualTo(ParseResult.Command(RemoteCommand.Light(LightAction.OFF), null))
        assertThat(parse("#BED 조명 토글")).isEqualTo(ParseResult.Command(RemoteCommand.Light(LightAction.TOGGLE), null))
        assertThat(parse("#BED light toggle")).isEqualTo(ParseResult.Command(RemoteCommand.Light(LightAction.TOGGLE), null))
    }

    @Test
    fun `malformed commands are invalid`() {
        listOf("#BED", "#BED preset", "#BED stop now", "#BED light", "#BED light bright", "#BED preset 0", "#BED preset 없는이름")
            .forEach { assertThat(parse(it)).isInstanceOf(ParseResult.Invalid::class.java) }
    }

    @Test
    fun `with a PIN the first token is always taken as the PIN`() {
        assertThat(parse("#BED", withPin)).isEqualTo(ParseResult.Invalid("missing command", null))
        assertThat(parse("#BED 4821", withPin)).isEqualTo(ParseResult.Invalid("missing command", "4821"))
        assertThat(parse("#BED stop", withPin)).isEqualTo(ParseResult.Invalid("missing command", "stop"))
    }

    companion object {
        @JvmStatic
        fun exampleTable(): List<Arguments> =
            listOf(
                Arguments.of("#BED preset 2", false, ParseResult.Command(RemoteCommand.Preset(PresetSlot.P2), null)),
                Arguments.of("#bed 프리셋 기상", false, ParseResult.Command(RemoteCommand.Preset(PresetSlot.P1), null)),
                Arguments.of("아침 루틴 실행됨 #BED light on", false, ParseResult.Command(RemoteCommand.Light(LightAction.ON), null)),
                Arguments.of("#BED 4821 preset 3", true, ParseResult.Command(RemoteCommand.Preset(PresetSlot.P3), "4821")),
                // PIN 설정 상태: 'preset'이 PIN 자리 → 채널이 AUTH_FAILED로 판정 (NotificationChannelTest)
                Arguments.of("#BED preset 3", true, ParseResult.Invalid("", "preset")),
                Arguments.of("#BED 0000 move up", true, ParseResult.Invalid("", "0000")),
                Arguments.of("#BED preset 7", false, ParseResult.Invalid("", null)),
                Arguments.of("#BED move up", false, ParseResult.Invalid("", null)),
                Arguments.of("현관문이 열렸습니다", false, ParseResult.NotACommand),
            )
    }
}
