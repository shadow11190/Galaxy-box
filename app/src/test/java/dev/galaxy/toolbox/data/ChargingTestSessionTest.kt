package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.ChargingPowerReading
import dev.galaxy.toolbox.model.ChargingTestResult
import dev.galaxy.toolbox.model.ChargingTestState
import dev.galaxy.toolbox.model.addReading
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargingTestSessionTest {
    @Test fun netEnergyPreservesSignAndSkipsMissingReadingsAndLongGaps() {
        val initial = ChargingTestState(active = true, result = ChargingTestResult(startedAt = 1))
        val charging = ChargingPowerReading(currentMicroAmps = -2_000_000, voltageMilliVolts = 4000,
            invertCurrent = true, voltageEstimated = true)
        val discharging = charging.copy(currentMicroAmps = 1_000_000)
        val result = requireNotNull(initial.addReading(charging, 0)
            .addReading(discharging, 2_000)
            .addReading(null, 4_000)
            .addReading(charging, 6_000)
            .addReading(charging, 14_000).result)
        assertEquals(4, result.samples)
        assertEquals(2_000L, result.measuredMs)
        assertEquals(8.0, requireNotNull(result.peak), 0.001)
        assertEquals(2.0, requireNotNull(result.average), 0.001)
        assertEquals(4_000.0 / 3_600_000, requireNotNull(result.energyWh), 0.000001)
        assertEquals(true, result.estimatedVoltage)
    }
}
