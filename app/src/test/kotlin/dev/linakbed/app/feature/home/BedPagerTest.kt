package dev.linakbed.app.feature.home

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.linakbed.app.ui.theme.LinakBedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** S13: 침대마다 카드, 2대 미만이면 마지막에 "침대 추가" 카드. */
@RunWith(AndroidJUnit4::class)
class BedPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        beds: List<String>,
        canAdd: Boolean,
    ) {
        compose.setContent {
            LinakBedTheme {
                BedPager(bedIds = beds, canAdd = canAdd, bedPage = { Text("card $it") }, addPage = { Text("add card") })
            }
        }
    }

    @Test
    fun `one bed shows its card first and the add card after a swipe`() {
        show(listOf("bed-1"), canAdd = true)

        compose.onNodeWithText("card bed-1").assertExists()
        compose.onNodeWithTag(PAGE_DOTS_TAG).assertExists()
        compose.onNodeWithTag(PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("add card").assertExists()
    }

    @Test
    fun `two beds show two cards and no add card`() {
        show(listOf("bed-1", "bed-2"), canAdd = false)

        compose.onNodeWithTag(PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("card bed-2").assertExists()
        compose.onNodeWithTag(PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onAllNodesWithText("add card").assertCountEquals(0)
    }

    @Test
    fun `a single bed without room to add shows no page dots`() {
        show(listOf("bed-1"), canAdd = false)

        compose.onNodeWithTag(PAGE_DOTS_TAG).assertDoesNotExist()
    }
}
