package dev.linakbed.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.feature.control.ControlAction
import dev.linakbed.app.feature.control.ControlUiState
import dev.linakbed.app.feature.control.ControlViewModel
import dev.linakbed.app.feature.preset.PresetAction
import dev.linakbed.app.feature.preset.PresetDialogs
import dev.linakbed.app.feature.preset.PresetUiState
import dev.linakbed.app.feature.preset.PresetViewModel
import dev.linakbed.app.feature.preset.presetLabel
import dev.linakbed.app.feature.preset.presetMessage
import dev.linakbed.app.ui.StopButton
import dev.linakbed.app.ui.message
import dev.linakbed.app.ui.theme.LinakBedTheme
import dev.linakbed.domain.controller.LightState
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.PresetSlot

/** 홈 (ADR-009): 연결 상태 · 높이 · 조정 · 프리셋 · 조명 · STOP을 한 화면에. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    control: ControlViewModel = hiltViewModel(),
    presets: PresetViewModel = hiltViewModel(),
) {
    val controlState by control.uiState.collectAsStateWithLifecycle()
    val presetState by presets.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { control.onAction(ControlAction.ScreenStarted) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { control.onAction(ControlAction.ScreenStopped) }
    HomeContent(
        control = controlState,
        presets = presetState,
        onControl = control::onAction,
        onPreset = presets::onAction,
        onOpenSettings = onOpenSettings,
        onOpenAbout = onOpenAbout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    control: ControlUiState,
    presets: PresetUiState,
    onControl: (ControlAction) -> Unit,
    onPreset: (PresetAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    UserMessages(control, presets, snackbar, onControl, onPreset)
    var editing by remember { mutableStateOf<PresetSlot?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = { HomeTopBar(control, onControl, onOpenSettings, onOpenAbout) },
        snackbarHost = { SnackbarHost(snackbar) },
        // STOP은 하단 고정, 연결 상태와 무관하게 항상 누를 수 있다.
        bottomBar = { StopButton(onClick = { onControl(ControlAction.Stop) }) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = MAX_WIDTH.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                DeviceControls(control, onControl)
                PresetGrid(
                    names = presets.names,
                    running = control.runningPreset,
                    enabled = control.controlsEnabled,
                    onRecall = { onPreset(PresetAction.Recall(it)) },
                    onEdit = { editing = it },
                )
            }
        }
    }
    editing?.let { slot ->
        PresetEditSheet(
            name = presetLabel(presets, slot),
            onSave = {
                editing = null
                onPreset(PresetAction.RequestSave(slot))
            },
            onRename = {
                editing = null
                onPreset(PresetAction.StartRename(slot))
            },
            onDismiss = { editing = null },
        )
    }
    PresetDialogs(presets, onPreset)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    control: ControlUiState,
    onControl: (ControlAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = { Text(control.bedName ?: stringResource(R.string.app_label_launcher), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            ConnectionChip(control.link, onReconnect = { onControl(ControlAction.Reconnect) })
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_open_settings))
            }
            IconButton(onClick = onOpenAbout) { Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.home_open_about)) }
        },
    )
}

@Composable
private fun UserMessages(
    control: ControlUiState,
    presets: PresetUiState,
    snackbar: SnackbarHostState,
    onControl: (ControlAction) -> Unit,
    onPreset: (PresetAction) -> Unit,
) {
    val controlMessage = control.userMessage?.message()
    LaunchedEffect(controlMessage) {
        if (controlMessage != null) {
            snackbar.showSnackbar(controlMessage)
            onControl(ControlAction.MessageShown)
        }
    }
    val presetText = presets.message?.let { presetMessage(it, presets.names) }
    LaunchedEffect(presetText) {
        if (presetText != null) {
            snackbar.showSnackbar(presetText)
            onPreset(PresetAction.MessageShown)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1530)
@Composable
private fun HomeContentPreview() {
    LinakBedTheme {
        HomeContent(
            control =
                ControlUiState(bedName = "DPG1M", link = LinkState.Connected, headCm = 32.5, feetCm = 12.0, light = LightState.ON),
            presets = PresetUiState(),
            onControl = {},
            onPreset = {},
            onOpenSettings = {},
            onOpenAbout = {},
        )
    }
}

private const val MAX_WIDTH = 600
