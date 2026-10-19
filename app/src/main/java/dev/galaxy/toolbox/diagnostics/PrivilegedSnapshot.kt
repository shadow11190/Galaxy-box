package dev.galaxy.toolbox.diagnostics

import dev.galaxy.toolbox.data.BugreportParser
import dev.galaxy.toolbox.data.GpuMetrics
import dev.galaxy.toolbox.data.TemperatureFileResult
import dev.galaxy.toolbox.model.*
import org.json.JSONObject
import java.util.Locale

data class PrivilegedSnapshot(val kind: ProbeKind, val atMs: Long, val fields: List<Reading>, val diagnostics: List<String>)

object PrivilegedSnapshotParser {
    fun parse(payload: String, expected: ProbeKind): PrivilegedSnapshot {
        require(payload.length <= 180_000) { "診斷回應過大" }
        val json = JSONObject(payload)
        require(json.getInt("schema") == 1 && json.getString("kind") == expected.name) { "診斷回應格式不符" }
        require(json.getInt("uid") == 2000) { "僅接受 ADB 模式的 Shizuku 回應" }
        val at = json.getLong("at"); require(at > 0) { "缺少讀取時間" }
        val array = json.getJSONArray("files")
        val allowed = ProbeContracts.files(expected).associateBy { it.path }
        require(array.length() <= allowed.size) { "節點數量不符" }
        val results = linkedMapOf<String, TemperatureFileResult>()
        repeat(array.length()) { i ->
            val item = array.getJSONObject(i); val path = item.getString("path")
            require(path in allowed && path !in results) { "未允許或重複的來源" }
            val raw = if (item.isNull("raw")) null else item.getString("raw").also { require(it.length <= 4096) }
            results[path] = TemperatureFileResult(raw, TemperatureReadStatus.valueOf(item.getString("status")))
        }
        val fields = mutableListOf<Reading>()
        val diagnostics = mutableListOf<String>()
        for ((path, result) in results) {
            val spec = allowed.getValue(path)
            if (spec.value == ProbeValue.TYPE) continue
            if (result.status != TemperatureReadStatus.OK) { diagnostics += "$path · ${result.status.title}"; continue }
            if (expected == ProbeKind.BATTERY) {
                val type = results[path.substringBeforeLast('/') + "/type"]
                if (type?.status != TemperatureReadStatus.OK || !type.raw.equals("Battery", true)) {
                    diagnostics += "$path · 電源類型未確認為 Battery，未採用"; continue
                }
            }
            val value = format(spec.value, result.raw)
            if (value == null) { diagnostics += "$path · 格式或數值無效 · 原始值 ${result.raw?.take(160)}"; continue }
            val unit = when (spec.value) {
                ProbeValue.CHARGE_UAH -> "Linux power_supply：µAh → mAh；驅動設計值或最近滿充門檻，非本 App 實測。"
                ProbeValue.ENERGY_UWH -> "Linux power_supply：µWh → mWh，不與電荷容量混算。"
                ProbeValue.SAMSUNG_ASOC -> "Samsung ASOC 原始值，非本 App 實測 SOH。"
                ProbeValue.SAMSUNG_RAW -> "非標準廠商欄位，單位未確認；不除以 100 或當作實際循環次數。"
                ProbeValue.CYCLES -> "標準 cycle_count 原始回報；保留 0，不與其他來源平均或覆蓋。"
                ProbeValue.GPU_HZ -> "devfreq：Hz → MHz；手動快照，非目前持續頻率或 GPU 使用率。"
                ProbeValue.GPU_BUSY -> "KGSL busy / total 驅動取樣區間比，非 FPS。"
                else -> ""
            }
            fields += Reading("${spec.label} · ${if (expected == ProbeKind.BATTERY) path.split('/')[4] else "Shizuku"}", value,
                "Shizuku · ADB UID 2000\n$path", "$unit\n原始值 ${result.raw}")
        }
        if (expected == ProbeKind.BATTERY) {
            val dump = json.getJSONObject("dump")
            if (dump.getString("status") == "OK") {
                val text = dump.getString("text"); require(text.length <= 48 * 1024)
                val report = runCatching { BugreportParser.parseReport(text.byteInputStream()) }.getOrNull()
                if (report == null) diagnostics += "dumpsys battery · 報告格式無法解析，保留其他可用來源"
                report?.fields?.forEach { evidence ->
                    if (evidence.value != null) fields += Reading("${evidence.key} · 系統報告", evidence.value.toString(),
                        "Shizuku · ADB UID 2000 · dumpsys battery", evidence.note + "\n" + evidence.lines.joinToString("\n"))
                    else diagnostics += "dumpsys battery · ${evidence.key} · ${evidence.status.title}"
                }
            } else diagnostics += "dumpsys battery · ${dump.getString("status")}"
        }
        return PrivilegedSnapshot(expected, at, fields, diagnostics)
    }
    internal fun format(kind: ProbeValue, raw: String?): String? {
        if (kind == ProbeValue.GPU_BUSY) return GpuMetrics.busyFraction(raw)?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) }
        val n = raw?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return when (kind) {
            ProbeValue.CHARGE_UAH -> n.takeIf { it > 0 }?.let { String.format(Locale.ROOT, "%.1f mAh", it / 1000.0) }
            ProbeValue.ENERGY_UWH -> n.takeIf { it > 0 }?.let { String.format(Locale.ROOT, "%.1f mWh", it / 1000.0) }
            ProbeValue.GPU_HZ -> String.format(Locale.ROOT, "%.0f MHz", n / 1_000_000.0)
            ProbeValue.SAMSUNG_ASOC -> n.takeIf { it <= 100 }?.toString()
            ProbeValue.CYCLES -> "$n 次（來源回報）"
            ProbeValue.SAMSUNG_RAW -> n.toString()
            else -> null
        }
    }
}
