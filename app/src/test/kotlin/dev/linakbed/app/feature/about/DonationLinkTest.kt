package dev.linakbed.app.feature.about

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DonationLinkTest {
    private val usdt = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
    private val recipient = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
    private val config =
        DonationConfig(
            enabled = true,
            recipient = recipient,
            chainId = 1,
            networkName = "Ethereum",
            tokenContract = usdt,
            tokenDecimals = 6,
        )

    @Test
    fun `keccak-256 matches known digests`() {
        assertThat(Keccak256.hash(ByteArray(0)).toHex()).isEqualTo("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
        assertThat(
            Keccak256.hash("abc".toByteArray()).toHex(),
        ).isEqualTo("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45")
        // 한 블록(136바이트)을 넘는 입력
        assertThat(Keccak256.hash(ByteArray(200) { 'a'.code.toByte() }).size).isEqualTo(32)
    }

    @Test
    fun `EIP-55 test vectors pass and altered case fails`() {
        listOf(
            "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
            "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
            "0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB",
            "0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb",
            usdt,
        ).forEach { assertThat(Eip55.isValid(it)).isTrue() }

        assertThat(Eip55.isValid("0x5AAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")).isFalse()
        assertThat(Eip55.isValid(recipient.lowercase())).isFalse()
        assertThat(Eip55.isValid("0x1234")).isFalse()
        assertThat(Eip55.isValid("")).isFalse()
    }

    @Test
    fun `MetaMask link carries contract, chain, recipient and 1 USDT in smallest units`() {
        assertThat(DonationLink.metaMask(config)).isEqualTo(
            "https://link.metamask.io/send/$usdt@1/transfer?address=$recipient&uint256=1000000",
        )
    }

    @Test
    fun `amount follows the token decimals without exponent notation`() {
        assertThat(config.copy(tokenDecimals = 18).amountInSmallestUnit.toString()).isEqualTo("1000000000000000000")
        assertThat(config.copy(tokenDecimals = 0).amountInSmallestUnit.toString()).isEqualTo("1")
    }

    @Test
    fun `no link when the recipient is empty, invalid, or donation is disabled`() {
        assertThat(DonationLink.metaMask(config.copy(recipient = ""))).isNull()
        assertThat(DonationLink.metaMask(config.copy(recipient = recipient.lowercase()))).isNull()
        assertThat(DonationLink.metaMask(config.copy(enabled = false))).isNull()
        assertThat(DonationLink.metaMask(config.copy(chainId = 0))).isNull()
        assertThat(DonationLink.metaMask(config.copy(networkName = ""))).isNull()
        assertThat(DonationLink.metaMask(config.copy(tokenContract = "0xdac17f958d2ee523a2206206994597c13d831ec7"))).isNull()
    }

    @Test
    fun `the shipped build config is valid apart from the empty recipient`() {
        val shipped = DonationConfig.fromBuildConfig()

        assertThat(Eip55.isValid(shipped.tokenContract)).isTrue()
        assertThat(shipped.chainId).isGreaterThan(0L)
        assertThat(shipped.networkName).isNotEmpty()
        assertThat(shipped.copy(recipient = recipient).isUsable).isEqualTo(shipped.enabled)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
