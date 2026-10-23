package dev.linakbed.app.feature.preset

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.ui.theme.LinakBedTheme
import dev.linakbed.domain.preset.PresetName
import dev.linakbed.protocol.PresetSlot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** S6: 저장 버튼은 확인 체크 전에는 눌리지 않는다. */
@RunWith(AndroidJUnit4::class)
class SavePresetDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<PresetAction>()

    private fun show(confirmed: Boolean) {
        compose.setContent {
            LinakBedTheme {
                PresetDialogs(
                    state =
                        PresetUiState(
                            names = PresetSlot.entries.associateWith(PresetName::defaultFor),
                            pendingSave = PresetSlot.P2,
                            saveConfirmed = confirmed,
                        ),
                    onAction = { actions += it },
                )
            }
        }
    }

    @Test
    fun `S6 overwrite button is disabled until the checkbox is ticked`() {
        show(confirmed = false)

        compose.onNodeWithText("현재 침대 위치로 ‘프리셋 2’을(를) 덮어씁니다. 이전 위치는 되돌릴 수 없습니다.").assertExists()
        compose.onNodeWithText("덮어쓰기").assertIsNotEnabled()
        compose.onNodeWithText("덮어쓰는 것을 확인했습니다").performClick()

        assertThat(actions).containsExactly(PresetAction.SaveConfirmedChanged(true))
    }

    @Test
    fun `S6 overwrite button works once confirmed`() {
        show(confirmed = true)

        compose.onNodeWithText("덮어쓰기").assertIsEnabled().performClick()

        assertThat(actions).containsExactly(PresetAction.ConfirmSave)
    }
}
