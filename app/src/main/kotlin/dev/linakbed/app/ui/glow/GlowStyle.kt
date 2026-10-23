package dev.linakbed.app.ui.glow

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.linakbed.app.ui.theme.BrandColors

/** glow 버튼 공통 값 (S10). */
object GlowStyle {
    val Shape: Shape = RoundedCornerShape(22.dp)
    val Accent = BrandColors.Cyan
    const val DISABLED_ALPHA = 0.38f
    const val PRESSED_SCALE = 1.06f
    val GLOW_SPREAD = 14.dp
    const val GLOW_LAYERS = 6
    const val LAYER_ALPHA = 0.08f
    const val ANIMATION_MS = 180
}

/**
 * 요소 뒤로 [color] 빛이 번진다 — [shape] 테두리를 따라 폭이 일정한 후광(버튼 크기와 무관). [intensity] 0이면 안 보임.
 * 요소 경계 밖까지 그린다.
 */
fun Modifier.glow(
    color: Color,
    intensity: Float,
    shape: Shape,
): Modifier =
    drawBehind {
        if (intensity <= 0f) return@drawBehind
        val outline = shape.createOutline(size, layoutDirection, this)
        val spread = GlowStyle.GLOW_SPREAD.toPx()
        // 넓은 획부터 좁은 획까지 겹쳐 그리면 가장자리로 갈수록 옅어지는 부드러운 빛이 된다.
        for (layer in GlowStyle.GLOW_LAYERS downTo 1) {
            drawOutline(
                outline = outline,
                color = color.copy(alpha = GlowStyle.LAYER_ALPHA * intensity),
                style = Stroke(width = spread * 2 * layer / GlowStyle.GLOW_LAYERS),
            )
        }
    }
