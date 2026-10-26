package dev.linakbed.app.feature.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.ui.theme.LinakBedTheme
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.log.ExecutionEntry
import dev.linakbed.domain.remote.RemoteChannel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExecutionLogScreen(viewModel: ExecutionLogViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    ExecutionLogContent(entries)
}

/** 출처(수동/원격 채널)·requestId·결과·사유. */
@Composable
fun ExecutionLogContent(entries: List<ExecutionEntry>) {
    if (entries.isEmpty()) {
        Text(stringResource(R.string.log_empty), modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(
                            R.string.log_line,
                            TIME.format(entry.at.atZone(ZoneId.systemDefault())),
                            source(entry),
                            entry.action,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(entry.result, fontFamily = FontFamily.Monospace)
                    entry.requestId?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    entry.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun source(entry: ExecutionEntry): String =
    when (entry.source) {
        CommandSource.MANUAL -> stringResource(R.string.log_source_manual)
        CommandSource.REMOTE ->
            when (entry.channel) {
                RemoteChannel.SMARTTHINGS_NOTIFICATION, null -> stringResource(R.string.remote_channel_smartthings)
                RemoteChannel.APP_SHORTCUT -> stringResource(R.string.remote_channel_shortcut)
            }
    }

private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

@Preview(showBackground = true)
@Composable
private fun ExecutionLogPreview() {
    LinakBedTheme {
        ExecutionLogContent(
            listOf(
                ExecutionEntry(
                    Instant.EPOCH,
                    CommandSource.REMOTE,
                    RemoteChannel.SMARTTHINGS_NOTIFICATION,
                    "a1b2",
                    "preset P2",
                    "executed",
                ),
            ),
        )
    }
}
