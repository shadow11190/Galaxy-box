package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import dev.galaxy.toolbox.widgets.WidgetPresentation
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DashboardMetricsTest {
    @Test fun resourceBoundsDistinguishEmptyFromUnavailable() {
        assertEquals(0, ByteUsage.of(0, 100)!!.percent)
        assertEquals(100, ByteUsage.of(100, 100)!!.percent)
        assertNull(ByteUsage.of(1, 0))
        assertNull(ByteUsage.of(-1, 100))
        assertNull(ByteUsage.of(101, 100))
    }
    @Test fun resourcePercentDoesNotOverflowLongCounters() {
        val usage = ByteUsage.of(Long.MAX_VALUE / 2, Long.MAX_VALUE)!!
        assertEquals(50, usage.percent)
        assertEquals(.5f, usage.fraction, .0001f)
        assertEquals(100, ByteUsage.of(Long.MAX_VALUE, Long.MAX_VALUE)!!.percent)
    }
    @Test fun coreFrequencyRatioNeedsBothReadings() {
        assertNull(CpuCoreSample(0, null, 3000000).frequencyRatio)
        assertNull(CpuCoreSample(0, 0, 3000000).frequencyRatio)
        assertNull(CpuCoreSample(0, 2000000, null).frequencyRatio)
        assertNull(CpuCoreSample(0, 2000000, 0).frequencyRatio)
        assertEquals(.5f, CpuCoreSample(0, 1500000, 3000000).frequencyRatio!!, .0001f)
        assertEquals(1f, CpuCoreSample(0, 4000000, 3000000).frequencyRatio!!, .0001f)
    }
    @Test fun componentsAreSeparatedIntoHardwareCategories() {
        fun category(label: String) = HardwareCategory.of(Reading(label, null, "test"))
        assertEquals(HardwareCategory.CPU, category("SoC"))
        assertEquals(HardwareCategory.GPU, category("GPU / OpenGL"))
        assertEquals(HardwareCategory.MEMORY, category("資料分割區可用 / 總量"))
        assertEquals(HardwareCategory.SENSORS, category("感測器 · accelerometer"))
        assertEquals(HardwareCategory.CODECS, category("解碼 · OMX"))
        assertEquals(HardwareCategory.DEVICE, category("裝置"))
        assertEquals(HardwareCategory.THERMAL, category("溫度 · KGSL GPU（驅動）"))
        assertEquals(HardwareCategory.THERMAL, category("熱區 · skin · thermal_zone0"))
    }
    @Test fun widgetDoesNotTurnMissingReadingsIntoZero() {
        val data = WidgetPresentation.from(Snapshot())
        assertEquals("—", data.battery)
        assertEquals("—", data.memory)
        assertEquals("—", data.temperature)
        assertEquals("—", data.cpu)
        assertNull(data.batteryProgress)
        assertNull(data.memoryProgress)
        assertTrue(data.updated.contains("尚未取樣"))
    }
    @Test fun widgetRetainsRealZeroReadings() {
        val data = WidgetPresentation.from(Snapshot(batteryPercent = 0, batteryTemperature = 0f, memory = ByteUsage.of(0, 100)))
        assertEquals("0%", data.battery)
        assertEquals("0.0 °C", data.temperature)
        assertEquals("0%", data.memory)
        assertEquals(0, data.batteryProgress)
        assertEquals(0, data.memoryProgress)
    }
    @Test fun invalidBatteryAndTemperatureRemainUnknown() {
        val data = WidgetPresentation.from(Snapshot(batteryPercent = 101, batteryTemperature = Float.NaN))
        assertEquals("—", data.battery)
        assertNull(data.batteryProgress)
        assertEquals("—", data.temperature)
    }
    @Test fun widgetShowsSampleTimeIncludingDateInChosenZone() {
        val data = WidgetPresentation.from(Snapshot(sampledAtMillis = Instant.parse("2026-10-18T16:03:00Z").toEpochMilli()), ZoneId.of("Asia/Taipei"))
        assertEquals("快照 10/19 00:03", data.updated)
    }
    @Test fun widgetCpuValueIsHighestReadableFrequencyInGHz() {
        val data = WidgetPresentation.from(Snapshot(cores = listOf(CpuCoreSample(0, null, null), CpuCoreSample(1, 2400000, 3000000), CpuCoreSample(2, 1200000, 2200000))))
        assertEquals("2.40", data.cpu)
    }
}
