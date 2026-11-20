package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.HardwareCategory
import dev.galaxy.toolbox.ui.ToolboxNavigationHistory
import dev.galaxy.toolbox.ui.ToolboxRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolboxNavigationHistoryTest {
    private val overview = ToolboxRoute(0, HardwareCategory.CPU)

    @Test fun batteryBackReturnsToOverview() {
        val battery = ToolboxRoute(2, HardwareCategory.CPU)
        val history = ToolboxNavigationHistory.visit(emptyList(), overview, battery)

        val (previous, remaining) = ToolboxNavigationHistory.previous(history, battery)

        assertEquals(overview, previous)
        assertTrue(remaining.isEmpty())
    }

    @Test fun hardwareCategoriesBackOutOneLevelAtATime() {
        val display = ToolboxRoute(1, HardwareCategory.DISPLAY)
        val audio = ToolboxRoute(1, HardwareCategory.AUDIO)
        val first = ToolboxNavigationHistory.visit(emptyList(), overview, display)
        val second = ToolboxNavigationHistory.visit(first, display, audio)

        val (previousCategory, remaining) = ToolboxNavigationHistory.previous(second, audio)
        val (previousPage, rootHistory) = ToolboxNavigationHistory.previous(remaining, previousCategory)

        assertEquals(display, previousCategory)
        assertEquals(overview, previousPage)
        assertTrue(rootHistory.isEmpty())
    }

    @Test fun selectingCurrentRouteDoesNotDuplicateHistoryAndOverviewResetsIt() {
        val battery = ToolboxRoute(2, HardwareCategory.CPU)
        val history = ToolboxNavigationHistory.visit(emptyList(), overview, battery)

        assertEquals(history, ToolboxNavigationHistory.visit(history, battery, battery))
        assertTrue(ToolboxNavigationHistory.visit(history, battery, overview).isEmpty())
    }

    @Test fun unusableSavedHistoryReturnsToOverview() {
        val battery = ToolboxRoute(2, HardwareCategory.CPU)

        val (previous, remaining) = ToolboxNavigationHistory.previous(listOf("bad-route"), battery)

        assertEquals(overview, previous)
        assertTrue(remaining.isEmpty())
    }
}
