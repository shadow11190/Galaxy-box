package dev.galaxy.toolbox.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.edit
import dev.galaxy.toolbox.ToolboxState
import dev.galaxy.toolbox.ToolboxViewModel
import dev.galaxy.toolbox.model.FirmwareQuery
import dev.galaxy.toolbox.model.Reading
import dev.galaxy.toolbox.model.HardwareCategory
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import dev.galaxy.toolbox.diagnostics.*
import android.content.ClipData
import android.content.ClipboardManager

private val Pages = listOf("總覽", "硬體", "電池", "更新", "檢測")

@Composable
fun ToolboxApp(model: ToolboxViewModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsStateWithLifecycle()
    var aboutVisible by rememberSaveable { mutableStateOf(false) }
    var catalogVisible by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val navigation = rememberNavigationMotion(page, Pages.size)
    val pageScrollStates = Pages.map { androidx.compose.foundation.lazy.rememberLazyListState() }
    val hardwareTabScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    var screenTool by rememberSaveable { mutableStateOf<String?>(null) }
    var hardwareCategory by rememberSaveable { mutableStateOf(HardwareCategory.CPU) }
    var testQueue by rememberSaveable { mutableStateOf(listOf<DeviceTest>()) }
    var testResults by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    var sourceReading by remember { mutableStateOf<Reading?>(null) }
    var temperatureId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedTemperatures by rememberSaveable { mutableStateOf(listOf("cpu", "soc", "gpu", "battery")) }
    val context = LocalContext.current
    val availableTests = remember(context) { TestCatalogue.available(TestCatalogue.read(context)) }
    val preferences = remember(context) { context.getSharedPreferences("display", android.content.Context.MODE_PRIVATE) }
    var glassEnabled by rememberSaveable { mutableStateOf(preferences.getBoolean("liquid_glass", false)) }
    val glassAvailable = rememberGlassAvailable()
    var glassFailed by remember { mutableStateOf(false) }
    val glass = glassEnabled && glassAvailable && !glassFailed
    var navigationBackground by rememberSaveable { mutableStateOf(preferences.getBoolean("navigation_background", true)) }
    var themeMode by rememberSaveable { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val navigationBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)
    val view = LocalView.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val temperatureVisible = screenTool == null && testQueue.isEmpty() && (page == 0 || page == 1 && hardwareCategory in listOf(HardwareCategory.THERMAL, HardwareCategory.GPU))
    DisposableEffect(temperatureVisible) {
        model.setTemperatureVisible(temperatureVisible)
        onDispose { model.setTemperatureVisible(false) }
    }
    GalaxyBoxTheme(dark) {
      CompositionLocalProvider(LocalGlass provides GlassEnvironment(glass, backgroundBackdrop) { view.post { glassFailed = true } }) {
      Box(modifier.fillMaxSize()) {
        GalaxyBackdrop(if (glass) Modifier.layerBackdrop(backgroundBackdrop) else Modifier)
        if (screenTool != null) {
            if (screenTool == "貼膜") FilmAssistant { screenTool = null }
            else ScreenTool(screenTool!!) { screenTool = null }
        } else {
        Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, bottomBar = {
            GlassNavigation(Pages, page, { page = it }, navigationBackdrop, glass, navigation, navigationBackground)
        }) { inset ->
            NavigationPages(navigation, if (glass) Modifier.layerBackdrop(contentBackdrop) else Modifier) { displayedPage ->
            AppScrollContent(inset, state = pageScrollStates[displayedPage]) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Galaxy box", color = Mint, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { aboutVisible = true }, modifier = Modifier.semantics { contentDescription = "關於本應用" }) { Text("關於") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(Pages[displayedPage], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("免 Root · Shizuku 可選 · v0.7.0", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                when (displayedPage) {
                    0 -> {
                        dashboardItems(state, { hardwareCategory = it; page = 1 }, { page = 2 }, { temperatureId = it })
                        item { InfoCard("顯示效果") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("液態玻璃", Modifier.weight(1f))
                                Switch(checked = glassEnabled, onCheckedChange = {
                                    glassEnabled = it; glassFailed = false
                                    preferences.edit { putBoolean("liquid_glass", it) }
                                }, modifier = Modifier.semantics { contentDescription = "液態玻璃" })
                            }
                            Text(when {
                                glass -> "已啟用 · 透底模糊、弧面折射與柔和亮邊。"
                                glassEnabled && glassFailed -> "特效未能啟動，已使用一般卡片；可關閉後重新開啟。"
                                glassEnabled -> "目前無法啟用特效，使用一般卡片；需 Android 13 以上、硬體加速且關閉省電模式。"
                                else -> "預設關閉；開啟後套用至導覽列與資訊卡片。"
                            },
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text("底部導覽列可點按或左右拖動切換分頁。", color = Muted, style = MaterialTheme.typography.bodySmall)
                            RefreshStatus()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("導覽列背景", Modifier.weight(1f))
                                Switch(checked = navigationBackground, onCheckedChange = {
                                    navigationBackground = it; preferences.edit { putBoolean("navigation_background", it) }
                                }, modifier = Modifier.semantics { contentDescription = "導覽列背景" })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("system" to "跟隨系統", "light" to "淺色", "dark" to "深色").forEach { (mode, label) ->
                                    FilterChip(selected = themeMode == mode, onClick = {
                                        themeMode = mode; preferences.edit { putString("theme", mode) }
                                    }, label = { Text(label) })
                                }
                            }
                        } }
                    }
                    1 -> {
                        item { TextButton(onClick = { catalogVisible = true }) { Text("離線裝置與處理器目錄") } }
                        if (hardwareCategory in listOf(HardwareCategory.NETWORK, HardwareCategory.AUDIO)) item {
                            TextButton(onClick = { model.refreshConnectionDetails(hardwareCategory) }) { Text("重新讀取連線與裝置") }
                        }
                        hardwareItems(state, hardwareCategory, { hardwareCategory = it }, { sourceReading = it }, model::rescanTemperatureSources,
                            { temperatureId = it }, expandedTemperatures, { id -> expandedTemperatures = if (id in expandedTemperatures) expandedTemperatures - id else expandedTemperatures + id }, model::refreshCameras, model::rescanGpuSources, categoryScroll = hardwareTabScroll)
                        if (hardwareCategory == HardwareCategory.GPU) item {
                            ShizukuDiagnosticsCard(ProbeKind.GPU, state.shizuku, state.privilegedBusy,
                                state.privilegedGpu, state.privilegedError.takeIf { state.privilegedKind == ProbeKind.GPU },
                                { model.requestPrivileged(ProbeKind.GPU) }, model::cancelPrivileged, model::refreshShizuku, { sourceReading = it })
                        }
                    }
                    2 -> {
                        item { BatteryReadOptionsCard(state.snapshot.chargingPower) }
                        item { ShizukuDiagnosticsCard(ProbeKind.BATTERY, state.shizuku, state.privilegedBusy,
                            state.privilegedBattery, state.privilegedError.takeIf { state.privilegedKind == ProbeKind.BATTERY },
                            { model.requestPrivileged(ProbeKind.BATTERY) }, model::cancelPrivileged, model::refreshShizuku, { sourceReading = it }) }
                        item { ChargingPowerCard(state.snapshot.chargingPower, state.temperatureNowMs, state.snapshot.battery.firstOrNull { it.label == "充放電狀態" }?.value) }
                        item { ChargingNotificationCard() }
                        item { Notice("電池「良好」是系統狀態，不是容量健康百分比。循環數、容量與溫度依手機韌體支援程度回報。") }
                        batteryInformation(state.snapshot.battery)
                        item { ImportCard(state, model) }
                    }
                    3 -> {
                        item { ReadingCard(Reading("Android / API", "${Build.VERSION.RELEASE} / ${Build.VERSION.SDK_INT}", "Build.VERSION")) }
                        item { ReadingCard(Reading("安全性更新", Build.VERSION.SECURITY_PATCH, "Build.VERSION.SECURITY_PATCH")) }
                        item { ReadingCard(Reading("顯示版本", Build.DISPLAY, "Build.DISPLAY")) }
                        item { ReadingCard(Reading("增量版本", Build.VERSION.INCREMENTAL, "Build.VERSION.INCREMENTAL")) }
                        item { FirmwareLinksCard() }
                    }
                    4 -> {
                        deviceTestGrid(availableTests, testResults) { testQueue = it }
                        item { InfoCard("貼膜輔助") {
                            Text("全螢幕檢視、格線對位與自訂邊框。", color = Muted)
                            Button(onClick = { screenTool = "貼膜" }) { Text("開啟貼膜輔助") }
                        } }
                        if (testResults.isNotEmpty()) item { InfoCard("本次手動檢查紀錄") {
                            availableTests.forEach { test -> testResults[test.name]?.let { Text("${test.title} · $it") } }
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard.setPrimaryClip(ClipData.newPlainText("Galaxy box 手動檢測", "${Build.MODEL} · 使用者手動觀察，非官方認證\n" +
                                    availableTests.mapNotNull { test -> testResults[test.name]?.let { "${test.title}: $it" } }.joinToString("\n")))
                            }) { Text("複製手動檢測摘要") }
                            TextButton(onClick = { testResults = emptyMap() }) { Text("清除本次紀錄") }
                        } }
                    }
                }
                state.monitorError?.let { item { Notice(it) } }
                item { Text("獨立開發工具，與 Samsung 無隸屬關係。", color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
            }
        }
        }
      }
        testQueue.firstOrNull()?.let { test ->
            key(test) {
                DeviceTestSession(test, state.snapshot, testQueue.size, { result ->
                    testResults = testResults + (test.name to result); testQueue = testQueue.drop(1)
                }, { testQueue = emptyList() })
            }
        }
        if (aboutVisible) { AboutApp { aboutVisible = false } }
        if (catalogVisible) { HardwareCatalogSheet { catalogVisible = false } }
        sourceReading?.let { ReadingSourceSheet(it) { sourceReading = null } }
        temperatureId?.let { id ->
            state.temperatures.firstOrNull { it.sensor.id == id }?.let { reading ->
                TemperatureDetailSheet(reading, state.temperatureHistory[id].orEmpty(), state.temperatureNowMs) { temperatureId = null }
            }
        }
      }
    }
}

@Composable fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Mint, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable private fun Notice(text: String) { Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium) }
@Composable internal fun ReadingCard(reading: Reading) {
    InfoCard(reading.label) {
        Text(reading.value ?: "未知", style = MaterialTheme.typography.titleLarge,
            color = if (reading.value == null) Muted else MaterialTheme.colorScheme.onSurface)
        Text("來源：${reading.source}", style = MaterialTheme.typography.bodySmall, color = Muted)
        if (reading.detail.isNotEmpty()) Text(reading.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
@Composable internal fun Sparkline(values: List<Float>) {
    if (values.size < 2) Text("收集樣本中…", color = Muted)
    val lineColor = Mint
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        (0..4).forEach { i -> drawLine(Color(0xFF29374C), Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4)) }
        if (values.size >= 2) {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * size.width / (values.size - 1)
                val y = size.height * (1 - value.coerceIn(0f, 1f))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        }
    }
    Text("縱軸 0–100% · ${values.size} 筆", style = MaterialTheme.typography.labelSmall, color = Muted)
}
@Composable private fun ImportCard(state: ToolboxState, model: ToolboxViewModel) {
    BugreportImportCard(state, model::importReport, model::cancelImport, model::clearReport)
}

