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
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.PresetSlot

/**
 * 하단 프리셋 4개 (S10·S12). 1~3은 그 프리셋의 침대 모양 그림(도착·저장 때 기록), 4는 앱에서 저장할 수 있는 "Custom".
 * 누르면 이동, Custom만 길게 눌러 현재 자세 저장(확인 필수, S6). 이동 중인 프리셋은 빛난다.
 */
@Composable
fun PresetGrid(
    poses: Map<PresetSlot, PresetPose>,
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
                        PresetPicture(slot, poses[slot])
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
private fun PresetPicture(
    slot: PresetSlot,
    pose: PresetPose?,
) {
    val description =
        if (pose == null) {
            stringResource(R.string.preset_icon_unknown, slot.number)
        } else {
            stringResource(R.string.preset_icon, slot.number)
        }
    BedPoseIcon(
        pose = BedPoseMapper.from(pose?.headCm, pose?.feetCm),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).semantics { contentDescription = description },
    )
}

/** 앱에서 저장(커스터마이즈)할 수 있는 유일한 프리셋 (S12). */
val CUSTOM_SLOT = PresetSlot.P4

private const val BUTTON_HEIGHT = 56
