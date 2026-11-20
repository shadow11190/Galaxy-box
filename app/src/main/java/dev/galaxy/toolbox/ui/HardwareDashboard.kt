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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.galaxy.toolbox.ToolboxState
import dev.galaxy.toolbox.data.BrandMarks
import dev.galaxy.toolbox.model.*
import java.util.Locale

private val Teal: Color @Composable get() = GalaxyPalette.telemetry.battery
private val Cobalt: Color @Composable get() = GalaxyPalette.telemetry.memory
private val Gold: Color @Composable get() = GalaxyPalette.telemetry.storage
private val Coral: Color @Composable get() = GalaxyPalette.telemetry.cpu

internal fun LazyListScope.dashboardItems(state: ToolboxState, onHardware: (HardwareCategory) -> Unit, onBattery: () -> Unit, onTemperature: (String) -> Unit = {}) {
    item(key = "overview-device", contentType = "device-card") {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val deviceName = state.hardware.firstOrNull { it.label == "裝置名稱" }?.value
                val mark = BrandMarks.resolve(Build.MANUFACTURER, Build.BRAND, deviceName)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GalaxyText("目前裝置", color = Muted, style = GalaxyTypography.labelLarge)
                        DeviceNameTitle(deviceName ?: "裝置名稱待辨識")
                        GalaxyText(Build.MODEL, color = Muted, style = GalaxyTypography.bodyMedium)
                    }
                    if (mark != null) {
                        Spacer(Modifier.width(10.dp))
                        BrandLogo(mark)
                    }
                }
                GalaxyText(state.hardware.firstOrNull { it.label == "處理器名稱" }?.value ?: "處理器名稱未知",
                    color = GalaxyPalette.onSurface, style = GalaxyTypography.bodyMedium)
                GalaxyText(state.hardware.firstOrNull { it.label == "GPU 名稱" }?.value?.let { "GPU · $it" } ?: "GPU 名稱未知",
                    color = Muted, style = GalaxyTypography.bodyMedium)
                GalaxyText("Android ${Build.VERSION.RELEASE}  ·  ${Runtime.getRuntime().availableProcessors()} 個邏輯核心",
                    color = Muted, style = GalaxyTypography.bodySmall)
            }
        }
    }
    item(key = "overview-metrics", contentType = "metric-grid") {
        val s = state.snapshot
        val cpu = s.cores.mapNotNull { it.currentKHz?.takeIf { value -> value > 0 } }.maxOrNull()
        MetricGrid(listOf(
            Metric("電池", s.batteryPercent?.let { "$it%" } ?: "未知", "電池溫度 ${temperature(s.batteryTemperature)}", Teal,
                s.batteryPercent?.div(100f), onBattery),
            Metric("記憶體", s.memory?.let { "${it.percent}%" } ?: "未知", s.memory?.let { "${gib(it.used)} / ${gib(it.total)} GiB 已用" } ?: "等待系統回報", Cobalt,
                s.memory?.fraction, { onHardware(HardwareCategory.MEMORY) }),
            Metric("儲存空間", s.storage?.let { "${it.percent}%" } ?: "未知", s.storage?.let { "可用 ${gib(it.available)} GiB" } ?: "資料分割區", Gold,
                s.storage?.fraction, { onHardware(HardwareCategory.MEMORY) }),
            Metric("核心頻率", cpu?.let { String.format(Locale.ROOT, "%.2f GHz", it / 1_000_000.0) } ?: "未知", "目前可讀核心中的最高值", Coral,
                null, { onHardware(HardwareCategory.CPU) })
        ))
    }
    item(key = "overview-cores", contentType = "cpu-cores") { CpuCorePanel(state.snapshot.cores) { onHardware(HardwareCategory.CPU) } }
    item(key = "overview-temperatures", contentType = "temperature-summary") { TemperatureSummaryCard(state, onTemperature) { onHardware(HardwareCategory.THERMAL) } }
    item(key = "overview-memory-history", contentType = "chart") {
        InfoCard("記憶體走勢") {
            Sparkline(state.history)
            GalaxyText("最近 60 次前景取樣 · 含系統與快取使用量", color = Muted, style = GalaxyTypography.bodySmall)
        }
    }
    item(key = "overview-export", contentType = "action-card") { DeviceReportExport(state) }
    item(key = "overview-widgets", contentType = "action-card") { WidgetActionsCard() }
}

@Composable
internal fun DeviceNameTitle(name: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val style = GalaxyTypography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        val fontSize = listOf(24.sp, 22.sp, 20.sp, 18.sp, 16.sp).firstOrNull { size ->
            measurer.measure(AnnotatedString(name), style = style.copy(fontSize = size),
                maxLines = 1, softWrap = false).size.width <= widthPx
        } ?: 16.sp
        GalaxyText(name, modifier = Modifier.semantics { contentDescription = name },
            style = style.copy(fontSize = fontSize), maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis)
    }
}

internal fun LazyListScope.hardwareItems(
    state: ToolboxState, category: HardwareCategory, onCategory: (HardwareCategory) -> Unit, onReading: (Reading) -> Unit,
    onRescanTemperatures: () -> Unit = {}, onTemperature: (String) -> Unit = {},
    expandedTemperatures: List<String> = emptyList(), onToggleTemperature: (String) -> Unit = {}, onRefreshCameras: () -> Unit = {}, onRescanGpu: () -> Unit = {},
    categoryScroll: androidx.compose.foundation.lazy.LazyListState
) {
    item(key = "hardware-category-tabs", contentType = "category-tabs") { HardwareCategoryTabs(category, onCategory, categoryScroll) }
    item(key = "hardware-category-heading", contentType = "heading") {
        GalaxyText(category.title, style = GalaxyTypography.titleLarge, fontWeight = FontWeight.SemiBold)
        GalaxyText("即時監控每 2 秒更新；規格與能力為讀取時快照。點按資料查看詳細資訊。", color = Muted, style = GalaxyTypography.bodySmall)
    }
    if (category == HardwareCategory.THERMAL) {
        temperatureItems(state, expandedTemperatures, onToggleTemperature, onTemperature, onRescanTemperatures)
        return
    }
    if (category == HardwareCategory.CPU) {
        item(key = "hardware-cpu-cores", contentType = "cpu-cores") { CpuCorePanel(state.snapshot.cores, onCore = { core ->
            onReading(Reading("CPU ${core.index} 核心頻率", core.currentKHz?.let { "${it / 1000} MHz" },
                "/sys/devices/system/cpu/cpu${core.index}/cpufreq/scaling_cur_freq",
                "標示上限：${core.maximumKHz?.let { "${it / 1000} MHz" } ?: "未知"}，來源 cpuinfo_max_freq。頻率比例不是 CPU 使用率；休眠核心或系統限制時未知。"))
        }) }
    }
    if (category == HardwareCategory.MEMORY) {
        item(key = "hardware-memory-raw", contentType = "memory-card") { RawMemoryCard() }
        item(key = "hardware-memory-metrics", contentType = "metric-grid") {
            MetricGrid(listOf(
                Metric("RAM 已用", state.snapshot.memory?.let { "${it.percent}%" } ?: "未知",
                    state.snapshot.memory?.let { "${gib(it.used)} / ${gib(it.total)} GiB" } ?: "系統未提供", Cobalt, state.snapshot.memory?.fraction) {
                    state.snapshot.hardware.firstOrNull { it.label.startsWith("RAM") }?.let(onReading)
                },
                Metric("資料分割區已用", state.snapshot.storage?.let { "${it.percent}%" } ?: "未知",
                    state.snapshot.storage?.let { "可用 ${gib(it.available)} GiB" } ?: "系統未提供", Gold, state.snapshot.storage?.fraction) {
                    state.snapshot.hardware.firstOrNull { it.label.startsWith("資料分割區") }?.let(onReading)
                }
            ))
        }
        item(key = "hardware-memory-chart", contentType = "chart") { InfoCard("RAM 使用走勢") { Sparkline(state.history) } }
    }
    if (category == HardwareCategory.GPU) item(key = "hardware-gpu-sources", contentType = "source-card") {
        GpuSourcesCard(state.snapshot.gpu, state.temperatureNowMs,
            TemperatureSelection.summary(state.temperatures, state.temperatureNowMs).gpu, onRescanGpu, onReading) { onCategory(HardwareCategory.THERMAL) }
    }
    if (category == HardwareCategory.DISPLAY) item(key = "hardware-display-mode", contentType = "display-card") { ScreenModeCard() }
    if (category == HardwareCategory.AUDIO) {
        item(key = "hardware-audio-monitor", contentType = "audio-card") { AudioMonitoringCard() }
        item(key = "hardware-bluetooth-audio", contentType = "audio-card") { BluetoothAudioCard() }
    }
    val readings = (state.snapshot.hardware + state.hardware).filter { HardwareCategory.of(it) == category }
        .filterNot { category == HardwareCategory.GPU && (it.value.isNullOrBlank() || it.group == "GPU 即時監控" && !state.snapshot.gpu.fresh(state.temperatureNowMs)) }
        .filterNot { category == HardwareCategory.CPU && Regex("CPU \\d+ 當前頻率").matches(it.label) }
    if (category == HardwareCategory.CAMERA) item(key = "hardware-camera-access", contentType = "camera-card") { CameraAccessCard(state.cameraBusy, onRefreshCameras) }
    if (category == HardwareCategory.CAMERA) item(key = "hardware-camera-info", contentType = "camera-card") {
        InfoCard("鏡頭與拍攝能力") {
            GalaxyText("依公開 Camera ID 分列；邏輯與實體相機可能重疊。一般輸出、最大解析度模式及像素陣列分開顯示，未知項目不以手機規格補值。", color = Muted, style = GalaxyTypography.bodySmall)
        }
    }
    readings.groupBy { HardwareSections.title(it, category) }.forEach { (title, group) ->
        item(key = "${category.name}-$title", contentType = "hardware-detail") { HardwareDetailCard(title, group, category, onReading) }
    }
    if (readings.isEmpty() && category != HardwareCategory.CPU) {
        if (category != HardwareCategory.GPU) item(key = "hardware-empty", contentType = "empty") { InfoCard("等待資料") { GalaxyText("此分類目前沒有系統可讀的資料。", color = Muted) } }
    }
}

private data class Metric(val label: String, val value: String, val detail: String, val color: Color, val fraction: Float?, val action: () -> Unit)

@Composable private fun MetricGrid(metrics: List<Metric>) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    if (LocalAppStyle.current == AppStyle.LIQUID_GLASS) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 300.dp || largeText) 1 else 2
            val divider = GalaxyPalette.onSurfaceVariant.copy(alpha = .18f)
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    metrics.chunked(columns).forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            row.forEachIndexed { columnIndex, metric ->
                                if (columnIndex > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
                                MetricCompactCell(metric, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        return
    }
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

@Composable private fun MetricCompactCell(metric: Metric, modifier: Modifier) {
    Column(modifier.clickable(role = Role.Button, onClick = metric.action)
        .heightIn(min = 108.dp).padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        GalaxyText(metric.label, color = Muted, style = GalaxyTypography.labelLarge)
        GalaxyText(metric.value, color = GalaxyPalette.readableAccent(metric.color),
            style = GalaxyTypography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        GalaxyText(metric.detail, color = Muted, style = GalaxyTypography.labelSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        metric.fraction?.let { fraction ->
            Box(Modifier.fillMaxWidth().height(3.dp).background(metric.color.copy(alpha = .14f), RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                    .background(metric.color, RoundedCornerShape(50)))
            }
        }
    }
}

@Composable private fun MetricTile(metric: Metric, modifier: Modifier) {
    GlassCard(onClick = metric.action, modifier = modifier, shape = GalaxyShapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GalaxyText(metric.label, color = Muted, style = GalaxyTypography.labelLarge, modifier = Modifier.weight(1f))
                Canvas(Modifier.size(25.dp)) {
                    drawArc(metric.color.copy(alpha = .13f), -90f, 360f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    metric.fraction?.let { drawArc(metric.color, -90f, 360f * it.coerceIn(0f, 1f), false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)) }
                    if (metric.fraction == null) drawCircle(metric.color, 3.dp.toPx())
                }
            }
            GalaxyText(metric.value, color = GalaxyPalette.readableAccent(metric.color),
                style = GalaxyTypography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold, maxLines = 1)
            GalaxyText(metric.detail, color = Muted, style = GalaxyTypography.labelSmall, minLines = 2)
        }
    }
}

@Composable private fun CpuCorePanel(cores: List<CpuCoreSample>, onOpen: (() -> Unit)? = null, onCore: ((CpuCoreSample) -> Unit)? = null) {
    val language = LocalAppLanguage.current
    InfoCard("CPU 核心頻率") {
        GalaxyText("${cores.count { it.currentKHz != null }} / ${cores.size} 個核心可讀", color = Muted, style = GalaxyTypography.labelMedium)
        val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
        if (cores.isEmpty()) GalaxyText("等待系統回報", color = Muted)
        cores.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { core ->
                    Column(Modifier.weight(1f).background(Navy, GalaxyShapes.small)
                        .clickable(role = Role.Button, onClickLabel = AppText.translate("查看 CPU ${core.index} 資料", language)) { onCore?.invoke(core) ?: onOpen?.invoke() }
                        .padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        GalaxyText("CPU ${core.index}", color = Muted, style = GalaxyTypography.labelSmall)
                        GalaxyText(core.currentKHz?.let { "${it / 1000}" } ?: "未知", color = GalaxyPalette.readableAccent(Coral),
                            fontWeight = FontWeight.SemiBold, style = GalaxyTypography.titleMedium.copy(fontFeatureSettings = "tnum"))
                        GalaxyText("MHz", color = Muted, style = GalaxyTypography.labelSmall)
                        val barColor = Coral
                        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                            drawRoundRect(barColor.copy(alpha = .12f))
                            core.frequencyRatio?.let { drawRoundRect(barColor, size = Size(size.width * it, size.height)) }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        GalaxyText("橫條為當前頻率相對該核心標示上限，並非 CPU 使用率。", color = Muted, style = GalaxyTypography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReadingDetailSheet(reading: Reading, onDismiss: () -> Unit) {
    GalaxyBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GalaxyText(reading.label, style = GalaxyTypography.titleLarge, fontWeight = FontWeight.Bold)
            GalaxyText(catalogReadingValue(reading, LocalAppLanguage.current), color = Mint, style = GalaxyTypography.headlineSmall)
            if (reading.detail.isNotEmpty()) GalaxyText(reading.detail, color = Muted)
            GalaxyTextButton(onClick = onDismiss) { GalaxyText("關閉") }
        }
    }
}

@Composable internal fun NavigationGlyph(index: Int, color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val unit = size.width / 24f
        val width = 1.9f * unit
        val stroke = Stroke(width, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), width, cap = StrokeCap.Round)
        when (index) {
            0 -> listOf(3f to 3f, 13f to 3f, 3f to 13f, 13f to 13f).forEach { (x, y) ->
                drawRoundRect(color, Offset(x * unit, y * unit), Size(8 * unit, 8 * unit),
                    cornerRadius = CornerRadius(2.1f * unit), style = stroke)
            }
            1 -> {
                drawRoundRect(color, Offset(5 * unit, 5 * unit), Size(14 * unit, 14 * unit),
                    cornerRadius = CornerRadius(2.4f * unit), style = stroke)
                listOf(8f, 16f).forEach { p ->
                    line(p, 2f, p, 5f); line(p, 19f, p, 22f)
                    line(2f, p, 5f, p); line(19f, p, 22f, p)
                }
            }
            2 -> {
                // Horizontal battery silhouette, distinct from a trash can.
                drawRoundRect(color, Offset(2 * unit, 6 * unit), Size(18 * unit, 12 * unit),
                    cornerRadius = CornerRadius(2.6f * unit), style = stroke)
                drawRoundRect(color, Offset(21 * unit, 10 * unit), Size(2 * unit, 4 * unit),
                    cornerRadius = CornerRadius(unit))
                drawRoundRect(color, Offset(5 * unit, 9 * unit), Size(10 * unit, 6 * unit),
                    cornerRadius = CornerRadius(unit))
            }
            3 -> {
                line(12f, 3f, 12f, 15f)
                line(7.5f, 11f, 12f, 15.5f)
                line(16.5f, 11f, 12f, 15.5f)
                line(5f, 20f, 19f, 20f)
            }
            else -> {
                drawCircle(color, 9f * unit, center = Offset(12 * unit, 12 * unit), style = stroke)
                line(8f, 12f, 16f, 12f)
                line(12f, 8f, 12f, 16f)
            }
        }
    }
}

private fun gib(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1073741824.0)
private fun temperature(value: Float?) = value?.takeIf { it.isFinite() }?.let { String.format(Locale.ROOT, "%.1f °C", it) } ?: "未知"
