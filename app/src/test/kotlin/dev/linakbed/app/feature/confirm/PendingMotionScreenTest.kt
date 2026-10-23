package dev.linakbed.app.feature.confirm

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.ui.theme.LinakBedTheme
import dev.linakbed.protocol.PresetSlot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** S9 레이아웃 규칙 (S07 스텝 문서): 취소 버튼이 높이 2/3 이상 · 폭 전체 · 유일한 버튼. */
@RunWith(AndroidJUnit4::class)
class PendingMotionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var cancelled = 0

    private fun show(state: PendingMotionUiState = PendingMotionUiState(slot = PresetSlot.P2, remainingSeconds = 8)) {
        compose.setContent {
            LinakBedTheme {
                PendingMotionScreen(state = state, onCancel = { cancelled++ }, modifier = Modifier.size(SCREEN_WIDTH, SCREEN_HEIGHT))
            }
        }
    }

    @Test
    fun `cancel button fills the full width and at least two thirds of the height`() {
        show()

        val root = compose.onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithTag(CANCEL_TAG).fetchSemanticsNode().boundsInRoot

        assertThat(cancel.width).isEqualTo(root.width)
        assertThat(cancel.height).isAtLeast(root.height * 2 / 3 - 1)
        assertThat(cancel.bottom).isEqualTo(root.bottom)
    }

    @Test
    fun `shows the preset and the remaining seconds`() {
        show(PendingMotionUiState(slot = PresetSlot.P2, names = mapOf(PresetSlot.P2 to "기상"), remainingSeconds = 8))

        compose.onNodeWithText("침대가 ‘기상’(으)로 움직입니다").assertIsDisplayed()
        compose.onNodeWithText("8초").assertIsDisplayed()
    }

    @Test
    fun `the cancel button is the only clickable element`() {
        show()

        val clickable = compose.onAllNodes(SemanticsMatcher("clickable") { it.config.getOrNull(SemanticsActions.OnClick) != null })
        assertThat(clickable.fetchSemanticsNodes()).hasSize(1)
        compose.onNodeWithContentDescription("침대 이동 취소").assertIsDisplayed()
    }

    @Test
    fun `clicking cancel triggers the cancel action`() {
        show()

        compose.onNodeWithTag(CANCEL_TAG).performClick()

        assertThat(cancelled).isEqualTo(1)
    }

    private companion object {
        val SCREEN_WIDTH = 360.dp
        val SCREEN_HEIGHT = 720.dp
    }
}
