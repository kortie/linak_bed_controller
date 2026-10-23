package dev.linakbed.app.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.linakbed.app.R
import dev.linakbed.domain.port.LinkState

/** 연결 상태. 끊겼거나 실패했으면 누르면 다시 연결한다 (ADR-009). */
@Composable
fun ConnectionChip(
    link: LinkState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canReconnect = link == LinkState.Disconnected || link is LinkState.Failed
    val label =
        stringResource(
            when (link) {
                LinkState.Connected -> R.string.link_connected
                LinkState.Connecting -> R.string.link_connecting
                LinkState.Disconnected, is LinkState.Failed -> R.string.home_reconnect
            },
        )
    AssistChip(
        onClick = { if (canReconnect) onReconnect() },
        label = { Text(label) },
        leadingIcon = if (canReconnect) ({ Icon(Icons.Filled.Refresh, contentDescription = null) }) else null,
        colors =
            if (canReconnect) {
                AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
            } else {
                AssistChipDefaults.assistChipColors()
            },
        modifier = modifier.testTag(CONNECTION_CHIP_TAG),
    )
}

const val CONNECTION_CHIP_TAG = "connection_chip"
