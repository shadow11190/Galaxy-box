package dev.galaxy.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.model.*
import java.time.Instant

@Composable internal fun GpuSourcesCard(snapshot: GpuSnapshot, now: Long, temperature: TemperatureReading?, onRescan: () -> Unit,
    onReading: (Reading) -> Unit, onTemperature: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    InfoCard("GPU 一般讀取 · 免 Root／ADB") {
        Text(if (!snapshot.fresh(now)) "等待最新取樣" else if (snapshot.selected.isEmpty()) "一般來源尚無可用數據" else "已取得 ${snapshot.selected.size} 類即時數據", color = Mint)
        temperature?.takeIf { it.usableInSummary(now) }?.let { t ->
            TextButton(onClick = { onReading(Reading("GPU 溫度", t.displayValue(now), t.sensor.path,
                "${t.sensor.rawName} · ${t.sensor.classification.basis}\n讀取 epoch ms ${t.attemptedAtMs}")) }) { Text("GPU 溫度 ${t.displayValue(now)} · ${t.sensor.rawName}") }
        }
        TextButton(onClick = onTemperature) { Text("查看溫度來源") }
        OutlinedButton(onClick = onRescan) { Text("重新偵測一般 GPU 來源") }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收合 GPU 來源診斷" else "查看 GPU 來源診斷") }
        if (expanded) {
            snapshot.atMs?.let { Text("取樣 ${formatImportTime(Instant.ofEpochMilli(it).toString())}", color = Muted, style = MaterialTheme.typography.bodySmall) }
            Text("以下是各來源最後一次嘗試，不代表每個來源本次都有採用。", color = Muted, style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                snapshot.attempts.forEach { a ->
                    TextButton(onClick = { onReading(Reading(a.metric?.title ?: "來源探索", a.raw, a.path,
                        "${a.status.title} · 單位 ${a.unit.title}\n最後嘗試 ${formatImportTime(Instant.ofEpochMilli(a.atMs).toString())}\n${a.basis}")) }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(a.path, style = MaterialTheme.typography.labelSmall, color = Muted)
                            Text("${a.status.title} · ${a.unit.title}" + if (snapshot.selected.any { it.path == a.path }) " · 本次採用" else "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            TextButton(onClick = {
                val text = buildString {
                    appendLine("Galaxy box GPU 一般讀取診斷 · epoch ms ${snapshot.atMs}")
                    snapshot.attempts.forEach { a -> appendLine("${a.path} | ${a.status.name} | ${a.unit.name} | raw=${a.raw?.take(256)} | attempted=${a.atMs} | selected=${snapshot.selected.any { it.path == a.path }}") }
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("GPU 一般讀取診斷", text))
            }) { Text("複製 GPU 來源診斷") }
        }
    }
}
