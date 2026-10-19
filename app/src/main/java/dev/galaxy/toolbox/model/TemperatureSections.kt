package dev.galaxy.toolbox.model

data class TemperatureSection(val id: String, val title: String, val readings: List<TemperatureReading>, val advanced: Boolean)
object TemperatureSections {
    fun build(readings: List<TemperatureReading>, nowMs: Long): List<TemperatureSection> {
        val ordered = linkedMapOf(
            "cpu" to ("CPU" to false), "soc" to ("SoC" to false), "gpu" to ("GPU" to false), "battery" to ("電池" to false), "other" to ("其他已辨識測點" to false),
            "unknown" to ("未辨識節點／用途待確認" to true), "protection" to ("保護狀態候選" to true),
            "ambiguous" to ("疑似門檻／用途待確認" to true), "threshold" to ("已確認門檻欄位" to true), "invalid" to ("無有效讀值／已過期" to true)
        )
        val grouped = readings.groupBy { reading ->
            val c = reading.sensor.classification
            when {
                reading.effectiveStatus(nowMs) != TemperatureReadStatus.OK -> "invalid"
                c.kind == TemperatureKind.THRESHOLD -> "threshold"
                c.kind == TemperatureKind.PROTECTION_STATE -> "protection"
                c.ruleId == "ambiguous-hw-trip" -> "ambiguous"
                c.kind == TemperatureKind.UNKNOWN -> "unknown"
                c.group == TemperatureGroup.CPU -> "cpu"
                c.group == TemperatureGroup.SOC -> "soc"
                c.group == TemperatureGroup.GPU -> "gpu"
                c.group == TemperatureGroup.BATTERY -> "battery"
                c.group == TemperatureGroup.OTHER -> "other"
                else -> "unknown"
            }
        }
        return ordered.map { (id, info) -> TemperatureSection(id, info.first, grouped[id].orEmpty().sortedBy { it.sensor.rawName }, info.second) }
    }
}
