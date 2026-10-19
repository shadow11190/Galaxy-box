package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import org.junit.Assert.*
import org.junit.Test

class SocTemperatureDiagnosticsTest {
    private val now = 100_000L
    private fun reading(name: String, raw: String = "49000", metadata: Map<String, String> = emptyMap(),
        status: TemperatureReadStatus = TemperatureReadStatus.OK, atMs: Long = now, model: String = ""): TemperatureReading {
        val path = "/sys/class/thermal/$name/temp"
        val rule = TemperatureClassifier().classify(name, path, TemperatureSource.THERMAL, TemperatureUnit.MILLI_C, metadata, model)
        val descriptor = TemperatureDescriptor(name, name, path, TemperatureSource.THERMAL, rule.unit, rule.classification, metadata)
        return TemperatureConversion.observation(descriptor, TemperatureFileResult(if (status == TemperatureReadStatus.OK) raw else null, status), atMs)
    }

    @Test fun explicitSocSensorLabelsKeepUnitsAndValidZero() {
        for (label in listOf("SoC-temp-sensor-1", "soc_die_0", "soc-thermal-usr")) {
            val zero = reading(label, "0")
            assertEquals(TemperatureGroup.SOC, zero.sensor.classification.group)
            assertEquals(0.0, zero.celsius!!, .001)
            assertEquals(zero, SocTemperatureDiagnostics.build(listOf(zero), now).summary)
        }
    }

    @Test fun tsensNeedsLocalBindingAndAUnitContract() {
        for (compatible in listOf("qcom,tsens-v0_1", "qcom,sm8650-tsens\u0000qcom,tsens-v2\u0000", "qcom,tsens-v1")) {
            val local = reading("tsens_tz_sensor0", metadata = mapOf("compatible" to compatible))
            assertTrue(local.usableInSummary(now))
            assertEquals(TemperatureGroup.SOC, local.sensor.classification.group)
            assertEquals(49.0, local.celsius!!, .001)
        }
        assertFalse(reading("tsens_tz_sensor0").usableInSummary(now))
        assertFalse(reading("tsens_tz_sensor0", metadata = mapOf("SoC manufacturer" to "Qualcomm")).usableInSummary(now))
        assertFalse(reading("camera-0", metadata = mapOf("compatible" to "qcom,tsens-v2")).usableInSummary(now))
        val raw = TemperatureClassifier().classify("tsens_tz_sensor0", "/temp", TemperatureSource.THERMAL,
            TemperatureUnit.RAW, mapOf("compatible" to "qcom,tsens-v2"))
        assertFalse(raw.classification.summaryEligible)
    }

    @Test fun exactLocalTsensDriverMayCorroborateButSubstringDoesNot() {
        assertTrue(reading("tsens0", metadata = mapOf("driver" to "/sys/bus/platform/drivers/qcom-tsens")).usableInSummary(now))
        assertFalse(reading("tsens0", metadata = mapOf("driver" to "/sys/bus/platform/drivers/not-qcom-tsens")).usableInSummary(now))
    }

    @Test fun otherSubsystemLabelsAreShownForComparisonWithoutInventingSoc() {
        val readings = listOf("camera-0", "video", "ddr", "qme-0-0", "tsens_tz_sensor0").map { reading(it) }
        val diagnostics = SocTemperatureDiagnostics.build(readings, now)
        assertEquals(readings.map { it.sensor.id }.toSet(), diagnostics.unconfirmed.map { it.sensor.id }.toSet())
        assertNull(diagnostics.summary)
        assertTrue(diagnostics.identified.isEmpty())
        assertFalse(diagnostics.explanation.contains("權限不足"))
        assertTrue(diagnostics.explanation.contains("不代表手機沒有感測器"))
    }

    @Test fun protectionNodesAndOtherComponentsAreExcludedFromSocCandidates() {
        val readings = listOf("cpu-hw-trip-0", "pm8998-bcl-lvl0", "cpu-0-0-0", "gpuss-0", "battery").map { reading(it) }
        val diagnostics = SocTemperatureDiagnostics.build(readings, now)
        assertTrue(diagnostics.unconfirmed.isEmpty())
        assertNull(diagnostics.summary)
    }

    @Test fun knownSocPermissionErrorIsShownAsActualFailure() {
        val denied = reading("soc-therm", status = TemperatureReadStatus.PERMISSION_DENIED)
        val diagnostics = SocTemperatureDiagnostics.build(listOf(denied), now)
        assertEquals(listOf(denied), diagnostics.identified)
        assertNull(diagnostics.summary)
        assertTrue(diagnostics.explanation.contains("權限不足 1"))
    }

    @Test fun staleAndUnparsableSocNeverBecomeFreshSummaryOrZero() {
        val old = reading("soc0", atMs = now - TemperatureReading.FRESH_FOR_MS - 1)
        val malformed = reading("soc1", "not-a-number")
        val diagnostics = SocTemperatureDiagnostics.build(listOf(old, malformed), now)
        assertNull(diagnostics.summary)
        assertTrue(diagnostics.explanation.contains("已過期 1"))
        assertTrue(diagnostics.explanation.contains("格式無法解析 1"))
        assertNull(malformed.celsius)
    }

    @Test fun discoveryErrorDoesNotTurnUnidentifiedNodesIntoPermissionFailures() {
        val issue = diagnostic("/sys/class/thermal", TemperatureReadStatus.PERMISSION_DENIED, now, "EACCES")
        val unknown = reading("qcm-0-0")
        val diagnostics = SocTemperatureDiagnostics.build(listOf(issue, unknown), now)
        assertEquals(listOf(issue), diagnostics.discoveryIssues)
        assertEquals(listOf(unknown), diagnostics.unconfirmed)
        assertEquals(TemperatureReadStatus.OK, unknown.status)
        assertNull(diagnostics.summary)
    }

    @Test fun userDevCheckVideoMappingIsLimitedToS26UltraAndRemainsExplicitlyLowConfidence() {
        val metadata = mapOf("SoC manufacturer" to "Qualcomm")
        val video = reading("video", "41000", metadata, model = "SM-S9480")
        assertEquals(TemperatureGroup.SOC, video.sensor.classification.group)
        assertEquals(EvidenceConfidence.LOW, video.sensor.classification.confidence)
        assertEquals("soc-s26-video-category", video.sensor.classification.ruleId)
        assertEquals(video, TemperatureSelection.summary(listOf(video), now).soc)
        assertEquals(41.0, video.celsius!!, .001)
        assertFalse(reading("video", metadata = metadata).usableInSummary(now))
        assertFalse(reading("video", metadata = metadata, model = "SM-S9380").usableInSummary(now))
        assertFalse(reading("video", model = "SM-S9480").usableInSummary(now))
        assertFalse(reading("camera", metadata = metadata, model = "SM-S9480").usableInSummary(now))
        assertFalse(reading("video", metadata = metadata, model = "SM-S9480", status = TemperatureReadStatus.PERMISSION_DENIED).usableInSummary(now))
        val wrongUnit = TemperatureClassifier().classify("video", "/temp", TemperatureSource.THERMAL,
            TemperatureUnit.RAW, metadata, "SM-S9480")
        assertFalse(wrongUnit.classification.summaryEligible)
    }
}
