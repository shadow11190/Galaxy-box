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
    InfoCard("GPU 即時監控") {
        GalaxyText(if (!snapshot.fresh(now)) "等待最新取樣" else if (snapshot.selected.isEmpty()) "一般來源尚無可用數據" else "已取得 ${snapshot.selected.size} 類即時數據", color = Mint)
        temperature?.takeIf { it.usableInSummary(now) }?.let { t ->
            GalaxyTextButton(onClick = { onReading(Reading("GPU 溫度", t.displayValue(now), t.sensor.path,
                "${t.sensor.rawName} · ${t.sensor.classification.basis}\n讀取 epoch ms ${t.attemptedAtMs}")) }) { GalaxyText("GPU 溫度 ${t.displayValue(now)} · ${t.sensor.rawName}") }
        }
        GalaxyTextButton(onClick = onTemperature) { GalaxyText("查看溫度來源") }
        GalaxyOutlinedButton(onClick = onRescan) { GalaxyText("重新偵測一般 GPU 來源") }
        GalaxyTextButton(onClick = { expanded = !expanded }) { GalaxyText(if (expanded) "收合 GPU 來源診斷" else "查看 GPU 來源診斷") }
        if (expanded) {
            snapshot.atMs?.let { GalaxyText("取樣 ${formatImportTime(Instant.ofEpochMilli(it).toString())}", color = Muted, style = GalaxyTypography.bodySmall) }
            GalaxyText("以下是各來源最後一次嘗試，不代表每個來源本次都有採用。", color = Muted, style = GalaxyTypography.bodySmall)
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                snapshot.attempts.forEach { a ->
                    GalaxyTextButton(onClick = { onReading(Reading(a.metric?.title ?: "來源探索", a.raw, a.path,
                        "${a.status.title} · 單位 ${a.unit.title}\n最後嘗試 ${formatImportTime(Instant.ofEpochMilli(a.atMs).toString())}\n${a.basis}")) }) {
                        Column(Modifier.fillMaxWidth()) {
                            GalaxyText(a.path, style = GalaxyTypography.labelSmall, color = Muted)
                            GalaxyText("${a.status.title} · ${a.unit.title}" + if (snapshot.selected.any { it.path == a.path }) " · 本次採用" else "", style = GalaxyTypography.bodySmall)
                        }
                    }
                }
            }
            GalaxyTextButton(onClick = {
                val title = AppText.forContext(context, "GPU 一般讀取診斷")
                val text = buildString {
                    appendLine("Galaxy box $title · epoch ms ${snapshot.atMs}")
                    snapshot.attempts.forEach { a -> appendLine("${a.path} | ${a.status.name} | ${a.unit.name} | raw=${a.raw?.take(256)} | attempted=${a.atMs} | selected=${snapshot.selected.any { it.path == a.path }}") }
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, text))
            }) { GalaxyText("複製 GPU 來源診斷") }
        }
    }
}
