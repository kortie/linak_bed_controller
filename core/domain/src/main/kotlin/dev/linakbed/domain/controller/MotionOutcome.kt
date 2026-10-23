package dev.linakbed.domain.controller

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.safety.SafetyReason

/** 모터 동작 하나가 끝난 이유. */
internal sealed interface MotionOutcome {
    /** 프리셋 도착(센서 안정) 또는 센서 없는 프리셋의 고정 대기 종료. */
    data object Completed : MotionOutcome

    data class SafetyStopped(
        val reason: SafetyReason,
    ) : MotionOutcome

    data class Failed(
        val error: BedError,
    ) : MotionOutcome

    data class Interrupted(
        val reason: InterruptReason,
    ) : MotionOutcome
}

internal sealed interface InterruptReason {
    /** S3: 수동 이동 버튼에서 손을 뗌. */
    data object Released : InterruptReason

    /** 중재 표에 따라 새 동작으로 교체됨. */
    data class Replaced(
        val by: CommandSource,
    ) : InterruptReason

    /** STOP 요청. Stop은 `stop()`이 직접 보낸다. */
    data class Stopped(
        val by: CommandSource,
    ) : InterruptReason
}

/** STOP으로 끝난 경우만 동작 쪽에서 Stop을 보내지 않는다 (`stop()`이 보냄). */
internal val MotionOutcome.needsStop: Boolean
    get() = !(this is MotionOutcome.Interrupted && reason is InterruptReason.Stopped)

/** status.lastError로 보여 줄 오류 (안전 정지·실패만). */
internal val MotionOutcome.errorOrNull: BedError?
    get() =
        when (this) {
            is MotionOutcome.SafetyStopped -> BedError.SafetyStop(reason)
            is MotionOutcome.Failed -> error
            MotionOutcome.Completed, is MotionOutcome.Interrupted -> null
        }

internal fun MotionOutcome.toResult(): BedResult<Unit> =
    when (this) {
        MotionOutcome.Completed -> BedResult.Ok(Unit)
        is MotionOutcome.SafetyStopped -> BedResult.Err(BedError.SafetyStop(reason))
        is MotionOutcome.Failed -> BedResult.Err(error)
        is MotionOutcome.Interrupted ->
            when (val why = reason) {
                InterruptReason.Released -> BedResult.Ok(Unit)
                is InterruptReason.Replaced -> BedResult.Err(BedError.Interrupted(why.by))
                is InterruptReason.Stopped -> BedResult.Err(BedError.Interrupted(why.by))
            }
    }

/** 실행 기록의 result 문자열. */
internal fun MotionOutcome.describe(): String =
    when (this) {
        MotionOutcome.Completed -> "ok"
        is MotionOutcome.SafetyStopped -> "safety_stop:$reason"
        is MotionOutcome.Failed -> "error:$error"
        is MotionOutcome.Interrupted ->
            when (val why = reason) {
                InterruptReason.Released -> "released"
                is InterruptReason.Replaced -> "interrupted:${why.by}"
                is InterruptReason.Stopped -> "stopped:${why.by}"
            }
    }

/** 쓰기 결과 → 동작을 끝낼 이유 (성공이면 null). 끊김은 S5 안전 정지. */
internal fun BedResult<Unit>.failureOutcome(): MotionOutcome? =
    when (this) {
        is BedResult.Ok -> null
        is BedResult.Err ->
            if (error == BedError.Disconnected) {
                MotionOutcome.SafetyStopped(SafetyReason.DISCONNECTED)
            } else {
                MotionOutcome.Failed(error)
            }
    }
