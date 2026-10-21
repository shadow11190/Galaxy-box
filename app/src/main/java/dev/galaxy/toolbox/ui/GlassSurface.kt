package dev.galaxy.toolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import java.util.concurrent.atomic.AtomicBoolean

internal val Mint: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val Navy: Color @Composable get() = MaterialTheme.colorScheme.background
internal val Panel: Color @Composable get() = MaterialTheme.colorScheme.surface
internal val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

internal enum class GalaxyVisualStyle(val preferenceValue: String, val label: String) {
    ORIGINAL("original", "原有風格"), MIUIX("miuix", "Miuix 風格");

    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull { it.preferenceValue == value } ?: ORIGINAL
    }
}

internal val LocalVisualStyle = staticCompositionLocalOf { GalaxyVisualStyle.ORIGINAL }

internal data class GlassEnvironment(val enabled: Boolean = false, val backdrop: Backdrop? = null, val onFailure: () -> Unit = {})
internal val LocalGlass = staticCompositionLocalOf { GlassEnvironment() }

/** All in-app cards and navigation use this one effect; content is drawn after the effect. */
@Composable internal fun Modifier.galaxyGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    backdrop: Backdrop? = LocalGlass.current.backdrop,
    enabled: Boolean = LocalGlass.current.enabled,
    floating: Boolean = false,
    compact: Boolean = false
): Modifier {
    val tint = Panel
    val dark = MaterialTheme.colorScheme.background.red < .3f
    val failed = remember(backdrop) { AtomicBoolean(false) }
    val onFailure = LocalGlass.current.onFailure
    return if (!enabled || backdrop == null) this else drawBackdrop(
        backdrop = backdrop, shape = { shape },
        effects = {
            if (!failed.get()) try {
                colorControls(saturation = 1.2f)
                blur((if (compact) 8.dp else if (floating) 18.dp else 12.dp).toPx())
                lens((if (compact) 8.dp else if (floating) 16.dp else 12.dp).toPx(),
                    (if (compact) 14.dp else if (floating) 36.dp else 24.dp).toPx(), depthEffect = true)
            }
            catch (_: Exception) { renderEffect = null; padding = 0f; failed.set(true); onFailure() }
        },
        highlight = { Highlight(width = 1.dp, alpha = .85f,
            style = HighlightStyle.Default(intensity = .65f)) },
        shadow = if (floating) ({ Shadow(radius = 18.dp, color = Color.Black.copy(alpha = .18f)) }) else null,
        onDrawSurface = {
            val opacity = if (compact) { if (dark) .18f else .26f }
                else if (floating) { if (dark) .22f else .38f } else { if (dark) .34f else .50f }
            drawRect(tint.copy(alpha = if (failed.get()) 1f else opacity))
            if (!failed.get()) drawRect(Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (dark) .16f else .32f), Color.Transparent,
                    Color.White.copy(alpha = .035f)), start = Offset.Zero, end = Offset(size.width, size.height)))
        }
    )
}

@Composable internal fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlass.current.let { it.enabled && it.backdrop != null }
    val resolvedShape = shape ?: if (LocalVisualStyle.current == GalaxyVisualStyle.MIUIX) RoundedCornerShape(24.dp) else RoundedCornerShape(28.dp)
    Surface(modifier.galaxyGlass(resolvedShape), shape = resolvedShape,
        color = if (glass) Color.Transparent else Panel,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
            content = content)
    }
}

/** A separate sibling capture: cards can never record themselves into their own backdrop. */
@Composable internal fun GalaxyBackdrop(modifier: Modifier = Modifier) {
    val base = Navy
    val accent = Mint
    val dark = base.red < .3f
    val miuix = LocalVisualStyle.current == GalaxyVisualStyle.MIUIX
    val glass = LocalGlass.current.enabled
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        if (miuix && !glass) return@Canvas
        if (miuix) {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (dark) .20f else .13f), Color.Transparent),
                center = Offset(size.width * .15f, size.height * .14f), radius = size.width * .95f),
                radius = size.width * .95f, center = Offset(size.width * .15f, size.height * .14f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF90A6E8).copy(alpha = .13f), Color.Transparent),
                center = Offset(size.width * .92f, size.height * .72f), radius = size.width),
                radius = size.width, center = Offset(size.width * .92f, size.height * .72f))
            return@Canvas
        }
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (dark) .38f else .26f), Color.Transparent),
            center = Offset(size.width * .12f, size.height * .22f), radius = size.width * .85f),
            radius = size.width * .85f, center = Offset(size.width * .12f, size.height * .22f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF8C86DE).copy(alpha = if (dark) .32f else .26f), Color.Transparent),
            center = Offset(size.width * .98f, size.height * .72f), radius = size.width),
            radius = size.width, center = Offset(size.width * .98f, size.height * .72f))
        // Quiet, recognizable contours make refraction visible without competing with data.
        repeat(6) { index ->
            drawCircle(accent.copy(alpha = if (dark) .14f else .15f),
                radius = size.width * (.38f + index * .13f),
                center = Offset(size.width * 1.08f, size.height * .38f), style = Stroke(1.2.dp.toPx()))
        }
    }
}

@Composable internal fun GalaxyBoxTheme(dark: Boolean = isSystemInDarkTheme(),
    style: GalaxyVisualStyle = GalaxyVisualStyle.ORIGINAL, content: @Composable () -> Unit) {
    val colors = if (style == GalaxyVisualStyle.MIUIX && dark) darkColorScheme(
        primary = Color(0xFF8BB7FF), onPrimary = Color(0xFF132D51),
        background = Color(0xFF111216), onBackground = Color(0xFFF5F6FA),
        surface = Color(0xFF23252B), onSurface = Color(0xFFF5F6FA),
        onSurfaceVariant = Color(0xFFC1C5CE), secondary = Color(0xFFB2BEF5),
        surfaceContainer = Color(0xFF23252B)
    ) else if (style == GalaxyVisualStyle.MIUIX) lightColorScheme(
        primary = Color(0xFF1766C4), onPrimary = Color.White,
        background = Color(0xFFF4F5F7), onBackground = Color(0xFF1A1D23),
        surface = Color.White, onSurface = Color(0xFF1A1D23),
        onSurfaceVariant = Color(0xFF555D68), secondary = Color(0xFF4568B8),
        surfaceContainer = Color.White
    ) else if (dark) darkColorScheme(
        primary = Color(0xFF76E1C3), onPrimary = Color(0xFF073C31),
        background = Color(0xFF0C1424), onBackground = Color(0xFFEDF3FF),
        surface = Color(0xFF162237), onSurface = Color(0xFFEDF3FF),
        onSurfaceVariant = Color(0xFFB3C1D6), secondary = Color(0xFF92B8FF)
    ) else lightColorScheme(
        primary = Color(0xFF096B56), onPrimary = Color.White,
        background = Color(0xFFEDF3F6), onBackground = Color(0xFF142537),
        surface = Color(0xFFF8FBFD), onSurface = Color(0xFF142537),
        onSurfaceVariant = Color(0xFF45596C), secondary = Color(0xFF365D9B)
    )
    val shapes = if (style == GalaxyVisualStyle.MIUIX)
        Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp))
    else Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp))
    MaterialTheme(colorScheme = colors, shapes = shapes) {
        CompositionLocalProvider(LocalVisualStyle provides style, content = content)
    }
}
