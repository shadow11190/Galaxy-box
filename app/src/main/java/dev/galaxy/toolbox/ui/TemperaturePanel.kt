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
import androidx.compose.ui.draw.clip
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal fun LazyListScope.temperatureItems(state: ToolboxState, expanded: List<String>, onToggle: (String) -> Unit,
    onSensor: (String) -> Unit, onRescan: () -> Unit) {
    item(key = "temperature-summary", contentType = "temperature-summary") { TemperatureSummaryCard(state, onSensor) }
    item(key = "temperature-actions", contentType = "temperature-actions") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GalaxyOutlinedButton(onClick = onRescan) { GalaxyText("重新偵測溫度來源") }
            TemperatureExport(state)
            GalaxyText("每 2 秒取樣；離開總覽／溫度／GPU 頁即停止檔案輪詢。點按測點查看原始資料。", color = Muted, style = GalaxyTypography.bodySmall)
        }
    }
    val sections = TemperatureSections.build(state.temperatures, state.temperatureNowMs)
    item(key = "temperature-identified-heading", contentType = "heading") { GalaxyText("分類感測器", style = GalaxyTypography.titleLarge) }
    sections.filterNot { it.advanced }.forEach { section ->
        temperatureSection(section, section.id in expanded, onToggle, onSensor, state.temperatureNowMs)
    }
    item(key = "temperature-diagnostics-heading", contentType = "heading") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GalaxyText("進階診斷", style = GalaxyTypography.titleLarge)
            GalaxyText("原始狀態、門檻與未辨識項目不參與最高溫計算；數字不一定代表 °C。", color = Muted, style = GalaxyTypography.bodySmall)
        }
    }
    sections.filter { it.advanced }.forEach { section ->
        temperatureSection(section, section.id in expanded, onToggle, onSensor, state.temperatureNowMs)
    }
}

private fun LazyListScope.temperatureSection(section: TemperatureSection, expanded: Boolean, onToggle: (String) -> Unit,
    onSensor: (String) -> Unit, nowMs: Long) {
    item(key = "temperature-section-${section.id}", contentType = "temperature-section") {
        val language = LocalAppLanguage.current
        GlassCard(Modifier.fillMaxWidth(), shape = GalaxyShapes.medium) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = AppText.translate(if (expanded) "收合${section.title}" else "展開${section.title}", language)) { onToggle(section.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GalaxyText(section.title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                GalaxyText("${section.readings.size}", color = Muted)
                GalaxyText(if (expanded) "  −" else "  ＋", color = Muted)
            }
        }
    }
    if (expanded) {
        if (section.readings.isEmpty()) item(key = "temperature-empty-${section.id}", contentType = "empty") { GalaxyText("無可用資料", color = Muted, style = GalaxyTypography.bodySmall) }
        items(section.readings.chunked(2), key = { "temperature-${it.first().sensor.id}" }, contentType = { "temperature-reading-pair" }) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { reading -> Box(Modifier.weight(1f)) { TemperatureCompactRow(reading, nowMs) { onSensor(reading.sensor.id) } } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable internal fun TemperatureSummaryCard(state: ToolboxState, onSensor: (String) -> Unit = {}, onAll: (() -> Unit)? = null) {
    val language = LocalAppLanguage.current
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
        Column(Modifier.fillMaxWidth().background(Navy.copy(alpha = .45f), GalaxyShapes.medium).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GalaxyText("$selected · 溫度趨勢", style = GalaxyTypography.titleSmall)
            if (active == null) GalaxyText(if (selected == "SoC") socDiagnostics.explanation else "無可用資料；等待此元件的有效來源。", color = Muted, style = GalaxyTypography.bodySmall)
            else {
                GalaxyText("${active.sensor.rawName} · ${sourceTitle(active.sensor.source)}", color = Muted, style = GalaxyTypography.labelSmall)
                TemperatureTrend(state.temperatureHistory[active.sensor.id].orEmpty(), state.temperatureNowMs)
                GalaxyText("各測點獨立記錄，來源切換不拼接曲線。", color = Muted, style = GalaxyTypography.labelSmall)
                GalaxyTextButton(onClick = { onSensor(active.sensor.id) }) { GalaxyText("查看原始資料與來源") }
            }
        }
        summary.soc?.sensor?.let { sensor ->
            GalaxyText("SoC 來源 · " + sensor.rawName + " · " + sourceTitle(sensor.source) + "\n" + sensor.path,
                color = Muted, style = GalaxyTypography.bodySmall)
        }
        GalaxyTextButton(onClick = { socDetail = true }) { GalaxyText("查看 SoC 來源與讀取狀態") }
        GalaxyDivider(color = Muted.copy(alpha = .12f))
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
            onClickLabel = AppText.translate("查看系統熱狀態原始資料", language)) { systemDetail = true }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GalaxyText("系統熱狀態", Modifier.weight(1f), color = Muted)
            GalaxyText(state.snapshot.systemThermal.title(state.temperatureNowMs), Modifier.weight(1f), textAlign = TextAlign.End)
        }
        GalaxyText("Android 熱狀態 API · 更新 ${temperatureTime(state.snapshot.systemThermal.atMs)}", color = Muted, style = GalaxyTypography.labelSmall)
        GalaxyText(SystemThermalReading.EXPLANATION, color = Muted, style = GalaxyTypography.bodySmall)
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { systemDetail = true }, verticalAlignment = Alignment.CenterVertically) {
            GalaxyText("熱壓力預估 · 10 秒", Modifier.weight(1f), color = Muted, style = GalaxyTypography.bodySmall)
            GalaxyText(state.snapshot.systemThermal.headroom.display(state.temperatureNowMs), fontWeight = FontWeight.Medium)
        }
        GalaxyText("更新 ${temperatureTime(state.snapshot.systemThermal.headroom.atMs)} · 每 15 秒讀取", color = Muted, style = GalaxyTypography.labelSmall)
        GalaxyText("CPU／SoC 分開統計，不代表所有核心或整顆晶片。點按卡片切換趨勢；清單可查看原始資料。", color = Muted, style = GalaxyTypography.bodySmall)
        onAll?.let { action -> GalaxyTextButton(onClick = action) { GalaxyText("查看感測器與進階診斷") } }
    }
    if (systemDetail) SystemThermalDetailSheet(state.snapshot.systemThermal, state.temperatureNowMs) { systemDetail = false }
    if (socDetail) SocSourceSheet(state, socDiagnostics, { socDetail = false }, { id -> socDetail = false; onSensor(id) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SocSourceSheet(state: ToolboxState, diagnostics: SocTemperatureDiagnostics,
    onDismiss: () -> Unit, onSensor: (String) -> Unit) {
    GalaxyBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GalaxyText("SoC 來源診斷", style = GalaxyTypography.titleLarge)
                Spacer(Modifier.height(8.dp))
                GalaxyText(diagnostics.explanation)
                GalaxyText("CPU、GPU、電池與保護節點不拿來補 SoC。點按來源可查看分類依據與驅動設定。",
                    color = Muted, style = GalaxyTypography.bodySmall)
            }
            item { GalaxyText("已辨識 SoC 來源 · ${diagnostics.identified.size}", fontWeight = FontWeight.Medium) }
            items(diagnostics.identified, key = { "soc-known-${it.sensor.id}" }, contentType = { "soc-source" }) { reading ->
                SocSourceRow(reading, state.temperatureNowMs) { onSensor(reading.sensor.id) }
            }
            item {
                GalaxyText("其他來源 · 未確認為 SoC · ${diagnostics.unconfirmed.size}", fontWeight = FontWeight.Medium)
                GalaxyText("此清單供對照原始名稱，並非已確認的 SoC 測點，不納入 SoC 摘要。", color = Muted,
                    style = GalaxyTypography.bodySmall)
            }
            items(diagnostics.unconfirmed, key = { "soc-other-${it.sensor.id}" }, contentType = { "soc-source" }) { reading ->
                SocSourceRow(reading, state.temperatureNowMs) { onSensor(reading.sensor.id) }
            }
            if (diagnostics.discoveryIssues.isNotEmpty()) {
                item { GalaxyText("來源探索記錄", fontWeight = FontWeight.Medium) }
                items(diagnostics.discoveryIssues, key = { "soc-issue-${it.sensor.id}" }, contentType = { "soc-source" }) { reading ->
                    SocSourceRow(reading, state.temperatureNowMs) { onSensor(reading.sensor.id) }
                }
            }
            item { TemperatureExport(state) }
            item { GalaxyTextButton(onClick = onDismiss) { GalaxyText("關閉") } }
        }
    }
}

@Composable private fun SocSourceRow(reading: TemperatureReading, nowMs: Long, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalaxyText(reading.sensor.rawName, Modifier.weight(1f))
            GalaxyText(reading.displayValue(nowMs), Modifier.weight(1f), textAlign = TextAlign.End)
        }
        GalaxyText("${sourceTitle(reading.sensor.source)} · ${reading.effectiveStatus(nowMs).title}\n${reading.sensor.path}",
            color = Muted, style = GalaxyTypography.labelSmall)
        GalaxyText("更新 ${temperatureTime(reading.lastSuccessAtMs)}", color = Muted, style = GalaxyTypography.labelSmall)
        GalaxyDivider(color = Muted.copy(alpha = .12f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SystemThermalDetailSheet(reading: SystemThermalReading, nowMs: Long, onDismiss: () -> Unit) {
    GalaxyBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GalaxyText("系統熱狀態", style = GalaxyTypography.titleLarge)
            DetailField("原始名稱與 API", SystemThermalReading.SOURCE)
            DetailField("原始值／單位", "${reading.level ?: "未提供"} · 系統熱壓力等級（不是 °C）")
            DetailField("對應結果／狀態", "${reading.title(nowMs)} / ${reading.status.title}")
            DetailField("分類、依據與可信程度", "系統狀態；高可信度：Android 公開 API 常數。Android 10 / API 29 起支援。")
            DetailField("本次嘗試／最後成功讀取", "${temperatureTime(reading.atMs, true)}\n${temperatureTime(reading.lastSuccessAtMs, true)}")
            DetailField("溫度摘要／最高溫資格", "不參與攝氏溫度計算，於摘要獨立呈現狀態。")
            GalaxyText(SystemThermalReading.EXPLANATION, color = Muted)
            DetailField("熱壓力預估 API／原始值", "${ThermalHeadroomReading.SOURCE}\n${reading.headroom.value ?: "未提供"} · 無單位\n${reading.headroom.display(nowMs)} · 更新 ${temperatureTime(reading.headroom.atMs, true)}")
            GalaxyText(ThermalHeadroomReading.EXPLANATION, color = Muted)
            DetailField("裝置回報的預估門檻 · API 35+", reading.headroom.thresholds.entries.sortedBy { it.key }.joinToString("\n") {
                "${SystemThermalReading.labels.getOrNull(it.key) ?: it.key.toString()}：${it.value}"
            }.ifEmpty { "無可用門檻；不自行套用通用分級。" })
            DetailField("官方常數對照", SystemThermalReading.labels.mapIndexed { index, label -> "$index · $label" }.joinToString("\n"))
            DetailField("官方定義", "https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus()")
            GalaxyTextButton(onClick = onDismiss) { GalaxyText("關閉") }
        }
    }
}

@Composable private fun SummaryTemperature(title: String, reading: TemperatureReading?, nowMs: Long, selected: Boolean, onSelect: () -> Unit) {
    val language = LocalAppLanguage.current
    Column(Modifier.fillMaxWidth().clip(GalaxyShapes.medium)
        .background(Navy.copy(alpha = .45f), GalaxyShapes.medium)
        .border(1.dp, if (selected) Mint.copy(alpha = .5f) else Color.Transparent, GalaxyShapes.medium)
        .clickable(role = Role.Button, onClickLabel = AppText.translate("切換$title 趨勢", language), onClick = onSelect)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        GalaxyText(title, color = Muted, style = GalaxyTypography.labelMedium)
        GalaxyText(reading?.displayValue(nowMs) ?: "無可用資料", fontWeight = FontWeight.Medium,
            style = if (reading == null) GalaxyTypography.titleMedium else GalaxyTypography.headlineSmall)
        GalaxyText(reading?.let { "${it.sensor.rawName}\n${sourceTitle(it.sensor.source)} · ${temperatureTime(it.lastSuccessAtMs)}" } ?: "等待可辨識且有效的來源",
            color = Muted, style = GalaxyTypography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun TemperatureCompactRow(reading: TemperatureReading, nowMs: Long, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GalaxyShapes.medium, onClick = onClick) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        GalaxyText(reading.sensor.rawName, color = Muted, style = GalaxyTypography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        GalaxyText(reading.displayValue(nowMs), Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = GalaxyTypography.titleMedium)
        GalaxyText(sourceTitle(reading.sensor.source), color = Muted, style = GalaxyTypography.labelSmall)
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TemperatureDetailSheet(reading: TemperatureReading, points: List<TemperaturePoint>, nowMs: Long, onDismiss: () -> Unit) {
    val c = reading.sensor.classification
    GalaxyBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GalaxyText(c.title, style = GalaxyTypography.titleLarge)
            GalaxyText(reading.displayValue(nowMs), style = GalaxyTypography.headlineSmall)
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
                GalaxyText("可讀驅動／裝置設定（探索時快照）", fontWeight = FontWeight.Medium)
                reading.sensor.metadata.forEach { (key, value) -> DetailField(key, value) }
            }
            if (reading.sensor.reference.isNotBlank()) DetailField("介面依據", reading.sensor.reference)
            GalaxyTextButton(onClick = onDismiss) { GalaxyText("關閉") }
        }
    }
}

@Composable private fun DetailField(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        GalaxyText(title, color = Muted, style = GalaxyTypography.labelMedium)
        SelectionContainer { GalaxyText(value, style = GalaxyTypography.bodyMedium) }
    }
}

@Composable private fun TemperatureTrend(points: List<TemperaturePoint>, nowMs: Long) {
    val temperatureUnit = LocalTemperatureDisplayUnit.current
    val visible = points.filter { it.atMs in (nowMs - TemperatureHistory.WINDOW_MS)..nowMs }
    val valid = visible.mapNotNull { it.celsius?.takeIf(Double::isFinite) }
    GalaxyText("最近五分鐘 · 此來源", fontWeight = FontWeight.Medium)
    if (valid.isEmpty()) GalaxyText("無有效溫度樣本", color = Muted) else {
        val minimum = valid.min()
        val maximum = valid.max()
        GalaxyText(String.format(Locale.ROOT, "最小 %.1f °C · 最大 %.1f °C", minimum, maximum), color = Muted, style = GalaxyTypography.bodySmall)
        val axis = TemperatureTrendAxis.from(minimum, maximum)
        val axisColor = Muted
        val trendColor = Mint
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(44.dp).height(112.dp), verticalArrangement = Arrangement.SpaceBetween) {
                axis.ticksDescending.forEach { value ->
                    GalaxyText(axis.label(TemperatureText.displayNumber(value, temperatureUnit)), Modifier.fillMaxWidth(), color = axisColor,
                        textAlign = TextAlign.End, style = GalaxyTypography.labelSmall, maxLines = 1)
                }
            }
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.weight(1f).height(112.dp)) {
                axis.ticksDescending.forEach { value ->
                    val y = (size.height * (axis.maximum - value) / axis.span).toFloat()
                    drawLine(axisColor.copy(alpha = .23f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                listOf(0f, .5f, 1f).forEach { fraction ->
                    val x = size.width * fraction
                    drawLine(axisColor.copy(alpha = .13f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
                var previous: Pair<TemperaturePoint, Offset>? = null
                visible.forEach { point ->
                    val value = point.celsius
                    if (value == null || !value.isFinite()) previous = null else {
                        val position = Offset(((point.atMs - (nowMs - TemperatureHistory.WINDOW_MS)).toFloat() / TemperatureHistory.WINDOW_MS * size.width),
                            (size.height * (axis.maximum - value) / axis.span).toFloat().coerceIn(0f, size.height))
                        previous?.takeIf { point.atMs - it.first.atMs <= TemperatureHistory.MAX_CONNECTED_GAP_MS }
                            ?.let { drawLine(trendColor, it.second, position, 2.dp.toPx()) }
                        drawCircle(trendColor, 1.5.dp.toPx(), position)
                        previous = point to position
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(50.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("−5 分", "−2.5 分", "現在").forEach { label ->
                    GalaxyText(label, color = axisColor, style = GalaxyTypography.labelSmall)
                }
            }
        }
        GalaxyText("縱軸：${if (temperatureUnit == TemperatureDisplayUnit.CELSIUS) "°C" else "°F"} · 橫軸：距今時間。失敗或停止輪詢的時段留空，不補 0。", color = Muted, style = GalaxyTypography.bodySmall)
    }
}

internal data class TemperatureTrendAxis(val minimum: Double, val maximum: Double, val step: Double) {
    val span: Double get() = maximum - minimum
    val ticksDescending: List<Double> get() = (0..((span / step).toInt())).map { maximum - it * step }
    fun label(value: Double): String = String.format(Locale.ROOT, if (step < 1.0) "%.1f°" else "%.0f°", value)

    companion object {
        fun from(minimum: Double, maximum: Double): TemperatureTrendAxis {
            require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum)
            val rough = (maximum - minimum).coerceAtLeast(1.0) / 4.0
            val magnitude = 10.0.pow(floor(log10(rough)))
            val fraction = rough / magnitude
            val step = magnitude * when {
                fraction <= 1.0 -> 1.0
                fraction <= 2.0 -> 2.0
                fraction <= 5.0 -> 5.0
                else -> 10.0
            }
            val lower = floor((minimum - step * .25) / step) * step
            val upper = ceil((maximum + step * .25) / step) * step
            return TemperatureTrendAxis(lower, upper, step)
        }
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
