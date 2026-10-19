package dev.galaxy.toolbox.widgets

import dev.galaxy.toolbox.model.Snapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class WidgetKind { OVERVIEW, BATTERY, RESOURCES }

data class WidgetPresentation(
    val battery: String, val temperature: String, val memory: String, val memoryDetail: String,
    val storage: String, val storageDetail: String, val cpu: String, val charging: String,
    val updated: String, val batteryProgress: Int?, val memoryProgress: Int?, val storageProgress: Int?,
    val model: String = "—", val traffic: String = "—"
) {
    fun metric(field: WidgetField): WidgetMetric = when (field) {
        WidgetField.BATTERY -> WidgetMetric(field.title, battery, charging, batteryProgress)
        WidgetField.TEMPERATURE -> WidgetMetric(field.title, temperature, "Android 電池 API")
        WidgetField.MEMORY -> WidgetMetric(field.title, memory, memoryDetail, memoryProgress)
        WidgetField.STORAGE -> WidgetMetric(field.title, storage, storageDetail, storageProgress)
        WidgetField.CPU -> WidgetMetric(field.title, if (cpu == "—") cpu else "$cpu GHz", "可讀核心最高值")
        WidgetField.TRAFFIC -> WidgetMetric(field.title, traffic, "TrafficStats · 收＋送")
    }
    companion object {
        fun from(snapshot: Snapshot, zone: ZoneId = ZoneId.systemDefault(), model: String = "—", trafficBytes: Long? = null): WidgetPresentation {
            fun gib(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1073741824.0)
            val battery = snapshot.batteryPercent?.takeIf { it in 0..100 }
            val temperature = snapshot.batteryTemperature?.takeIf { it.isFinite() }
                ?.let { String.format(Locale.ROOT, "%.1f °C", it) } ?: "—"
            val cpu = snapshot.cores.mapNotNull { it.currentKHz?.takeIf { value -> value > 0 } }.maxOrNull()
                ?.let { String.format(Locale.ROOT, "%.2f", it / 1_000_000.0) } ?: "—"
            val updated = snapshot.sampledAtMillis?.let {
                "快照 " + DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(zone).format(Instant.ofEpochMilli(it))
            } ?: "尚未取樣 · 點 ↻ 更新"
            return WidgetPresentation(battery?.let { "$it%" } ?: "—", temperature,
                snapshot.memory?.let { "${it.percent}%" } ?: "—",
                snapshot.memory?.let { "${gib(it.used)} / ${gib(it.total)} GiB" } ?: "—",
                snapshot.storage?.let { "${it.percent}%" } ?: "—",
                snapshot.storage?.let { "可用 ${gib(it.available)} GiB" } ?: "—",
                cpu, snapshot.battery.firstOrNull { it.label == "充放電狀態" }?.value ?: "—", updated,
                battery, snapshot.memory?.percent, snapshot.storage?.percent, model,
                trafficBytes?.takeIf { it >= 0 }?.let { "${gib(it)} GiB" } ?: "—")
        }
    }
}
