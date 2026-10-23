package dev.linakbed.domain.remote

import dev.linakbed.domain.BedError
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import java.time.Instant

/**
 * 채널과 무관한 원격 명령 (02 문서 Part A). 연속 이동·프리셋 저장은 **없다** (S4) — 타입으로 막는다.
 * 이 파일(공통 층)은 채널 층(`remote/notification`)을 알지 못한다 (ADR-006).
 */
sealed interface RemoteCommand {
    data class Preset(
        val slot: PresetSlot,
    ) : RemoteCommand

    data object Stop : RemoteCommand

    data class Light(
        val action: LightAction,
    ) : RemoteCommand
}

data class RemoteRequest(
    /** 채널이 부여. 중복 판정·결과 회신 키. */
    val requestId: String,
    val channel: RemoteChannel,
    val command: RemoteCommand,
    val receivedAt: Instant,
    /** 보낸 쪽 시각 (모르면 null). */
    val sentAt: Instant?,
)

sealed interface PolicyDecision {
    data object Accept : PolicyDecision

    data class Reject(
        val reason: RejectReason,
    ) : PolicyDecision
}

enum class RejectReason {
    // 공통 정책 (S8, 02 문서 A3)
    DISABLED,
    STALE,
    DUPLICATE,
    RATE_LIMITED,

    // 실행 전 대기 중 취소·대체 (S9, A4)
    CANCELLED_BY_USER,
    SUPERSEDED,

    // 채널 검사 (S8)
    UNTRUSTED_SOURCE,
    AUTH_FAILED,
    INVALID,
}

data class RemoteOutcome(
    val requestId: String,
    val result: RemoteResult,
)

/** 02 문서 A2 결과 JSON의 executed / failed / rejected와 1:1. */
sealed interface RemoteResult {
    data object Executed : RemoteResult

    data class Failed(
        val error: BedError,
    ) : RemoteResult

    data class Rejected(
        val reason: RejectReason,
    ) : RemoteResult
}

/** 결과 알림을 띄울지 (02 문서 Part B §4): 실패는 항상, 실행됨은 설정에 따라, 거절은 안 띄운다. */
fun RemoteOutcome.shouldNotifyUser(settings: RemoteSettings): Boolean =
    when (result) {
        RemoteResult.Executed -> settings.notifyOnSuccess
        is RemoteResult.Failed -> true
        is RemoteResult.Rejected -> false
    }
