package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.diagnostics.*
import dev.galaxy.toolbox.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadOnlyDiagnosticsTest {
    private fun payload(kind: ProbeKind = ProbeKind.BATTERY, values: Map<String, String> = emptyMap(), dump: String = ""): JSONObject {
        val items = JSONArray()
        values.forEach { (path, raw) -> items.put(JSONObject().put("path", path).put("raw", raw).put("status", "OK")) }
        return JSONObject().put("schema", 1).put("kind", kind.name).put("uid", 2000).put("at", 1000).put("files", items)
            .put("dump", JSONObject().put("status", "OK").put("text", dump))
    }
    private val battery = "/sys/class/power_supply/battery"
    @Test fun batteryDataUsesContractUnitsAndDoesNotInferHealthOrCycles() {
        val data = payload(values = mapOf("$battery/type" to "Battery", "$battery/charge_full_design" to "5000000",
            "$battery/charge_full" to "4850000", "$battery/energy_full_design" to "19000000",
            "$battery/batt_asoc" to "97", "$battery/battery_cycle" to "12345", "$battery/cycle_count" to "0"))
        val result = PrivilegedSnapshotParser.parse(data.toString(), ProbeKind.BATTERY)
        assertEquals(6, result.fields.size)
        assertEquals("5000.0 mAh", result.fields.first { it.label.startsWith("設計電荷") }.value)
        assertEquals("4850.0 mAh", result.fields.first { it.label.startsWith("驅動回報滿充容量") }.value)
        assertEquals("19000.0 mWh", result.fields.first { it.label.startsWith("設計能量") }.value)
        assertEquals("12345", result.fields.first { it.label.startsWith("Samsung 循環欄位") }.value)
        assertTrue(result.fields.first { it.label.startsWith("Samsung 循環欄位") }.detail.contains("不除以 100"))
        assertEquals("0 次（來源回報）", result.fields.first { it.label.startsWith("循環次數") }.value)
        assertFalse(result.fields.any { it.label.contains("SOH") })
    }
    @Test fun wrongPowerSupplyTypeCannotBecomeBatteryCapacity() {
        for (type in listOf("USB", "Unknown", "")) {
            val data = payload(values = mapOf("$battery/type" to type, "$battery/charge_full_design" to "5000000"))
            assertTrue(PrivilegedSnapshotParser.parse(data.toString(), ProbeKind.BATTERY).fields.isEmpty())
        }
    }
    @Test fun missingTypeAndPermissionDeniedStayOutOfUsableRows() {
        val data = payload(values = mapOf("$battery/charge_full" to "1234"))
        val item = data.getJSONArray("files").getJSONObject(0)
        item.put("status", "PERMISSION_DENIED").put("raw", JSONObject.NULL)
        val result = PrivilegedSnapshotParser.parse(data.toString(), ProbeKind.BATTERY)
        assertTrue(result.fields.isEmpty()); assertTrue(result.diagnostics.any { it.contains("權限") })
    }
    @Test fun dumpAndSysfsValuesRemainSeparateInsteadOfAveragedOrOverwritten() {
        val data = payload(values = mapOf("$battery/type" to "Battery", "$battery/cycle_count" to "12"),
            dump = "Current Battery Service state:\ncycle count: 0\nmSavedBatteryAsoc: [96]\nmSavedBatteryUsage: [12345]")
        val result = PrivilegedSnapshotParser.parse(data.toString(), ProbeKind.BATTERY)
        val cycles = result.fields.filter { it.label.startsWith("循環次數") }
        assertEquals(2, cycles.size)
        assertTrue(cycles.any { it.value == "12 次（來源回報）" }); assertTrue(cycles.any { it.value == "0" })
        assertTrue(result.fields.any { it.label == "Samsung ASOC · 系統報告" && it.value == "96" })
    }
    @Test fun conflictingReportValuesDoNotGetSelectedButOtherSourcesSurvive() {
        val data = payload(values = mapOf("$battery/type" to "Battery", "$battery/charge_full_design" to "5000000"),
            dump = "Current Battery Service state:\ncycle count: [4, 5]")
        val result = PrivilegedSnapshotParser.parse(data.toString(), ProbeKind.BATTERY)
        assertEquals(1, result.fields.size)
        assertTrue(result.diagnostics.any { it.contains("衝突") })
    }
    @Test fun malformedOrOversizedMetadataIsRejected() {
        val invalid = listOf(payload().put("schema", 2), payload().put("uid", 0), payload().put("kind", "GPU"), payload().put("at", 0),
            payload(values = mapOf("/data/private" to "secret")))
        invalid.forEach { data -> assertThrows(IllegalArgumentException::class.java) { PrivilegedSnapshotParser.parse(data.toString(), ProbeKind.BATTERY) } }
        assertThrows(IllegalArgumentException::class.java) { PrivilegedSnapshotParser.parse("x".repeat(180001), ProbeKind.BATTERY) }
    }
    @Test fun duplicateSourcesAreRejected() {
        val data = payload(values = mapOf("$battery/type" to "Battery"))
        val files = data.getJSONArray("files"); files.put(files.getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { PrivilegedSnapshotParser.parse(data.toString(), ProbeKind.BATTERY) }
    }
    @Test fun gpuZeroFrequencyAndBusyUseDifferentUnits() {
        val result = PrivilegedSnapshotParser.parse(payload(ProbeKind.GPU, mapOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" to "0",
            "/sys/class/kgsl/kgsl-3d0/gpubusy" to "25 100")).toString(), ProbeKind.GPU)
        assertTrue(result.fields.any { it.value == "0 MHz" }); assertTrue(result.fields.any { it.value == "25.0%" })
        assertTrue(result.fields.all { it.source.contains("Shizuku") })
    }
    @Test fun zeroUndefinedCapacityInvalidAsocAndNegativeValuesAreNotInvented() {
        assertNull(PrivilegedSnapshotParser.format(ProbeValue.CHARGE_UAH, "0"))
        assertNull(PrivilegedSnapshotParser.format(ProbeValue.SAMSUNG_ASOC, "101"))
        assertEquals("0", PrivilegedSnapshotParser.format(ProbeValue.SAMSUNG_ASOC, "0"))
        assertNull(PrivilegedSnapshotParser.format(ProbeValue.GPU_BUSY, "1 0"))
        assertNull(PrivilegedSnapshotParser.format(ProbeValue.CYCLES, "-1"))
        assertNull(PrivilegedSnapshotParser.format(ProbeValue.CHARGE_UAH, "999999999999999999999999"))
    }
    @Test fun supportedOperationsCannotSetBatteryOrRunCallerProvidedCommands() {
        assertEquals(listOf("/system/bin/dumpsys", "-t", "5", "battery"), ProbeContracts.batteryCommand)
        assertTrue(ProbeContracts.batteryFiles.all { it.path.startsWith("/sys/class/power_supply/") })
        assertTrue(ProbeContracts.gpuFiles.all { it.path.startsWith("/sys/class/kgsl/kgsl-3d0/") })
        assertFalse(ProbeContracts.batteryFiles.any { it.path.endsWith("batt_full_capacity") })
    }
    @Test fun unavailableRowsDisappearButZeroIsStillVisible() {
        val readings = listOf(Reading("SOH", null, "API"), Reading("empty", " ", "API"), BatteryPresentation.cycleCount(0), BatteryPresentation.cycleCount(null))
        val visible = BatteryPresentation.visible(readings)
        assertEquals(1, visible.size); assertEquals("系統回報 0", visible.single().value)
        assertNull(BatteryPresentation.cycleCount(-1).value)
    }
    @Test fun firmwareLinkUsesOnlyValidatedLocalModelWithoutCscOrInput() {
        assertEquals("SM-S9480", FirmwareLinks.deviceModel("Samsung", "sm-s9480/DS"))
        assertEquals("https://samfw.com/firmware/SM-S9480", FirmwareLinks.catalogue("SM-S9480"))
        assertNull(FirmwareLinks.deviceModel("other", "SM-S9480"))
        assertNull(FirmwareLinks.deviceModel("Samsung", "SM-S9480/../../evil"))
        assertThrows(IllegalArgumentException::class.java) { FirmwareLinks.catalogue("SM-S9480?x=1") }
    }
    @Test fun digitalAudioKeepsChannelsIndependentAndDoesNotTreatSilenceAsMissing() {
        val left = AudioSignal.tone(0)
        assertTrue(left.indices.filter { it % 2 == 1 }.all { left[it].toInt() == 0 })
        assertTrue(left.indices.filter { it % 2 == 0 }.any { left[it].toInt() != 0 })
        val right = AudioSignal.tone(1)
        assertTrue(right.indices.filter { it % 2 == 0 }.all { right[it].toInt() == 0 })
        assertEquals(Double.NEGATIVE_INFINITY, AudioSignal.dbfs(ShortArray(20), 20)!!, 0.0)
        assertNull(AudioSignal.dbfs(ShortArray(2), 0))
        assertTrue(AudioSignal.dbfs(left, left.size)!! < 0)
    }
    @Test fun capabilitiesHideUnavailableTestsAndDoNotSplitBiometricModalitiesOrWirelessClaims() {
        val minimum = TestCatalogue.available(TestCapabilities())
        assertEquals(setOf(DeviceTest.SYSTEM, DeviceTest.BATTERY, DeviceTest.DISPLAY, DeviceTest.CHARGING), minimum.toSet())
        val full = TestCatalogue.available(TestCapabilities(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true))
        assertEquals(21, full.size)
        assertEquals(1, full.count { it == DeviceTest.BIOMETRIC })
        assertTrue(full.contains(DeviceTest.STYLUS)); assertFalse(minimum.contains(DeviceTest.STYLUS))
    }
    @Test fun zeroTrafficIsDifferentFromUnsupported() {
        assertNull(ConnectionDetails.traffic(-1)); assertNotNull(ConnectionDetails.traffic(0))
    }
}
