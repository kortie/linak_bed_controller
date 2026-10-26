package dev.linakbed.domain.remote

import dev.linakbed.domain.port.Clock
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * S8 공통 수용 정책 (02 문서 A3). 순서대로 검사해 처음 걸리는 사유로 거절한다:
 * 요청 채널의 스위치 꺼짐 → 신선도 → 같은 requestId 중복(10분) → 속도 제한(1분). 중복·속도 제한은 채널 구분 없이 함께 센다.
 * 중복·속도 제한을 위해 최근 수락 기록을 메모리에 둔다 (프로세스가 재시작돼도 신선도 검사가 오래된 요청을 막는다).
 */
class RemoteCommandPolicy(
    private val settings: () -> RemoteSettings,
    private val clock: Clock,
) {
    private val accepted = ArrayDeque<Pair<String, Instant>>()

    @Synchronized
    fun evaluate(request: RemoteRequest): PolicyDecision {
        val current = settings()
        val now = clock.now()
        prune(now)
        val reason =
            when {
                !current.isEnabled(request.channel) -> RejectReason.DISABLED
                isStale(request, current, now) -> RejectReason.STALE
                accepted.any { it.first == request.requestId } -> RejectReason.DUPLICATE
                acceptedWithin(now, RATE_WINDOW) >= current.rateLimitPerMinute -> RejectReason.RATE_LIMITED
                else -> null
            }
        if (reason != null) return PolicyDecision.Reject(reason)
        accepted.addLast(request.requestId to now)
        return PolicyDecision.Accept
    }

    /** 보낸 시각이 받은 시각보다 미래면(시계 차이) 받은 시각을 쓴다. 정확히 freshness는 수락. */
    private fun isStale(
        request: RemoteRequest,
        settings: RemoteSettings,
        now: Instant,
    ): Boolean {
        val reference = request.sentAt?.takeUnless { it.isAfter(request.receivedAt) } ?: request.receivedAt
        return Duration.between(reference, now) > settings.freshness.toJavaDuration()
    }

    private fun acceptedWithin(
        now: Instant,
        window: Duration,
    ): Int = accepted.count { Duration.between(it.second, now) < window }

    private fun prune(now: Instant) {
        while (accepted.isNotEmpty() && Duration.between(accepted.first().second, now) >= DUPLICATE_WINDOW) accepted.removeFirst()
    }

    private companion object {
        val DUPLICATE_WINDOW: Duration = 10.minutes.toJavaDuration()
        val RATE_WINDOW: Duration = 1.minutes.toJavaDuration()
    }
}
