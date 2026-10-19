package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*

/** Foreground IO polling; discovery is cached until explicit rescan, values are never carried forward. */
class GpuMonitor(private val files: TemperatureFiles) {
    private data class Spec(val metric: GpuMetric, val path: String, val unit: GpuUnit)
    private var specs: List<Spec>? = null
    private val latest = linkedMapOf<String, GpuAttempt>()
    private val preferred = mutableMapOf<GpuMetric, String>()
    fun reset() { specs = null; latest.clear(); preferred.clear() }
    private fun discover(atMs: Long): List<Spec> = buildList {
        val onDirectory: (String, TemperatureDirectoryResult) -> Unit = { path, result ->
            if (result.status != TemperatureReadStatus.OK) latest[path] = GpuAttempt(null, path, GpuUnit.RAW, null, result.status, atMs,
                basis = "目錄探索結果，不能代替個別檔案的讀取狀態。${result.detail}")
        }
        fun addSet(base: String) {
            add(Spec(GpuMetric.CURRENT, "$base/devfreq/cur_freq", GpuUnit.HZ))
            add(Spec(GpuMetric.CURRENT, "$base/gpuclk", GpuUnit.HZ))
            add(Spec(GpuMetric.CURRENT, "$base/clock_mhz", GpuUnit.MHZ))
            add(Spec(GpuMetric.MINIMUM, "$base/devfreq/min_freq", GpuUnit.HZ))
            add(Spec(GpuMetric.MINIMUM, "$base/min_clock_mhz", GpuUnit.MHZ))
            add(Spec(GpuMetric.MAXIMUM, "$base/devfreq/max_freq", GpuUnit.HZ))
            add(Spec(GpuMetric.MAXIMUM, "$base/max_gpuclk", GpuUnit.HZ))
            add(Spec(GpuMetric.MAXIMUM, "$base/max_clock_mhz", GpuUnit.MHZ))
            add(Spec(GpuMetric.BUSY, "$base/gpu_busy_percentage", GpuUnit.PERCENT))
            add(Spec(GpuMetric.BUSY, "$base/gpubusy", GpuUnit.BUSY_TOTAL))
        }
        GpuPaths.bases(files, onDirectory).forEach(::addSet)
        GpuPaths.devfreq(files, onDirectory).forEach { base ->
            add(Spec(GpuMetric.CURRENT, "$base/cur_freq", GpuUnit.HZ))
            add(Spec(GpuMetric.MINIMUM, "$base/min_freq", GpuUnit.HZ))
            add(Spec(GpuMetric.MAXIMUM, "$base/max_freq", GpuUnit.HZ))
        }
        // Driver-provided symlinks have explicit MHz/percent contracts only when their local target matches KGSL.
        mapOf("gpu_clock" to Triple(GpuMetric.CURRENT, "clock_mhz", GpuUnit.MHZ),
            "gpu_min_clock" to Triple(GpuMetric.MINIMUM, "min_clock_mhz", GpuUnit.MHZ),
            "gpu_max_clock" to Triple(GpuMetric.MAXIMUM, "max_clock_mhz", GpuUnit.MHZ),
            "gpu_busy" to Triple(GpuMetric.BUSY, "gpu_busy_percentage", GpuUnit.PERCENT)).forEach { (leaf, contract) ->
            val path = "/sys/kernel/gpu/$leaf"; val target = GpuPaths.canonical(files, path)
            if (target.startsWith("/sys/devices/") && target.endsWith("/kgsl/kgsl-3d0/${contract.second}"))
                add(Spec(contract.first, path, contract.third))
        }
    }.distinctBy { it.path }.take(108)
    fun sample(atMs: Long = System.currentTimeMillis()): GpuSnapshot {
        val candidates = specs ?: discover(atMs).also { specs = it }
        val selected = mutableListOf<GpuAttempt>()
        for (metric in GpuMetric.entries) {
            val group = candidates.filter { it.metric == metric }.sortedBy { if (it.path == preferred[metric]) 0 else 1 }
            for (spec in group) {
                val result = GpuPaths.read(files, spec.path)
                val value = if (result.status == TemperatureReadStatus.OK) decode(spec.unit, result.raw) else null
                val status = if (result.status == TemperatureReadStatus.OK && value == null) TemperatureReadStatus.UNPARSABLE else result.status
                val attempt = GpuAttempt(metric, spec.path, spec.unit, result.raw, status, atMs, value,
                    "KGSL／devfreq 公開驅動定義；依來源單位轉換，不按數字大小猜測。$Gpu_REFERENCE")
                latest[spec.path] = attempt
                if (value != null) { selected += attempt; preferred[metric] = spec.path; break }
            }
            if (selected.none { it.metric == metric }) preferred.remove(metric)
        }
        return GpuSnapshot(atMs, latest.values.toList(), selected)
    }
    companion object {
        private const val Gpu_REFERENCE = GpuPaths.REFERENCE
        internal fun decode(unit: GpuUnit, raw: String?): Double? {
            if (unit == GpuUnit.BUSY_TOTAL) return GpuMetrics.busyFraction(raw)?.times(100)
            if (unit == GpuUnit.PERCENT) {
                val text = raw?.trim() ?: return null
                if (!Regex("[0-9]+(?:[.][0-9]+)?\\s*%?").matches(text)) return null
                return text.removeSuffix("%").trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }
            }
            val n = raw?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            return when (unit) { GpuUnit.HZ -> n / 1_000_000.0; GpuUnit.MHZ -> n.toDouble(); else -> null }
        }
    }
}
