package dev.linakbed.app.feature.about

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.ui.theme.LinakBedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val emailed = mutableListOf<String>()
    private val copied = mutableListOf<String>()
    private val actions =
        object : AboutActions {
            override fun open(uri: String) {
                opened += uri
            }

            override fun email(address: String) {
                emailed += address
            }

            override fun copy(text: String) {
                copied += text
            }
        }

    private val recipient = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
    private val donation =
        DonationConfig(true, recipient, 1, "Ethereum", "0xdAC17F958D2ee523a2206206994597C13D831ec7", 6)
    private val info =
        AboutInfo(
            version = "1.0.0",
            developerName = "juanStudio",
            developerEmail = "kortie00@gmail.com",
            privacyPolicyUrl = "",
            donation = donation,
        )

    private fun show(info: AboutInfo) {
        compose.setContent { LinakBedTheme { AboutContent(info, actions) } }
    }

    @Test
    fun `shows version and developer, email opens the mail app`() {
        show(info)

        compose.onNodeWithText("버전 1.0.0").assertExists()
        compose.onNodeWithText("juanStudio").performScrollTo()
        compose.onNodeWithText("kortie00@gmail.com").performScrollTo().performClick()

        assertThat(emailed).containsExactly("kortie00@gmail.com")
    }

    @Test
    fun `donation button opens the MetaMask link and the address can be copied`() {
        show(info)

        compose.onNodeWithText("1 USDT 후원하기 (MetaMask)").performScrollTo().performClick()
        compose.onNodeWithText("받는 주소 복사").performScrollTo().performClick()

        assertThat(opened).containsExactly(DonationLink.metaMask(donation))
        assertThat(copied).containsExactly(recipient)
        compose.onNodeWithText("Ethereum 네트워크의 USDT만 보내 주세요. 다른 네트워크로 보내면 받을 수 없습니다.").assertExists()
    }

    @Test
    fun `no donation card without a recipient or when disabled`() {
        show(info.copy(donation = donation.copy(recipient = "")))
        compose.onNodeWithTag(DONATION_CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun `disabled donation hides the card`() {
        show(info.copy(donation = donation.copy(enabled = false)))
        compose.onNodeWithTag(DONATION_CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun `privacy policy link appears only with a URL`() {
        show(info)
        compose.onNodeWithText("개인정보 처리방침 보기").assertDoesNotExist()
    }

    @Test
    fun `privacy policy link opens the URL`() {
        show(info.copy(privacyPolicyUrl = "https://example.com/privacy"))

        compose.onNodeWithText("개인정보 처리방침 보기").performScrollTo().performClick()

        assertThat(opened).containsExactly("https://example.com/privacy")
    }
}
