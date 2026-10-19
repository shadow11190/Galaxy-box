package dev.galaxy.toolbox.model

import java.util.Locale

enum class TemperatureKind(val title: String) { MEASUREMENT("溫度測量"), THRESHOLD("保護門檻"), PROTECTION_STATE("保護狀態候選"), UNKNOWN("用途待確認") }
enum class TemperatureGroup(val title: String) { CPU("CPU"), SOC("SoC"), GPU("GPU"), BATTERY("電池"), OTHER("其他已辨識測點"), UNKNOWN("未辨識節點"), PROTECTION("保護狀態") }
enum class EvidenceConfidence(val title: String) { HIGH("高 · API／來源定義"), MEDIUM("中 · 驅動標籤與介面"), LOW("低 · 僅線索"), UNKNOWN("未確認") }
enum class TemperatureSource { BATTERY_API, THERMAL, TRIP_POINT, HWMON, KGSL, DIAGNOSTIC }
enum class TemperatureUnit(val title: String, val divisor: Double?) { DECI_C("0.1 °C", 10.0), MILLI_C("0.001 °C", 1000.0), CELSIUS("°C", 1.0), RAW("原始值／單位未確認", null) }
enum class TemperatureReadStatus(val title: String) {
    OK("讀取成功"), PERMISSION_DENIED("權限不足"), NO_DATA("暫無讀值"), UNPARSABLE("格式無法解析"), NOT_FOUND("來源不存在"),
    UNAVAILABLE("無法取得"), UNSUPPORTED("不支援"), STALE("已過期"), DISABLED("來源停用"), FAULT("來源回報故障"), UNIT_UNCONFIRMED("單位未確認")
}

data class TemperatureClassification(
    val kind: TemperatureKind, val group: TemperatureGroup, val title: String,
    val confidence: EvidenceConfidence, val basis: String, val ruleId: String,
    val summaryEligible: Boolean = false, val priority: Int = 100
)
data class TemperatureDescriptor(
    val id: String, val rawName: String, val path: String, val source: TemperatureSource, val declaredUnit: TemperatureUnit,
    val classification: TemperatureClassification, val metadata: Map<String, String> = emptyMap(), val reference: String = ""
)
data class TemperatureReading(
    val sensor: TemperatureDescriptor, val raw: String?, val celsius: Double?, val status: TemperatureReadStatus,
    val attemptedAtMs: Long, val lastSuccessAtMs: Long? = null, val lastGoodCelsius: Double? = null, val statusDetail: String = ""
) {
    fun effectiveStatus(nowMs: Long): TemperatureReadStatus = if (status == TemperatureReadStatus.OK &&
        (lastSuccessAtMs == null || nowMs < lastSuccessAtMs || nowMs - lastSuccessAtMs > FRESH_FOR_MS)) TemperatureReadStatus.STALE else status
    fun validMeasurement(nowMs: Long) = effectiveStatus(nowMs) == TemperatureReadStatus.OK &&
        sensor.classification.kind == TemperatureKind.MEASUREMENT && celsius?.isFinite() == true
    fun usableInSummary(nowMs: Long) = sensor.classification.summaryEligible && validMeasurement(nowMs)
    fun displayValue(nowMs: Long): String {
        val effective = effectiveStatus(nowMs)
        if (effective != TemperatureReadStatus.OK) return effective.title
        return if (celsius != null && sensor.classification.kind in listOf(TemperatureKind.MEASUREMENT, TemperatureKind.THRESHOLD))
            String.format(Locale.ROOT, "%.1f °C", celsius) else raw?.let { "$it · 原始值" } ?: "無可用資料"
    }
    companion object { const val FRESH_FOR_MS = 10_000L }
}
data class TemperaturePoint(val atMs: Long, val celsius: Double?)
data class TemperatureSummary(val battery: TemperatureReading?, val cpu: TemperatureReading?, val gpu: TemperatureReading?, val soc: TemperatureReading? = null)
object TemperatureSelection {
    fun summary(readings: List<TemperatureReading>, nowMs: Long): TemperatureSummary {
        val valid = readings.filter { it.usableInSummary(nowMs) }
        // Battery is always the Android broadcast. Other battery sources never replace or average it.
        val battery = valid.filter { it.sensor.source == TemperatureSource.BATTERY_API }.minByOrNull { it.sensor.classification.priority }
        val cpu = valid.filter { it.sensor.classification.group == TemperatureGroup.CPU }.maxWithOrNull(compareBy<TemperatureReading> { it.celsius }.thenBy { it.sensor.id })
        // Verified model/source rules first, corroborated thermal next, corroborated hwmon last. KGSL has no default approval.
        val gpu = valid.filter { it.sensor.classification.group == TemperatureGroup.GPU }
            .sortedWith(compareBy<TemperatureReading> { it.sensor.classification.priority }.thenBy { it.sensor.rawName }.thenBy { it.sensor.id }).firstOrNull()
        val soc = valid.filter { it.sensor.classification.group == TemperatureGroup.SOC }.maxWithOrNull(compareBy<TemperatureReading> { it.celsius }.thenBy { it.sensor.id })
        return TemperatureSummary(battery, cpu, gpu, soc)
    }
}
data class SystemThermalReading(val level: Int? = null, val status: TemperatureReadStatus = TemperatureReadStatus.UNAVAILABLE, val atMs: Long? = null,
    val lastSuccessAtMs: Long? = if (status == TemperatureReadStatus.OK) atMs else null,
    val headroom: ThermalHeadroomReading = ThermalHeadroomReading()) {
    fun title(nowMs: Long): String {
        if (status != TemperatureReadStatus.OK) return status.title
        if (atMs == null || nowMs < atMs || nowMs - atMs > TemperatureReading.FRESH_FOR_MS) return TemperatureReadStatus.STALE.title
        return labels.getOrNull(level ?: -1) ?: "無法取得"
    }
    companion object {
        const val SOURCE = "PowerManager.currentThermalStatus · Android 10 / API 29+"
        const val EXPLANATION = "系統回報的熱壓力等級，不代表已確認所有元件均未降頻。"
        val labels = listOf("未回報熱壓力", "輕度", "中度", "嚴重", "危急", "緊急", "關機等級")
    }
}

data class ThermalHeadroomReading(val value: Float? = null, val status: TemperatureReadStatus = TemperatureReadStatus.UNAVAILABLE,
    val atMs: Long? = null, val thresholds: Map<Int, Float> = emptyMap()) {
    fun display(nowMs: Long): String {
        if (status != TemperatureReadStatus.OK) return status.title
        if (atMs == null || nowMs < atMs || nowMs - atMs > 30_000L) return TemperatureReadStatus.STALE.title
        return value?.takeIf { it.isFinite() && it >= 0f }?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "無可用資料"
    }
    companion object {
        const val SOURCE = "PowerManager.getThermalHeadroom(10) · Android 11 / API 30+"
        const val EXPLANATION = "系統對未來 10 秒的熱壓力預估，非溫度或剩餘百分比；1.0 對應嚴重熱壓力參考點，可超過 1.0。剛開始可能先回報當前值。"
    }
}
