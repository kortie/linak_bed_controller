package dev.linakbed.smartthings

import android.service.notification.StatusBarNotification
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.debug
import dev.linakbed.domain.log.info
import dev.linakbed.domain.port.NotificationChannelSettingsStore
import dev.linakbed.domain.port.RemoteRequestDispatcher
import dev.linakbed.domain.remote.notification.ChannelResult
import dev.linakbed.domain.remote.notification.IncomingNotification
import dev.linakbed.domain.remote.notification.NotificationChannel
import dev.linakbed.domain.remote.notification.NotificationChannelSettings
import dev.linakbed.domain.remote.notification.shouldDismissSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 알림 리스너의 로직 (리스너 자체는 얇은 어댑터). 콜백 스레드에서는 확인·추출까지만 하고 나머지는 [scope]로 넘긴다.
 * 1. 허용 패키지가 아니면 **텍스트를 읽지 않고** 바로 반환 (개인정보)
 * 2. 그룹 요약 알림, 리스너 연결 전에 게시된 알림(S8 ⑦)은 무시
 * 3. 채널 검사 → 요청이면 디스패처 → 결과에 따라 원래 알림 삭제 (02 문서 Part B §4)
 */
class NotificationBridge(
    private val channel: NotificationChannel,
    private val dispatcher: RemoteRequestDispatcher,
    settingsStore: NotificationChannelSettingsStore,
    private val scope: CoroutineScope,
    private val logger: BedLogger,
    private val extract: (StatusBarNotification) -> IncomingNotification = NotificationTextExtractor::extract,
) {
    private val settings: StateFlow<NotificationChannelSettings> =
        settingsStore.settings().stateIn(scope, SharingStarted.Eagerly, NotificationChannelSettings())

    @Volatile
    private var connectedAtMillis = Long.MAX_VALUE

    fun onListenerConnected(nowMillis: Long) {
        connectedAtMillis = nowMillis
        logger.info(AREA, "listener connected")
    }

    fun onPosted(
        sbn: StatusBarNotification,
        cancelNotification: (key: String) -> Unit,
    ) {
        if (!accepts(sbn)) return
        val incoming = extract(sbn)
        scope.launch {
            val result = channel.translate(incoming)
            if (result is ChannelResult.Request) {
                val outcome = dispatcher.dispatch(result.request)
                logger.info(AREA, "${result.request.requestId} → ${outcome.result}")
                if (outcome.shouldDismissSource(settings.value)) cancelNotification(incoming.key)
            }
        }
    }

    /** S8: 허용 패키지가 아니면 텍스트를 보기 전에 버린다. 그룹 요약과 연결 이전 게시 알림(S8 ⑦)도 버린다. */
    private fun accepts(sbn: StatusBarNotification): Boolean {
        val stale = sbn.postTime < connectedAtMillis
        if (stale && sbn.packageName in settings.value.allowedPackages) {
            logger.debug(AREA, "ignored notification posted before the listener connected")
        }
        return sbn.packageName in settings.value.allowedPackages && !NotificationTextExtractor.isGroupSummary(sbn) && !stale
    }

    private companion object {
        const val AREA = "Notification"
    }
}

/** Application이 구현한다 — 라이브러리 모듈에 Hilt를 넣지 않고 리스너에 의존성을 넘기기 위함. */
interface NotificationBridgeProvider {
    val notificationBridge: NotificationBridge
}
