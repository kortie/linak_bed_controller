package dev.linakbed.app.protolab

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.PresetSlot

/**
 * Protocol Lab의 "Controller" 영역: 원시 링크가 아니라 BedController(중재·안전 감독)를 거쳐 시험한다 (backlog HW-S03).
 * 정식 수동 제어 화면은 S04.
 */
@Composable
fun ControllerTab(
    state: ProtocolLabUiState,
    onAction: (ProtocolLabAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.protolab_section_controller), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onAction(ProtocolLabAction.UseSelectedAsControllerTarget) }, enabled = state.selectedAddress != null) {
            Text(stringResource(R.string.protolab_controller_target))
        }
        ControllerStatus(state.controller)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetSlot.entries.forEach { slot ->
                OutlinedButton(onClick = { onAction(ProtocolLabAction.ControllerPreset(slot)) }) {
                    Text(stringResource(R.string.protolab_controller_preset, slot.number))
                }
            }
        }
        Text(stringResource(R.string.protolab_controller_hold_hint), style = MaterialTheme.typography.bodySmall)
        Actuator.entries.forEach { actuator ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(actuator.name, modifier = Modifier.weight(1f))
                Direction.entries.forEach { direction ->
                    HoldButton(
                        label = direction.name,
                        onPress = { onAction(ProtocolLabAction.ControllerMovePress(actuator, direction)) },
                        onRelease = { onAction(ProtocolLabAction.ControllerMoveRelease) },
                    )
                }
            }
        }
        OutlinedButton(onClick = { onAction(ProtocolLabAction.ControllerStop) }) {
            Text(stringResource(R.string.protolab_controller_stop))
        }
    }
}

@Composable
private fun ControllerStatus(status: BedStatus) {
    val text =
        listOf(
            "link=${status.link}",
            "motion=${status.activeMotion ?: "-"}",
            "head=${status.headCm?.let { "%.1f".format(it) } ?: "-"} feet=${status.feetCm?.let { "%.1f".format(it) } ?: "-"}",
            "light=${status.light}",
            "lastError=${status.lastError ?: "-"}",
        ).joinToString("\n")
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

/** 누르는 동안만 동작 (손가락이 벗어나거나 제스처가 취소돼도 release). */
@Composable
private fun HoldButton(
    label: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier =
            Modifier.heightIn(min = 48.dp).pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label)
        }
    }
}
