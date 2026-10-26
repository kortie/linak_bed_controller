package dev.linakbed.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.app.feature.control.ControlAction
import dev.linakbed.app.feature.control.ControlUiState
import dev.linakbed.app.feature.control.HoldToMoveButton
import dev.linakbed.app.feature.control.ManualMove
import dev.linakbed.app.ui.glow.GlowButton
import dev.linakbed.domain.controller.LightState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LightAction

/**
 * 기기 화면 (S10): 가운데 침대 그림, 머리 쪽·발 쪽에 각 부위 버튼, 그림 아래 전체 버튼, 위에 조명.
 * 누르고 있는 동안만 움직인다 (S3).
 */
@Composable
fun DeviceControls(
    control: ControlUiState,
    onControl: (ControlAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = control.controlsEnabled
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LightButton(control.light, enabled) { onControl(ControlAction.Light(LightAction.TOGGLE)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActuatorButtons(Actuator.HEAD, R.string.control_head, control, onControl)
            BedIllustration(
                pose = BedPoseMapper.from(control.headCm, control.feetCm),
                highlight = control.pressed?.actuator,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            ActuatorButtons(Actuator.FEET, R.string.control_feet, control, onControl)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val title = stringResource(R.string.control_both)
            MoveButton(ManualMove(Actuator.BOTH, Direction.DOWN), title, control, onControl)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(LABEL_WIDTH.dp))
            MoveButton(ManualMove(Actuator.BOTH, Direction.UP), title, control, onControl)
        }
    }
}

@Composable
private fun ActuatorButtons(
    actuator: Actuator,
    titleRes: Int,
    control: ControlUiState,
    onControl: (ControlAction) -> Unit,
) {
    val title = stringResource(titleRes)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        MoveButton(ManualMove(actuator, Direction.UP), title, control, onControl)
        MoveButton(ManualMove(actuator, Direction.DOWN), title, control, onControl)
    }
}

@Composable
private fun MoveButton(
    move: ManualMove,
    title: String,
    control: ControlUiState,
    onControl: (ControlAction) -> Unit,
) {
    val glyph = stringResource(if (move.direction == Direction.UP) R.string.arrow_up else R.string.arrow_down)
    val label = stringResource(if (move.direction == Direction.UP) R.string.control_up else R.string.control_down)
    HoldToMoveButton(
        label = glyph,
        description = stringResource(R.string.control_move_description, title, label),
        pressed = control.pressed == move,
        onPress = { onControl(ControlAction.Press(move)) },
        onRelease = { onControl(ControlAction.Release) },
        enabled = control.controlsEnabled,
        modifier = Modifier.size(BUTTON_SIZE.dp),
    )
}

@Composable
private fun LightButton(
    light: LightState,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val state =
        stringResource(
            when (light) {
                LightState.ON -> R.string.light_on
                LightState.OFF -> R.string.light_off
                LightState.UNKNOWN -> R.string.light_unknown
            },
        )
    GlowButton(
        onClick = onToggle,
        enabled = enabled,
        active = light == LightState.ON,
        shape = CircleShape,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.home_light, state), modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }
}

private const val BUTTON_SIZE = 48
private const val LABEL_WIDTH = 48
