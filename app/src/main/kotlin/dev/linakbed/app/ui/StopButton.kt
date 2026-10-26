package dev.linakbed.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * 연결 상태와 무관하게 항상 활성, 늘 빨갛게 빛나는 알약형 버튼.
 */
@Composable
fun StopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.control_stop_description)
    // 화면 끝까지 그리는(edge-to-edge) 앱이라 시스템 내비게이션 바 높이만큼 띄워야 STOP이 가려지지 않는다.
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        GlowButton(
            onClick = onClick,
            active = true,
            accent = BrandColors.Red,
            shape = CircleShape,
            modifier =
                Modifier
                    .width(WIDTH.dp)
                    .height(HEIGHT.dp)
                    .semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.control_stop), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** S12: 폭 180dp·높이 48dp(접근성 최소 터치 크기). 빨간 glow로 눈에 띄게 유지. */
private const val WIDTH = 180
private const val HEIGHT = 48
