package dev.galaxy.toolbox.ui

import dev.galaxy.toolbox.ToolboxState
import dev.galaxy.toolbox.model.Reading
import dev.galaxy.toolbox.model.TemperatureReading
import dev.galaxy.toolbox.model.TemperatureSelection
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal data class ReportBuildFields(
    val manufacturer: String?,
    val brand: String?,
    val model: String?,
    val device: String?,
    val product: String?,
    val board: String?,
    val socManufacturer: String?,
    val socModel: String?,
    val androidRelease: String?,
    val sdk: Int,
)

/** A small, explicit allowlist keeps network identifiers, serials, and imported reports out. */
internal object DeviceDiagnosticReport {
    private val hardwareLabels = setOf(
        "裝置名稱", "裝置型號", "地區版本", "品牌 / 製造商", "內部裝置代號",
        "產品代號", "主機板代號", "處理器名稱", "處理器製造商", "SoC 原始型號",
        "CPU 品牌／架構（規格）", "CPU 製程（規格）", "GPU 名稱",
        "GPU 型號（晶片規格）", "Android / API", "安全性更新日期",
    )

    fun render(state: ToolboxState, build: ReportBuildFields, appVersion: String, exportedAtMs: Long): String {
        val snapshot = state.snapshot
        val sampleAt = snapshot.sampledAtMillis
        val sampleStatus = when {
            state.monitorError != null -> "sample_failed"
            sampleAt == null -> "not_sampled"
            exportedAtMs < sampleAt || exportedAtMs - sampleAt > 10_000L -> "stale"
            else -> "current"
        }
        val summaries = TemperatureSelection.summary(state.temperatures, exportedAtMs)
        return JSONObject().apply {
            put("schema", "galaxy-box-device-diagnostic-v1")
            put("appVersion", appVersion)
            put("exportedAt", Instant.ofEpochMilli(exportedAtMs).toString())
            put("exportedAtMs", exportedAtMs)
            put("scope", "本機唯讀裝置與監控快照；不含 Bugreport、序號、網路識別與帳號資訊")
            put("androidBuild", JSONObject().apply {
                put("manufacturer", build.manufacturer.json())
                put("brand", build.brand.json())
                put("model", build.model.json())
                put("device", build.device.json())
                put("product", build.product.json())
                put("board", build.board.json())
                put("socManufacturer", build.socManufacturer.json())
                put("socModel", build.socModel.json())
                put("androidRelease", build.androidRelease.json())
                put("sdk", build.sdk)
                put("source", "Android Build / Build.VERSION")
                put("readAtMs", exportedAtMs)
            })
            put("hardwareLoadedAtMs", state.hardwareLoadedAtMs.json())
            put("hardware", JSONArray().apply {
                state.hardware.filter { it.label in hardwareLabels }.forEach { put(it.toJson(state.hardwareLoadedAtMs)) }
            })
            put("monitor", JSONObject().apply {
                put("sampledAtMs", sampleAt.json())
                put("status", sampleStatus)
                put("battery", JSONArray().apply {
                    snapshot.battery.forEach { put(it.toJson(sampleAt)) }
                })
                put("memory", JSONObject().apply {
                    put("usedBytes", snapshot.memory?.used.json())
                    put("totalBytes", snapshot.memory?.total.json())
                    put("source", "ActivityManager.MemoryInfo")
                })
                put("storage", JSONObject().apply {
                    put("usedBytes", snapshot.storage?.used.json())
                    put("totalBytes", snapshot.storage?.total.json())
                    put("source", "StatFs · App 所在分割區")
                })
                put("cpuCores", JSONArray().apply {
                    snapshot.cores.forEach { core ->
                        put(JSONObject().apply {
                            put("index", core.index)
                            put("currentKHz", core.currentKHz.json())
                            put("maximumKHz", core.maximumKHz.json())
                            put("source", "/sys/devices/system/cpu/cpu${core.index}/cpufreq")
                        })
                    }
                })
                put("systemThermal", JSONObject().apply {
                    put("level", snapshot.systemThermal.level.json())
                    put("status", snapshot.systemThermal.status.name)
                    put("readAtMs", snapshot.systemThermal.atMs.json())
                    put("source", "PowerManager.currentThermalStatus")
                })
            })
            put("temperatureSummary", JSONObject().apply {
                put("battery", summaries.battery.toJson(exportedAtMs))
                put("cpu", summaries.cpu.toJson(exportedAtMs))
                put("soc", summaries.soc.toJson(exportedAtMs))
                put("gpu", summaries.gpu.toJson(exportedAtMs))
                put("note", "僅列可用於摘要的來源；完整節點及讀取失敗原因可在溫度頁另行匯出。")
            })
        }.toString(2)
    }

    private fun Reading.toJson(readAtMs: Long?) = JSONObject().apply {
        put("label", label)
        put("value", value.json())
        put("status", if (value == null) "unknown" else "available")
        put("source", source)
        put("detail", detail)
        put("readAtMs", readAtMs.json())
    }

    private fun TemperatureReading?.toJson(nowMs: Long): Any =
        if (this == null) JSONObject.NULL else JSONObject().apply {
            put("name", sensor.rawName)
            put("path", sensor.path)
            put("source", sensor.source.name)
            put("celsius", celsius?.takeIf { it.isFinite() }.json())
            put("raw", raw.json())
            put("status", effectiveStatus(nowMs).name)
            put("lastSuccessAtMs", lastSuccessAtMs.json())
        }

    private fun Any?.json(): Any = this ?: JSONObject.NULL
}
