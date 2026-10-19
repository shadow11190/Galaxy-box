package dev.galaxy.toolbox.data

import org.junit.Assert.*
import org.junit.Test

class GpuMetricsTest {
    @Test fun hertzIsParsedWithoutGuessingFrequencyUnitsFromMagnitude() {
        assertEquals(800_000_000L, GpuMetrics.frequencyHz("800000000\n"))
        assertEquals(800L, GpuMetrics.frequencyHz("800"))
        assertEquals(0L, GpuMetrics.frequencyHz("0"))
        assertNull(GpuMetrics.frequencyHz("800 MHz"))
    }
    @Test fun busyFractionUsesTwoDriverTimesIncludingAValidIdleSample() {
        assertEquals(.25, GpuMetrics.busyFraction("250 1000")!!, .0001)
        assertEquals(0.0, GpuMetrics.busyFraction("0 1000")!!, .0001)
        assertEquals(1.0, GpuMetrics.busyFraction("1000 1000")!!, .0001)
    }
    @Test fun missingOrContradictoryDriverTimesAreNotShownAsZeroOrGuessedPercent() {
        listOf(null, "", "0 0", "500 200", "-1 200", "25", "25%", "2 4 8").forEach { assertNull(GpuMetrics.busyFraction(it)) }
    }
}
