package dev.linakbed.domain.log

/** 로그·실행 기록 마스킹의 단일 지점 (ADR-007). */
object LogRedaction {
    private const val PIN_MASK = "****"
    private const val INVALID_MAC = "**:**:**:**:**:**"
    private const val MAC_OCTETS = 6
    private val MAC_PATTERN = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

    /** `AA:BB:CC:DD:EE:FF` → `AA:BB:**:**:**:FF`. MAC 형식이 아니면 전부 가린다. */
    fun mac(address: String): String {
        if (!MAC_PATTERN.matches(address)) return INVALID_MAC
        val octets = address.split(':')
        return "${octets.first()}:${octets[1]}:**:**:**:${octets[MAC_OCTETS - 1]}"
    }

    /** [text] 안의 [pin]을 모두 `****`로 바꾼다. PIN이 설정되지 않았으면 원문 그대로. */
    fun pin(
        text: String,
        pin: String?,
    ): String = if (pin.isNullOrEmpty()) text else text.replace(pin, PIN_MASK)
}
