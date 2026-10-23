package dev.linakbed.domain.remote

/** 모든 원격 채널의 단일 진입점: 공통 정책 → (S9 대기) → 컨트롤러 → 실행 기록. */
interface RemoteCommandHandler {
    suspend fun handle(request: RemoteRequest): RemoteOutcome

    /** 채널 검사에서 거절된 요청을 기록한다. [detail]은 채널이 가린(PIN 등) 원문. */
    suspend fun reject(
        channel: RemoteChannel,
        requestId: String,
        reason: RejectReason,
        detail: String?,
    )
}
