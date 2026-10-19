package dev.galaxy.toolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.ToolboxState
import dev.galaxy.toolbox.data.TemperatureHistory
import dev.galaxy.toolbox.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun LazyListScope.temperatureItems(state: ToolboxState, expanded: List<String>, onToggle: (String) -> Unit,
    onSensor: (String) -> Unit, onRescan: () -> Unit) {
    item { TemperatureSummaryCard(state, onSensor) }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRescan) { Text("重新偵測溫度來源") }
            TemperatureExport(state)
            Text("每 2 秒取樣；離開總覽／溫度／GPU 頁即停止檔案輪詢。點按測點查看原始資料。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    val sections = TemperatureSections.build(state.temperatures, state.temperatureNowMs)
    item { Text("分類感測器", style = MaterialTheme.typography.titleLarge) }
    sections.filterNot { it.advanced }.forEach { section ->
        temperatureSection(section, section.id in expanded, onToggle, onSensor, state.temperatureNowMs)
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("進階診斷", style = MaterialTheme.typography.titleLarge)
            Text("原始狀態、門檻與未辨識項目不參與最高溫計算；數字不一定代表 °C。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    sections.filter { it.advanced }.forEach { section ->
        temperatureSection(section, section.id in expanded, onToggle, onSensor, state.temperatureNowMs)
    }
}

private fun LazyListScope.temperatureSection(section: TemperatureSection, expanded: Boolean, onToggle: (String) -> Unit,
    onSensor: (String) -> Unit, nowMs: Long) {
    item(key = "temperature-section-${section.id}") {
        GlassCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (expanded) "收合${section.title}" else "展開${section.title}") { onToggle(section.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(section.title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text("${section.readings.size}", color = Muted)
                Text(if (expanded) "  −" else "  ＋", color = Muted)
            }
        }
    }
    if (expanded) {
        if (section.readings.isEmpty()) item { Text("無可用資料", color = Muted, style = MaterialTheme.typography.bodySmall) }
        items(section.readings.chunked(2), key = { "temperature-${it.first().sensor.id}" }) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { reading -> Box(Modifier.weight(1f)) { TemperatureCompactRow(reading, nowMs) { onSensor(reading.sensor.id) } } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable internal fun TemperatureSummaryCard(state: ToolboxState, onSensor: (String) -> Unit = {}, onAll: (() -> Unit)? = null) {
    val summary = TemperatureSelection.summary(state.temperatures, state.temperatureNowMs)
    var systemDetail by remember { mutableStateOf(false) }
    var socDetail by remember { mutableStateOf(false) }
    val socDiagnostics = SocTemperatureDiagnostics.build(state.temperatures, state.temperatureNowMs)
    var selected by rememberSaveable { mutableStateOf("CPU") }
    InfoCard("溫度摘要") {
        val tiles = listOf(Triple("CPU", "CPU 測點最高溫", summary.cpu), Triple("SoC", "SoC 相關測點最高溫", summary.soc),
            Triple("GPU", "GPU", summary.gpu), Triple("電池", "電池", summary.battery))
        val columns = if (LocalConfiguration.current.fontScale > 1.3f) 1 else 2
        tiles.chunked(columns).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                group.forEach { (component, title, reading) ->
                    Box(Modifier.weight(1f)) { SummaryTemperature(title, reading, state.temperatureNowMs, selected == component) { selected = component } }
                }
            }
        }
        val active = tiles.first { it.first == selected }.third
        Column(Modifier.fillMaxWidth().background(Navy.copy(alpha = .45f), MaterialTheme.shapes.medium).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$selected · 溫度趨勢", style = MaterialTheme.typography.titleSmall)
            if (active == null) Text(if (selected == "SoC") socDiagnostics.explanation else "無可用資料；等待此元件的有效來源。", color = Muted, style = MaterialTheme.typography.bodySmall)
            else {
                Text("${active.sensor.rawName} · ${sourceTitle(active.sensor.source)}", color = Muted, style = MaterialTheme.typography.labelSmall)
                TemperatureTrend(state.temperatureHistory[active.sensor.id].orEmpty(), state.temperatureNowMs)
                Text("各測點獨立記錄，來源切換不拼接曲線。", color = Muted, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { onSensor(active.sensor.id) }) { Text("查看原始資料與來源") }
            }
        }
        if (summary.soc?.sensor?.classification?.ruleId == "soc-s26-video-category")
            Text("SoC 採 video 影音相關測點，與你提供的 DevCheck 分類對應；實體位置未確認。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { socDetail = true }) { Text("查看 SoC 來源與讀取狀態") }
        HorizontalDivider(color = Muted.copy(alpha = .12f))
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看系統熱狀態原始資料") { systemDetail = true }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("系統熱狀態", Modifier.weight(1f), color = Muted)
            Text(state.snapshot.systemThermal.title(state.temperatureNowMs), Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Text("Android 熱狀態 API · 更新 ${temperatureTime(state.snapshot.systemThermal.atMs)}", color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(SystemThermalReading.EXPLANATION, color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { systemDetail = true }, verticalAlignment = Alignment.CenterVertically) {
            Text("熱壓力預估 · 10 秒", Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(state.snapshot.systemThermal.headroom.display(state.temperatureNowMs), fontWeight = FontWeight.Medium)
        }
        Text("更新 ${temperatureTime(state.snapshot.systemThermal.headroom.atMs)} · 每 15 秒讀取", color = Muted, style = MaterialTheme.typography.labelSmall)
        Text("CPU／SoC 分開統計，不代表所有核心或整顆晶片。點按卡片切換趨勢；清單可查看原始資料。", color = Muted, style = MaterialTheme.typography.bodySmall)
        onAll?.let { action -> TextButton(onClick = action) { Text("查看感測器與進階診斷") } }
    }
    if (systemDetail) SystemThermalDetailSheet(state.snapshot.systemThermal, state.temperatureNowMs) { systemDetail = false }
    if (socDetail) SocSourceSheet(state, socDiagnostics, { socDetail = false }, { id -> socDetail = false; onSensor(id) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SocSourceSheet(state: ToolboxState, diagnostics: SocTemperatureDiagnostics,
    onDismiss: () -> Unit, onSensor: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("SoC 來源診斷", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(diagnostics.explanation)
                Text("CPU、GPU、電池與保護節點不拿來補 SoC。點按來源可查看分類依據與驅動設定。",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            item { Text("已辨識 SoC 來源 · ${diagnostics.identified.size}", fontWeight = FontWeight.Medium) }
            items(diagnostics.identified, key = { "soc-known-${it.sensor.id}" }) { reading ->
                SocSourceRow(reading, state.temperatureNowMs) { onSensor(reading.sensor.id) }
            }
            item {
                Text("其他來源 · 未確認為 SoC · ${diagnostics.unconfirmed.size}", fontWeight = FontWeight.Medium)
                Text("此清單供對照原始名稱，並非已確認的 SoC 測點，不納入 SoC 摘要。", color = Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
            items(diagnostics.unconfirmed, key = { "soc-other-${it.sensor.id}" }) { reading ->
                SocSourceRow(reading, state.temperatureNowMs) { onSensor(reading.sensor.id) }
            }
            if (diagnostics.discoveryIssues.isNotEmpty()) {
                item { Text("來源探索記錄", fontWeight = FontWeight.Medium) }
                items(diagnostics.discoveryIssues, key = { it.sensor.id }) { reading ->
                    SocSourceRow(reading, state.temperatureNowMs) { onSensor(reading.sensor.id) }
                }
            }
            item { TemperatureExport(state) }
            item { TextButton(onClick = onDismiss) { Text("關閉") } }
        }
    }
}

@Composable private fun SocSourceRow(reading: TemperatureReading, nowMs: Long, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reading.sensor.rawName, Modifier.weight(1f))
            Text(reading.displayValue(nowMs), Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Text("${sourceTitle(reading.sensor.source)} · ${reading.effectiveStatus(nowMs).title}\n${reading.sensor.path}",
            color = Muted, style = MaterialTheme.typography.labelSmall)
        Text("更新 ${temperatureTime(reading.lastSuccessAtMs)}", color = Muted, style = MaterialTheme.typography.labelSmall)
        HorizontalDivider(color = Muted.copy(alpha = .12f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SystemThermalDetailSheet(reading: SystemThermalReading, nowMs: Long, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("系統熱狀態", style = MaterialTheme.typography.titleLarge)
            DetailField("原始名稱與 API", SystemThermalReading.SOURCE)
            DetailField("原始值／單位", "${reading.level ?: "未提供"} · 系統熱壓力等級（不是 °C）")
            DetailField("對應結果／狀態", "${reading.title(nowMs)} / ${reading.status.title}")
            DetailField("分類、依據與可信程度", "系統狀態；高可信度：Android 公開 API 常數。Android 10 / API 29 起支援。")
            DetailField("本次嘗試／最後成功讀取", "${temperatureTime(reading.atMs, true)}\n${temperatureTime(reading.lastSuccessAtMs, true)}")
            DetailField("溫度摘要／最高溫資格", "不參與攝氏溫度計算，於摘要獨立呈現狀態。")
            Text(SystemThermalReading.EXPLANATION, color = Muted)
            DetailField("熱壓力預估 API／原始值", "${ThermalHeadroomReading.SOURCE}\n${reading.headroom.value ?: "未提供"} · 無單位\n${reading.headroom.display(nowMs)} · 更新 ${temperatureTime(reading.headroom.atMs, true)}")
            Text(ThermalHeadroomReading.EXPLANATION, color = Muted)
            DetailField("裝置回報的預估門檻 · API 35+", reading.headroom.thresholds.entries.sortedBy { it.key }.joinToString("\n") {
                "${SystemThermalReading.labels.getOrNull(it.key) ?: it.key.toString()}：${it.value}"
            }.ifEmpty { "無可用門檻；不自行套用通用分級。" })
            DetailField("官方常數對照", SystemThermalReading.labels.mapIndexed { index, label -> "$index · $label" }.joinToString("\n"))
            DetailField("官方定義", "https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus()")
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    }
}

@Composable private fun SummaryTemperature(title: String, reading: TemperatureReading?, nowMs: Long, selected: Boolean, onSelect: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Navy.copy(alpha = .45f), MaterialTheme.shapes.medium)
        .border(1.dp, if (selected) Mint.copy(alpha = .5f) else Color.Transparent, MaterialTheme.shapes.medium)
        .clickable(role = Role.Button, onClickLabel = "切換$title 趨勢", onClick = onSelect)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Muted, style = MaterialTheme.typography.labelMedium)
        Text(reading?.displayValue(nowMs) ?: "無可用資料", fontWeight = FontWeight.Medium,
            style = if (reading == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
        Text(reading?.let { "${it.sensor.rawName}\n${sourceTitle(it.sensor.source)} · ${temperatureTime(it.lastSuccessAtMs)}" } ?: "等待可辨識且有效的來源",
            color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun TemperatureCompactRow(reading: TemperatureReading, nowMs: Long, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, onClick = onClick) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(reading.sensor.rawName, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(reading.displayValue(nowMs), Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium)
        Text(sourceTitle(reading.sensor.source), color = Muted, style = MaterialTheme.typography.labelSmall)
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TemperatureDetailSheet(reading: TemperatureReading, points: List<TemperaturePoint>, nowMs: Long, onDismiss: () -> Unit) {
    val c = reading.sensor.classification
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(c.title, style = MaterialTheme.typography.titleLarge)
            Text(reading.displayValue(nowMs), style = MaterialTheme.typography.headlineSmall)
            DetailField("原始名稱", reading.sensor.rawName)
            DetailField("來源／實際路徑或 API", "${sourceTitle(reading.sensor.source)}\n${reading.sensor.path}")
            DetailField("原始值", reading.raw ?: "本次無原始值")
            DetailField("來源單位", reading.sensor.declaredUnit.title)
            DetailField("轉換後值", reading.celsius?.let { "${String.format(Locale.ROOT, "%.3f", it)} °C" } ?: "未轉換／不適用；保留原始值")
            DetailField("資料種類／分類", "${c.kind.title} / ${c.group.title}")
            DetailField("辨識依據／可信程度", "${c.basis}\n${c.confidence.title}\n規則：${c.ruleId}")
            DetailField("本次讀取狀態", "${reading.effectiveStatus(nowMs).title}\n${reading.statusDetail}")
            DetailField("本次嘗試／最後成功讀取", "${temperatureTime(reading.attemptedAtMs, true)}\n${temperatureTime(reading.lastSuccessAtMs, true)}")
            if (reading.effectiveStatus(nowMs) != TemperatureReadStatus.OK && reading.lastGoodCelsius != null)
                DetailField("上次有效溫度（過期，非本次讀值）", "${String.format(Locale.ROOT, "%.1f °C", reading.lastGoodCelsius)} · ${temperatureTime(reading.lastSuccessAtMs)}")
            DetailField("摘要／最高溫資格", if (reading.usableInSummary(nowMs)) "本次允許；${c.group.title} 依摘要優先規則選取"
                else if (c.summaryEligible) "規則允許，但本次失效或過期，排除統計" else "排除；${c.basis}")
            DetailField("摘要選擇規則", "電池只採 Android 電池 API。CPU／SoC 分類各取有效最高測點，互不替代。GPU 順序：機型驗證規則 → 本機裝置樹綁定 → 驅動綁定 thermal → 公開 Qualcomm 類型與本機 SoC 佐證 → 驅動綁定 hwmon → 本機 compatible 佐證的 KGSL。相同優先序依原始名稱、來源 ID 固定選取；不挑最高或平均。有效資料上限 10 秒。")
            if (c.kind == TemperatureKind.MEASUREMENT) TemperatureTrend(points, nowMs)
            if (reading.sensor.metadata.isNotEmpty()) {
                Text("可讀驅動／裝置設定（探索時快照）", fontWeight = FontWeight.Medium)
                reading.sensor.metadata.forEach { (key, value) -> DetailField(key, value) }
            }
            if (reading.sensor.reference.isNotBlank()) DetailField("介面依據", reading.sensor.reference)
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    }
}

@Composable private fun DetailField(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Muted, style = MaterialTheme.typography.labelMedium)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable private fun TemperatureTrend(points: List<TemperaturePoint>, nowMs: Long) {
    val visible = points.filter { it.atMs in (nowMs - TemperatureHistory.WINDOW_MS)..nowMs }
    val valid = visible.mapNotNull { it.celsius?.takeIf(Double::isFinite) }
    Text("最近五分鐘 · 此來源", fontWeight = FontWeight.Medium)
    if (valid.isEmpty()) Text("無有效溫度樣本", color = Muted) else {
        val minimum = valid.min()
        val maximum = valid.max()
        Text(String.format(Locale.ROOT, "最小 %.1f °C · 最大 %.1f °C", minimum, maximum), color = Muted, style = MaterialTheme.typography.bodySmall)
        val trendColor = Muted
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val span = (maximum - minimum).coerceAtLeast(1.0)
            val bottom = minimum - if (maximum == minimum) .5 else 0.0
            var previous: Pair<TemperaturePoint, Offset>? = null
            visible.forEach { point ->
                val value = point.celsius
                if (value == null || !value.isFinite()) previous = null else {
                    val position = Offset(((point.atMs - (nowMs - TemperatureHistory.WINDOW_MS)).toFloat() / TemperatureHistory.WINDOW_MS * size.width),
                        (size.height * (1 - (value - bottom) / span)).toFloat().coerceIn(0f, size.height))
                    previous?.takeIf { point.atMs - it.first.atMs <= TemperatureHistory.MAX_CONNECTED_GAP_MS }?.let { drawLine(trendColor, it.second, position, 2.dp.toPx()) }
                    drawCircle(trendColor, 1.5.dp.toPx(), position)
                    previous = point to position
                }
            }
        }
        Text("失敗樣本與停止輪詢的時段留空；不同來源分開記錄，不補 0。縱軸依此來源有效範圍縮放。", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

internal fun temperatureTime(time: Long?, date: Boolean = false): String = time?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (date) "MM/dd HH:mm:ss" else "HH:mm:ss"))
} ?: "尚無成功讀值"
private fun sourceTitle(source: TemperatureSource) = when (source) {
    TemperatureSource.BATTERY_API -> "Android 電池 API"
    TemperatureSource.THERMAL -> "thermal temp"
    TemperatureSource.TRIP_POINT -> "trip point 門檻"
    TemperatureSource.HWMON -> "hwmon"
    TemperatureSource.KGSL -> "KGSL sysfs"
    TemperatureSource.DIAGNOSTIC -> "來源診斷"
}
