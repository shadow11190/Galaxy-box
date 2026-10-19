package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.widgets.*
import dev.galaxy.toolbox.model.Snapshot
import org.junit.Assert.*
import org.junit.Test

class WidgetSettingsTest {
    @Test fun transparentRemovesEveryDecorativeLayerRegardlessOfStoredOpacity() {
        listOf(0, 40, 100).forEach { opacity ->
            val s = WidgetSettings(background = WidgetBackground.TRANSPARENT, opacity = opacity)
            assertFalse(s.drawsBackground); assertFalse(s.drawsDecoration)
        }
    }
    @Test fun solidHasNoGlassDecorationAndZeroOpacityIsTrulyEmpty() {
        assertFalse(WidgetSettings(background = WidgetBackground.SOLID).drawsDecoration)
        assertFalse(WidgetSettings(opacity = 0).drawsBackground)
        assertFalse(WidgetSettings(opacity = 0).drawsDecoration)
    }
    @Test fun settingsRoundTripAllChoicesAndEmptyFieldSelection() {
        val s = WidgetSettings(WidgetBackground.TRANSPARENT, 35, 0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt(), false, false, emptySet(), WidgetClick.NONE)
        assertEquals(s, WidgetSettings.decode(s.encode(), WidgetKind.OVERVIEW))
    }
    @Test fun malformedSettingsFallbackAndIgnoreFutureUnknownFields() {
        val s = WidgetSettings.decode(mapOf("background" to "future", "opacity" to "190", "clock" to "maybe", "fields" to "MEMORY,new", "click" to "new"), WidgetKind.BATTERY)
        assertEquals(WidgetBackground.GLASS, s.background)
        assertEquals(100, s.opacity)
        assertFalse(s.showClock)
        assertEquals(setOf(WidgetField.MEMORY), s.fields)
    }
    @Test fun fullAndCompactHaveAppropriateDefaults() {
        val full = WidgetSettings.defaults(WidgetKind.OVERVIEW)
        assertTrue(full.showClock); assertTrue(full.showModel)
        assertTrue(full.fields.containsAll(listOf(WidgetField.MEMORY, WidgetField.STORAGE, WidgetField.BATTERY, WidgetField.TEMPERATURE)))
        assertEquals(setOf(WidgetField.BATTERY, WidgetField.TEMPERATURE), WidgetSettings.defaults(WidgetKind.BATTERY).fields)
    }
    @Test fun largeTextAndNarrowWidthsSwitchToSingleColumnWithoutDroppingFields() {
        val s = WidgetSettings()
        assertEquals(2, WidgetLayoutPlan.forSize(320, 280, 1f, s).columns)
        assertEquals(1, WidgetLayoutPlan.forSize(200, 380, 1f, s).columns)
        assertEquals(1, WidgetLayoutPlan.forSize(320, 380, 1.6f, s).columns)
        assertTrue(WidgetLayoutPlan.forSize(200, 200, 1.6f, s).dense)
    }
    @Test fun trafficMissingAndZeroAreDifferentAndPeriodIsExplicit() {
        assertEquals("—", WidgetPresentation.from(Snapshot(), trafficBytes = -1).traffic)
        assertEquals("0.0 GiB", WidgetPresentation.from(Snapshot(), trafficBytes = 0).traffic)
        assertTrue(WidgetField.TRAFFIC.title.contains("自開機累計"))
        assertEquals("—", WidgetPresentation.from(Snapshot()).metric(WidgetField.STORAGE).value)
    }
    @Test fun colorInputRequiresSixHexDigits() {
        assertEquals(0xFF123ABC.toInt(), WidgetSettings.parseColor("#123abc"))
        assertNull(WidgetSettings.parseColor("#12")); assertNull(WidgetSettings.parseColor("GGGGGG"))
    }
}
