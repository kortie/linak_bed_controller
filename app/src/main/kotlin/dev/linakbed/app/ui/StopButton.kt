package dev.linakbed.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.linakbed.app.R
import dev.linakbed.app.ui.glow.GlowButton
import dev.linakbed.app.ui.theme.BrandColors

/**
 * 모터를 움직일 수 있는 모든 화면 하단 가운데에 고정하는 STOP (linak-conventions §6, S10).
 * 연결 상태와 무관하게 항상 활성, 늘 빨갛게 빛나는 큰 알약형 버튼.
 */
@Composable
fun StopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.control_stop_description)
    Box(modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        GlowButton(
            onClick = onClick,
            active = true,
            accent = BrandColors.Red,
            shape = CircleShape,
            modifier =
                Modifier
                    .widthIn(min = MIN_WIDTH.dp, max = MAX_WIDTH.dp)
                    .fillMaxWidth()
                    .height(HEIGHT.dp)
                    .semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.control_stop), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private const val MIN_WIDTH = 240
private const val MAX_WIDTH = 360
private const val HEIGHT = 72
