package dev.linakbed.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.linakbed.app.R
import dev.linakbed.domain.BedError
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.safety.SafetyReason

/** [BedError] → 사용자 문구의 단일 매핑 지점 (linak-conventions §3). */
object BedErrorMessages {
    /** 문구 리소스와 (있으면) 인자 하나. */
    data class ErrorText(
        @param:StringRes val res: Int,
        val arg: Any? = null,
    )

    fun of(error: BedError): ErrorText =
        when (error) {
            BedError.NotPaired -> ErrorText(R.string.error_not_paired)
            BedError.BluetoothOff -> ErrorText(R.string.error_bluetooth_off)
            is BedError.PermissionMissing -> ErrorText(R.string.error_permission_missing)
            BedError.ConnectTimeout -> ErrorText(R.string.error_connect_timeout)
            BedError.Disconnected -> ErrorText(R.string.error_disconnected)
            is BedError.Busy ->
                when (error.activeSource) {
                    CommandSource.MANUAL -> ErrorText(R.string.error_busy_manual)
                    CommandSource.REMOTE -> ErrorText(R.string.error_busy_remote)
                }
            is BedError.SafetyStop ->
                when (error.reason) {
                    SafetyReason.MAX_DURATION -> ErrorText(R.string.error_safety_max_duration)
                    SafetyReason.DISCONNECTED -> ErrorText(R.string.error_safety_disconnected)
                    SafetyReason.STALL -> ErrorText(R.string.error_safety_stall)
                }
            is BedError.WriteFailed -> ErrorText(R.string.error_write_failed, error.gattStatus)
            is BedError.GattFailure -> ErrorText(R.string.error_gatt_failure, error.stage.name)
            is BedError.Interrupted ->
                when (error.by) {
                    CommandSource.MANUAL -> ErrorText(R.string.error_interrupted_manual)
                    CommandSource.REMOTE -> ErrorText(R.string.error_interrupted_remote)
                }
        }
}

@Composable
fun BedError.message(): String {
    val text = BedErrorMessages.of(this)
    return if (text.arg == null) stringResource(text.res) else stringResource(text.res, text.arg)
}
