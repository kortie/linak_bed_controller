package dev.linakbed.domain.remote.notification

import dev.linakbed.domain.log.LogRedaction
import dev.linakbed.domain.port.NotificationChannelSettingsStore
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.preset.PresetName
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommandHandler
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

sealed interface ChannelResult {
    /** 명령이 아님 → 서비스를 시작하지 않고 기록도 하지 않는다. */
    data object Ignore : ChannelResult

    /** 채널 검사에서 거절 (이미 기록됨). */
    data class Rejected(
        val reason: RejectReason,
    ) : ChannelResult

    /** 실행할 요청 → 디스패처로. */
    data class Request(
        val request: RemoteRequest,
    ) : ChannelResult
}

/**
 * SmartThings 알림 채널 층 (02 문서 Part B). 리스너에서 불리며 실행은 하지 않는다.
 * 검사 순서(Part B §3): 허용 패키지 → 파싱(명령 아님이면 무시) → PIN(형식 오류보다 먼저) → 형식 → [RemoteRequest] 생성.
 */
class NotificationChannel(
    private val settingsStore: NotificationChannelSettingsStore,
    private val presetNames: PresetNameStore,
    private val handler: RemoteCommandHandler,
) {
    suspend fun translate(notification: IncomingNotification): ChannelResult {
        val settings = settingsStore.settings().first()
        val text = notification.texts.joinToString("\n")
        val requestId = requestIdOf(notification, text)
        if (notification.packageName !in settings.allowedPackages) {
            return reject(requestId, RejectReason.UNTRUSTED_SOURCE, detail = null)
        }
        val detail = LogRedaction.pin(text, settings.pin)
        return when (val parsed = NotificationCommandParser.parse(text, settings, presetNames.names().first())) {
            ParseResult.NotACommand -> ChannelResult.Ignore
            is ParseResult.Invalid ->
                if (pinMismatch(settings, parsed.pinToken)) {
                    reject(requestId, RejectReason.AUTH_FAILED, detail)
                } else {
                    reject(requestId, RejectReason.INVALID, "$detail (${parsed.reason})")
                }
            is ParseResult.Command ->
                if (pinMismatch(settings, parsed.pinToken)) {
                    reject(requestId, RejectReason.AUTH_FAILED, detail)
                } else {
                    ChannelResult.Request(
                        RemoteRequest(
                            requestId = requestId,
                            channel = CHANNEL,
                            command = parsed.command,
                            receivedAt = notification.postedAt,
                            sentAt = notification.sentAt,
                        ),
                    )
                }
        }
    }

    private fun pinMismatch(
        settings: NotificationChannelSettings,
        pinToken: String?,
    ): Boolean = settings.pin != null && pinToken != settings.pin

    private suspend fun reject(
        requestId: String,
        reason: RejectReason,
        detail: String?,
    ): ChannelResult {
        handler.reject(CHANNEL, requestId, reason, detail)
        return ChannelResult.Rejected(reason)
    }

    companion object {
        val CHANNEL = RemoteChannel.SMARTTHINGS_NOTIFICATION
        private const val REQUEST_ID_LENGTH = 16

        /** Part B §3: sha256(key | postedAt(ms) | 정규화된 알림 텍스트)의 16진수 앞 16자. */
        fun requestIdOf(
            notification: IncomingNotification,
            text: String = notification.texts.joinToString("\n"),
        ): String {
            val source = "${notification.key}|${notification.postedAt.toEpochMilli()}|${PresetName.normalize(text)}"
            val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(REQUEST_ID_LENGTH)
        }
    }
}

/** Part B §4: 실행됨·실패·사용자 취소만 SmartThings 알림을 지운다 (그 외 거절은 사용자가 볼 수 있게 남김). */
fun RemoteOutcome.shouldDismissSource(settings: NotificationChannelSettings): Boolean =
    settings.dismissHandledNotification &&
        when (val r = result) {
            RemoteResult.Executed, is RemoteResult.Failed -> true
            is RemoteResult.Rejected -> r.reason == RejectReason.CANCELLED_BY_USER
        }
