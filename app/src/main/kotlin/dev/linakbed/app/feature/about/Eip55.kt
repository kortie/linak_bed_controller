package dev.linakbed.app.feature.about

/** EIP-55 대소문자 체크섬. 오타 난 후원 주소로 보내지 않도록 표시 전에 확인한다. */
object Eip55 {
    private val ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")
    private const val UPPERCASE_FROM = 8
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0F

    /** 형식이 맞고 체크섬(대소문자)이 정확할 때만 true. 전부 소문자/대문자인 주소는 체크섬이 없으므로 거부한다. */
    fun isValid(address: String): Boolean = ADDRESS.matches(address) && checksummed(address) == address

    fun checksummed(address: String): String {
        val hex = address.removePrefix("0x").lowercase()
        val hash = Keccak256.hash(hex.toByteArray(Charsets.US_ASCII))
        val chars =
            hex.mapIndexed { i, c ->
                val nibble = (hash[i / 2].toInt() shr (if (i % 2 == 0) NIBBLE_BITS else 0)) and NIBBLE_MASK
                if (c in 'a'..'f' && nibble >= UPPERCASE_FROM) c.uppercaseChar() else c
            }
        return "0x" + chars.joinToString("")
    }
}
