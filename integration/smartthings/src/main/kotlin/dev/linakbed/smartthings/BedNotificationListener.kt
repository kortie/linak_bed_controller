package dev.linakbed.smartthings

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** 시스템이 연결하는 알림 리스너. 로직은 [NotificationBridge]에 있다. */
class BedNotificationListener : NotificationListenerService() {
    private val bridge: NotificationBridge get() = (application as NotificationBridgeProvider).notificationBridge

    override fun onListenerConnected() {
        bridge.onListenerConnected(System.currentTimeMillis())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        bridge.onPosted(sbn) { key -> cancelNotification(key) }
    }
}
