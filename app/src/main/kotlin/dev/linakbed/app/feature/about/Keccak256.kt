// Keccak-f[1600] 표준 상수표(라운드 상수·회전량·π 순서)라 이름 붙일 수 없는 숫자가 많다.
@file:Suppress("MagicNumber")

package dev.linakbed.app.feature.about

/** 이더리움 Keccak-256 (SHA3 표준화 이전 패딩 0x01). EIP-55 주소 체크섬 검증에만 쓴다. */
internal object Keccak256 {
    private const val RATE_BYTES = 136
    private const val OUTPUT_BYTES = 32
    private const val LANES = 25
    private const val ROUNDS = 24
    private const val ROW = 5
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFFL
    private const val PAD_FIRST = 0x01
    private const val PAD_LAST = 0x80

    private val ROUND_CONSTANTS =
        listOf(
            0x0000000000000001uL,
            0x0000000000008082uL,
            0x800000000000808AuL,
            0x8000000080008000uL,
            0x000000000000808BuL,
            0x0000000080000001uL,
            0x8000000080008081uL,
            0x8000000000008009uL,
            0x000000000000008AuL,
            0x0000000000000088uL,
            0x0000000080008009uL,
            0x000000008000000AuL,
            0x000000008000808BuL,
            0x800000000000008BuL,
            0x8000000000008089uL,
            0x8000000000008003uL,
            0x8000000000008002uL,
            0x8000000000000080uL,
            0x000000000000800AuL,
            0x800000008000000AuL,
            0x8000000080008081uL,
            0x8000000000008080uL,
            0x0000000080000001uL,
            0x8000000080008008uL,
        ).map { it.toLong() }

    private val ROTATIONS = intArrayOf(1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44)
    private val PI_LANES = intArrayOf(10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1)

    fun hash(input: ByteArray): ByteArray {
        val size = (input.size / RATE_BYTES + 1) * RATE_BYTES
        val padded = input.copyOf(size)
        padded[input.size] = (padded[input.size].toInt() xor PAD_FIRST).toByte()
        padded[size - 1] = (padded[size - 1].toInt() xor PAD_LAST).toByte()
        val state = LongArray(LANES)
        for (offset in 0 until size step RATE_BYTES) {
            for (lane in 0 until RATE_BYTES / Long.SIZE_BYTES) {
                state[lane] = state[lane] xor readLane(padded, offset + lane * Long.SIZE_BYTES)
            }
            permute(state)
        }
        return ByteArray(OUTPUT_BYTES) { i ->
            (state[i / Long.SIZE_BYTES] ushr (BITS_PER_BYTE * (i % Long.SIZE_BYTES))).toByte()
        }
    }

    private fun readLane(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        var lane = 0L
        for (i in 0 until Long.SIZE_BYTES) lane = lane or ((bytes[offset + i].toLong() and BYTE_MASK) shl (BITS_PER_BYTE * i))
        return lane
    }

    private fun permute(state: LongArray) {
        val column = LongArray(ROW)
        repeat(ROUNDS) { round ->
            // θ
            for (x in 0 until ROW) column[x] = state[x] xor state[x + 5] xor state[x + 10] xor state[x + 15] xor state[x + 20]
            for (x in 0 until ROW) {
                val d = column[(x + 4) % ROW] xor column[(x + 1) % ROW].rotateLeft(1)
                for (y in 0 until LANES step ROW) state[y + x] = state[y + x] xor d
            }
            // ρ, π
            var carry = state[1]
            for (i in ROTATIONS.indices) {
                val target = PI_LANES[i]
                val next = state[target]
                state[target] = carry.rotateLeft(ROTATIONS[i])
                carry = next
            }
            // χ
            for (y in 0 until LANES step ROW) {
                for (x in 0 until ROW) column[x] = state[y + x]
                for (x in 0 until ROW) state[y + x] = state[y + x] xor (column[(x + 1) % ROW].inv() and column[(x + 2) % ROW])
            }
            // ι
            state[0] = state[0] xor ROUND_CONSTANTS[round]
        }
    }
}
