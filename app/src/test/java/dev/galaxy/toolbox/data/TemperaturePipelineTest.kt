package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TemperaturePipelineTest {
    @get:Rule val temporary = TemporaryFolder()
    private val at = 1_000_000L
    private val classifier = TemperatureClassifier()
    private fun reading(name: String, raw: String?, source: TemperatureSource = TemperatureSource.THERMAL,
        path: String = "/sys/class/thermal/thermal_zone0/temp", metadata: Map<String, String> = emptyMap(),
        status: TemperatureReadStatus = if (raw == null) TemperatureReadStatus.NO_DATA else TemperatureReadStatus.OK,
        time: Long = at, rules: TemperatureClassifier = classifier): TemperatureReading {
        val unit = if (source == TemperatureSource.BATTERY_API) TemperatureUnit.DECI_C else TemperatureUnit.MILLI_C
        val result = rules.classify(name, path, source, unit, metadata, "SM-S9480")
        val descriptor = TemperatureDescriptor("${source.name}|$path|$name|${result.unit}", name, path, source, result.unit, result.classification, metadata)
        return TemperatureConversion.observation(descriptor, TemperatureFileResult(raw, status), time)
    }
    @Test fun cpuGpuAndBatteryUseSourceDefinedUnits() {
        val cpu = reading("cpu-therm", "42500")
        val gpu = reading("gpu-therm", "45000", metadata = mapOf("driver" to "/drivers/kgsl"))
        val battery = reading("battery", "320", TemperatureSource.BATTERY_API, "Android/temperature")
        assertEquals(42.5, cpu.celsius!!, .0001)
        assertEquals(45.0, gpu.celsius!!, .0001)
        assertEquals(32.0, battery.celsius!!, .0001)
        val summary = TemperatureSelection.summary(listOf(cpu, gpu, battery), at)
        assertEquals(cpu, summary.cpu); assertEquals(gpu, summary.gpu); assertEquals(battery, summary.battery)
    }
    @Test fun hwTrip105FromTempRemainsUnconfirmedEvenWhenTripSettingMatches() {
        for (index in 0..1) {
            val trip = reading("cpu-hw-trip-$index", "105000", metadata = mapOf("trip_point_0_temp" to "105000"))
            assertEquals(TemperatureKind.UNKNOWN, trip.sensor.classification.kind)
            assertEquals(TemperatureUnit.RAW, trip.sensor.declaredUnit)
            assertEquals("105000", trip.raw)
            assertNull(trip.celsius)
            assertFalse(trip.displayValue(at).contains("°C"))
            assertNull(TemperatureSelection.summary(listOf(trip), at).cpu)
        }
    }
    @Test fun actualTripPointPathIsAThresholdAndNeverACurrentTemperature() {
        val trip = reading("cpu-hw-trip-0", "105000", TemperatureSource.TRIP_POINT, "/thermal_zone0/trip_point_0_temp")
        assertEquals(TemperatureKind.THRESHOLD, trip.sensor.classification.kind)
        assertEquals(105.0, trip.celsius!!, .0001)
        assertFalse(trip.usableInSummary(at))
    }
    @Test fun bclLevelsKeepZeroAsRawStateWithoutInventingMeaning() {
        for (index in 0..2) {
            val state = reading("pm8550b-bcl-lvl$index", "0")
            assertEquals(TemperatureKind.PROTECTION_STATE, state.sensor.classification.kind)
            assertEquals(TemperatureReadStatus.OK, state.status)
            assertNull(state.celsius)
            assertEquals("0 · 原始值", state.displayValue(at))
            assertFalse(state.usableInSummary(at))
        }
    }
    @Test fun legalZeroTemperatureIsPreserved() {
        val zero = reading("cpu0", "0")
        assertEquals(0.0, zero.celsius!!, .0001)
        assertEquals("0.0 °C", zero.displayValue(at))
        assertEquals(zero, TemperatureSelection.summary(listOf(zero), at).cpu)
    }
    @Test fun highAndConstantReadingsAreNotDiscardedForTheirMagnitude() {
        val high = reading("cpu-therm", "210000")
        assertEquals(210.0, high.celsius!!, .0001)
        assertTrue(high.usableInSummary(at))
        val history = TemperatureHistory()
        repeat(3) { history.update(listOf(high.copy(attemptedAtMs = at + it * 2000)), at + it * 2000) }
        assertEquals(listOf(210.0, 210.0, 210.0), history.series()[high.sensor.id]!!.map { it.celsius })
    }
    @Test fun unknownNamesAndZoneNumbersDoNotSelectAComponent() {
        val unknown = reading("tsens_tz_sensor7", "50000")
        assertEquals(TemperatureKind.UNKNOWN, unknown.sensor.classification.kind)
        assertNull(unknown.celsius)
        assertEquals("tsens_tz_sensor7", unknown.sensor.classification.title)
        val cpuDifferentZone = reading("cpu-therm", "50000", path = "/thermal_zone99/temp")
        assertEquals(TemperatureGroup.CPU, cpuDifferentZone.sensor.classification.group)
    }
    @Test fun gpuAndKgslNamesAloneAreInsufficientForSummary() {
        val gpu = reading("gpu", "50000")
        val kgsl = reading("KGSL GPU", "51000", TemperatureSource.KGSL, "/sys/class/kgsl/kgsl-3d0/temp", mapOf("driver" to "kgsl"))
        assertNull(TemperatureSelection.summary(listOf(gpu, kgsl), at).gpu)
        assertEquals(TemperatureGroup.GPU, kgsl.sensor.classification.group)
        assertEquals(EvidenceConfidence.LOW, kgsl.sensor.classification.confidence)
        assertEquals(TemperatureKind.UNKNOWN, kgsl.sensor.classification.kind)
        assertNull(kgsl.celsius)
        assertFalse(kgsl.displayValue(at).contains("°C"))
    }
    @Test fun verifiedRuleMustMatchExactModelSourceAndPath() {
        val path = "/sys/class/kgsl/kgsl-3d0/temp"
        val rules = TemperatureClassifier(listOf(VerifiedTemperatureRule("SM-S9480", "KGSL GPU", path, TemperatureSource.KGSL,
            TemperatureGroup.GPU, TemperatureUnit.MILLI_C, "fixture: documented exact device source", 0)))
        assertTrue(reading("KGSL GPU", "51000", TemperatureSource.KGSL, path, rules = rules).usableInSummary(at))
        assertFalse(reading("KGSL GPU", "51000", TemperatureSource.KGSL, "$path-other", rules = rules).usableInSummary(at))
    }
    @Test fun cameraVideoAndDdrUseRelatedPointLabels() {
        listOf("camera", "video", "ddr").forEach { name ->
            val sensor = reading(name, "40000")
            assertEquals(TemperatureGroup.OTHER, sensor.sensor.classification.group)
            assertTrue(sensor.sensor.classification.title.contains("相關測點"))
            assertFalse(sensor.usableInSummary(at))
        }
    }
    @Test fun missingEmptyBadFormatAndPermissionRemainDistinct() {
        val statuses = listOf(TemperatureReadStatus.PERMISSION_DENIED, TemperatureReadStatus.NOT_FOUND,
            TemperatureReadStatus.NO_DATA, TemperatureReadStatus.UNAVAILABLE)
        statuses.forEach { status ->
            val failed = reading("cpu-therm", null, status = status)
            assertEquals(status, failed.status); assertNull(failed.celsius); assertFalse(failed.usableInSummary(at))
        }
        assertEquals(TemperatureReadStatus.UNPARSABLE, reading("cpu-therm", "N/A").status)
        assertEquals(TemperatureReadStatus.UNPARSABLE, reading("cpu-therm", "NaN").status)
        assertEquals(TemperatureReadStatus.UNPARSABLE, reading("cpu-therm", "42.5").status)
    }
    @Test fun errnoAndExceptionMappingNeverGuessUnknownFailureReasons() {
        assertEquals(TemperatureReadStatus.PERMISSION_DENIED, NioTemperatureFiles.errnoStatus(13))
        assertEquals(TemperatureReadStatus.PERMISSION_DENIED, NioTemperatureFiles.errnoStatus(1))
        assertEquals(TemperatureReadStatus.NO_DATA, NioTemperatureFiles.errnoStatus(61))
        assertEquals(TemperatureReadStatus.NO_DATA, NioTemperatureFiles.errnoStatus(11))
        assertEquals(TemperatureReadStatus.NOT_FOUND, NioTemperatureFiles.errnoStatus(2))
        assertEquals(TemperatureReadStatus.UNAVAILABLE, NioTemperatureFiles.errnoStatus(5))
        assertEquals(TemperatureReadStatus.PERMISSION_DENIED, NioTemperatureFiles.fileFailure(AccessDeniedException("sensor")))
        assertEquals(TemperatureReadStatus.NOT_FOUND, NioTemperatureFiles.fileFailure(NoSuchFileException("sensor")))
        assertEquals(TemperatureReadStatus.UNAVAILABLE, NioTemperatureFiles.fileFailure(java.io.IOException("unknown")))
    }
    @Test fun staleReadingsDoNotEnterCpuSummary() {
        val cpu = reading("cpu-therm", "42000")
        assertTrue(cpu.usableInSummary(at + 10_000))
        assertFalse(cpu.usableInSummary(at + 10_001))
        assertEquals(TemperatureReadStatus.STALE, cpu.effectiveStatus(at + 10_001))
        assertNull(TemperatureSelection.summary(listOf(cpu), at + 10_001).cpu)
    }
    @Test fun failedReadNeverPresentsLastSuccessfulTemperatureAsCurrent() {
        val cpu = reading("cpu-therm", "42000")
        val history = TemperatureHistory()
        history.update(listOf(cpu), at)
        history.update(listOf(reading("cpu-therm", null, status = TemperatureReadStatus.PERMISSION_DENIED, time = at + 2000)), at + 2000)
        val failed = history.readings().single()
        assertNull(failed.celsius)
        assertEquals(42.0, failed.lastGoodCelsius!!, .0001)
        assertEquals(at, failed.lastSuccessAtMs)
        assertEquals("權限不足", failed.displayValue(at + 2000))
        assertEquals(listOf(42.0, null), history.series()[cpu.sensor.id]!!.map { it.celsius })
    }
    @Test fun cpuSummaryUsesOnlyFreshIdentifiedMeasurements() {
        val cool = reading("cpu0", "42000")
        val warm = reading("cpu1", "55000", path = "/thermal_zone1/temp")
        val unknown = reading("sensor", "120000", path = "/thermal_zone2/temp")
        val trip = reading("cpu-hw-trip-0", "105000", path = "/thermal_zone3/temp")
        assertEquals(warm, TemperatureSelection.summary(listOf(cool, warm, unknown, trip), at).cpu)
    }
    @Test fun batterySourcesAreNotAveragedOrUsedForCpuGpuFallback() {
        val api = reading("battery", "320", TemperatureSource.BATTERY_API, "Android/temperature")
        val secondary = reading("battery", "45000", path = "/thermal_zone9/temp")
        val result = TemperatureSelection.summary(listOf(api, secondary), at)
        assertEquals(api, result.battery)
        assertNull(result.cpu); assertNull(result.gpu)
        assertNull(TemperatureSelection.summary(listOf(secondary), at).battery)
    }
    @Test fun gpuPriorityIsExplicitAndDoesNotChooseTheHottestSource() {
        val thermal = reading("gpu", "40000", path = "/thermal_zone1/temp", metadata = mapOf("driver" to "kgsl"))
        val hwmon = reading("gpu", "60000", TemperatureSource.HWMON, "/hwmon0/temp1_input", mapOf("compatible" to "qcom,kgsl-3d0"))
        assertEquals(thermal, TemperatureSelection.summary(listOf(hwmon, thermal), at).gpu)
    }
    @Test fun historyKeepsBatterySourcesSeparateAndPrunesFiveMinutes() {
        val api = reading("battery", "320", TemperatureSource.BATTERY_API, "Android/temperature")
        val secondary = reading("battery", "45000", path = "/thermal_zone9/temp")
        val history = TemperatureHistory()
        history.update(listOf(api, secondary), at)
        assertEquals(listOf(32.0), history.series()[api.sensor.id]!!.map { it.celsius })
        assertEquals(listOf(45.0), history.series()[secondary.sensor.id]!!.map { it.celsius })
        history.update(listOf(api.copy(attemptedAtMs = at + 300001)), at + 300001)
        assertEquals(1, history.series()[api.sensor.id]!!.size)
        assertTrue(history.series()[secondary.sensor.id]!!.isEmpty())
    }
    @Test fun sectionPartitionKeepsEachSensorOnceAndInvalidItemsInDiagnostics() {
        val cpu = reading("cpu0", "42000")
        val values = listOf(cpu, reading("cpu-hw-trip-0", "105000"), reading("pm-bcl-lvl0", "0"), reading("sdr0", null))
        val sections = TemperatureSections.build(values, at)
        assertEquals(4, sections.sumOf { it.readings.size })
        assertEquals(cpu, sections.first { it.id == "cpu" }.readings.single())
        assertEquals("sdr0", sections.first { it.id == "invalid" }.readings.single().sensor.rawName)
    }
    @Test fun systemThermalLabelsFollowApi29ConstantsWithoutClaimingNoThrottling() {
        assertEquals("未回報熱壓力", SystemThermalReading(0, TemperatureReadStatus.OK, at).title(at))
        assertEquals("關機等級", SystemThermalReading(6, TemperatureReadStatus.OK, at).title(at))
        assertEquals("不支援", SystemThermalReading(status = TemperatureReadStatus.UNSUPPORTED).title(at))
        assertEquals("已過期", SystemThermalReading(0, TemperatureReadStatus.OK, at).title(at + 10001))
        assertFalse(SystemThermalReading.EXPLANATION.contains("無熱節流"))
    }
    @Test fun thermalReaderKeepsActualTempAndTripPathsAndMissingStatus() {
        val root = temporary.newFolder()
        val zone = File(root, "thermal_zone0").apply { mkdir() }
        File(zone, "type").writeText("cpu-hw-trip-0")
        File(zone, "temp").writeText("105000")
        File(zone, "trip_point_0_temp").writeText("105000")
        val probe = ThermalProbe(listOf(root, root))
        val readings = probe.sample(at)
        assertEquals(2, readings.size)
        assertEquals(TemperatureKind.UNKNOWN, readings.first { it.sensor.source == TemperatureSource.THERMAL }.sensor.classification.kind)
        assertEquals(TemperatureKind.THRESHOLD, readings.first { it.sensor.source == TemperatureSource.TRIP_POINT }.sensor.classification.kind)
        assertEquals("105000", readings.first().sensor.metadata[File(zone, "trip_point_0_temp").path])
        assertTrue(File(zone, "temp").delete())
        assertEquals(TemperatureReadStatus.NOT_FOUND, probe.sample(at + 2000).first { it.sensor.source == TemperatureSource.THERMAL }.status)
    }
    @Test fun hwmonReadsAllChannelsButThermistorUnitsStayUnconfirmed() {
        val root = temporary.newFolder()
        val chip = File(root, "hwmon0").apply { mkdir() }
        File(chip, "name").writeText("driver")
        File(chip, "temp1_label").writeText("cpu0")
        File(chip, "temp1_input").writeText("0")
        File(chip, "temp2_input").writeText("2000")
        File(chip, "temp2_type").writeText("4")
        val probe = HwmonProbe(listOf(root, root), emptyList())
        val readings = probe.sample(at)
        assertEquals(2, readings.size)
        assertEquals(0.0, readings.first { it.sensor.rawName == "cpu0" }.celsius!!, .0001)
        assertEquals(TemperatureReadStatus.UNIT_UNCONFIRMED, readings.first { it.raw == "2000" }.status)
        File(chip, "temp1_fault").writeText("1")
        assertEquals(TemperatureReadStatus.FAULT, probe.sample(at + 2000).first { it.sensor.rawName == "cpu0" }.status)
    }
    @Test fun readPermissionFailureSurvivesReaderPipeline() {
        val root = temporary.newFolder()
        val zone = File(root, "thermal_zone0").apply { mkdir() }
        File(zone, "type").writeText("cpu-therm")
        val input = File(zone, "temp").apply { writeText("42000") }
        val files = object : NioTemperatureFiles() {
            override fun read(path: String) = if (path == input.path) TemperatureFileResult(null, TemperatureReadStatus.PERMISSION_DENIED, "errno=13") else super.read(path)
        }
        val reading = ThermalProbe(listOf(root), files).sample(at).single()
        assertEquals(TemperatureReadStatus.PERMISSION_DENIED, reading.status)
        assertNull(reading.celsius)
    }
    @Test fun inaccessibleHwmonTypeDoesNotAssumeCelsius() {
        val root = temporary.newFolder()
        val chip = File(root, "hwmon0").apply { mkdir() }
        File(chip, "name").writeText("cpu0")
        File(chip, "temp1_input").writeText("45000")
        val type = File(chip, "temp1_type")
        val files = object : NioTemperatureFiles() {
            override fun read(path: String) = if (path == type.path) TemperatureFileResult(null, TemperatureReadStatus.PERMISSION_DENIED, "errno=13") else super.read(path)
        }
        val reading = HwmonProbe(listOf(root), emptyList(), files).sample(at).single()
        assertEquals(TemperatureReadStatus.PERMISSION_DENIED, reading.status)
        assertEquals(TemperatureUnit.RAW, reading.sensor.declaredUnit)
        assertNull(reading.celsius)
        assertFalse(reading.usableInSummary(at))
    }
    @Test fun discoveryIsCachedAndSubsequentSampleStillReadsFreshValue() {
        val root = temporary.newFolder()
        val zone = File(root, "thermal_zone0").apply { mkdir() }
        File(zone, "type").writeText("cpu0")
        val input = File(zone, "temp").apply { writeText("42000") }
        var listings = 0
        val files = object : NioTemperatureFiles() {
            override fun list(path: String): TemperatureDirectoryResult { listings++; return super.list(path) }
        }
        val probe = ThermalProbe(listOf(root), files)
        assertEquals(42.0, probe.sample(at).single().celsius!!, .0001)
        val afterDiscovery = listings
        input.writeText("43000")
        assertEquals(43.0, probe.sample(at + 2000).single().celsius!!, .0001)
        assertEquals(afterDiscovery, listings)
    }
}
