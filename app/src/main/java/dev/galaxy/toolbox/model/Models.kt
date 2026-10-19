package dev.galaxy.toolbox.model

data class Reading(val label: String, val value: String?, val source: String, val detail: String = "",
    val category: HardwareCategory? = null, val group: String? = null)
data class Snapshot(
    val hardware: List<Reading> = emptyList(),
    val battery: List<Reading> = emptyList(),
    val memoryUsed: Float? = null,
    val batteryTemperature: Float? = null,
    val sampledAt: String = "等待取樣",
    val memory: ByteUsage? = null,
    val storage: ByteUsage? = null,
    val cores: List<CpuCoreSample> = emptyList(),
    val batteryPercent: Int? = null,
    val sampledAtMillis: Long? = null,
    val temperatureReadings: List<TemperatureReading> = emptyList(),
    val systemThermal: SystemThermalReading = SystemThermalReading(),
    val temperatureFilesPolled: Boolean = false,
    val chargingPower: ChargingPowerReading = ChargingPowerReading(),
    val gpu: GpuSnapshot = GpuSnapshot()
)

@ConsistentCopyVisibility
data class ByteUsage private constructor(val used: Long, val total: Long) {
    val fraction: Float get() = (used.toDouble() / total).toFloat()
    val percent: Int get() = kotlin.math.round(used.toDouble() / total * 100).toInt()
    val available: Long get() = total - used
    companion object {
        fun of(used: Long, total: Long): ByteUsage? =
            if (total > 0 && used in 0..total) ByteUsage(used, total) else null
    }
}

data class CpuCoreSample(val index: Int, val currentKHz: Long?, val maximumKHz: Long?) {
    val frequencyRatio: Float? get() {
        val current = currentKHz?.takeIf { it > 0 } ?: return null
        val maximum = maximumKHz?.takeIf { it > 0 } ?: return null
        return (current.toDouble() / maximum).toFloat().coerceIn(0f, 1f)
    }
}
enum class ImportedFieldStatus(val title: String) {
    FOUND("已讀取"), MISSING("未找到欄位"), UNSUPPORTED("來源未提供"), INVALID("無有效數值"), CONFLICT("數值衝突")
}
data class Evidence(val key: String, val value: Int?, val lines: List<String>, val note: String,
    val status: ImportedFieldStatus = if (value == null) ImportedFieldStatus.MISSING else ImportedFieldStatus.FOUND)
data class ReportDiagnostics(val sources: List<String> = emptyList(), val batterySections: Int = 0,
    val skippedLongLines: Int = 0, val outsideSectionFields: Int = 0, val omittedEvidenceLines: Int = 0)
data class ParsedBatteryReport(val fields: List<Evidence>, val diagnostics: ReportDiagnostics)
data class ImportedReport(val file: String, val fields: List<Evidence>, val parsedAt: String,
    val diagnostics: ReportDiagnostics? = null)
data class FirmwareQuery(val model: String, val csc: String) {
    companion object {
        fun create(model: String, csc: String): FirmwareQuery {
            val m = model.trim().uppercase(java.util.Locale.ROOT)
            val c = csc.trim().uppercase(java.util.Locale.ROOT)
            require(Regex("SM-[A-Z0-9]{3,12}").matches(m)) { "型號格式應為 SM-S9280；請勿加 /DS" }
            require(Regex("[A-Z0-9]{3}").matches(c)) { "CSC 必須是 3 位英數代碼，例如 BRI、TGY、EUX" }
            return FirmwareQuery(m, c)
        }
    }
    val xmlUrl get() = "https://fota-cloud-dn.ospserver.net/firmware/$csc/$model/version.xml"
    val changelogUrl get() = "https://doc.samsungmobile.com/$model/$csc/doc.html"
}
data class FirmwareResult(val query: FirmwareQuery, val version: String, val fetchedAt: String,
    val sourceUrl: String = query.xmlUrl, val sourceLabel: String = "Samsung 公開 XML latest",
    val releaseDate: String? = null, val securityPatch: String? = null, val note: String? = null)
