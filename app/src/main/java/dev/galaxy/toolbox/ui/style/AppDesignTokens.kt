package dev.galaxy.toolbox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class AppDesignTokens(
    val background: Color,
    val surface: Color,
    val foreground: Color,
    val secondaryText: Color,
    val accent: Color,
    val border: Color,
    val cardRadius: Dp,
    val floatingRadius: Dp,
    val sideInset: Dp,
    val sectionGap: Dp,
    val largeTitle: TextUnit,
    val title: TextUnit,
    val body: TextUnit,
    val caption: TextUnit,
    val springDamping: Float,
    val springStiffness: Float,
    val glassBlur: Dp,
    val glassLensHeight: Dp,
    val glassLensAmount: Dp,
    val glassHighlightWidth: Dp,
    val glassShadowRadius: Dp,
)

internal val LocalAppDesignTokens = staticCompositionLocalOf {
    AppDesignTokens(
        background = Color(0xFFF2F5F8), surface = Color(0xFFF9FAFC),
        foreground = Color(0xFF1B2936), secondaryText = Color(0xFF4C6072),
        accent = Color(0xFF0067C8), border = Color(0xFFCCD6DF),
        cardRadius = 22.dp, floatingRadius = 28.dp, sideInset = 16.dp,
        sectionGap = 10.dp, largeTitle = 34.sp, title = 22.sp, body = 17.sp,
        caption = 12.sp, springDamping = .78f, springStiffness = 410f,
        glassBlur = 8.dp, glassLensHeight = 10.dp, glassLensAmount = 20.dp,
        glassHighlightWidth = 1.dp, glassShadowRadius = 18.dp,
    )
}

@Composable internal fun appDesignTokens(style: AppStyle): AppDesignTokens {
    val base = LocalAppDesignTokens.current
    return if (style == AppStyle.MIUIX) base.copy(
        background = MiuixTheme.colorScheme.background,
        surface = MiuixTheme.colorScheme.surfaceContainer,
        foreground = MiuixTheme.colorScheme.onSurface,
        secondaryText = MiuixTheme.colorScheme.onSecondaryVariant,
        accent = MiuixTheme.colorScheme.primary,
        border = MiuixTheme.colorScheme.dividerLine,
        cardRadius = 20.dp, floatingRadius = 30.dp, sectionGap = 12.dp,
        springDamping = .82f, springStiffness = 440f,
    ) else base.copy(
        background = MaterialTheme.colorScheme.background,
        surface = MaterialTheme.colorScheme.surface,
        foreground = MaterialTheme.colorScheme.onSurface,
        secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
        accent = MaterialTheme.colorScheme.primary,
    )
}
