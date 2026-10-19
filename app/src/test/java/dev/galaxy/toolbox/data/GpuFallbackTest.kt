package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GpuFallbackTest {
    private val base = GpuPaths.CLASS
    private class Fake : TemperatureFiles {
        val values = mutableMapOf<String, TemperatureFileResult>()
        val dirs = mutableMapOf<String, TemperatureDirectoryResult>()
        val links = mutableMapOf<String, String>()
        val reads = mutableListOf<String>(); var listings = 0
        fun put(path: String, raw: String) { values[path] = TemperatureFileResult(raw, TemperatureReadStatus.OK) }
        override fun read(path: String): TemperatureFileResult { reads += path; return values[path.replace('\\', '/')] ?: TemperatureFileResult(null, TemperatureReadStatus.NOT_FOUND) }
        override fun list(path: String): TemperatureDirectoryResult { listings++; return dirs[path] ?: TemperatureDirectoryResult(emptyList(), TemperatureReadStatus.NOT_FOUND) }
        override fun canonical(path: String) = links[path.replace('\\', '/')] ?: path.replace('\\', '/')
    }
    @Test fun gpuclkWorksWhenDevfreqIsDeniedWithoutPrivilege() {
        val f = Fake(); f.values["$base/devfreq/cur_freq"] = TemperatureFileResult(null, TemperatureReadStatus.PERMISSION_DENIED); f.put("$base/gpuclk", "725000000")
        val s = GpuMonitor(f).sample(1000)
        assertEquals(725.0, s.selected.single().value!!, 0.0)
        assertEquals("$base/gpuclk", s.readings(1000).single().source)
        assertTrue(s.attempts.any { it.path.endsWith("cur_freq") && it.status == TemperatureReadStatus.PERMISSION_DENIED })
    }
    @Test fun clockMhzIsNotDividedOrGuessedByMagnitude() {
        val f = Fake(); f.put("$base/clock_mhz", "800")
        assertEquals(800.0, GpuMonitor(f).sample(1000).selected.single().value!!, 0.0)
        assertEquals(.0008, GpuMonitor.decode(GpuUnit.HZ, "800")!!, .00000001)
        assertNull(GpuMonitor.decode(GpuUnit.RAW, "800000000"))
        val low = Fake(); low.put("$base/gpuclk", "800")
        assertEquals("0.0008 MHz", GpuMonitor(low).sample(1000).readings(1000).single().value)
    }
    @Test fun percentUsesItsContractAndZeroRemainsZero() {
        assertEquals(0.0, GpuMonitor.decode(GpuUnit.PERCENT, "0 %")!!, 0.0)
        assertEquals(.5, GpuMonitor.decode(GpuUnit.PERCENT, "0.5")!!, 0.0)
        listOf("101 %", "-1", "NaN", "25 50", "unknown 20%", "25%%").forEach { assertNull(GpuMonitor.decode(GpuUnit.PERCENT, it)) }
    }
    @Test fun malformedPercentFallsBackToBusyTotalWithoutAveraging() {
        val f = Fake(); f.put("$base/gpu_busy_percentage", "unknown"); f.put("$base/gpubusy", "250 1000")
        val s = GpuMonitor(f).sample(1000)
        assertEquals(25.0, s.selected.single().value!!, 0.0)
        assertEquals("$base/gpubusy", s.selected.single().path)
        assertTrue(s.attempts.any { it.status == TemperatureReadStatus.UNPARSABLE })
    }
    @Test fun preferredSourceIsPolledWithoutDirectoryRescansAndFailureClearsValue() {
        val f = Fake(); f.put("$base/gpuclk", "700000000"); val monitor = GpuMonitor(f)
        monitor.sample(1000); val count = f.listings; f.reads.clear()
        assertEquals(700.0, monitor.sample(3000).selected.single().value!!, 0.0)
        assertEquals(count, f.listings); assertFalse(f.reads.contains("$base/devfreq/cur_freq"))
        f.values.remove("$base/gpuclk"); val failed = monitor.sample(5000)
        assertTrue(failed.selected.isEmpty()); assertTrue(failed.readings(5000).isEmpty())
        monitor.reset(); monitor.sample(7000); assertTrue(f.listings > count)
    }
    @Test fun verifiedDevfreqAliasWorksWhileDdrAndUnconfirmedGpufreqAreIgnored() {
        val f = Fake(); val root = "/sys/class/devfreq"; val gpu = "$root/3d00000.qcom,kgsl-3d0"
        f.dirs[root] = TemperatureDirectoryResult(listOf("$root/ddr", "$root/gpufreq", gpu), TemperatureReadStatus.OK)
        f.put("$root/ddr/cur_freq", "999000000"); f.put("$root/gpufreq/cur_freq", "900000"); f.put("$gpu/cur_freq", "650000000")
        assertEquals(650.0, GpuMonitor(f).sample(1000).selected.single().value!!, 0.0)
        assertFalse(f.reads.any { it.contains("/ddr/") || it.contains("/gpufreq/") })
    }
    @Test fun platformAddressesAreDiscoveredWithoutFixedZoneOrAddressAssumptions() {
        val f = Fake(); val root = "/sys/devices/platform/soc"; val device = "$root/abc123.qcom,kgsl-3d0"
        f.dirs[root] = TemperatureDirectoryResult(listOf(device), TemperatureReadStatus.OK)
        f.put("$device/kgsl/kgsl-3d0/gpuclk", "610000000")
        assertEquals(610.0, GpuMonitor(f).sample(1000).selected.single().value!!, 0.0)
    }
    @Test fun kernelGpuMhzAliasRequiresLocalKgslTarget() {
        val f = Fake(); val path = "/sys/kernel/gpu/gpu_clock"; f.put(path, "650")
        assertTrue(GpuMonitor(f).sample(1000).selected.isEmpty())
        f.links[path] = "/sys/devices/platform/1.qcom,kgsl-3d0/kgsl/kgsl-3d0/clock_mhz"
        assertEquals(650.0, GpuMonitor(f).sample(1000).selected.single().value!!, 0.0)
    }
    @Test fun realZeroFrequencyIsValidAndOldSnapshotsAreNotLatest() {
        val f = Fake(); f.put("$base/gpuclk", "0"); val s = GpuMonitor(f).sample(1000)
        assertEquals("0.0 MHz", s.readings(1000).single().value)
        assertTrue(s.readings(11001).isEmpty()); assertTrue(s.readings(999).isEmpty())
    }
    @Test fun directoryDenialIsNotAppliedToSuccessfulFile() {
        val f = Fake(); f.dirs["/sys/devices/platform"] = TemperatureDirectoryResult(emptyList(), TemperatureReadStatus.PERMISSION_DENIED)
        f.put("$base/gpuclk", "700000000"); val s = GpuMonitor(f).sample(1000)
        assertEquals(1, s.selected.size); assertTrue(s.attempts.any { it.metric == null && it.status == TemperatureReadStatus.PERMISSION_DENIED })
    }
    @Test fun kgslTemperatureSentinelIsNotARealTemperatureButZeroIsValid() {
        val f = Fake(); val path = "$base/temp"
        f.put("$base/device/of_node/compatible", "qcom,kgsl-3d0")
        f.put(path, "-2147483648")
        fun sample() = HwmonProbe(roots = emptyList(), gpuFiles = listOf(File(path)), files = f).sample(1000).single()
        assertEquals(TemperatureReadStatus.NO_DATA, sample().status)
        f.put(path, "0"); assertEquals(0.0, sample().celsius!!, 0.0)
    }
    @Test fun kgslTemperatureAliasStillNeedsBindingAndDoesNotGuessFromGpuName() {
        val classifier = TemperatureClassifier(); val path = "/sys/devices/platform/aa.qcom,kgsl-3d0/kgsl/kgsl-3d0/temp"
        val unconfirmed = classifier.classify("KGSL GPU", path, TemperatureSource.KGSL, TemperatureUnit.MILLI_C, emptyMap(), "")
        assertFalse(unconfirmed.classification.summaryEligible)
        val confirmed = classifier.classify("KGSL GPU", path, TemperatureSource.KGSL, TemperatureUnit.MILLI_C, mapOf("compatible" to "qcom,kgsl-3d0"), "")
        assertTrue(confirmed.classification.summaryEligible)
    }
}
