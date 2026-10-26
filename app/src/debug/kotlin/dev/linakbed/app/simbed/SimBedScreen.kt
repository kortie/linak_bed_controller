package dev.linakbed.app.simbed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
    onPair: (Int) -> Unit,
    onUnpair: (Int) -> Unit,
    onOpenApp: () -> Unit,
    onFailConnect: (Int, Boolean) -> Unit,
    onDropLink: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.simbed_notice), style = MaterialTheme.typography.bodyMedium) }
        if (state.anyPaired) {
            item { Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.simbed_open_app)) } }
        }
        state.cards.forEach { card ->
            item { SimBedCardView(card, state.canAdd, onPair, onUnpair, onFailConnect, onDropLink) }
        }
    }
}

@Composable
private fun SimBedCardView(
    card: SimBedCard,
    canAdd: Boolean,
    onPair: (Int) -> Unit,
    onUnpair: (Int) -> Unit,
    onFailConnect: (Int, Boolean) -> Unit,
    onDropLink: (Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.simbed_card_title, card.index), style = MaterialTheme.typography.titleMedium)
            if (card.paired) {
                OutlinedButton(onClick = { onUnpair(card.index) }) { Text(stringResource(R.string.simbed_unpair)) }
                Status(card)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.simbed_fail_connect), Modifier.weight(1f))
                    Switch(checked = card.failConnect, onCheckedChange = { onFailConnect(card.index, it) })
                }
                OutlinedButton(onClick = { onDropLink(card.index) }, enabled = card.link == LinkState.Connected) {
                    Text(stringResource(R.string.simbed_drop_link))
                }
                card.received.take(RECEIVED_SHOWN).forEach { entry ->
                    Text(
                        String.format(Locale.ROOT, "%7.1fs  %s", entry.atMs / MS_PER_SECOND, entry.command),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Button(onClick = { onPair(card.index) }, enabled = canAdd) { Text(stringResource(R.string.simbed_pair)) }
                if (!canAdd) Text(stringResource(R.string.simbed_full), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Status(card: SimBedCard) {
    val snapshot = card.snapshot ?: return
    val calibration = ProtocolProfile.DEFAULT.calibration
    val head = LinakSensorDecoder.fromRaw(Actuator.HEAD, snapshot.position.head, null, calibration)
    val feet = LinakSensorDecoder.fromRaw(Actuator.FEET, snapshot.position.feet, null, calibration)
    Text(stringResource(R.string.simbed_link, card.link.toString()))
    Text(stringResource(R.string.simbed_height, stringResource(R.string.control_head), head.cm, head.raw))
    Text(stringResource(R.string.simbed_height, stringResource(R.string.control_feet), feet.cm, feet.raw))
    Text(stringResource(if (snapshot.lightOn) R.string.light_on else R.string.light_off))
    Text(stringResource(R.string.simbed_moving, snapshot.moving?.toString() ?: "-"))
}

private const val RECEIVED_SHOWN = 5
private const val MS_PER_SECOND = 1000.0
