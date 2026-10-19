package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TemperatureComponentTest {
    @get:Rule val temp = TemporaryFolder()
    private val now = 1_000_000L
    private fun value(name: String, raw: String = "45000", metadata: Map<String, String> = emptyMap()): TemperatureReading {
        val result = TemperatureClassifier().classify(name, "/thermal/$name/temp", TemperatureSource.THERMAL, TemperatureUnit.MILLI_C, metadata)
        val sensor = TemperatureDescriptor(name, name, "/thermal/$name/temp", TemperatureSource.THERMAL, result.unit, result.classification, metadata)
        return TemperatureConversion.observation(sensor, TemperatureFileResult(raw, TemperatureReadStatus.OK), now)
    }
    @Test fun usrCpuLabelsDoNotDisappearIntoUnknownOrSoc() {
        listOf("cpu-1-0-usr", "cpu-0-7-usr", "cpuss-0-usr", "cpu0-gold-usr", "cpu-0-5-1", "cpu-1-1-0", "cpullc-0-1").forEach {
            val reading = value(it)
            assertEquals(TemperatureGroup.CPU, reading.sensor.classification.group)
            assertTrue(reading.usableInSummary(now))
        }
    }
    @Test fun fourComponentsRemainSeparateAndNoComponentSubstitutesForSoc() {
        val cpu = value("cpu0", "55000")
        val soc = value("soc-therm", "48000")
        val gpu = value("gpuss-0-usr", "46000", mapOf("SoC manufacturer" to "Qualcomm"))
        val selection = TemperatureSelection.summary(listOf(cpu, soc, gpu), now)
        assertEquals(cpu, selection.cpu); assertEquals(soc, selection.soc); assertEquals(gpu, selection.gpu)
        assertNull(selection.battery)
        assertNull(TemperatureSelection.summary(listOf(cpu, gpu), now).soc)
        val sections = TemperatureSections.build(listOf(cpu, soc, gpu), now)
        assertEquals(soc, sections.single { it.id == "soc" }.readings.single())
    }
    @Test fun documentedGpuLabelsRequireTheActualSocVendorAndDefinedAbi() {
        val name = "gpuss-0-usr"
        assertFalse(value(name).usableInSummary(now))
        assertFalse(value(name, metadata = mapOf("SoC manufacturer" to "Samsung")).usableInSummary(now))
        assertTrue(value(name, metadata = mapOf("SoC manufacturer" to "Qualcomm")).usableInSummary(now))
        assertTrue(value(name, metadata = mapOf("SoC manufacturer" to "QTI")).usableInSummary(now))
        assertTrue(value(name, metadata = mapOf("晶片代碼對照廠商" to "Qualcomm")).usableInSummary(now))
        assertFalse(value("gpu-skin-avg-step", metadata = mapOf("SoC manufacturer" to "Qualcomm")).usableInSummary(now))
        val raw = TemperatureClassifier().classify(name, "/temp", TemperatureSource.THERMAL, TemperatureUnit.RAW,
            mapOf("SoC manufacturer" to "Qualcomm"))
        assertFalse(raw.classification.summaryEligible)
    }
    @Test fun readableLocalGpuDeviceTreeCanIdentifyACustomLabelWithoutZoneNumberRules() {
        val metadata = mapOf("gpu compatible" to "qcom,kgsl-3d0", "gpu thermal-zone names" to "gpuss-7-usr gpuss-8-usr")
        val mapped = value("gpuss-7-usr", metadata = metadata)
        assertTrue(mapped.usableInSummary(now))
        assertEquals("gpu-local-dt", mapped.sensor.classification.ruleId)
        assertFalse(value("gpuss-9-usr", metadata = metadata).usableInSummary(now))
    }
    @Test fun readerPropagatesVendorEvidenceAndKeepsMissingGpuReadFailure() {
        val zone = File(temp.root, "thermal_zone77").apply { mkdir() }
        File(zone, "type").writeText("gpuss-0-usr")
        val input = File(zone, "temp").apply { writeText("45000") }
        val probe = ThermalProbe(listOf(temp.root), platformMetadata = mapOf("SoC manufacturer" to "Qualcomm"))
        assertEquals(45.0, probe.sample(now).single().celsius!!, .001)
        assertTrue(input.delete())
        val failed = probe.sample(now + 2000).single()
        assertEquals(TemperatureReadStatus.NOT_FOUND, failed.status)
        assertFalse(failed.usableInSummary(now + 2000))
    }
    @Test fun crystalAndBoardRelatedNamesAreNotInventedSocTemperature() {
        listOf("xo-therm", "quiet_therm", "skin", "tsens_tz_sensor0").forEach {
            assertNull(TemperatureSelection.summary(listOf(value(it)), now).soc)
        }
    }
    @Test fun screenshotGpussNumericFamilyUsesDefinedUnitsAndVendorEvidence() {
        for (name in listOf("gpuss-0", "gpuss-9", "gpuss-10", "gpuss-1-thermal")) {
            val reading = value(name, "45300", mapOf("SoC manufacturer" to "Qualcomm"))
            assertTrue(reading.usableInSummary(now))
            assertEquals(45.3, reading.celsius!!, .001)
            assertFalse(value(name).usableInSummary(now))
        }
    }
    @Test fun kgslNeedsExactPathAndLocalCompatibleNotOnlyItsNameOrDriverString() {
        val classifier = TemperatureClassifier()
        fun classified(path: String, compatible: String) = classifier.classify("KGSL GPU", path, TemperatureSource.KGSL, TemperatureUnit.MILLI_C,
            mapOf("compatible" to compatible))
        assertTrue(classified("/sys/class/kgsl/kgsl-3d0/temp", "qcom,kgsl-3d0").classification.summaryEligible)
        assertFalse(classified("/different/temp", "qcom,kgsl-3d0").classification.summaryEligible)
        assertFalse(classified("/sys/class/kgsl/kgsl-3d0/temp", "unknown").classification.summaryEligible)
    }
    @Test fun tripDiagnosticsDoNotConsumeTheBudgetForLaterMeasurementZones() {
        repeat(100) { i ->
            val zone = File(temp.root, "thermal_zone$i").apply { mkdir() }
            File(zone, "type").writeText(if (i == 99) "gpuss-0" else "cpu-$i-0-0")
            File(zone, "temp").writeText("45000")
            repeat(8) { trip -> File(zone, "trip_point_${trip}_temp").writeText("105000") }
        }
        val readings = ThermalProbe(listOf(temp.root), platformMetadata = mapOf("SoC manufacturer" to "Qualcomm")).sample(now)
        assertEquals(100, readings.count { it.sensor.source == TemperatureSource.THERMAL })
        assertEquals(256, readings.count { it.sensor.source == TemperatureSource.TRIP_POINT })
        assertEquals("gpuss-0", TemperatureSelection.summary(readings, now).gpu!!.sensor.rawName)
    }
    @Test fun headroomKeepsValidZeroOverOneAndStaleSeparateFromDegrees() {
        assertEquals("0.00", ThermalHeadroomReading(0f, TemperatureReadStatus.OK, now).display(now))
        assertEquals("1.25", ThermalHeadroomReading(1.25f, TemperatureReadStatus.OK, now).display(now))
        assertEquals("已過期", ThermalHeadroomReading(.8f, TemperatureReadStatus.OK, now).display(now + 30001))
        assertEquals("無可用資料", ThermalHeadroomReading(Float.NaN, TemperatureReadStatus.OK, now).display(now))
        assertEquals("不支援", ThermalHeadroomReading(status = TemperatureReadStatus.UNSUPPORTED).display(now))
    }
}
