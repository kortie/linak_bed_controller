package dev.linakbed.app.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.R
import dev.linakbed.app.feature.pairing.NotFoundHelp
import dev.linakbed.app.feature.pairing.PairingAction
import dev.linakbed.app.feature.pairing.PairingEffects
import dev.linakbed.app.feature.pairing.PairingUiState
import dev.linakbed.app.feature.pairing.PairingViewModel
import dev.linakbed.app.ui.LanguageToggle
import dev.linakbed.app.ui.theme.LinakBedTheme

/** 연결 안내 (ADR-009). 침대가 연결(페어링)되면 앱 루트가 홈으로 바꾼다. */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    pairing: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pairingState by pairing.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onAction(OnboardingAction.PermissionResult(it))
        }
    LifecycleEventEffect(
        Lifecycle.Event.ON_RESUME,
    ) { viewModel.onAction(OnboardingAction.PermissionChecked(context.hasBluetoothPermission())) }
    BackHandler(enabled = state.step != OnboardingStep.INTRO) { viewModel.onAction(OnboardingAction.Back) }
    PairingEffects(pairingState, pairing::onAction, snackbar)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            OnboardingContent(
                state = state,
                pairing = pairingState,
                onStart = { viewModel.onAction(OnboardingAction.Start(context.hasBluetoothPermission())) },
                onRequestPermission = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                onOpenAppSettings = { context.startActivity(appSettings(context)) },
                onPairing = pairing::onAction,
            )
            LanguageToggle(Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    pairing: PairingUiState,
    onStart: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPairing: (PairingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stepLabel(state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        when (state.step) {
            OnboardingStep.INTRO -> Intro(onStart)
            OnboardingStep.PERMISSION -> Permission(state.permissionDenied, onRequestPermission, onOpenAppSettings)
            OnboardingStep.CONNECT -> Connect(pairing, onPairing)
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit) {
    Title(stringResource(R.string.onboarding_intro_title))
    Body(stringResource(R.string.onboarding_intro_body))
    Spacer(Modifier.heightIn(min = 8.dp))
    PrimaryButton(stringResource(R.string.onboarding_start), onStart)
}

@Composable
private fun Permission(
    denied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Title(stringResource(R.string.permission_ble_title))
    Body(stringResource(R.string.permission_ble_rationale))
    PrimaryButton(stringResource(R.string.permission_ble_request), onRequest)
    if (denied) {
        Body(stringResource(R.string.onboarding_permission_denied))
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.permission_open_settings)) }
    }
}

@Composable
private fun Connect(
    pairing: PairingUiState,
    onPairing: (PairingAction) -> Unit,
) {
    Title(stringResource(R.string.onboarding_connect_title))
    Body(stringResource(R.string.onboarding_connect_body))
    if (pairing.inProgress) {
        CircularProgressIndicator(Modifier.size(32.dp))
    } else {
        PrimaryButton(stringResource(R.string.onboarding_find_bed)) { onPairing(PairingAction.Pair) }
    }
    NotFoundHelp(pairing, onPairing)
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(text) }
}

@Composable
private fun stepLabel(state: OnboardingUiState): String =
    stringResource(R.string.onboarding_step, state.stepNumber, state.visibleSteps.size)

private fun Context.hasBluetoothPermission() =
    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    LinakBedTheme {
        OnboardingContent(
            state = OnboardingUiState(step = OnboardingStep.CONNECT),
            pairing = PairingUiState(loaded = true),
            onStart = {},
            onRequestPermission = {},
            onOpenAppSettings = {},
            onPairing = {},
        )
    }
}
