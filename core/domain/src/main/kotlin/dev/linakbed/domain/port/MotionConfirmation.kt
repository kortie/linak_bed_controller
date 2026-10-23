package dev.linakbed.domain.port

import dev.linakbed.domain.remote.RemoteRequest
import kotlin.time.Duration

/**
 * 원격 프리셋 실행 전 취소 화면 (S9, ADR-008). 구현은 app (전체 화면 알림 + 큰 취소 버튼).
 * 대기 시간 측정·취소 판단은 도메인이 하고, 이 포트는 화면만 맡는다.
 */
fun interface MotionConfirmation {
    fun show(
        request: RemoteRequest,
        delay: Duration,
    ): PendingConfirmation
}

interface PendingConfirmation {
    /** 사용자가 취소하면 반환한다. */
    suspend fun awaitCancel()

    /** 화면·알림을 닫는다 (실행·취소·대체 모두). */
    fun dismiss()
}
