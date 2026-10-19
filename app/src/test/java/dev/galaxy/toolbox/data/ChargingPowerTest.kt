package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.ChargingPowerReading
import org.junit.Assert.*
import org.junit.Test

class ChargingPowerTest {
    @Test fun microAmpsAndMilliVoltsProduceBatterySideWatts() {
        val reading = ChargingPowerReading(2_000_000, 4000, 50, true, atMs = 1000)
        assertEquals(8.0, reading.watts!!, .0001)
        assertEquals("+8.0 W", reading.powerText(1000))
    }
    @Test fun pluggedInNetDischargeIsNotTurnedIntoPositiveChargingPower() {
        assertEquals(-2.0, ChargingPowerReading(-500_000, 4000, connected = true).watts!!, .0001)
        assertEquals(0.0, ChargingPowerReading(0, 4000).watts!!, .0001)
    }
    @Test fun unsupportedCurrentMissingVoltageAndStaleDataRemainUnknown() {
        assertNull(ChargingPowerReading(Int.MIN_VALUE, 4000).watts)
        assertNull(ChargingPowerReading(1000, null).watts)
        assertNull(ChargingPowerReading(1000, 0).watts)
        assertEquals("無最新資料", ChargingPowerReading(2_000_000, 4000, atMs = 1000).powerText(16001))
    }
    @Test fun fullIntCurrentCannotOverflowDuringConversion() {
        assertEquals(10_737.418235, ChargingPowerReading(Int.MAX_VALUE, 5000).watts!!, .000001)
    }
}
