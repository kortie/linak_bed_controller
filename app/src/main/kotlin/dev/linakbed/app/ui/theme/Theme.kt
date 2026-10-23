// 브랜드 색상표(ARGB 16진수)라 이름 없는 숫자를 그대로 쓴다.
@file:Suppress("MagicNumber")

package dev.linakbed.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandDark =
    darkColorScheme(
        primary = BrandColors.Cyan,
        onPrimary = BrandColors.OnCyan,
        primaryContainer = Color(0xFF16405A),
        onPrimaryContainer = Color(0xFFCFF4FF),
        secondary = Color(0xFF9DB3E6),
        onSecondary = Color(0xFF0E1D45),
        secondaryContainer = BrandColors.NavyHigh,
        onSecondaryContainer = Color(0xFFDCE4FF),
        tertiary = Color(0xFFC6B6FF),
        tertiaryContainer = Color(0xFF2E2860),
        onTertiaryContainer = Color(0xFFE8DEFF),
        background = BrandColors.Navy,
        onBackground = BrandColors.Text,
        surface = BrandColors.Navy,
        onSurface = BrandColors.Text,
        surfaceVariant = BrandColors.NavySurface,
        onSurfaceVariant = BrandColors.TextDim,
        surfaceContainerLowest = Color(0xFF08102A),
        surfaceContainerLow = Color(0xFF0F1A36),
        surfaceContainer = BrandColors.NavyRaised,
        surfaceContainerHigh = Color(0xFF172447),
        surfaceContainerHighest = BrandColors.NavySurface,
        outline = BrandColors.Outline,
        outlineVariant = Color(0xFF26355C),
        error = BrandColors.Red,
        onError = Color(0xFF3A0008),
        errorContainer = Color(0xFF5A1620),
        onErrorContainer = Color(0xFFFFDADC),
    )

/** 앱 전체 고정 다크 브랜드 테마 (S10): 네이비 + 시안. 시스템 라이트/다크 설정과 무관. */
@Composable
fun LinakBedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BrandDark, content = content)
}
