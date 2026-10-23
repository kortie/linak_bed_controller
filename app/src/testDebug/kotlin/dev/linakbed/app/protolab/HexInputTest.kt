package dev.linakbed.app.protolab

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexInputTest {
    @Test
    fun `accepts exactly two bytes with any case and spacing`() {
        listOf("0b00", "0B00", "0b 00", "  0b 0 0 ").forEach { input ->
            assertThat(HexInput.parse(input)).isEqualTo(byteArrayOf(0x0b, 0x00))
        }
        assertThat(HexInput.parse("ff00")).isEqualTo(byteArrayOf(0xff.toByte(), 0x00))
    }

    @Test
    fun `rejects anything that is not two bytes of hex`() {
        listOf("", "0b", "0b0", "0b0000", "zz00", "0x0b", "0b-0").forEach { input ->
            assertThat(HexInput.parse(input)).isNull()
        }
    }

    @Test
    fun `format is lowercase two digits per byte`() {
        assertThat(HexInput.format(byteArrayOf(0x0b, 0xff.toByte()))).isEqualTo("0bff")
    }
}
