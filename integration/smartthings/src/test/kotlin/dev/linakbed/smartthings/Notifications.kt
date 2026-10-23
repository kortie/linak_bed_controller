package dev.linakbed.smartthings

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import dev.linakbed.domain.remote.notification.NotificationChannelSettings

/** 테스트용 StatusBarNotification. 모든 인자에 기본값이 있어 이름 붙여 필요한 것만 바꾼다. */
@Suppress("DEPRECATION", "LongParameterList")
fun statusBarNotification(
    packageName: String = NotificationChannelSettings.SMARTTHINGS_PACKAGE,
    title: String? = "SmartThings",
    text: String? = "#BED preset 2",
    bigText: String? = null,
    lines: Array<CharSequence>? = null,
    whenMillis: Long = 1_000L,
    postTime: Long = 10_000L,
    groupSummary: Boolean = false,
    id: Int = 1,
): StatusBarNotification {
    val extras =
        Bundle().apply {
            title?.let { putCharSequence(Notification.EXTRA_TITLE, it) }
            text?.let { putCharSequence(Notification.EXTRA_TEXT, it) }
            bigText?.let { putCharSequence(Notification.EXTRA_BIG_TEXT, it) }
            lines?.let { putCharSequenceArray(Notification.EXTRA_TEXT_LINES, it) }
        }
    val notification =
        Notification().apply {
            this.extras = extras
            `when` = whenMillis
            if (groupSummary) flags = flags or Notification.FLAG_GROUP_SUMMARY
        }
    return StatusBarNotification(packageName, packageName, id, null, 0, 0, 0, notification, Process.myUserHandle(), postTime)
}
