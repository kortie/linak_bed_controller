package dev.linakbed.domain.remote.notification

import java.time.Instant

/** integration:smartthings가 StatusBarNotification에서 뽑아 넘기는 값 (허용 패키지의 알림만). */
data class IncomingNotification(
    val packageName: String,
    /** StatusBarNotification.key */
    val key: String,
    val postedAt: Instant,
    /** Notification.when (0이면 null). SmartThings가 보낸 시각을 넣는지는 실측 항목(HW-S07). */
    val sentAt: Instant?,
    /** title, text, bigText, textLines … 순서대로. */
    val texts: List<String>,
)
