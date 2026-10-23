package dev.linakbed.app.feature.remote

import dev.linakbed.app.feature.diagnostics.SystemCheck
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteSettings
import dev.linakbed.domain.remote.notification.NotificationChannelSettings

data class RemoteSettingsUiState(
    val remote: RemoteSettings = RemoteSettings(),
    val channel: NotificationChannelSettings = NotificationChannelSettings(),
    val prefixInput: String = NotificationChannelSettings.DEFAULT_PREFIX,
    val pinInput: String = "",
    val prefixInvalid: Boolean = false,
    val pinInvalid: Boolean = false,
    val checks: Map<SystemCheck, Boolean> = emptyMap(),
    /** 켜려다 막힌 경우 갖춰야 할 항목. 비어 있으면 안내를 숨긴다. */
    val missing: List<SystemCheck> = emptyList(),
    val testerInput: String = "",
    val testerResult: TesterResult? = null,
) {
    /** 지금 설정에 맞춘 루틴 알림 문구 예시. */
    val exampleCommand: String get() = listOfNotNull(channel.prefix, channel.pin, "preset 2").joinToString(" ")
}

sealed interface TesterResult {
    data object NotACommand : TesterResult

    data class Rejected(
        val reason: RejectReason,
    ) : TesterResult

    data class Command(
        val command: RemoteCommand,
    ) : TesterResult
}

sealed interface RemoteSettingsAction {
    data class SetEnabled(
        val enabled: Boolean,
    ) : RemoteSettingsAction

    /** 시스템 설정에서 돌아왔을 때 (ON_RESUME). */
    data object RefreshChecks : RemoteSettingsAction

    data class PrefixChanged(
        val text: String,
    ) : RemoteSettingsAction

    data class PinChanged(
        val text: String,
    ) : RemoteSettingsAction

    data object SaveChannel : RemoteSettingsAction

    data class DelaySelected(
        val seconds: Int,
    ) : RemoteSettingsAction

    data class NotifyOnSuccess(
        val enabled: Boolean,
    ) : RemoteSettingsAction

    data class DismissHandled(
        val enabled: Boolean,
    ) : RemoteSettingsAction

    data class TesterInputChanged(
        val text: String,
    ) : RemoteSettingsAction

    data object RunTester : RemoteSettingsAction
}
