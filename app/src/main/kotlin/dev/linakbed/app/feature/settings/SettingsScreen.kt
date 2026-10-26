package dev.linakbed.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.app.feature.pairing.PairingSection
import dev.linakbed.app.navigation.SettingsRoute

/** 설정: 침대 관리 · SmartThings 루틴 연동 · 루틴 설정 방법 · 실행 기록 · 고급(진단). */
@Composable
fun SettingsScreen(onOpen: (SettingsRoute) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PairingSection(snackbar)
            HorizontalDivider()
            Column {
                SettingsRoute.entries.forEach { page ->
                    ListItem(
                        headlineContent = { Text(stringResource(page.title)) },
                        supportingContent = descriptionOf(page)?.let { res -> { Text(stringResource(res)) } },
                        modifier = Modifier.clickable { onOpen(page) },
                    )
                }
            }
        }
    }
}

private fun descriptionOf(page: SettingsRoute): Int? =
    when (page) {
        SettingsRoute.REMOTE -> R.string.settings_remote_desc
        SettingsRoute.ROUTINES -> R.string.settings_routines_desc
        SettingsRoute.GUIDE -> null
        SettingsRoute.LOG -> R.string.settings_log_desc
        SettingsRoute.DIAGNOSTICS -> R.string.settings_diagnostics_desc
    }
