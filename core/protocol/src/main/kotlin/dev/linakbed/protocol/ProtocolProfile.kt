package dev.linakbed.protocol

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 명령을 1회만 쓰면 되는지, 동작 내내 주기적으로 다시 써야 하는지 (ADR-004). 해석은 `MotionRunner`만 한다. */
sealed interface CommandTiming {
    data object OneShot : CommandTiming

    data class Repeat(
        val interval: Duration,
    ) : CommandTiming
}

/** 실기기마다 다를 수 있는 프로토콜 동작 값. 코드에 하드코딩하지 않고 여기로 모은다. */
data class ProtocolProfile(
    val moveTiming: CommandTiming,
    val presetTiming: CommandTiming,
    val lightTiming: CommandTiming,
    /** 제어 특성 write type (with/without response). */
    val writeWithResponse: Boolean,
    val calibration: SensorCalibration,
) {
    companion object {
        private val DEFAULT_REPEAT_INTERVAL = 200.milliseconds

        /** README 기반 잠정값, 근거는 docs/spec/hw-findings.md. 실측(S08) 후 갱신한다. */
        val DEFAULT =
            ProtocolProfile(
                moveTiming = CommandTiming.Repeat(DEFAULT_REPEAT_INTERVAL),
                presetTiming = CommandTiming.Repeat(DEFAULT_REPEAT_INTERVAL),
                lightTiming = CommandTiming.OneShot,
                writeWithResponse = true,
                calibration = SensorCalibration.DEFAULT,
            )
    }
}
