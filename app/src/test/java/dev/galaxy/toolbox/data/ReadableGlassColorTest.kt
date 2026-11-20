package dev.galaxy.toolbox.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.galaxy.toolbox.ui.ReadableGlassColor
import dev.galaxy.toolbox.ui.TelemetryColors
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableGlassColorTest {
    @Test fun telemetryColorsStayDistinctAndReadableInBothThemes() {
        for ((palette, background, surface) in listOf(
            Triple(TelemetryColors.light, Color(0xFFF3F4F7), Color.White),
            Triple(TelemetryColors.dark, Color(0xFF18191D), Color(0xFF292A30)),
        )) {
            val accents = listOf(palette.battery, palette.memory, palette.storage, palette.cpu)
            assertTrue(accents.distinct().size == accents.size)
            accents.forEach { accent ->
                assertTrue(contrast(accent, background) >= 4.5)
                assertTrue(contrast(accent, surface) >= 4.5)
            }
        }
    }

    @Test fun paleAccentBecomesReadableOnLightGlass() {
        val result = ReadableGlassColor.accent(
            Color(0xFFE8EAFF), Color.White, Color(0xFFF3F4F7), Color(0xFF171921),
        )
        assertTrue(contrast(result, Color.White) >= 4.5)
        assertTrue(contrast(result, Color(0xFFF3F4F7)) >= 4.5)
    }

    @Test fun darkAccentBecomesReadableOnDarkGlass() {
        val result = ReadableGlassColor.accent(
            Color(0xFF30333D), Color(0xFF292A30), Color(0xFF18191D), Color(0xFFF5F6FA),
        )
        assertTrue(contrast(result, Color(0xFF292A30)) >= 4.5)
        assertTrue(contrast(result, Color(0xFF18191D)) >= 4.5)
    }

    private fun contrast(first: Color, second: Color): Double {
        val higher = maxOf(first.luminance(), second.luminance())
        val lower = minOf(first.luminance(), second.luminance())
        return ((higher + .05f) / (lower + .05f)).toDouble()
    }
}
