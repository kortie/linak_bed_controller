package dev.linakbed.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.app.ui.glow.GlowButton
import dev.linakbed.protocol.PresetSlot

/**
 * 하단 프리셋 4개 (S10·S12). 1~3은 고정 침대 그림(ADR-016: 평평·조금·머리 많이), 4는 앱에서 저장할 수 있는 "Custom".
 * 누르면 이동, Custom만 길게 눌러 현재 자세 저장(확인 필수, S6). 이동 중인 프리셋은 빛난다.
 */
@Composable
fun PresetGrid(
    running: PresetSlot?,
    enabled: Boolean,
    onRecall: (PresetSlot) -> Unit,
    onEditCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.control_presets), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetSlot.entries.forEach { slot ->
                val custom = slot == CUSTOM_SLOT
                GlowButton(
                    onClick = { onRecall(slot) },
                    onLongClick = if (custom) onEditCustom else null,
                    onClickLabel = stringResource(R.string.home_preset_go),
                    onLongClickLabel = if (custom) stringResource(R.string.home_preset_edit) else null,
                    enabled = enabled,
                    active = slot == running,
                    modifier = Modifier.weight(1f).heightIn(min = BUTTON_HEIGHT.dp),
                ) {
                    if (custom) {
                        Text(
                            stringResource(R.string.preset_custom),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(6.dp),
                        )
                    } else {
                        PresetPicture(slot)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.home_preset_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PresetPicture(slot: PresetSlot) {
    val description = stringResource(R.string.preset_icon, slot.number)
    BedPoseIcon(
        pose = presetPicture(slot),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).semantics { contentDescription = description },
    )
}

/**
 * 프리셋 버튼의 고정 그림 (ADR-016). 센서로 기록하면 도착 판정·센서 지연에 따라 그림이 수시로 바뀌어서 고정했다.
 * 실제 침대에 저장된 위치와는 다를 수 있다. Custom([CUSTOM_SLOT])은 그림 대신 글자라 평평으로 둔다.
 */
fun presetPicture(slot: PresetSlot): BedPose =
    when (slot) {
        PresetSlot.P1 -> BedPose(head = 0f, feet = 0f)
        PresetSlot.P2 -> BedPose(head = SLIGHT_HEAD, feet = SLIGHT_FEET)
        PresetSlot.P3 -> BedPose(head = HIGH_HEAD, feet = 0f)
        PresetSlot.P4 -> BedPose(head = 0f, feet = 0f)
    }

/** 앱에서 저장(커스터마이즈)할 수 있는 유일한 프리셋 (S12). */
val CUSTOM_SLOT = PresetSlot.P4

private const val BUTTON_HEIGHT = 56
private const val SLIGHT_HEAD = 0.45f
private const val SLIGHT_FEET = 0.3f
private const val HIGH_HEAD = 1f
