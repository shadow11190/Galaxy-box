package dev.galaxy.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun ImportedBatteryResults(report: ImportedReport) {
    val found = report.fields.count { it.value != null }
    var details by rememberSaveable(report.parsedAt) { mutableStateOf(false) }
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    var copied by remember(report.parsedAt) { mutableStateOf<String?>(null) }
    GalaxyText(if (found > 0) "已取得 $found 項電池資料" else "報告已讀取，未取得可用電池資料", fontWeight = FontWeight.Medium)
    GalaxyText("匯入時間 ${formatImportTime(report.parsedAt)}", color = Muted, style = GalaxyTypography.bodySmall)
    GalaxyText("這是匯入報告的快照；來源裝置與產生時間未驗證。", color = Muted, style = GalaxyTypography.bodySmall)
    report.fields.forEach { field ->
        var expanded by rememberSaveable(report.parsedAt, field.key) { mutableStateOf(false) }
        GalaxyDivider(color = Muted.copy(alpha = .15f))
        if (LocalDensity.current.fontScale > 1.3f) {
            GalaxyText(field.key, fontWeight = FontWeight.Medium)
            GalaxyText(field.value?.toString() ?: "未知", style = GalaxyTypography.titleLarge)
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GalaxyText(field.key, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            GalaxyText(field.value?.toString() ?: "未知", style = GalaxyTypography.titleLarge)
        }
        GalaxyText(if (field.value == null) field.status.title else field.note, color = Muted, style = GalaxyTypography.bodySmall)
        if (field.lines.isNotEmpty()) {
            GalaxyTextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics {
                contentDescription = AppText.translate(if (expanded) "收合 ${field.key} 原始依據" else "查看 ${field.key} 原始依據", language)
            }) { GalaxyText(if (expanded) "收合來源" else "查看來源") }
            if (expanded) {
                if (field.value == null) GalaxyText(field.note, color = Muted, style = GalaxyTypography.bodySmall)
                SelectionContainer { GalaxyText(field.lines.joinToString("\n"), style = GalaxyTypography.bodySmall) }
            }
        }
    }
    GalaxyTextButton(onClick = { details = !details }) { GalaxyText(if (details) "收合解析詳細資訊" else "查看解析詳細資訊") }
    if (details) {
        report.diagnostics?.let { d ->
            GalaxyText("已讀取 ${d.sources.size} 份文字報告 · 辨識 ${d.batterySections} 個電池區段", color = Muted, style = GalaxyTypography.bodySmall)
            if (d.skippedLongLines > 0) GalaxyText("已略過 ${d.skippedLongLines} 條電池區段以外的超長行；這不等於電池欄位讀取失敗。", color = Muted, style = GalaxyTypography.bodySmall)
            if (d.outsideSectionFields > 0) GalaxyText("另有 ${d.outsideSectionFields} 行電池欄位位於未辨識或其他服務區段，未採用其值。", color = Muted, style = GalaxyTypography.bodySmall)
            if (d.omittedEvidenceLines > 0) GalaxyText("原始依據僅保留每欄前 16 行；另有 ${d.omittedEvidenceLines} 行未列出，衝突判定仍涵蓋完整掃描。", color = Muted, style = GalaxyTypography.bodySmall)
            d.sources.forEach { GalaxyText(it, color = Muted, style = GalaxyTypography.labelSmall) }
        }
        GalaxyText("接受數字及 Samsung 方括號格式。ASOC 是廠商原始值；使用量不換算成循環，缺值不推估。", color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyText("可定位的 ZIP 直接讀取；串流來源才建立可取消的私有暫存。支援 ZIP64，文字上限 16 GiB。", color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyOutlinedButton(onClick = {
            copied = runCatching {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Galaxy box 電池解析摘要", batteryImportSummary(report)))
                "已複製解析摘要，不含完整 Bugreport。"
            }.getOrElse { "無法複製解析摘要。" }
        }) { GalaxyText("複製解析摘要") }
        copied?.let { GalaxyText(it, color = Muted, style = GalaxyTypography.bodySmall) }
    }
}

internal fun formatImportTime(value: String, zone: ZoneId = ZoneId.systemDefault()): String = runCatching {
    Instant.parse(value).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
}.getOrElse { "時間格式無法辨識" }

internal fun batteryImportSummary(report: ImportedReport): String = buildString {
    appendLine("Galaxy box 電池解析摘要")
    appendLine("匯入時間 ${formatImportTime(report.parsedAt)}；來源裝置未驗證")
    report.diagnostics?.let { d ->
        appendLine("報告 ${d.sources.size} 份；電池區段 ${d.batterySections}；略過超長行 ${d.skippedLongLines}；區段外欄位 ${d.outsideSectionFields}")
    }
    report.fields.forEach { field ->
        appendLine("${field.key}：${field.value ?: "未知"}（${field.status.title}）")
        field.lines.forEach { appendLine(it) }
    }
}
