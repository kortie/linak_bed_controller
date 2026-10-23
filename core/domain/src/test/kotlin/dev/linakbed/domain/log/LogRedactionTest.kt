package dev.linakbed.domain.log

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LogRedactionTest {
    @Test
    fun `mac keeps first two and last octet`() {
        assertThat(LogRedaction.mac("AA:BB:CC:DD:EE:FF")).isEqualTo("AA:BB:**:**:**:FF")
    }

    @Test
    fun `mac accepts lowercase hex`() {
        assertThat(LogRedaction.mac("a1:b2:c3:d4:e5:f6")).isEqualTo("a1:b2:**:**:**:f6")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "AA:BB:CC:DD:EE", "AA-BB-CC-DD-EE-FF", "AA:BB:CC:DD:EE:GG", "not a mac"])
    fun `mac masks everything when input is not a MAC address`(input: String) {
        assertThat(LogRedaction.mac(input)).isEqualTo("**:**:**:**:**:**")
    }

    @Test
    fun `pin masks the configured pin`() {
        assertThat(LogRedaction.pin("#BED 4821 preset 3", "4821")).isEqualTo("#BED **** preset 3")
    }

    @Test
    fun `pin masks every occurrence`() {
        assertThat(LogRedaction.pin("4821 / 4821", "4821")).isEqualTo("**** / ****")
    }

    @Test
    fun `pin keeps text when no pin is configured`() {
        assertThat(LogRedaction.pin("#BED 4821 preset 3", null)).isEqualTo("#BED 4821 preset 3")
        assertThat(LogRedaction.pin("#BED 4821 preset 3", "")).isEqualTo("#BED 4821 preset 3")
    }
}
