package dev.linakbed.domain.log

import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.remote.RemoteChannel
import java.time.Instant

/**
 * 사용자에게 보이는 실행 기록 한 건. 기록 책임(아키텍처 §5.3): MANUAL 결과·모든 안전 정지는 컨트롤러,
 * 원격 요청의 결과·거절은 원격 핸들러. 같은 사건을 두 곳에서 기록하지 않는다.
 */
data class ExecutionEntry(
    val at: Instant,
    val source: CommandSource,
    val channel: RemoteChannel? = null,
    val requestId: String? = null,
    val action: String,
    val result: String,
    val detail: String? = null,
)
