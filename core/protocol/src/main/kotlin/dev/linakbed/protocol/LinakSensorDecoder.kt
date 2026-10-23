package dev.linakbed.protocol

/**
 * 센서 보정 상수. README 식: 머리 `raw / 5 / 5.0`, 발 `raw / 6 / 5.0` cm, 속도 `raw / 100.0` cm/min.
 * README의 첫 나눗셈이 정수 나눗셈인지는 미검증이므로 실수 나눗셈으로 계산한다 (docs/spec/hw-findings.md).
 */
data class SensorCalibration(
    val headDivisor: Int,
    val feetDivisor: Int,
    val scale: Double,
    val speedDivisor: Double,
) {
    companion object {
        /** README 기반 잠정값. 근거는 docs/spec/hw-findings.md. */
        val DEFAULT = SensorCalibration(headDivisor = 5, feetDivisor = 6, scale = 5.0, speedDivisor = 100.0)
    }
}

data class HeightReading(
    val actuator: Actuator,
    val raw: Int,
    val cm: Double,
    val speedCmPerMin: Double?,
)

/** 높이 센서 notify/read 값 디코딩. little-endian unsigned 16비트: `[lo, hi, (speedLo, speedHi)]`. */
object LinakSensorDecoder {
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val HEIGHT_OFFSET = 0
    private const val SPEED_OFFSET = 2
    private const val MIN_PAYLOAD_SIZE = 2
    private const val PAYLOAD_SIZE_WITH_SPEED = 4

    /**
     * @param actuator 센서는 머리·발에만 있으므로 [Actuator.HEAD] 또는 [Actuator.FEET].
     * @return payload가 2바이트 미만이면 null.
     */
    fun decode(
        actuator: Actuator,
        payload: ByteArray,
        calibration: SensorCalibration,
    ): HeightReading? {
        require(actuator != Actuator.BOTH) { "height sensors exist only for HEAD and FEET" }
        if (payload.size < MIN_PAYLOAD_SIZE) return null
        val speedRaw = if (payload.size >= PAYLOAD_SIZE_WITH_SPEED) payload.uint16At(SPEED_OFFSET) else null
        return fromRaw(actuator, payload.uint16At(HEIGHT_OFFSET), speedRaw, calibration)
    }

    /** 이미 정수로 읽은 값에서 같은 보정식으로 [HeightReading]을 만든다 (디버그 가상 침대도 이것을 쓴다). */
    fun fromRaw(
        actuator: Actuator,
        raw: Int,
        speedRaw: Int?,
        calibration: SensorCalibration,
    ): HeightReading {
        val divisor =
            when (actuator) {
                Actuator.HEAD -> calibration.headDivisor
                Actuator.FEET -> calibration.feetDivisor
                Actuator.BOTH -> throw IllegalArgumentException("height sensors exist only for HEAD and FEET")
            }
        return HeightReading(
            actuator = actuator,
            raw = raw,
            cm = raw.toDouble() / divisor / calibration.scale,
            speedCmPerMin = speedRaw?.let { it / calibration.speedDivisor },
        )
    }

    private fun ByteArray.uint16At(offset: Int): Int =
        (this[offset + 1].toInt() and BYTE_MASK shl BITS_PER_BYTE) or (this[offset].toInt() and BYTE_MASK)
}
