package dev.linakbed.app.feature.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private var donations = 0
    private val actions =
        object : AboutActions {
            override fun open(uri: String) {
                opened += uri
            }

            override fun email(address: String) {
                emailed += address
            }
        }

    private val info =
        AboutInfo(
            version = "1.0.0",
            developerName = "juanStudio",
            developerEmail = "kortie00@gmail.com",
            privacyPolicyUrl = "",
        )
    private val donation = DonationUiState(price = "₩1,500")

    private fun show(
        info: AboutInfo,
        donation: DonationUiState = this.donation,
    ) {
        compose.setContent { LinakBedTheme { AboutContent(info, donation, onDonate = { donations++ }, actions = actions) } }
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
    fun `S16 the donation button shows the Play price and starts a purchase`() {
        show(info)

        compose.onNodeWithText("커피 한 잔 후원하기 · ₩1,500").performScrollTo().performClick()

        assertThat(donations).isEqualTo(1)
    }

    @Test
    fun `S16 no donation card when Play billing or the product is unavailable`() {
        show(info, DonationUiState(price = null))

        compose.onNodeWithTag(DONATION_CARD_TAG).assertDoesNotExist()
        compose.onNodeWithText("juanStudio").performScrollTo()
    }

    @Test
    fun `S16 thanks, pending and failure messages`() {
        var status by mutableStateOf(DonationStatus.THANKS)
        compose.setContent { LinakBedTheme { AboutContent(info, donation.copy(status = status), onDonate = {}, actions = actions) } }

        compose.onNodeWithText("후원해 주셔서 고맙습니다!").performScrollTo()
        status = DonationStatus.PENDING
        compose.onNodeWithText("결제가 확인되면 후원이 완료됩니다.").performScrollTo()
        status = DonationStatus.FAILED
        compose.onNodeWithText("결제를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.").performScrollTo()
        status = DonationStatus.IDLE
        compose.onNodeWithText("결제를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.").assertDoesNotExist()
    }

    @Test
    fun `licenses are listed and the Apache license opens`() {
        show(info)

        compose.onNodeWithText("Apache License 2.0 전문 보기").performScrollTo().performClick()

        assertThat(opened).containsExactly("https://www.apache.org/licenses/LICENSE-2.0")
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
