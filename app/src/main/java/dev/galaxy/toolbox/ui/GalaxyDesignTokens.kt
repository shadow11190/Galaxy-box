package dev.galaxy.toolbox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Bridges existing page content to the selected design system without a Material wrapper. */
internal object GalaxyPalette {
    val primary: Color @Composable get() = if (isMiuixStyle()) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
    val secondary: Color @Composable get() = if (isMiuixStyle()) MiuixTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary
    val background: Color @Composable get() = if (isMiuixStyle()) MiuixTheme.colorScheme.background else MaterialTheme.colorScheme.background
    val onBackground: Color @Composable get() = if (isMiuixStyle()) MiuixTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground
    val surface: Color @Composable get() = if (isMiuixStyle()) MiuixTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    val onSurface: Color @Composable get() = if (isMiuixStyle()) MiuixTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant: Color @Composable get() {
        val base = if (background.luminance() < .35f) Color(0xFFC9D5E4) else Color(0xFF52627A)
        return ReadableGlassColor.accent(base, surface, background, onSurface)
    }
    val error: Color @Composable get() = if (isMiuixStyle()) MiuixTheme.colorScheme.error else MaterialTheme.colorScheme.error
    val telemetry: TelemetryPalette @Composable get() = TelemetryColors.forBackground(background)

    /** Dynamic and MIUIX secondary accents can be too pale or dark on glass. */
    @Composable fun readableAccent(accent: Color): Color = ReadableGlassColor.accent(
        accent, surface, background, onSurface,
    )
}

/** A few purposeful data colors; surfaces and the main brand color still follow the theme. */
internal data class TelemetryPalette(val battery: Color, val memory: Color, val storage: Color, val cpu: Color)

internal object TelemetryColors {
    val light = TelemetryPalette(
        battery = Color(0xFF087458), memory = Color(0xFF1459B8),
        storage = Color(0xFF8B5700), cpu = Color(0xFFA44931),
    )
    val dark = TelemetryPalette(
        battery = Color(0xFF70DEC0), memory = Color(0xFF88BBFF),
        storage = Color(0xFFF1C875), cpu = Color(0xFFFFB29B),
    )

    fun forBackground(background: Color) = if (background.luminance() < .35f) dark else light
}

internal object ReadableGlassColor {
    fun accent(accent: Color, surface: Color, background: Color, onSurface: Color): Color {
        val opaque = accent.copy(alpha = 1f)
        for (step in 0..10) {
            val candidate = lerp(opaque, onSurface, step / 10f)
            if (contrast(candidate, surface) >= 4.5 && contrast(candidate, background) >= 4.5) return candidate
        }
        return onSurface
    }

    private fun contrast(first: Color, second: Color): Double {
        val brighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return ((brighter + .05f) / (darker + .05f)).toDouble()
    }
}

internal object GalaxyTypography {
    val bodySmall: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.footnote1 else MaterialTheme.typography.bodySmall
    val bodyMedium: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.body2 else MaterialTheme.typography.bodyMedium
    val bodyLarge: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.body1 else MaterialTheme.typography.bodyLarge
    val labelSmall: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.footnote2 else MaterialTheme.typography.labelSmall
    val labelMedium: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.footnote1 else MaterialTheme.typography.labelMedium
    val labelLarge: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.button else MaterialTheme.typography.labelLarge
    val titleSmall: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.subtitle else MaterialTheme.typography.titleSmall
    val titleMedium: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.title3 else MaterialTheme.typography.titleMedium
    val titleLarge: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.title2 else MaterialTheme.typography.titleLarge
    val headlineSmall: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.title1 else MaterialTheme.typography.headlineSmall
    val headlineMedium: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.headline2 else MaterialTheme.typography.headlineMedium
    val headlineLarge: TextStyle @Composable get() = if (isMiuixStyle()) MiuixTheme.textStyles.headline1 else MaterialTheme.typography.headlineLarge
}

@Composable private fun isMiuixStyle() = LocalAppStyle.current == AppStyle.MIUIX
