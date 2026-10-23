package dev.linakbed.domain.remote

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 모든 원격 채널에 공통인 설정 (02 문서 A3·A4). */
data class RemoteSettings(
    /** 원격 명령 기능. 사용자가 켜야 한다. */
    val enabled: Boolean = false,
    /** S8: 보낸(없으면 받은) 시각이 이보다 오래되면 거절. */
    val freshness: Duration = 120.seconds,
    /** S8: 1분에 수락하는 최대 명령 수. */
    val rateLimitPerMinute: Int = 6,
    /** S9: 원격 프리셋 호출 전 취소 기회 (0 = 끔, 최대 [MAX_PRE_EXECUTION_DELAY]). */
    val preExecutionDelay: Duration = 10.seconds,
    val notifyOnSuccess: Boolean = false,
) {
    companion object {
        val MAX_PRE_EXECUTION_DELAY = 60.seconds
    }
}
