package dev.linakbed.domain.remote.notification

/** SmartThings 알림 채널 전용 설정 (02 문서 Part B). */
data class NotificationChannelSettings(
    val prefix: String = DEFAULT_PREFIX,
    /** 4~8자리 숫자. null이면 PIN을 쓰지 않는다. */
    val pin: String? = null,
    val allowedPackages: Set<String> = setOf(SMARTTHINGS_PACKAGE),
    /** 실행됨·실패·사용자 취소한 요청의 SmartThings 알림을 지운다 (Part B §4). */
    val dismissHandledNotification: Boolean = true,
) {
    companion object {
        const val DEFAULT_PREFIX = "#BED"
        const val SMARTTHINGS_PACKAGE = "com.samsung.android.oneconnect"
        val PIN_PATTERN = Regex("^[0-9]{4,8}$")
    }
}
