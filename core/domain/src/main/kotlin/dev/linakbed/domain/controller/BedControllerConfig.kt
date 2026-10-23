package dev.linakbed.domain.controller

import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.safety.SafetyConfig
import dev.linakbed.protocol.ProtocolProfile
import kotlin.time.TimeSource

data class BedControllerConfig(
    val profile: ProtocolProfile = ProtocolProfile.DEFAULT,
    val safety: SafetyConfig = SafetyConfig(),
)

/**
 * 컨트롤러의 바깥 의존 중 시간·로그.
 * [timeSource]는 경과 시간 측정용(정체·도착 판단), [clock]은 실행 기록의 벽시계 시각용.
 */
data class ControllerEnvironment(
    val clock: Clock,
    val logger: BedLogger,
    val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
)
