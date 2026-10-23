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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.app.ui.glow.GlowButton
import dev.linakbed.protocol.PresetSlot

/** 하단 프리셋 4개 (S10). 누르면 이동, 길게 누르면 편집(저장·이름 변경). 이동 중인 프리셋은 빛난다. */
@Composable
fun PresetGrid(
    names: Map<PresetSlot, String>,
    running: PresetSlot?,
    enabled: Boolean,
    onRecall: (PresetSlot) -> Unit,
    onEdit: (PresetSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.control_presets), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PresetSlot.entries.forEach { slot ->
                GlowButton(
                    onClick = { onRecall(slot) },
                    onLongClick = { onEdit(slot) },
                    onClickLabel = stringResource(R.string.home_preset_go),
                    onLongClickLabel = stringResource(R.string.home_preset_edit),
                    enabled = enabled,
                    active = slot == running,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                ) {
                    Text(
                        names[slot] ?: stringResource(R.string.preset_fallback_name, slot.number),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                    )
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
