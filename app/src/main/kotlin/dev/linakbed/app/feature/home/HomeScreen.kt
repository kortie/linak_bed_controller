package dev.linakbed.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.feature.control.ControlAction
import dev.linakbed.app.feature.control.ControlUiState
import dev.linakbed.app.feature.control.ControlViewModel
import dev.linakbed.app.feature.pairing.NotFoundHelp
import dev.linakbed.app.feature.pairing.PairingAction
import dev.linakbed.app.feature.pairing.PairingEffects
import dev.linakbed.app.feature.pairing.PairingViewModel
import dev.linakbed.app.feature.preset.PresetAction
import dev.linakbed.app.feature.preset.PresetDialogs
import dev.linakbed.app.feature.preset.PresetUiState
import dev.linakbed.app.feature.preset.PresetViewModel
import dev.linakbed.app.feature.preset.presetMessage
import dev.linakbed.app.ui.LanguageToggle
import dev.linakbed.app.ui.StopButton
import dev.linakbed.app.ui.glow.GlowButton
import dev.linakbed.app.ui.message
import dev.linakbed.app.ui.theme.BrandColors
import dev.linakbed.app.ui.theme.LinakBedTheme
import dev.linakbed.domain.controller.LightState
import dev.linakbed.domain.port.LinkState

/** 홈 (ADR-009·012): 침대마다 기기 화면 카드를 좌우로 넘기고, 마지막은 "침대 추가". 하단 STOP은 모든 침대. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    home: HomeViewModel = hiltViewModel(),
) {
    val state by home.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HomeScaffold(snackbar, onStopAll = home::stopAll, onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout) {
        BedPager(
            bedIds = state.bedIds,
            canAdd = state.canAdd,
            bedPage = { bedId -> BedPage(bedId, snackbar) },
            addPage = { AddBedPage(snackbar) },
        )
    }
}

/** 침대 한 대 카드: 그 침대의 ViewModel들(bedId로 구분). 카드가 보이는 동안만 연결을 유지한다 (ADR-003). */
@Composable
private fun BedPage(
    bedId: String,
    snackbar: SnackbarHostState,
) {
    val control = hiltViewModel<ControlViewModel, ControlViewModel.Factory>(key = "control-$bedId") { it.create(bedId) }
    val presets = hiltViewModel<PresetViewModel, PresetViewModel.Factory>(key = "presets-$bedId") { it.create(bedId) }
    val controlState by control.uiState.collectAsStateWithLifecycle()
    val presetState by presets.uiState.collectAsStateWithLifecycle()
    // 앱이 백그라운드로 가거나 다른 카드로 넘겨 이 카드가 사라지면 움직이던 것을 멈추고 연결 유지를 놓는다 (S3).
    LifecycleStartEffect(bedId) {
        control.onAction(ControlAction.ScreenStarted)
        onStopOrDispose { control.onAction(ControlAction.ScreenStopped) }
    }
    BedCard(controlState, presetState, control::onAction, presets::onAction, snackbar)
}

/** "침대 추가" 카드: 시스템 기기 선택 창으로 두 번째 침대를 연결한다 (ADR-012). */
@Composable
private fun AddBedPage(snackbar: SnackbarHostState) {
    val pairing: PairingViewModel = hiltViewModel(key = "add-bed")
    val state by pairing.uiState.collectAsStateWithLifecycle()
    PairingEffects(state, pairing::onAction, snackbar)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.home_add_bed_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.home_add_bed_body), style = MaterialTheme.typography.bodyMedium)
        if (state.inProgress) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else {
            GlowButton(onClick = { pairing.onAction(PairingAction.Pair) }, shape = CircleShape, modifier = Modifier.testTag(ADD_BED_TAG)) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.pairing_add), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        NotFoundHelp(state, pairing::onAction)
    }
}

/** 카드를 좌우로 넘기는 영역과 몇 번째 카드인지 보여 주는 점. */
@Composable
fun BedPager(
    bedIds: List<String>,
    canAdd: Boolean,
    bedPage: @Composable (String) -> Unit,
    addPage: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = bedIds.size + if (canAdd) 1 else 0
    val pager = rememberPagerState { pageCount }
    Column(modifier.fillMaxSize()) {
        if (pageCount > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).testTag(PAGE_DOTS_TAG),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(pageCount) { index ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (index == pager.currentPage) BrandColors.Cyan else BrandColors.Outline, CircleShape),
                    )
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).testTag(PAGER_TAG)) { page ->
            if (page < bedIds.size) bedPage(bedIds[page]) else addPage()
        }
    }
}

/** 상단 바(설정·정보)와 하단 STOP(모든 침대)을 두르는 틀. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    snackbar: SnackbarHostState,
    onStopAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.app_label_launcher)) },
                actions = {
                    LanguageToggle()
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_open_settings))
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.home_open_about))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        // STOP은 하단 고정, 연결 상태·보고 있는 카드와 무관하게 모든 침대를 멈춘다.
        bottomBar = { StopButton(onClick = onStopAll) },
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
}

/** 침대 한 대의 기기 화면 카드: 이름·연결 상태, 침대 그림과 부위별 버튼, 프리셋. */
@Composable
fun BedCard(
    control: ControlUiState,
    presets: PresetUiState,
    onControl: (ControlAction) -> Unit,
    onPreset: (PresetAction) -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    UserMessages(control, presets, snackbar, onControl, onPreset)
    var editingCustom by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = MAX_WIDTH.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    control.bedName ?: stringResource(R.string.pairing_unnamed),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ConnectionChip(control.link, onReconnect = { onControl(ControlAction.Reconnect) })
            }
            DeviceControls(control, onControl)
            PresetGrid(
                poses = presets.poses,
                running = control.runningPreset,
                enabled = control.controlsEnabled,
                onRecall = { onPreset(PresetAction.Recall(it)) },
                onEditCustom = { editingCustom = true },
            )
        }
    }
    if (editingCustom) {
        PresetEditSheet(
            onSave = {
                editingCustom = false
                onPreset(PresetAction.RequestSave(CUSTOM_SLOT))
            },
            onDismiss = { editingCustom = false },
        )
    }
    PresetDialogs(presets, onPreset)
}

/** 침대 한 대만 있을 때의 홈 (미리보기·테스트용): 틀 + 카드 하나. STOP은 [ControlAction.Stop]. */
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
    HomeScaffold(snackbar, onStopAll = { onControl(ControlAction.Stop) }, onOpenSettings, onOpenAbout, modifier) {
        BedCard(control, presets, onControl, onPreset, snackbar)
    }
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

const val PAGER_TAG = "bed_pager"
const val PAGE_DOTS_TAG = "bed_page_dots"
const val ADD_BED_TAG = "add_bed"
private const val MAX_WIDTH = 600
