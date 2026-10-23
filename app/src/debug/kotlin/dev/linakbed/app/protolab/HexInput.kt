package dev.linakbed.app.protolab

/** Protocol Lab 원시 입력: 정확히 2바이트 16진수 (`0b00`, `0B 00` 등, 공백 무시). */
object HexInput {
    private const val COMMAND_LENGTH = 2
    private const val HEX_DIGITS_PER_BYTE = 2
    private const val RADIX = 16
    private val HEX = Regex("^[0-9a-fA-F]+$")

    fun parse(text: String): ByteArray? {
        val digits = text.filterNot(Char::isWhitespace)
        if (digits.length != COMMAND_LENGTH * HEX_DIGITS_PER_BYTE || !HEX.matches(digits)) return null
        return digits.chunked(HEX_DIGITS_PER_BYTE).map { it.toInt(RADIX).toByte() }.toByteArray()
    }

    fun format(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
