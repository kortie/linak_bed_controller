package dev.linakbed.app.ui.glow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.linakbed.app.ui.theme.BrandColors

/**
 * glow 버튼의 겉모습. [lit]이면(눌림·활성) 빛이 번지고 테두리가 밝아지며 살짝 커진다.
 * 입력 처리는 호출하는 쪽이 [modifier]로 붙인다 (누르고 있기 버튼은 별도 제스처를 쓰기 때문).
 */
@Composable
fun GlowSurface(
    lit: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = GlowStyle.Accent,
    shape: Shape = GlowStyle.Shape,
    content: @Composable () -> Unit,
) {
    val level by animateFloatAsState(if (lit && enabled) 1f else 0f, tween(GlowStyle.ANIMATION_MS), label = "glow")
    val fill =
        Brush.verticalGradient(
            listOf(
                lerpColor(BrandColors.NavyHigh, accent.copy(alpha = 0.55f), level),
                lerpColor(BrandColors.NavyRaised, accent.copy(alpha = 0.25f), level),
            ),
        )
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .graphicsLayer {
                    val scale = 1f + (GlowStyle.PRESSED_SCALE - 1f) * level
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else GlowStyle.DISABLED_ALPHA
                }.glow(accent, level, shape)
                .background(fill, shape)
                .border(1.dp, lerpColor(BrandColors.Outline, accent, level), shape),
    ) {
        CompositionLocalProvider(LocalContentColor provides lerpColor(BrandColors.Text, Color.White, level)) { content() }
    }
}

/** 탭(·길게 누르기) glow 버튼. [active]면 누르지 않아도 빛난다 (이동 중 프리셋, 켜진 조명). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    accent: Color = GlowStyle.Accent,
    shape: Shape = GlowStyle.Shape,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    GlowSurface(
        lit = pressed || active,
        enabled = enabled,
        accent = accent,
        shape = shape,
        modifier =
            modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        content = content,
    )
}

private fun lerpColor(
    from: Color,
    to: Color,
    fraction: Float,
): Color =
    androidx.compose.ui.graphics
        .lerp(from, to, fraction)
