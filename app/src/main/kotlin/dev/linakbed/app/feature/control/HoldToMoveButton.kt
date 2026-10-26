package dev.linakbed.app.feature.control

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.linakbed.app.ui.glow.GlowSurface

/**
 * 누르고 있는 동안만 동작하는 glow 버튼 (S3, S10). 손을 떼거나, 손가락이 버튼 밖으로 나가거나, 제스처가 취소돼도 [onRelease]가 불린다.
 * [label]은 화면에 보이는 짧은 글자(▲/▼), [description]은 접근성용 전체 설명.
 */
@Composable
fun HoldToMoveButton(
    label: String,
    description: String,
    pressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    GlowSurface(
        lit = pressed,
        enabled = enabled,
        modifier =
            modifier
                .defaultMinSize(minWidth = MIN_SIZE.dp, minHeight = MIN_SIZE.dp)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    if (!enabled) disabled()
                }.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            currentOnPress()
                            // 누른 채로 비활성화되거나(연결 끊김) 화면에서 사라져 제스처가 취소돼도 반드시 멈춘다 (S3).
                            try {
                                tryAwaitRelease()
                            } finally {
                                currentOnRelease()
                            }
                        },
                    )
                },
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private const val MIN_SIZE = 48
