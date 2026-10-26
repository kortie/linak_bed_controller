package dev.linakbed.domain.remote

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 모든 원격 채널에 공통인 설정 (02 문서 A3·A4). */
data class RemoteSettings(
    /** 앱 바로가기 채널 스위치 (ADR-011). 사용자가 루틴을 직접 만들어야 동작하므로 기본 켬. */
    val appShortcutsEnabled: Boolean = true,
    /** S8: 보낸(없으면 받은) 시각이 이보다 오래되면 거절. */
    val freshness: Duration = 120.seconds,
    /** S8: 1분에 수락하는 최대 명령 수. */
    val rateLimitPerMinute: Int = 6,
    /** S9: 원격 프리셋 호출 전 취소 기회 (0 = 끔, 최대 [MAX_PRE_EXECUTION_DELAY]). */
    val preExecutionDelay: Duration = 10.seconds,
    val notifyOnSuccess: Boolean = false,
) {
    /** 이 채널의 스위치가 켜져 있는가 (S8 첫 검사). */
    fun isEnabled(channel: RemoteChannel): Boolean =
        when (channel) {
            RemoteChannel.SMARTTHINGS_NOTIFICATION -> false // S14에서 제거된 채널 (ADR-013)
            RemoteChannel.APP_SHORTCUT -> appShortcutsEnabled
        }

    companion object {
        val MAX_PRE_EXECUTION_DELAY = 60.seconds
    }
}
