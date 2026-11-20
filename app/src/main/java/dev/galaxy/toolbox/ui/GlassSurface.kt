package dev.galaxy.toolbox.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import java.util.concurrent.atomic.AtomicBoolean
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

internal val Mint: Color @Composable get() = GalaxyPalette.primary
internal val Navy: Color @Composable get() = GalaxyPalette.background
internal val Panel: Color @Composable get() = GalaxyPalette.surface
internal val Muted: Color @Composable get() = GalaxyPalette.onSurfaceVariant

internal data class GlassEnvironment(val enabled: Boolean = false, val backdrop: Backdrop? = null, val onFailure: () -> Unit = {})
internal val LocalGlass = staticCompositionLocalOf { GlassEnvironment() }
internal val LocalReadableGlass = staticCompositionLocalOf { false }
internal val LocalGlassTier = staticCompositionLocalOf { GlassTier.STATIC }
internal val LocalCardBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** All in-app cards and navigation use this one effect; content is drawn after the effect. */
@Composable internal fun Modifier.galaxyGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    backdrop: Backdrop? = LocalGlass.current.backdrop,
    enabled: Boolean = LocalGlass.current.enabled,
    floating: Boolean = false,
    compact: Boolean = false
): Modifier {
    val tint = Panel
    val dark = Navy.red < .3f
    val readable = LocalReadableGlass.current
    val tier = LocalGlassTier.current
    val tokens = LocalAppDesignTokens.current
    val failed = remember(backdrop) { AtomicBoolean(false) }
    val onFailure = LocalGlass.current.onFailure
    if (tier == GlassTier.OFF) return this
    if (!enabled || backdrop == null || tier == GlassTier.STATIC || tier == GlassTier.REDUCED) {
        val opacity = if (tier == GlassTier.REDUCED) .96f else if (dark) .72f else .74f
        return this.background(tint.copy(alpha = opacity), shape)
            .border(tokens.glassHighlightWidth, Color.White.copy(alpha = if (dark) .24f else .72f), shape)
    }
    return drawBackdrop(
        backdrop = backdrop, shape = { shape },
        effects = {
            if (!failed.get()) try {
                colorControls(saturation = 1.35f)
                vibrancy()
                blur((if (compact) 5.dp else if (floating) 8.dp else tokens.glassBlur).toPx())
                if (shape is CornerBasedShape && tier == GlassTier.REFRACTIVE) lens(
                    (if (compact) 4.dp else if (floating) 8.dp else tokens.glassLensHeight).toPx(),
                    (if (compact) 10.dp else if (floating) 16.dp else tokens.glassLensAmount).toPx(),
                )
            }
            catch (_: Exception) { renderEffect = null; padding = 0f; failed.set(true); onFailure() }
        },
        highlight = { Highlight(width = tokens.glassHighlightWidth, alpha = if (dark) .75f else .9f,
            style = HighlightStyle.Default) },
        shadow = if (floating) ({ Shadow(radius = tokens.glassShadowRadius, color = Color.Black.copy(alpha = if (dark) .32f else .16f)) }) else null,
        innerShadow = { InnerShadow(radius = 6.dp, alpha = if (dark) .14f else .10f) },
        onDrawSurface = {
            val opacity = if (readable) { if (dark) .68f else .72f }
                else if (floating) { if (dark) .58f else .52f }
                else if (compact) { if (dark) .20f else .24f }
                else { if (dark) .52f else .50f }
            drawRect(tint.copy(alpha = if (failed.get()) 1f else opacity))
            if (!failed.get()) {
                // Specular sheen from the top-left, then a faint cool rim on the opposite corner.
                drawRect(Brush.linearGradient(
                    listOf(Color.White.copy(alpha = if (dark) .20f else .38f), Color.White.copy(alpha = .04f),
                        Color.Transparent, Color(0xFF9BD7FF).copy(alpha = if (dark) .10f else .08f)),
                    start = Offset.Zero, end = Offset(size.width, size.height)))
                // Thin bright band along the top edge, like light catching a glass lip.
                drawRect(Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (dark) .14f else .26f), Color.Transparent),
                    endY = size.height * .16f))
            }
        }
    )
}

/** Cards sample only the background layer, so their own text never enters the capture. */
@Composable internal fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalAppDesignTokens.current
    val backdrop = LocalCardBackdrop.current
    val glass = LocalGlassTier.current != GlassTier.OFF
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixGroupedCard(modifier = modifier, cornerRadius = tokens.cardRadius,
            insidePadding = PaddingValues(0.dp), verticalSpacing = 0.dp, onClick = onClick, content = content)
    } else {
        val resolvedShape = shape ?: GalaxySquircleShape(tokens.cardRadius)
        Surface(modifier.then(if (glass) Modifier.galaxyGlass(resolvedShape, backdrop = backdrop, enabled = false) else Modifier),
            shape = resolvedShape, color = if (glass) Color.Transparent else Panel,
            contentColor = GalaxyPalette.onSurface) {
            Column(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
                content = content)
        }
    }
}

@Composable internal fun GlassCard(
    modifier: Modifier = Modifier, shape: Shape? = null, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = AppCard(modifier, shape, onClick, content)

/** A separate sibling capture: cards can never record themselves into their own backdrop. */
@Composable internal fun GalaxyBackdrop(modifier: Modifier = Modifier) {
    val base = Navy
    val accent = Mint
    val dark = base.red < .3f
    val miuix = LocalAppStyle.current == AppStyle.MIUIX
    val glass = LocalGlass.current.enabled
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        if (!glass) return@Canvas
        // Both styles need recognizable content behind the Backdrop lens. MIUIX keeps
        // its palette and grouped cards while sharing the original optical backdrop.
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (dark) .38f else .26f), Color.Transparent),
            center = Offset(size.width * .12f, size.height * .22f), radius = size.width * .85f),
            radius = size.width * .85f, center = Offset(size.width * .12f, size.height * .22f))
        val ambient = if (dark) Color(0xFF77A9C2) else Color(0xFFD9AA75)
        drawCircle(Brush.radialGradient(listOf(ambient.copy(alpha = if (dark) .22f else .20f), Color.Transparent),
            center = Offset(size.width * .98f, size.height * .72f), radius = size.width),
            radius = size.width, center = Offset(size.width * .98f, size.height * .72f))
        // Extra colour pools give the lens distinct hues to bend at card edges.
        val violet = if (dark) Color(0xFF8E7CFF) else Color(0xFFB9A8FF)
        drawCircle(Brush.radialGradient(listOf(violet.copy(alpha = if (dark) .26f else .22f), Color.Transparent),
            center = Offset(size.width * .85f, size.height * .06f), radius = size.width * .7f),
            radius = size.width * .7f, center = Offset(size.width * .85f, size.height * .06f))
        val coral = if (dark) Color(0xFFFF8FB0) else Color(0xFFFFB3C7)
        drawCircle(Brush.radialGradient(listOf(coral.copy(alpha = if (dark) .16f else .18f), Color.Transparent),
            center = Offset(size.width * .1f, size.height * .92f), radius = size.width * .65f),
            radius = size.width * .65f, center = Offset(size.width * .1f, size.height * .92f))
        // Quiet, recognizable contours make refraction visible without competing with data.
        repeat(4) { index ->
            drawCircle(accent.copy(alpha = if (dark) .12f else .10f),
                radius = size.width * (.45f + index * .19f),
                center = Offset(size.width * 1.08f, size.height * .38f), style = Stroke(1.2.dp.toPx()))
        }
    }
}

@Composable internal fun GalaxyBoxTheme(dark: Boolean = isSystemInDarkTheme(),
    style: AppStyle = AppStyle.LIQUID_GLASS,
    themeMode: String = "system", content: @Composable () -> Unit) {
    if (style == AppStyle.MIUIX) {
        val lightColors = remember { miuixLightColorScheme(
            background = Color(0xFFF3F4F7), surface = Color(0xFFF3F4F7),
            surfaceContainer = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF3F4F7),
        ) }
        val darkColors = remember { miuixDarkColorScheme(
            background = Color(0xFF18191D), surface = Color(0xFF18191D),
            surfaceContainer = Color(0xFF292A30), surfaceVariant = Color(0xFF202126),
        ) }
        val mode = when (themeMode) {
            "light" -> ColorSchemeMode.Light
            "dark" -> ColorSchemeMode.Dark
            "monet" -> if (Build.VERSION.SDK_INT >= 31) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
            else -> ColorSchemeMode.System
        }
        val controller = remember(mode, dark) { ThemeController(
            colorSchemeMode = mode, lightColors = lightColors, darkColors = darkColors,
            isDark = dark,
        ) }
        MiuixTheme(controller = controller) {
            CompositionLocalProvider(LocalAppStyle provides style,
                LocalAppDesignTokens provides appDesignTokens(style),
                LocalContentColor provides MiuixTheme.colorScheme.onBackground, content = content)
        }
        return
    }
    val context = LocalContext.current
    val colors = if (themeMode == "monet" && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(
        primary = Color(0xFF80C8FF), onPrimary = Color(0xFF092D4A),
        background = Color(0xFF111820), onBackground = Color(0xFFF1F5FA),
        surface = Color(0xFF202B35), onSurface = Color(0xFFF1F5FA),
        onSurfaceVariant = Color(0xFFC0CEDA), secondary = Color(0xFF86DECB)
    ) else lightColorScheme(
        primary = Color(0xFF0067C8), onPrimary = Color.White,
        background = Color(0xFFF2F5F8), onBackground = Color(0xFF1B2936),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B2936),
        onSurfaceVariant = Color(0xFF4C6072), secondary = Color(0xFF0C7767)
    )
    val shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp))
    val typography = Typography(
        headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    )
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography) {
        CompositionLocalProvider(LocalAppStyle provides style,
            LocalAppDesignTokens provides appDesignTokens(style),
            LocalContentColor provides colors.onBackground, content = content)
    }
}
