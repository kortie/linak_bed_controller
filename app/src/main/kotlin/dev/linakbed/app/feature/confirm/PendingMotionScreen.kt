package dev.linakbed.app.feature.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.linakbed.app.R
import dev.linakbed.app.ui.theme.LinakBedTheme

/**
 * S9 실행 전 큰 취소 화면 (ADR-008). 잠결에도 누르기 쉽게 화면의 2/3를 취소 버튼 하나가 차지한다.
 * "바로 실행" 같은 다른 버튼은 두지 않는다 — 어디를 눌러도 결과는 취소 쪽이다.
 */
@Composable
fun PendingMotionScreen(
    state: PendingMotionUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cancelDescription = stringResource(R.string.confirm_cancel_description)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize().testTag(ROOT_TAG)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text =
                            stringResource(
                                R.string.confirm_moving_to,
                                state.presetName ?: stringResource(R.string.confirm_preset_unknown),
                            ),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.confirm_seconds, state.remainingSeconds),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Button(
                onClick = onCancel,
                shape = RectangleShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                modifier =
                    Modifier
                        .weight(2f)
                        .fillMaxWidth()
                        .testTag(CANCEL_TAG)
                        .semantics { contentDescription = cancelDescription },
            ) {
                Text(stringResource(R.string.confirm_cancel), fontSize = 64.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

const val ROOT_TAG = "pending_motion_root"
const val CANCEL_TAG = "pending_motion_cancel"

@Preview(showBackground = true)
@Composable
private fun PendingMotionScreenPreview() {
    LinakBedTheme { PendingMotionScreen(PendingMotionUiState(remainingSeconds = 8), onCancel = {}) }
}
