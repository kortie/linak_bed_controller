package dev.linakbed.domain.safety

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 안전 규칙 기본값 (아키텍처 §7). 값을 **완화**하는 변경(시간 증가·재시도 감소)은 사용자 확인 없이 하지 않는다.
 * 프리셋 관련 값은 실기기 검증(S08) 전 잠정값.
 */
data class SafetyConfig(
    /** S1: 수동 이동 최대 시간. */
    val manualMaxDuration: Duration = 30.seconds,
    /** S1: 프리셋 이동 최대 시간. */
    val presetMaxDuration: Duration = 60.seconds,
    /** S7: 수동 이동 중 센서가 이 시간 동안 변하지 않으면 정지. 프리셋은 움직임 없이 이 시간이 지나면 완료. */
    val stallTimeout: Duration = 3.seconds,
    /** 프리셋: 움직인 뒤 센서가 이 시간 동안 안정되면 도착으로 본다. */
    val presetSettle: Duration = 1500.milliseconds,
    /** 프리셋: 센서가 없을 때 고정 대기 후 완료. */
    val presetWithoutSensorDuration: Duration = 30.seconds,
    /** S2: Stop 쓰기 실패 시 추가 재시도 횟수와 간격. */
    val stopRetries: Int = 3,
    val stopRetryInterval: Duration = 100.milliseconds,
    /** 동작 전 연결 시간 제한 (실패 시 1회 재시도). */
    val connectTimeout: Duration = 10.seconds,
    /** ADR-003: 마지막 동작 후 이 시간 동안 요청·연결 유지 요청이 없으면 연결 해제. */
    val idleDisconnect: Duration = 60.seconds,
    /** 센서 감시 주기. */
    val monitorInterval: Duration = 100.milliseconds,
)
