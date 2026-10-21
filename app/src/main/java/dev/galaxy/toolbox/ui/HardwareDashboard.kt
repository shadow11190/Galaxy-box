package dev.galaxy.toolbox.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.ToolboxState
import dev.galaxy.toolbox.data.BrandMarks
import dev.galaxy.toolbox.model.*
import java.util.Locale

private val Blue: Color @Composable get() = MaterialTheme.colorScheme.secondary
private val Amber: Color @Composable get() = if (Navy.red < .3f) Color(0xFFF2CB86) else Color(0xFF80601B)
private val Lilac: Color @Composable get() = if (Navy.red < .3f) Color(0xFFC0ABF6) else Color(0xFF68518F)

internal fun LazyListScope.dashboardItems(state: ToolboxState, onHardware: (HardwareCategory) -> Unit, onBattery: () -> Unit, onTemperature: (String) -> Unit = {}) {
    item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val deviceName = state.hardware.firstOrNull { it.label == "裝置名稱" }?.value
                val mark = BrandMarks.resolve(Build.MANUFACTURER, Build.BRAND, deviceName)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("目前裝置", color = Muted, style = MaterialTheme.typography.labelLarge)
                        Text(deviceName ?: "裝置名稱待辨識", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(Build.MODEL, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (mark != null) {
                        Spacer(Modifier.width(10.dp))
                        BrandLogo(mark)
                    }
                }
                Text(state.hardware.firstOrNull { it.label == "處理器名稱" }?.value ?: "處理器名稱未知", color = Blue, style = MaterialTheme.typography.bodyMedium)
                Text(state.hardware.firstOrNull { it.label == "GPU 名稱" }?.value?.let { "GPU · $it" } ?: "GPU 名稱未知", color = Lilac, style = MaterialTheme.typography.bodyMedium)
                Text("Android ${Build.VERSION.RELEASE}  ·  ${Runtime.getRuntime().availableProcessors()} 個邏輯核心", color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    val dotColor = if (state.snapshot.sampledAtMillis == null) Muted else Mint
                    Canvas(Modifier.size(6.dp)) { drawCircle(dotColor) }
                    Text("${state.snapshot.sampledAt} · 前景每 2 秒取樣", color = Mint, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    item {
        val s = state.snapshot
        val cpu = s.cores.mapNotNull { it.currentKHz?.takeIf { value -> value > 0 } }.maxOrNull()
        MetricGrid(listOf(
            Metric("電池", s.batteryPercent?.let { "$it%" } ?: "未知", "電池溫度 ${temperature(s.batteryTemperature)}", Mint,
                s.batteryPercent?.div(100f), onBattery),
            Metric("記憶體", s.memory?.let { "${it.percent}%" } ?: "未知", s.memory?.let { "${gib(it.used)} / ${gib(it.total)} GiB 已用" } ?: "等待系統回報", Blue,
                s.memory?.fraction, { onHardware(HardwareCategory.MEMORY) }),
            Metric("儲存空間", s.storage?.let { "${it.percent}%" } ?: "未知", s.storage?.let { "可用 ${gib(it.available)} GiB" } ?: "資料分割區", Lilac,
                s.storage?.fraction, { onHardware(HardwareCategory.MEMORY) }),
            Metric("核心頻率", cpu?.let { String.format(Locale.ROOT, "%.2f GHz", it / 1_000_000.0) } ?: "未知", "目前可讀核心中的最高值", Amber,
                null, { onHardware(HardwareCategory.CPU) })
        ))
    }
    item { CpuCorePanel(state.snapshot.cores) { onHardware(HardwareCategory.CPU) } }
    item { TemperatureSummaryCard(state, onTemperature) { onHardware(HardwareCategory.THERMAL) } }
    item {
        InfoCard("記憶體走勢") {
            Sparkline(state.history)
            Text("最近 60 次前景取樣 · 含系統與快取使用量", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    item { WidgetActionsCard() }
}

internal fun LazyListScope.hardwareItems(
    state: ToolboxState, category: HardwareCategory, onCategory: (HardwareCategory) -> Unit, onReading: (Reading) -> Unit,
    onRescanTemperatures: () -> Unit = {}, onTemperature: (String) -> Unit = {},
    expandedTemperatures: List<String> = emptyList(), onToggleTemperature: (String) -> Unit = {}, onRefreshCameras: () -> Unit = {}, onRescanGpu: () -> Unit = {},
    categoryScroll: androidx.compose.foundation.lazy.LazyListState
) {
    item(key = "hardware-category-tabs") { HardwareCategoryTabs(category, onCategory, categoryScroll) }
    item {
        Text(category.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("即時監控每 2 秒更新；規格與能力為讀取時快照。點按資料查看詳細資訊。", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (category == HardwareCategory.THERMAL) {
        temperatureItems(state, expandedTemperatures, onToggleTemperature, onTemperature, onRescanTemperatures)
        return
    }
    if (category == HardwareCategory.CPU) {
        item { CpuCorePanel(state.snapshot.cores, onCore = { core ->
            onReading(Reading("CPU ${core.index} 核心頻率", core.currentKHz?.let { "${it / 1000} MHz" },
                "/sys/devices/system/cpu/cpu${core.index}/cpufreq/scaling_cur_freq",
                "標示上限：${core.maximumKHz?.let { "${it / 1000} MHz" } ?: "未知"}，來源 cpuinfo_max_freq。頻率比例不是 CPU 使用率；休眠核心或系統限制時未知。"))
        }) }
    }
    if (category == HardwareCategory.MEMORY) {
        item { RawMemoryCard() }
        item {
            MetricGrid(listOf(
                Metric("RAM 已用", state.snapshot.memory?.let { "${it.percent}%" } ?: "未知",
                    state.snapshot.memory?.let { "${gib(it.used)} / ${gib(it.total)} GiB" } ?: "系統未提供", Blue, state.snapshot.memory?.fraction) {
                    state.snapshot.hardware.firstOrNull { it.label.startsWith("RAM") }?.let(onReading)
                },
                Metric("資料分割區已用", state.snapshot.storage?.let { "${it.percent}%" } ?: "未知",
                    state.snapshot.storage?.let { "可用 ${gib(it.available)} GiB" } ?: "系統未提供", Lilac, state.snapshot.storage?.fraction) {
                    state.snapshot.hardware.firstOrNull { it.label.startsWith("資料分割區") }?.let(onReading)
                }
            ))
        }
        item { InfoCard("RAM 使用走勢") { Sparkline(state.history) } }
    }
    if (category == HardwareCategory.GPU) item {
        GpuSourcesCard(state.snapshot.gpu, state.temperatureNowMs,
            TemperatureSelection.summary(state.temperatures, state.temperatureNowMs).gpu, onRescanGpu, onReading) { onCategory(HardwareCategory.THERMAL) }
    }
    val readings = (state.snapshot.hardware + state.hardware).filter { HardwareCategory.of(it) == category }
        .filterNot { category == HardwareCategory.GPU && (it.value.isNullOrBlank() || it.group == "GPU 即時監控" && !state.snapshot.gpu.fresh(state.temperatureNowMs)) }
        .filterNot { category == HardwareCategory.CPU && Regex("CPU \\d+ 當前頻率").matches(it.label) }
    if (category == HardwareCategory.CAMERA) item { CameraAccessCard(state.cameraBusy, onRefreshCameras) }
    if (category == HardwareCategory.CAMERA) item {
        InfoCard("鏡頭與拍攝能力") {
            Text("依公開 Camera ID 分列；邏輯與實體相機可能重疊。一般輸出、最大解析度模式及像素陣列分開顯示，未知項目不以手機規格補值。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    readings.groupBy { HardwareSections.title(it, category) }.forEach { (title, group) ->
        item(key = "${category.name}-$title") { HardwareDetailCard(title, group, category, onReading) }
    }
    if (readings.isEmpty() && category != HardwareCategory.CPU) {
        if (category != HardwareCategory.GPU) item { InfoCard("等待資料") { Text("此分類目前沒有系統可讀的資料。", color = Muted) } }
    }
}

private data class Metric(val label: String, val value: String, val detail: String, val color: Color, val fraction: Float?, val action: () -> Unit)

@Composable private fun MetricGrid(metrics: List<Metric>) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 300.dp || largeText) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { metric -> MetricTile(metric, Modifier.weight(1f)) }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable private fun MetricTile(metric: Metric, modifier: Modifier) {
    GlassCard(onClick = metric.action, modifier = modifier, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(metric.label, color = Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Canvas(Modifier.size(25.dp)) {
                    drawArc(metric.color.copy(alpha = .13f), -90f, 360f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    metric.fraction?.let { drawArc(metric.color, -90f, 360f * it.coerceIn(0f, 1f), false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)) }
                    if (metric.fraction == null) drawCircle(metric.color, 3.dp.toPx())
                }
            }
            Text(metric.value, color = metric.color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(metric.detail, color = Muted, style = MaterialTheme.typography.labelSmall, minLines = 2)
        }
    }
}

@Composable private fun CpuCorePanel(cores: List<CpuCoreSample>, onOpen: (() -> Unit)? = null, onCore: ((CpuCoreSample) -> Unit)? = null) {
    InfoCard("CPU 核心頻率") {
        Text("${cores.count { it.currentKHz != null }} / ${cores.size} 個核心可讀", color = Muted, style = MaterialTheme.typography.labelMedium)
        val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
        if (cores.isEmpty()) Text("等待系統回報", color = Muted)
        cores.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { core ->
                    Column(Modifier.weight(1f).background(Navy, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = "查看 CPU ${core.index} 資料") { onCore?.invoke(core) ?: onOpen?.invoke() }
                        .padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("CPU ${core.index}", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(core.currentKHz?.let { "${it / 1000}" } ?: "未知", color = Amber, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text("MHz", color = Muted, style = MaterialTheme.typography.labelSmall)
                        val barColor = Amber
                        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                            drawRoundRect(barColor.copy(alpha = .12f))
                            core.frequencyRatio?.let { drawRoundRect(barColor, size = Size(size.width * it, size.height)) }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("橫條為當前頻率相對該核心標示上限，並非 CPU 使用率。", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReadingDetailSheet(reading: Reading, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(reading.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(reading.value ?: "未知", color = Mint, style = MaterialTheme.typography.headlineSmall)
            if (reading.detail.isNotEmpty()) Text(reading.detail, color = Muted)
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    }
}

@Composable internal fun NavigationGlyph(index: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
        val unit = size.width / 10
        when (index) {
            0 -> listOf(1f to 1f, 6f to 1f, 1f to 6f, 6f to 6f).forEach { (x, y) -> drawRoundRect(color, Offset(x * unit, y * unit), Size(3 * unit, 3 * unit), style = stroke) }
            1 -> { drawRoundRect(color, Offset(2 * unit, 2 * unit), Size(6 * unit, 6 * unit), style = stroke)
                listOf(3.5f, 6.5f).forEach { x -> drawLine(color, Offset(x * unit, 0f), Offset(x * unit, 2 * unit), 1.5.dp.toPx()); drawLine(color, Offset(x * unit, 8 * unit), Offset(x * unit, 10 * unit), 1.5.dp.toPx()) } }
            2 -> { drawRoundRect(color, Offset(2 * unit, 2 * unit), Size(6 * unit, 7 * unit), style = stroke); drawLine(color, Offset(4 * unit, .5f * unit), Offset(6 * unit, .5f * unit), 2.dp.toPx()); drawLine(color, Offset(5 * unit, 4 * unit), Offset(5 * unit, 7 * unit), 2.dp.toPx()) }
            3 -> {
                // A clear system-update/download symbol at 22 dp, instead of the broken circular arrow.
                drawLine(color, Offset(5f * unit, 1.2f * unit), Offset(5f * unit, 6.5f * unit), 1.8.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(2.9f * unit, 4.6f * unit), Offset(5f * unit, 6.7f * unit), 1.8.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(7.1f * unit, 4.6f * unit), Offset(5f * unit, 6.7f * unit), 1.8.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(2f * unit, 8.4f * unit), Offset(8f * unit, 8.4f * unit), 1.8.dp.toPx(), cap = StrokeCap.Round)
            }
            else -> { drawCircle(color, 4 * unit, style = stroke); drawLine(color, Offset(3 * unit, 5 * unit), Offset(7 * unit, 5 * unit), 1.7.dp.toPx()); drawLine(color, Offset(5 * unit, 3 * unit), Offset(5 * unit, 7 * unit), 1.7.dp.toPx()) }
        }
    }
}

private fun gib(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1073741824.0)
private fun temperature(value: Float?) = value?.takeIf { it.isFinite() }?.let { String.format(Locale.ROOT, "%.1f °C", it) } ?: "未知"
