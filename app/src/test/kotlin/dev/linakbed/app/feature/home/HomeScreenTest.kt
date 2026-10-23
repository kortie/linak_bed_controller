package dev.linakbed.app.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.feature.control.ControlAction
import dev.linakbed.app.feature.control.ControlUiState
import dev.linakbed.app.feature.control.ManualMove
import dev.linakbed.app.feature.preset.PresetAction
import dev.linakbed.app.feature.preset.PresetUiState
import dev.linakbed.app.ui.theme.LinakBedTheme
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 홈 (ADR-009): S3 누르는 동안만, 연결 끊김 시 비활성(STOP 제외), 프리셋 이동·편집, 상단 이동. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val controlActions = mutableListOf<ControlAction>()
    private val presetActions = mutableListOf<PresetAction>()
    private var openedSettings = false
    private var openedAbout = false

    private fun show(
        control: ControlUiState = ControlUiState(bedName = "DPG1M", link = LinkState.Connected),
        presets: PresetUiState = PresetUiState(),
    ) {
        compose.setContent {
            LinakBedTheme {
                HomeContent(
                    control = control,
                    presets = presets,
                    onControl = { controlActions += it },
                    onPreset = { presetActions += it },
                    onOpenSettings = { openedSettings = true },
                    onOpenAbout = { openedAbout = true },
                )
            }
        }
    }

    @Test
    fun `S3 pressing and lifting a move button sends press then release`() {
        show()

        compose.onNodeWithContentDescription("머리 ▲ 올리기 (누르고 있는 동안)").performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()

        assertThat(
            controlActions,
        ).containsExactly(ControlAction.Press(ManualMove(Actuator.HEAD, Direction.UP)), ControlAction.Release).inOrder()
    }

    @Test
    fun `S3 dragging the finger off the button still releases`() {
        show()

        compose.onNodeWithContentDescription("발 ▼ 내리기 (누르고 있는 동안)").performScrollTo().performTouchInput {
            down(center)
            moveBy(Offset(0f, height * 3f))
            up()
        }
        compose.waitForIdle()

        assertThat(controlActions.first()).isEqualTo(ControlAction.Press(ManualMove(Actuator.FEET, Direction.DOWN)))
        assertThat(controlActions.last()).isEqualTo(ControlAction.Release)
    }

    @Test
    fun `S3 a held button is released when the link drops mid-press`() {
        var control by mutableStateOf(ControlUiState(link = LinkState.Connected))
        compose.setContent {
            LinakBedTheme {
                HomeContent(control, PresetUiState(), { controlActions += it }, { presetActions += it }, {}, {})
            }
        }
        val button = compose.onNodeWithContentDescription("머리 ▲ 올리기 (누르고 있는 동안)")
        button.performTouchInput { down(center) }
        compose.waitForIdle()

        control = ControlUiState(link = LinkState.Disconnected)
        compose.waitForIdle()

        assertThat(
            controlActions,
        ).containsExactly(ControlAction.Press(ManualMove(Actuator.HEAD, Direction.UP)), ControlAction.Release).inOrder()
    }

    @Test
    fun `disconnected - move, preset and light are disabled but STOP works`() {
        show(ControlUiState(link = LinkState.Disconnected))

        compose.onNodeWithContentDescription("전체 ▲ 올리기 (누르고 있는 동안)").assertIsNotEnabled().performTouchInput {
            down(center)
            up()
        }
        compose.onNodeWithText("프리셋 1").performScrollTo().performClick()
        compose.onNodeWithText("조명 · 알 수 없음").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("침대 정지").assertIsEnabled().performClick()

        assertThat(controlActions).containsExactly(ControlAction.Stop)
        assertThat(presetActions).isEmpty()
    }

    @Test
    fun `connection chip reconnects only when disconnected`() {
        show(ControlUiState(link = LinkState.Disconnected))

        compose.onNodeWithTag(CONNECTION_CHIP_TAG).performClick()

        assertThat(controlActions).containsExactly(ControlAction.Reconnect)
    }

    @Test
    fun `connected chip does nothing`() {
        show()

        compose.onNodeWithTag(CONNECTION_CHIP_TAG).performClick()

        assertThat(controlActions).isEmpty()
    }

    @Test
    fun `tapping a preset recalls it, light toggles`() {
        show(presets = PresetUiState(names = mapOf(PresetSlot.P2 to "기상")))

        compose.onNodeWithText("기상").performScrollTo().performClick()
        compose.onNodeWithText("조명 · 알 수 없음").performScrollTo().performClick()

        assertThat(presetActions).containsExactly(PresetAction.Recall(PresetSlot.P2))
        assertThat(controlActions).containsExactly(ControlAction.Light(LightAction.TOGGLE))
    }

    @Test
    fun `S6 long-press opens the edit sheet and save only requests confirmation`() {
        show()

        compose.onNodeWithText("프리셋 3").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("현재 위치를 이 프리셋으로 저장").performClick()

        assertThat(presetActions).containsExactly(PresetAction.RequestSave(PresetSlot.P3))
    }

    @Test
    fun `S6 a pending save shows the confirmation dialog`() {
        show(presets = PresetUiState(pendingSave = PresetSlot.P1))

        compose.onNodeWithText("덮어쓰기").assertIsNotEnabled()
    }

    @Test
    fun `edit sheet rename starts renaming`() {
        show()

        compose.onNodeWithText("프리셋 4").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("이름 변경").performClick()

        assertThat(presetActions).containsExactly(PresetAction.StartRename(PresetSlot.P4))
    }

    @Test
    fun `top bar shows the bed name and opens settings and about`() {
        show()

        compose.onNodeWithText("DPG1M").assertExists()
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithContentDescription("정보").performClick()

        assertThat(openedSettings).isTrue()
        assertThat(openedAbout).isTrue()
    }

    @Test
    fun `S10 no height numbers, the bed drawing describes the pose instead`() {
        show(ControlUiState(link = LinkState.Connected, headCm = 30.0, feetCm = 0.0))

        compose.onNodeWithText("cm", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(BED_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "머리 50% · 발 0%"))
    }

    @Test
    fun `S10 the drawing follows sensor updates`() {
        var control by mutableStateOf(ControlUiState(link = LinkState.Connected, headCm = 0.0, feetCm = 0.0))
        compose.setContent {
            LinakBedTheme { HomeContent(control, PresetUiState(), {}, {}, {}, {}) }
        }

        control = control.copy(headCm = 60.0, feetCm = 15.0)
        compose.waitForIdle()

        compose.onNodeWithTag(BED_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "머리 100% · 발 25%"))
    }

    @Test
    fun `S10 without sensor values the drawing says the height is unknown`() {
        show(ControlUiState(link = LinkState.Connected))

        compose.onNodeWithTag(BED_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "높이 정보 없음"))
    }
}
