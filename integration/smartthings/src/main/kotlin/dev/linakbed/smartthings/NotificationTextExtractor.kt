package dev.linakbed.smartthings

import android.app.Notification
import android.service.notification.StatusBarNotification
import dev.linakbed.domain.remote.notification.IncomingNotification
import java.time.Instant

/**
 * StatusBarNotification → [IncomingNotification] (02 문서 Part B §2). SmartThings 알림 형식이 바뀌면 여기만 고친다.
 * 허용 패키지 확인은 호출 쪽(NotificationBridge)이 먼저 한다 — 이 함수가 불렸다면 이미 허용된 알림이다.
 */
object NotificationTextExtractor {
    fun isGroupSummary(sbn: StatusBarNotification): Boolean = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    fun extract(sbn: StatusBarNotification): IncomingNotification {
        val extras = sbn.notification.extras
        val texts =
            buildList {
                extras.getCharSequence(Notification.EXTRA_TITLE)?.let { add(it.toString()) }
                extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it.toString()) }
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it.toString()) }
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
            }.filter { it.isNotBlank() }.distinct()
        val sentAt =
            sbn.notification.`when`
                .takeIf { it > 0 }
                ?.let(Instant::ofEpochMilli)
        return IncomingNotification(
            packageName = sbn.packageName,
            key = sbn.key,
            postedAt = Instant.ofEpochMilli(sbn.postTime),
            sentAt = sentAt,
            texts = texts,
        )
    }
}
