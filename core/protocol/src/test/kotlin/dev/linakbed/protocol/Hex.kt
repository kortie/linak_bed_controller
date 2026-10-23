package dev.linakbed.protocol

/** 테스트용 16진수 변환 (소문자, 바이트당 2자리). */
internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have an even length: $this" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
