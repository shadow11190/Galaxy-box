package dev.galaxy.toolbox.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.galaxy.toolbox.ToolboxState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun TemperatureExport(state: ToolboxState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(state)
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val snapshot = latest
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        val exportedAt = System.currentTimeMillis()
                        val body = JSONObject().apply {
                            put("schema", "galaxy-box-temperature-v1")
                            @Suppress("DEPRECATION")
                            put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
                            put("model", Build.MODEL)
                            put("sdk", Build.VERSION.SDK_INT)
                            put("socManufacturer", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else JSONObject.NULL)
                            put("socModel", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else JSONObject.NULL)
                            put("exportedAtMs", exportedAt)
                            put("sampleTimeMs", snapshot.temperatureNowMs)
                            put("systemThermalStatus", snapshot.snapshot.systemThermal.level ?: JSONObject.NULL)
                            put("headroom10s", snapshot.snapshot.systemThermal.headroom.value ?: JSONObject.NULL)
                            put("headroomReadAtMs", snapshot.snapshot.systemThermal.headroom.atMs ?: JSONObject.NULL)
                            put("sensors", JSONArray().apply {
                                snapshot.temperatures.forEach { reading ->
                                    put(JSONObject().apply {
                                        val sensor = reading.sensor
                                        val c = sensor.classification
                                        put("id", sensor.id); put("rawName", sensor.rawName); put("path", sensor.path)
                                        put("source", sensor.source.name); put("raw", reading.raw ?: JSONObject.NULL)
                                        put("sourceUnit", sensor.declaredUnit.name); put("celsius", reading.celsius ?: JSONObject.NULL)
                                        put("kind", c.kind.name); put("group", c.group.name); put("rule", c.ruleId)
                                        put("confidence", c.confidence.name); put("basis", c.basis)
                                        put("readStatus", reading.status.name); put("effectiveStatus", reading.effectiveStatus(exportedAt).name)
                                        put("statusDetail", reading.statusDetail); put("attemptedAtMs", reading.attemptedAtMs)
                                        put("lastSuccessAtMs", reading.lastSuccessAtMs ?: JSONObject.NULL)
                                        put("summaryEligible", reading.usableInSummary(exportedAt))
                                        put("metadata", JSONObject(sensor.metadata)); put("reference", sensor.reference)
                                    })
                                }
                            })
                        }.toString(2)
                        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(body) }
                    }
                    result = "已匯出 ${snapshot.temperatures.size} 個來源；僅儲存至你選取的位置。"
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { result = "匯出失敗：${error.message ?: "無法寫入"}" }
                finally { busy = false }
            }
        }
    }
    Column {
        OutlinedButton(enabled = !busy && state.temperatures.isNotEmpty(), onClick = { launcher.launch("Galaxy-box-temperature.json") }) {
            Text(if (busy) "匯出中…" else "匯出溫度診斷")
        }
        Text("包含型號、測點、路徑、原始值與讀取狀態，方便核對手機的實際來源；不含完整 Bugreport。", color = Muted, style = MaterialTheme.typography.bodySmall)
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
