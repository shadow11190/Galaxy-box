package dev.galaxy.toolbox.ui

import dev.galaxy.toolbox.ToolboxState
import dev.galaxy.toolbox.model.Reading
import dev.galaxy.toolbox.model.Snapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceDiagnosticReportTest {
    private val build = ReportBuildFields(
        "Samsung", "samsung", "SM-S9480", "device", "product", "board",
        "Qualcomm", "SM8850", "16", 36,
    )

    @Test
    fun exportsSourcesAndTimesButOmitsPrivateNamesAndNetworkData() {
        val state = ToolboxState(
            hardware = listOf(
                Reading("裝置名稱", "Galaxy S26 Ultra", "完整型號對照"),
                Reading("處理器名稱", null, "Build.SOC_MODEL"),
                Reading("網路 SSID", "private-ssid", "WifiManager"),
                Reading("系統裝置名稱", "private-device-nickname", "Settings.Global device_name"),
            ),
            hardwareLoadedAtMs = 900L,
            snapshot = Snapshot(
                battery = listOf(Reading("電池溫度", "34.5 °C", "ACTION_BATTERY_CHANGED")),
                sampledAtMillis = 1000L,
            ),
        )
        val body = DeviceDiagnosticReport.render(state, build, "0.7.2", 1500L)
        val json = JSONObject(body)
        assertEquals("galaxy-box-device-diagnostic-v1", json.getString("schema"))
        assertEquals(900L, json.getLong("hardwareLoadedAtMs"))
        assertEquals("current", json.getJSONObject("monitor").getString("status"))
        val hardware = json.getJSONArray("hardware")
        assertEquals(2, hardware.length())
        assertEquals("完整型號對照", hardware.getJSONObject(0).getString("source"))
        assertEquals("unknown", hardware.getJSONObject(1).getString("status"))
        assertTrue(hardware.getJSONObject(1).isNull("value"))
        assertEquals(1000L, json.getJSONObject("monitor").getJSONArray("battery").getJSONObject(0).getLong("readAtMs"))
        assertFalse(body.contains("private-ssid"))
        assertFalse(body.contains("private-device-nickname"))
    }

    @Test
    fun marksOldSamplesStaleInsteadOfCallingThemCurrent() {
        val state = ToolboxState(snapshot = Snapshot(sampledAtMillis = 1000L))
        val json = JSONObject(DeviceDiagnosticReport.render(state, build, "0.7.2", 12_000L))
        assertEquals("stale", json.getJSONObject("monitor").getString("status"))
        assertTrue(json.getJSONObject("temperatureSummary").isNull("cpu"))
        assertTrue(json.getJSONObject("temperatureSummary").isNull("gpu"))
    }
}
