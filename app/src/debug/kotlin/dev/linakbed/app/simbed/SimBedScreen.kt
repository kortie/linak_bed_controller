package dev.linakbed.app.simbed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.LinakSensorDecoder
import dev.linakbed.protocol.ProtocolProfile
import java.util.Locale

@Composable
fun SimBedScreen(
    state: SimBedUiState,
    onPair: () -> Unit,
    onUnpair: () -> Unit,
    onFailConnect: (Boolean) -> Unit,
    onDropLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.simbed_notice), style = MaterialTheme.typography.bodyMedium) }
        item { PairingCard(state.pairing, onPair, onUnpair) }
        item { StatusCard(state) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.simbed_faults), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.simbed_fail_connect), Modifier.weight(1f))
                        Switch(checked = state.failConnect, onCheckedChange = onFailConnect)
                    }
                    OutlinedButton(onClick = onDropLink, enabled = state.link == LinkState.Connected) {
                        Text(stringResource(R.string.simbed_drop_link))
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text(stringResource(R.string.simbed_received), style = MaterialTheme.typography.titleMedium)
        }
        items(state.received) { entry ->
            Text(
                String.format(Locale.ROOT, "%7.1fs  %s", entry.atMs / MS_PER_SECOND, entry.command),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PairingCard(
    pairing: SimPairing,
    onPair: () -> Unit,
    onUnpair: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val text =
                when (pairing) {
                    SimPairing.NONE -> R.string.simbed_pairing_none
                    SimPairing.SIMULATED -> R.string.simbed_pairing_simulated
                    SimPairing.REAL -> R.string.simbed_pairing_real
                }
            Text(stringResource(text))
            if (pairing == SimPairing.SIMULATED) {
                OutlinedButton(onClick = onUnpair) { Text(stringResource(R.string.simbed_unpair)) }
            } else {
                Button(onClick = onPair) { Text(stringResource(R.string.simbed_pair)) }
            }
        }
    }
}

@Composable
private fun StatusCard(state: SimBedUiState) {
    val snapshot = state.snapshot ?: return
    val calibration = ProtocolProfile.DEFAULT.calibration
    val head = LinakSensorDecoder.fromRaw(Actuator.HEAD, snapshot.position.head, null, calibration)
    val feet = LinakSensorDecoder.fromRaw(Actuator.FEET, snapshot.position.feet, null, calibration)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.simbed_link, state.link.toString()))
            Text(stringResource(R.string.simbed_height, stringResource(R.string.control_head), head.cm, head.raw))
            Text(stringResource(R.string.simbed_height, stringResource(R.string.control_feet), feet.cm, feet.raw))
            Text(stringResource(if (snapshot.lightOn) R.string.light_on else R.string.light_off))
            Text(stringResource(R.string.simbed_moving, snapshot.moving?.toString() ?: "-"))
        }
    }
}

private const val MS_PER_SECOND = 1000.0
