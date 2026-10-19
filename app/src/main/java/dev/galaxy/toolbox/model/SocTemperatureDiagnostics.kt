package dev.galaxy.toolbox.model

/** Explains an empty summary without inferring hardware absence or a permission error. */
data class SocTemperatureDiagnostics(
    val identified: List<TemperatureReading>,
    val unconfirmed: List<TemperatureReading>,
    val discoveryIssues: List<TemperatureReading>,
    val summary: TemperatureReading?,
    val explanation: String
) {
    companion object {
        fun build(readings: List<TemperatureReading>, nowMs: Long): SocTemperatureDiagnostics {
            val identified = readings.filter { it.sensor.classification.group == TemperatureGroup.SOC }
                .sortedBy { it.sensor.rawName }
            val unconfirmed = readings.filter {
                it.sensor.source in setOf(TemperatureSource.THERMAL, TemperatureSource.HWMON) &&
                    it.sensor.classification.group in setOf(TemperatureGroup.UNKNOWN, TemperatureGroup.OTHER) &&
                    it.sensor.classification.kind in setOf(TemperatureKind.UNKNOWN, TemperatureKind.MEASUREMENT) &&
                    it.sensor.classification.ruleId != "ambiguous-hw-trip"
            }.sortedWith(compareBy<TemperatureReading> { it.sensor.rawName }.thenBy { it.sensor.id })
            val discovery = readings.filter { it.sensor.source == TemperatureSource.DIAGNOSTIC }
            val summary = TemperatureSelection.summary(identified, nowMs).soc
            val explanation = when {
                summary != null -> "使用已辨識 SoC 來源的有效最高測點；不代表整顆晶片的完整溫度。"
                identified.isNotEmpty() -> "已辨識 ${identified.size} 個 SoC 來源，目前無有效摘要。" +
                    identified.groupingBy { it.effectiveStatus(nowMs).title }.eachCount()
                        .entries.joinToString("、", prefix = " ") { "${it.key} ${it.value}" }
                else -> "尚未辨識到可納入摘要的 SoC 來源，不代表手機沒有感測器。其他來源保留原名，未確認前不替代 SoC。"
            }
            return SocTemperatureDiagnostics(identified, unconfirmed, discovery, summary, explanation)
        }
    }
}
