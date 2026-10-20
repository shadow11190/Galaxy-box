package dev.galaxy.toolbox.ui

import org.junit.Assert.*
import org.junit.Test

class TemperatureTrendAxisTest {
    @Test fun flatReadingStillHasReadableCelsiusScale() {
        val axis = TemperatureTrendAxis.from(42.0, 42.0)
        assertTrue(axis.minimum < 42.0)
        assertTrue(axis.maximum > 42.0)
        assertEquals(axis.ticksDescending.size, axis.ticksDescending.map(axis::label).distinct().size)
    }

    @Test fun changingAndZeroDegreeReadingsRemainOnScale() {
        val axis = TemperatureTrendAxis.from(0.0, 45.0)
        assertTrue(axis.minimum <= 0.0)
        assertTrue(axis.maximum >= 45.0)
        assertTrue(axis.ticksDescending.size in 3..8)
    }
}
