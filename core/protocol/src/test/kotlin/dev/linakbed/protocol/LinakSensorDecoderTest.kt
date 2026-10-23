package dev.linakbed.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LinakSensorDecoderTest {
    private val calibration = SensorCalibration.DEFAULT

    private fun decode(
        actuator: Actuator,
        hex: String,
        calibration: SensorCalibration = this.calibration,
    ) = LinakSensorDecoder.decode(actuator, hex.hexToBytes(), calibration)

    @Test
    fun `zero payload decodes to zero height`() {
        val reading = decode(Actuator.HEAD, "0000")!!

        assertThat(reading.raw).isEqualTo(0)
        assertThat(reading.cm).isEqualTo(0.0)
        assertThat(reading.speedCmPerMin).isNull()
    }

    @Test
    fun `bytes are little-endian`() {
        // 0x03e8 = 1000 → 1000 / 5 / 5.0 = 40.0 cm
        val reading = decode(Actuator.HEAD, "e803")!!

        assertThat(reading.raw).isEqualTo(1000)
        assertThat(reading.cm).isWithin(TOLERANCE).of(40.0)
    }

    @Test
    fun `values are unsigned - high bit in either byte never goes negative`() {
        assertThat(decode(Actuator.HEAD, "8000")!!.raw).isEqualTo(128)
        assertThat(decode(Actuator.FEET, "0080")!!.raw).isEqualTo(32768)
        assertThat(decode(Actuator.HEAD, "ffff")!!.raw).isEqualTo(65535)
    }

    @Test
    fun `head and feet use their own divisors`() {
        assertThat(decode(Actuator.HEAD, "e803")!!.cm).isWithin(TOLERANCE).of(1000.0 / 5 / 5.0)
        assertThat(decode(Actuator.FEET, "e803")!!.cm).isWithin(TOLERANCE).of(1000.0 / 6 / 5.0)
    }

    @Test
    fun `uses real division, not README integer division`() {
        // raw 7: 정수 나눗셈이면 (7 / 5) / 5.0 = 0.2, 실수 나눗셈이면 7 / 5 / 5.0 = 0.28
        assertThat(decode(Actuator.HEAD, "0700")!!.cm).isWithin(TOLERANCE).of(0.28)
    }

    @Test
    fun `payload shorter than two bytes is rejected`() {
        assertThat(decode(Actuator.HEAD, "")).isNull()
        assertThat(decode(Actuator.FEET, "e8")).isNull()
    }

    @Test
    fun `speed is decoded only when four bytes are present`() {
        assertThat(decode(Actuator.HEAD, "e80310")!!.speedCmPerMin).isNull()
        // 0x2710 = 10000 → 100.0 cm/min
        assertThat(decode(Actuator.HEAD, "e8031027")!!.speedCmPerMin).isWithin(TOLERANCE).of(100.0)
    }

    @Test
    fun `result follows calibration constants`() {
        val custom = SensorCalibration(headDivisor = 10, feetDivisor = 4, scale = 2.0, speedDivisor = 10.0)

        val head = decode(Actuator.HEAD, "e8031027", custom)!!
        val feet = decode(Actuator.FEET, "e803", custom)!!

        assertThat(head.cm).isWithin(TOLERANCE).of(50.0)
        assertThat(head.speedCmPerMin).isWithin(TOLERANCE).of(1000.0)
        assertThat(feet.cm).isWithin(TOLERANCE).of(125.0)
    }

    @Test
    fun `reading carries the actuator it was decoded for`() {
        assertThat(decode(Actuator.FEET, "e803")!!.actuator).isEqualTo(Actuator.FEET)
    }

    @Test
    fun `BOTH has no height sensor`() {
        assertThrows<IllegalArgumentException> { decode(Actuator.BOTH, "e803") }
    }

    @Test
    fun `fromRaw matches decode for the same values`() {
        assertThat(LinakSensorDecoder.fromRaw(Actuator.FEET, 1000, 250, calibration)).isEqualTo(decode(Actuator.FEET, "e803fa00"))
        assertThat(LinakSensorDecoder.fromRaw(Actuator.HEAD, 1000, null, calibration)).isEqualTo(decode(Actuator.HEAD, "e803"))
        assertThrows<IllegalArgumentException> { LinakSensorDecoder.fromRaw(Actuator.BOTH, 1, null, calibration) }
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
