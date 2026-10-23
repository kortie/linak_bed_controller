// 그림 비율(침대 각 부분의 위치·크기)이 많아 이름 없는 비율 숫자를 그대로 쓴다.
@file:Suppress("MagicNumber")

package dev.linakbed.app.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.linakbed.app.R
import dev.linakbed.app.ui.theme.BrandColors
import dev.linakbed.protocol.Actuator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 침대 옆모습 (S10, 머리가 왼쪽). 머리판·발판 각도가 [pose]를 부드럽게 따라가고,
 * 누르고 있는 부위([highlight])는 시안색으로 빛난다. 높이를 모르면 평평하고 흐리게 그린다.
 */
@Composable
fun BedIllustration(
    pose: BedPose,
    highlight: Actuator?,
    modifier: Modifier = Modifier,
) {
    val headAngle by animateFloatAsState((pose.head ?: 0f) * HEAD_MAX_DEG, tween(ANIMATION_MS), label = "head")
    val feetAngle by animateFloatAsState((pose.feet ?: 0f) * FEET_MAX_DEG, tween(ANIMATION_MS), label = "feet")
    val description = stringResource(R.string.home_bed_description)
    val state =
        if (pose.known) {
            stringResource(R.string.home_bed_state, percent(pose.head), percent(pose.feet))
        } else {
            stringResource(R.string.home_bed_unknown)
        }
    val alpha = if (pose.known) 1f else UNKNOWN_ALPHA
    Canvas(
        modifier
            .aspectRatio(ASPECT)
            .testTag(BED_TAG)
            .semantics {
                contentDescription = description
                stateDescription = state
            },
    ) {
        val g = Geometry(size)
        drawFrame(g, alpha)
        drawSegment(Offset(g.seatLeft + g.gap, g.top), g.seatLength - 2 * g.gap, g, lit = false, alpha)
        drawStrut(g, Offset(g.seatLeft, g.bottom), -g.headLength * STRUT_REACH, headAngle, FRAME.copy(alpha = alpha))
        drawStrut(g, Offset(g.seatRight, g.bottom), g.footLength * STRUT_REACH, feetAngle, FRAME.copy(alpha = alpha))
        rotate(degrees = headAngle, pivot = Offset(g.seatLeft, g.bottom)) {
            drawSegment(
                Offset(g.left, g.top),
                g.headLength - g.gap,
                g,
                lit = highlight == Actuator.HEAD || highlight == Actuator.BOTH,
                alpha,
            )
            drawRoundRect(
                color = PILLOW.copy(alpha = alpha),
                topLeft = Offset(g.left + g.headLength * 0.08f, g.top - g.thickness * 0.5f),
                size = Size(g.headLength * 0.42f, g.thickness * 0.6f),
                cornerRadius = CornerRadius(g.thickness * 0.3f),
            )
        }
        rotate(degrees = -feetAngle, pivot = Offset(g.seatRight, g.bottom)) {
            drawSegment(
                Offset(g.seatRight + g.gap, g.top),
                g.footLength - g.gap,
                g,
                lit = highlight == Actuator.FEET || highlight == Actuator.BOTH,
                alpha,
            )
        }
    }
}

/** 캔버스 크기에 비례한 침대 치수. */
private class Geometry(
    size: Size,
) {
    val left = size.width * 0.06f
    val length = size.width * 0.88f
    val frameTop = size.height * 0.66f
    val frameHeight = size.height * 0.08f
    val thickness = size.height * 0.12f
    val bottom = frameTop - size.height * 0.02f
    val top = bottom - thickness
    val headLength = length * 0.36f
    val seatLength = length * 0.24f
    val footLength = length * 0.40f
    val seatLeft = left + headLength
    val seatRight = seatLeft + seatLength
    val gap = size.width * 0.004f
    val legWidth = size.width * 0.025f
    val legHeight = size.height * 0.22f
    val stroke = size.width * 0.012f
}

private fun DrawScope.drawFrame(
    g: Geometry,
    alpha: Float,
) {
    val color = FRAME.copy(alpha = alpha)
    listOf(g.left + g.length * 0.06f, g.left + g.length * 0.94f - g.legWidth).forEach { x ->
        drawRoundRect(color, Offset(x, g.frameTop), Size(g.legWidth, g.legHeight), CornerRadius(g.legWidth / 2))
    }
    drawRoundRect(color, Offset(g.left, g.frameTop), Size(g.length, g.frameHeight), CornerRadius(g.frameHeight / 2))
}

private fun DrawScope.drawSegment(
    topLeft: Offset,
    width: Float,
    g: Geometry,
    lit: Boolean,
    alpha: Float,
) {
    val corner = CornerRadius(g.thickness * 0.35f)
    val segmentSize = Size(width, g.thickness)
    if (lit) {
        drawRoundRect(
            color = BrandColors.Cyan.copy(alpha = GLOW_ALPHA),
            topLeft = topLeft - Offset(g.stroke, g.stroke),
            size = Size(width + 2 * g.stroke, g.thickness + 2 * g.stroke),
            cornerRadius = CornerRadius(corner.x + g.stroke),
            style = Stroke(width = g.stroke * 2),
        )
    }
    val colors = if (lit) listOf(BrandColors.Cyan, Color(0xFF1FA6D6)) else listOf(MATTRESS_TOP, MATTRESS_BOTTOM)
    drawRoundRect(
        brush = Brush.verticalGradient(colors.map { it.copy(alpha = alpha) }, startY = topLeft.y, endY = topLeft.y + g.thickness),
        topLeft = topLeft,
        size = segmentSize,
        cornerRadius = corner,
    )
}

/** 올라간 판을 받치는 지지대. [reach]가 음수면 왼쪽(머리), 양수면 오른쪽(발)으로 뻗는다. */
private fun DrawScope.drawStrut(
    g: Geometry,
    pivot: Offset,
    reach: Float,
    angleDeg: Float,
    color: Color,
) {
    if (angleDeg < MIN_STRUT_DEG) return
    val radians = Math.toRadians(angleDeg.toDouble())
    val end = Offset(pivot.x + reach * cos(radians).toFloat(), pivot.y - abs(reach) * sin(radians).toFloat())
    val start = Offset(pivot.x + reach * 0.6f, g.frameTop)
    drawLine(color, start, end, strokeWidth = g.stroke, cap = StrokeCap.Round)
}

private fun percent(value: Float?) = ((value ?: 0f) * PERCENT).roundToInt()

const val BED_TAG = "bed_illustration"
private const val HEAD_MAX_DEG = 60f
private const val FEET_MAX_DEG = 30f
private const val ANIMATION_MS = 300
private const val ASPECT = 2.1f
private const val UNKNOWN_ALPHA = 0.45f
private const val GLOW_ALPHA = 0.35f
private const val MIN_STRUT_DEG = 1f
private const val STRUT_REACH = 0.55f
private const val PERCENT = 100
private val FRAME = Color(0xFF2E4275)
private val MATTRESS_TOP = Color(0xFF6A82C2)
private val MATTRESS_BOTTOM = Color(0xFF41588F)
private val PILLOW = Color(0xFFD5DFF7)
