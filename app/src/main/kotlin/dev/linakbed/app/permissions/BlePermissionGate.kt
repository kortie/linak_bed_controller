package dev.linakbed.app.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.app.ui.LanguageToggle
import dev.linakbed.app.ui.theme.LinakBedTheme

/** 블루투스 연결 권한(BLUETOOTH_CONNECT)이 있어야 [content]를 보여 준다. */
@Composable
fun BlePermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) {
        content()
    } else {
        PermissionRationaleContent(
            onRequest = { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

@Composable
fun PermissionRationaleContent(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.permission_ble_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.permission_ble_rationale), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRequest) { Text(stringResource(R.string.permission_ble_request)) }
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
        }
        LanguageToggle(Modifier.align(Alignment.TopEnd))
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionRationalePreview() {
    LinakBedTheme { PermissionRationaleContent(onRequest = {}, onOpenSettings = {}) }
}
