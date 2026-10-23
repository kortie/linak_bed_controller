package dev.linakbed.domain.safety

/** 안전 감독이 동작을 멈춘 이유 (아키텍처 §7). */
enum class SafetyReason {
    /** S1: 최대 동작 시간 초과 */
    MAX_DURATION,

    /** S5: 동작 중 연결 끊김 */
    DISCONNECTED,

    /** S7: 센서 정체 */
    STALL,
}
