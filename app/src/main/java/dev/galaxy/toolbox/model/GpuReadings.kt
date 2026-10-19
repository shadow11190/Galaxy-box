package dev.galaxy.toolbox.model

enum class GpuMetric(val title: String) { CURRENT("GPU 當前頻率"), MINIMUM("GPU 頻率下限"), MAXIMUM("GPU 頻率上限"), BUSY("GPU 忙碌比例") }
enum class GpuUnit(val title: String) { HZ("Hz"), MHZ("MHz"), PERCENT("%"), BUSY_TOTAL("busy / total"), RAW("單位未確認") }
data class GpuAttempt(val metric: GpuMetric?, val path: String, val unit: GpuUnit, val raw: String?, val status: TemperatureReadStatus,
    val atMs: Long, val value: Double? = null, val basis: String = "")
data class GpuSnapshot(val atMs: Long? = null, val attempts: List<GpuAttempt> = emptyList(), val selected: List<GpuAttempt> = emptyList()) {
    fun fresh(now: Long) = atMs != null && now >= atMs && now - atMs <= 10_000
    fun readings(now: Long): List<Reading> = if (!fresh(now)) emptyList() else selected.map { s ->
        Reading(s.metric!!.title, if (s.metric == GpuMetric.BUSY) java.lang.String.format(java.util.Locale.ROOT, "%.1f%%", s.value) else (if (s.value == 0.0) "0.0" else java.math.BigDecimal.valueOf(s.value!!).stripTrailingZeros().toPlainString()) + " MHz",
            s.path, "一般 App 唯讀 · 免 Root／ADB\n原始值 ${s.raw} · 原始單位 ${s.unit.title}\n讀取 epoch ms ${s.atMs}\n${s.basis}", HardwareCategory.GPU, "GPU 即時監控")
    }
}
