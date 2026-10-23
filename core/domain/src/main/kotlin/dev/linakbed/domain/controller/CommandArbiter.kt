package dev.linakbed.domain.controller

/**
 * 모터 동작 중재 표 (아키텍처 §6). STOP과 조명은 여기를 거치지 않는다.
 *
 * | 진행 중 \ 신규 | MANUAL  | REMOTE              |
 * |----------------|---------|---------------------|
 * | 없음           | 시작    | 시작                |
 * | MANUAL         | 교체    | 거절 Busy(MANUAL)   |
 * | REMOTE         | 교체    | 교체 (최신 우선)    |
 */
object CommandArbiter {
    sealed interface Decision {
        data object Start : Decision

        data object Replace : Decision

        data class Reject(
            val activeSource: CommandSource,
        ) : Decision
    }

    fun decide(
        active: CommandSource?,
        incoming: CommandSource,
    ): Decision =
        when {
            active == null -> Decision.Start
            incoming.priority < active.priority -> Decision.Reject(active)
            else -> Decision.Replace
        }
}
