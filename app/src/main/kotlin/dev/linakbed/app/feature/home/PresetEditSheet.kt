package dev.linakbed.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R

/** Custom 길게 누르기 (S12): 현재 위치로 저장 → 확인 다이얼로그(S6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditSheet(
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.preset_custom),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_preset_save_here)) },
                supportingContent = { Text(stringResource(R.string.home_preset_save_here_desc)) },
                leadingContent = { Icon(Icons.Filled.Done, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onSave),
            )
        }
    }
}
