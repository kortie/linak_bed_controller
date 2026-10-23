package dev.linakbed.app.feature.about

import dev.linakbed.app.BuildConfig
import java.math.BigInteger

/**
 * 커피값 후원 설정 (ADR-010). 값은 `app/donation.properties` → BuildConfig에서 온다.
 * 받는 주소가 비었거나 형식·체크섬이 틀리면 후원 카드를 숨긴다.
 */
data class DonationConfig(
    val enabled: Boolean,
    val recipient: String,
    val chainId: Long,
    val networkName: String,
    val tokenContract: String,
    val tokenDecimals: Int,
    val amountUnits: Int = 1,
) {
    /** 후원 카드를 보여도 되는가. */
    val isUsable: Boolean
        get() =
            enabled &&
                chainId > 0 &&
                networkName.isNotBlank() &&
                tokenDecimals >= 0 &&
                Eip55.isValid(recipient) &&
                Eip55.isValid(tokenContract)

    /** 토큰 최소 단위 금액 (1 USDT, 6자리 → 1000000). */
    val amountInSmallestUnit: BigInteger get() = BigInteger.valueOf(amountUnits.toLong()) * BigInteger.TEN.pow(tokenDecimals)

    companion object {
        fun fromBuildConfig() =
            DonationConfig(
                enabled = BuildConfig.DONATION_ENABLED,
                recipient = BuildConfig.DONATION_RECIPIENT,
                chainId = BuildConfig.DONATION_CHAIN_ID,
                networkName = BuildConfig.DONATION_NETWORK_NAME,
                tokenContract = BuildConfig.DONATION_TOKEN_CONTRACT,
                tokenDecimals = BuildConfig.DONATION_TOKEN_DECIMALS,
            )
    }
}

object DonationLink {
    /**
     * MetaMask 보내기 딥링크 (EIP-681 형식): 지정한 네트워크로 전환된 토큰 전송 화면을 연다.
     * 설정을 쓸 수 없으면 null.
     */
    fun metaMask(config: DonationConfig): String? =
        if (!config.isUsable) {
            null
        } else {
            "https://link.metamask.io/send/${config.tokenContract}@${config.chainId}/transfer" +
                "?address=${config.recipient}&uint256=${config.amountInSmallestUnit}"
        }
}
