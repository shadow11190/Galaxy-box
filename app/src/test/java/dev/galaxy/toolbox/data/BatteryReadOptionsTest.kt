package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.diagnostics.*
import dev.galaxy.toolbox.model.ChargingPowerReading
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryReadOptionsTest {
    @Test fun automaticChoicesRespectEachManualOverride() {
        val energy = 16_000_000_000L
        for (invert in listOf<Boolean?>(null, false, true))
            for (estimate in listOf<Boolean?>(null, false, true))
                for (dual in listOf<Boolean?>(null, false, true)) {
                    val auto = BatteryReadOptions.resolve(invert, estimate, dual, null, energy, 4_000_000)
                    assertEquals(invert ?: false, auto.invertCurrent)
                    assertEquals(estimate ?: true, auto.estimateVoltage)
                    assertEquals(dual ?: false, auto.dualCell)
                    val pack = BatteryReadOptions.resolve(invert, estimate, dual, 8000, energy, 4_000_000)
                    assertEquals(estimate ?: false, pack.estimateVoltage)
                    assertEquals(dual ?: true, pack.dualCell)
                }
        val missing = BatteryReadOptions.resolve(null, null, null, null, null, null)
        assertFalse(missing.estimateVoltage)
        assertFalse(missing.dualCell)
    }

    @Test fun allEightSwitchCombinationsKeepIndependentMeanings() {
        for (invert in listOf(false, true)) for (estimate in listOf(false, true)) for (dual in listOf(false, true)) {
            val options = BatteryReadOptions(invert, estimate, dual)
            val voltage = BatteryElectricalInterpretation.usableMilliVolts(null, options.estimateVoltage,
                16_000_000_000L, 4_000_000)
            assertEquals(estimate, voltage.second)
            assertEquals(if (estimate) 4000 else null, voltage.first)
            val reading = ChargingPowerReading(2_000_000, voltage.first, invertCurrent = invert,
                voltageEstimated = voltage.second, dualCell = dual)
            assertEquals(if (invert) -2_000_000 else 2_000_000, reading.adjustedCurrentMicroAmps)
            assertEquals(if (estimate) (if (invert) -8.0 else 8.0) else null, reading.watts)
            // A 4 V battery reading does not prove series cells, even when the option is on.
            assertNull(reading.possiblePerCellMilliVolts)
        }
    }

    @Test fun directVoltageWinsAndDualCellNeverDoublesPowerOrCapacity() {
        for (invert in listOf(false, true)) for (estimate in listOf(false, true)) for (dual in listOf(false, true)) {
            val voltage = BatteryElectricalInterpretation.usableMilliVolts(8000, estimate,
                16_000_000_000L, 4_000_000)
            assertEquals(8000, voltage.first)
            assertFalse(voltage.second)
            val reading = ChargingPowerReading(1_000_000, voltage.first, invertCurrent = invert, dualCell = dual)
            assertEquals(if (invert) -8.0 else 8.0, reading.watts!!, .0001)
            assertEquals(if (dual) 4000 else null, reading.possiblePerCellMilliVolts)
        }
    }

    @Test fun invalidOrMissingSourcesNeverBecomeEstimatedVoltageOrZeroWatts() {
        for (reported in listOf<Int?>(null, 0, 100, 11_000)) {
            for (charge in listOf<Int?>(null, 0, Int.MIN_VALUE, 50)) {
                val value = BatteryElectricalInterpretation.usableMilliVolts(reported, true, null, charge)
                assertNull(value.first)
                assertFalse(value.second)
            }
        }
        assertNull(BatteryElectricalInterpretation.estimatedMilliVolts(16_000_000_000L, null))
        assertNull(BatteryElectricalInterpretation.estimatedMilliVolts(1_000_000L, 4_000_000))
        assertNull(ChargingPowerReading(2_000_000, null, invertCurrent = true, dualCell = true).watts)
        assertNull(ChargingPowerReading(Int.MIN_VALUE, 8000, invertCurrent = true).adjustedCurrentMicroAmps)
        assertNull(ChargingPowerReading(Int.MIN_VALUE, 8000, invertCurrent = true).watts)
    }

    @Test fun vendorsAndSuspiciousChargeCounterAreSeparated() {
        assertEquals(BatteryVendor.SAMSUNG, BatteryVendor.of("samsung"))
        for (name in listOf("Xiaomi", "Redmi", "POCO")) assertEquals(BatteryVendor.XIAOMI, BatteryVendor.of(name))
        for (name in listOf("OPPO", "oplus")) assertEquals(BatteryVendor.OPPO, BatteryVendor.of(name))
        assertEquals(BatteryVendor.OTHER, BatteryVendor.of("vivo"))
        assertNull(BatteryVendorPolicy.remainingChargeMicroAh(50, 50, BatteryVendor.OPPO))
        assertNull(BatteryVendorPolicy.remainingChargeMicroAh(50, null, BatteryVendor.OPPO))
        assertEquals(3_000_000, BatteryVendorPolicy.remainingChargeMicroAh(3_000_000, 50, BatteryVendor.OPPO))
        assertNull(BatteryVendorPolicy.remainingChargeMicroAh(50, 50, BatteryVendor.SAMSUNG))
        assertNull(BatteryVendorPolicy.remainingChargeMicroAh(5_000, 50, BatteryVendor.XIAOMI))
        assertEquals(50, BatteryVendorPolicy.remainingChargeMicroAh(50, 0, BatteryVendor.SAMSUNG))
        assertNull(BatteryVendorPolicy.remainingChargeMicroAh(Int.MIN_VALUE, 50, BatteryVendor.XIAOMI))
    }

    @Test fun privilegedBatterySourcesAreScopedToDetectedVendor() {
        val samsung = ProbeContracts.batteryFilesFor(BatteryVendor.SAMSUNG)
        val xiaomi = ProbeContracts.batteryFilesFor(BatteryVendor.XIAOMI)
        val oppo = ProbeContracts.batteryFilesFor(BatteryVendor.OPPO)
        assertTrue(samsung.any { it.value == ProbeValue.SAMSUNG_ASOC })
        assertFalse(xiaomi.any { it.value == ProbeValue.SAMSUNG_ASOC })
        assertTrue(xiaomi.any { it.value == ProbeValue.XIAOMI_RAW })
        assertFalse(oppo.any { it.value in setOf(ProbeValue.SAMSUNG_ASOC, ProbeValue.XIAOMI_RAW) })
        assertTrue(oppo.any { it.value == ProbeValue.STATE_OF_HEALTH })
    }

    @Test fun vendorSpecificRawCyclesRemainRawAndSmallCapacityIsRejected() {
        fun payload(manufacturer: String, values: Map<String, String>): String {
            val files = JSONArray()
            values.forEach { (path, raw) -> files.put(JSONObject().put("path", path).put("raw", raw).put("status", "OK")) }
            return JSONObject().put("schema", 1).put("kind", "BATTERY").put("uid", 2000).put("at", 1000)
                .put("manufacturer", manufacturer).put("files", files)
                .put("dump", JSONObject().put("status", "UNAVAILABLE")).toString()
        }
        val base = "/sys/class/power_supply/battery"
        val xiaomi = PrivilegedSnapshotParser.parse(payload("Xiaomi", mapOf(
            "$base/type" to "Battery", "$base/battery_cycle" to "12345",
            "$base/charge_full_design" to "5000")), ProbeKind.BATTERY)
        assertTrue(xiaomi.fields.any { it.label.startsWith("小米系循環") && it.value == "12345" })
        assertFalse(xiaomi.fields.any { it.value == "5.0 mAh" })
        assertTrue(xiaomi.diagnostics.any { it.contains("charge_full_design") })
        val oppo = PrivilegedSnapshotParser.parse(payload("OPPO", mapOf(
            "$base/type" to "Battery", "$base/state_of_health" to "95",
            "$base/cycle_count" to "0")), ProbeKind.BATTERY)
        assertTrue(oppo.fields.any { it.label.startsWith("驅動回報健康狀態") && it.value == "95%（驅動回報）" })
        assertTrue(oppo.fields.any { it.label.startsWith("循環次數") && it.value == "未提供（原始 0）" })
        assertThrows(IllegalArgumentException::class.java) {
            PrivilegedSnapshotParser.parse(payload("OPPO", mapOf(
                "$base/type" to "Battery", "$base/battery_cycle" to "12345")), ProbeKind.BATTERY)
        }
    }
}
