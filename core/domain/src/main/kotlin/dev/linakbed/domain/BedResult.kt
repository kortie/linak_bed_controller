package dev.linakbed.domain

import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.safety.SafetyReason

/** 도메인 경계를 넘는 모든 결과. 실패를 예외로 던지지 않는다 (linak-conventions §3). */
sealed interface BedResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : BedResult<T>

    data class Err(
        val error: BedError,
    ) : BedResult<Nothing>
}

/** 성공이면 [next]로 이어 가고, 실패면 그 실패를 그대로 돌려준다 (단계별 절차를 early return 없이 잇는다). */
inline fun <T, R> BedResult<T>.andThen(next: (T) -> BedResult<R>): BedResult<R> =
    when (this) {
        is BedResult.Ok -> next(value)
        is BedResult.Err -> this
    }

sealed interface BedError {
    data object NotPaired : BedError

    data object BluetoothOff : BedError

    data class PermissionMissing(
        val permission: String,
    ) : BedError

    data object ConnectTimeout : BedError

    data object Disconnected : BedError

    data class Busy(
        val activeSource: CommandSource,
    ) : BedError

    data class SafetyStop(
        val reason: SafetyReason,
    ) : BedError

    data class WriteFailed(
        val gattStatus: Int,
    ) : BedError

    /** 연결 이후 단계의 실패. [gattStatus]가 null이면 해당 단계가 시간 안에 응답하지 않은 것. */
    data class GattFailure(
        val stage: GattStage,
        val gattStatus: Int?,
    ) : BedError

    /** 진행 중 동작이 다른 요청(교체)이나 STOP으로 중단됨. [by]는 중단시킨 쪽의 출처. */
    data class Interrupted(
        val by: CommandSource,
    ) : BedError
}

enum class GattStage { CONNECT, DISCOVER_SERVICES, MISSING_CONTROL_CHARACTERISTIC, ENABLE_NOTIFICATIONS, READ, WRITE }
