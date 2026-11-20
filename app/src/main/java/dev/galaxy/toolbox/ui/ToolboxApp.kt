package dev.galaxy.toolbox.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import dev.galaxy.toolbox.ToolboxState
import dev.galaxy.toolbox.ToolboxViewModel
import dev.galaxy.toolbox.model.FirmwareQuery
import dev.galaxy.toolbox.model.Reading
import dev.galaxy.toolbox.model.HardwareCategory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import dev.galaxy.toolbox.diagnostics.*
import dev.galaxy.toolbox.widgets.WidgetUpdates
import dev.galaxy.toolbox.charging.ChargingNotification
import android.content.ClipData
import android.content.ClipboardManager
import top.yukonga.miuix.kmp.preference.SwitchPreference

private val Pages = listOf("總覽", "硬體", "電池", "更新", "檢測")

@Composable
fun ToolboxApp(model: ToolboxViewModel, modifier: Modifier = Modifier, overviewRequest: Int = 0) {
    val state by model.state.collectAsStateWithLifecycle()
    var aboutVisible by rememberSaveable { mutableStateOf(false) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var backHistory by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
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
    LaunchedEffect(overviewRequest) {
        if (overviewRequest > 0) {
            aboutVisible = false; settingsVisible = false; screenTool = null; testQueue = emptyList(); sourceReading = null; temperatureId = null
            backHistory = arrayListOf(); hardwareCategory = HardwareCategory.CPU; page = 0
        }
    }
    val temperatureDetailVisible = temperatureId != null && state.temperatures.any { it.sensor.id == temperatureId }
    fun navigate(nextPage: Int, nextCategory: HardwareCategory) {
        if (nextPage !in Pages.indices) return
        val current = ToolboxRoute(page, hardwareCategory)
        val target = ToolboxRoute(nextPage, nextCategory)
        backHistory = ArrayList(ToolboxNavigationHistory.visit(backHistory, current, target))
        hardwareCategory = nextCategory
        page = nextPage
    }
    val canReturnToPage = page != 0 && screenTool == null && testQueue.isEmpty() &&
        !aboutVisible && !settingsVisible && sourceReading == null && !temperatureDetailVisible
    fun returnToPreviousPage() {
        val (previous, remaining) = ToolboxNavigationHistory.previous(backHistory, ToolboxRoute(page, hardwareCategory))
        backHistory = ArrayList(remaining)
        hardwareCategory = previous.hardwareCategory
        page = previous.page
    }
    if (Build.VERSION.SDK_INT >= 34) {
        PredictiveBackHandler(enabled = canReturnToPage) { progress ->
            val (previous, remaining) = ToolboxNavigationHistory.previous(backHistory, ToolboxRoute(page, hardwareCategory))
            val adjacent = kotlin.math.abs(previous.page - page) == 1
            var lastProgress = 0f
            if (adjacent) navigation.hold()
            try {
                progress.collect { event ->
                    if (adjacent) {
                        val fraction = event.progress.coerceIn(0f, 1f)
                        navigation.dragBy((previous.page - page) * (fraction - lastProgress), 0f)
                        lastProgress = fraction
                    }
                }
                backHistory = ArrayList(remaining)
                hardwareCategory = previous.hardwareCategory
                page = previous.page
            } catch (cancelled: CancellationException) {
                if (adjacent) navigation.cancelGesture()
                throw cancelled
            }
        }
    } else BackHandler(enabled = canReturnToPage) { returnToPreviousPage() }
    val context = LocalContext.current
    val settingsScope = rememberCoroutineScope()
    val availableTests by produceState(initialValue = emptyList<DeviceTest>(), context) {
        value = withContext(Dispatchers.IO) {
            runCatching { TestCatalogue.available(TestCatalogue.read(context)) }.getOrDefault(emptyList())
        }
    }
    val preferences = remember(context) { context.getSharedPreferences("display", android.content.Context.MODE_PRIVATE) }
    val appearanceRepository = remember(context) { AppearanceRepository(context) }
    var appearance by remember(appearanceRepository) { mutableStateOf(appearanceRepository.legacySettings()) }
    LaunchedEffect(appearanceRepository) {
        appearanceRepository.migrateLegacy()
        appearanceRepository.settings.collect { appearance = it }
    }
    val visualStyle = appearance.style
    val glassEnabled = appearance.glassEnabled
    val powerSave = rememberPowerSaveMode()
    val glassAvailable = rememberGlassAvailable()
    var glassFailed by remember { mutableStateOf(false) }
    val glassTier = GlassCompatibility.tier(visualStyle, glassEnabled, appearance.reduceTransparency,
        powerSave, Build.VERSION.SDK_INT, glassAvailable, false, GlassCompatibility.refractionSupported)
    val glass = glassTier == GlassTier.REFRACTIVE || glassTier == GlassTier.BLUR
    var navigationBackground by rememberSaveable { mutableStateOf(preferences.getBoolean("navigation_background", true)) }
    var themeMode by rememberSaveable { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
    var language by rememberSaveable { mutableStateOf(AppDisplayPreferences.language(context)) }
    var temperatureUnit by rememberSaveable { mutableStateOf(AppDisplayPreferences.temperatureUnit(context)) }
    var backgroundState by remember { mutableStateOf(BackgroundStore.load(context)) }
    val activeBackground = backgroundState.active.takeIf { backgroundState.enabled }
    val backgroundMedia by produceState<BackgroundMedia?>(null, activeBackground?.fileName) {
        value = activeBackground?.let { preset -> withContext(Dispatchers.IO) { BackgroundStore.decode(BackgroundStore.file(context, preset), preset.format) } }
    }
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> activeBackground?.let { it.luminance * it.brightness < .38f } ?: isSystemInDarkTheme()
    }
    val backdrops = rememberAppBackdrops()
    val backgroundBackdrop = backdrops.background
    val contentBackdrop = backdrops.content
    val navigationBackdrop = backdrops.controls
    val view = LocalView.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
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
    GalaxyBoxTheme(dark, visualStyle, themeMode) {
      val displayedPages = remember(language) { Pages.map { AppText.translate(it, language) } }
      CompositionLocalProvider(
          LocalGlass provides GlassEnvironment(glass, navigationBackdrop) { view.post { glassFailed = true } },
          LocalGlassTier provides glassTier,
          LocalCardBackdrop provides backgroundBackdrop,
          LocalAppLanguage provides language,
          LocalTemperatureDisplayUnit provides temperatureUnit,
      ) {
      Box(modifier.fillMaxSize()) {
        if (activeBackground == null) GalaxyBackdrop(if (glass) Modifier.captureAppBackdrop(backgroundBackdrop) else Modifier)
        else CustomBackgroundLayer(activeBackground, backgroundMedia,
            if (glass) Modifier.captureAppBackdrop(backgroundBackdrop) else Modifier)
        if (screenTool != null) {
            if (screenTool == "貼膜") FilmAssistant { screenTool = null }
            else ScreenTool(screenTool!!) { screenTool = null }
        } else {
        val pageContent: @Composable (PaddingValues) -> Unit = { inset ->
            NavigationPages(navigation, if (glass) Modifier.captureAppBackdrop(contentBackdrop) else Modifier) { displayedPage ->
            AppScrollContent(inset, state = pageScrollStates[displayedPage]) {
                if (visualStyle == AppStyle.LIQUID_GLASS) item(key = "page-heading", contentType = "heading") {
                    AppLargeTitle(displayedPages[displayedPage], pageScrollStates[displayedPage])
                }
                when (displayedPage) {
                    0 -> {
                        dashboardItems(state, { navigate(1, it) }, { navigate(2, hardwareCategory) }, { temperatureId = it })
                        item(key = "overview-refresh", contentType = "status") { RefreshStatus() }
                    }
                    1 -> {
                        if (hardwareCategory in listOf(HardwareCategory.NETWORK, HardwareCategory.AUDIO)) item(key = "hardware-refresh-connection", contentType = "action") {
                            GalaxyTextButton(text = "重新讀取連線與裝置", onClick = { model.refreshConnectionDetails(hardwareCategory) })
                        }
                        hardwareItems(state, hardwareCategory, { navigate(1, it) }, { sourceReading = it }, model::rescanTemperatureSources,
                            { temperatureId = it }, expandedTemperatures, { id -> expandedTemperatures = if (id in expandedTemperatures) expandedTemperatures - id else expandedTemperatures + id }, model::refreshCameras, model::rescanGpuSources, categoryScroll = hardwareTabScroll)
                        if (hardwareCategory == HardwareCategory.GPU) item(key = "hardware-gpu-shizuku", contentType = "source-card") {
                            ShizukuDiagnosticsCard(ProbeKind.GPU, state.shizuku, state.privilegedBusy,
                                state.privilegedGpu, state.privilegedError.takeIf { state.privilegedKind == ProbeKind.GPU },
                                { model.requestPrivileged(ProbeKind.GPU) }, model::cancelPrivileged, model::refreshShizuku, { sourceReading = it })
                        }
                    }
                    2 -> {
                        item(key = "battery-options", contentType = "settings-card") { BatteryReadOptionsCard(state.snapshot.chargingPower) }
                        item(key = "battery-shizuku", contentType = "source-card") { ShizukuDiagnosticsCard(ProbeKind.BATTERY, state.shizuku, state.privilegedBusy,
                            state.privilegedBattery, state.privilegedError.takeIf { state.privilegedKind == ProbeKind.BATTERY },
                            { model.requestPrivileged(ProbeKind.BATTERY) }, model::cancelPrivileged, model::refreshShizuku, { sourceReading = it }) }
                        item(key = "battery-power", contentType = "power-card") { ChargingPowerCard(state.snapshot.chargingPower, state.temperatureNowMs, state.snapshot.battery.firstOrNull { it.label == "充放電狀態" }?.value) }
                        item(key = "battery-charging-test", contentType = "power-card") {
                            ChargingTestCard(state.chargingTest, model::startChargingTest, model::stopChargingTest)
                        }
                        item(key = "battery-notification", contentType = "settings-card") { ChargingNotificationCard() }
                        item(key = "battery-notice", contentType = "notice") { Notice("電池「良好」是系統狀態，不是容量健康百分比。循環數、容量與溫度依手機韌體支援程度回報。") }
                        batteryInformation(state.snapshot.battery)
                    }
                    3 -> {
                        item(key = "updates-android", contentType = "reading") { ReadingCard(Reading("Android / API", "${Build.VERSION.RELEASE} / ${Build.VERSION.SDK_INT}", "Build.VERSION")) }
                        item(key = "updates-patch", contentType = "reading") { ReadingCard(Reading("安全性更新", Build.VERSION.SECURITY_PATCH, "Build.VERSION.SECURITY_PATCH")) }
                        item(key = "updates-display", contentType = "reading") { ReadingCard(Reading("顯示版本", Build.DISPLAY, "Build.DISPLAY")) }
                        item(key = "updates-incremental", contentType = "reading") { ReadingCard(Reading("增量版本", Build.VERSION.INCREMENTAL, "Build.VERSION.INCREMENTAL")) }
                        item(key = "updates-firmware", contentType = "firmware-card") { FirmwareLinksCard() }
                    }
                    4 -> {
                        deviceTestGrid(availableTests, testResults) { testQueue = it }
                        item(key = "tests-floating-monitor", contentType = "action-card") { FloatingMonitorCard() }
                        item(key = "tests-performance-notification", contentType = "action-card") { PerformanceNotificationCard() }
                        item(key = "tests-app-usage", contentType = "action-card") { AppUsageCard() }
                        if (Build.MANUFACTURER.equals("samsung", true)) item(key = "tests-samsung-apps", contentType = "action-card") { SamsungOfficialApps() }
                        if (SamsungServiceCodes.availableOn(context)) item(key = "tests-samsung-codes", contentType = "action-card") { SamsungServiceShortcuts() }
                        item(key = "tests-film", contentType = "action-card") { InfoCard("貼膜輔助") {
                            GalaxyText("全螢幕檢視、格線對位與自訂邊框。", color = Muted)
                            GalaxyPrimaryTextButton(text = "開啟貼膜輔助", onClick = { screenTool = "貼膜" })
                        } }
                        if (testResults.isNotEmpty()) item(key = "tests-results", contentType = "result-card") { InfoCard("本次手動檢查紀錄") {
                            availableTests.forEach { test -> testResults[test.name]?.let { GalaxyText("${test.title} · $it") } }
                            GalaxyTextButton(onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                val text = buildString {
                                    appendLine("${Build.MODEL} · ${AppText.forContext(context, "使用者手動觀察，非官方認證")}")
                                    availableTests.forEach { test -> testResults[test.name]?.let { result ->
                                        appendLine("${AppText.forContext(context, test.title)}: ${AppText.forContext(context, result)}")
                                    } }
                                }.trimEnd()
                                clipboard.setPrimaryClip(ClipData.newPlainText(AppText.forContext(context, "Galaxy box 手動檢測"), text))
                            }) { GalaxyText("複製手動檢測摘要") }
                            GalaxyTextButton(text = "清除本次紀錄", onClick = { testResults = emptyMap() })
                        } }
                    }
                }
                state.monitorError?.let { item(key = "page-monitor-error", contentType = "notice") { Notice(it) } }
                item(key = "page-disclaimer", contentType = "disclaimer") { GalaxyText("獨立開發工具，與 Samsung 無隸屬關係。", color = Muted, style = GalaxyTypography.bodySmall) }
            }
            }
            if (visualStyle == AppStyle.MIUIX && aboutVisible) {
                AboutApp { aboutVisible = false }
            }
        }
        AppScaffold(displayedPages, page, { navigate(it, hardwareCategory) }, navigationBackdrop,
            navigation, navigationBackground, pageScrollStates[page],
            onSettings = { settingsVisible = true }, onAbout = { aboutVisible = true },
            content = pageContent)
      }
        testQueue.firstOrNull()?.let { test ->
            key(test) {
                DeviceTestSession(test, state.snapshot, testQueue.size, { result ->
                    testResults = testResults + (test.name to result); testQueue = testQueue.drop(1)
                }, { testQueue = emptyList() })
            }
        }
        if (aboutVisible && visualStyle != AppStyle.MIUIX) { AboutApp { aboutVisible = false } }
        sourceReading?.let { ReadingDetailSheet(it) { sourceReading = null } }
        temperatureId?.let { id ->
            state.temperatures.firstOrNull { it.sensor.id == id }?.let { reading ->
                TemperatureDetailSheet(reading, state.temperatureHistory[id].orEmpty(), state.temperatureNowMs) { temperatureId = null }
            }
        }
        SettingsPanel(
            visible = settingsVisible, onDismiss = { settingsVisible = false },
            visualStyle = visualStyle, onVisualStyle = { choice ->
                appearance = appearance.copy(style = choice)
                settingsScope.launch { appearanceRepository.setStyle(choice) }
            },
            glassEnabled = glassEnabled,
            glassStatus = when {
                glass && glassFailed -> "玻璃已啟用；部分元件使用簡化效果。"
                glassTier == GlassTier.REFRACTIVE -> "導覽與浮層使用柔和模糊及折射。"
                glassTier == GlassTier.BLUR -> "導覽與浮層使用純模糊效果。"
                glassTier == GlassTier.STATIC -> "目前使用節能或相容的靜態半透明表面。"
                else -> "目前使用清晰的實色表面。"
            },
            onGlass = { choice ->
                appearance = appearance.copy(glassEnabled = choice)
                glassFailed = false
                settingsScope.launch { appearanceRepository.setGlassEnabled(choice) }
            },
            reduceTransparency = appearance.reduceTransparency,
            onReduceTransparency = { choice ->
                appearance = appearance.copy(reduceTransparency = choice)
                settingsScope.launch { appearanceRepository.setReduceTransparency(choice) }
            },
            navigationBackground = navigationBackground,
            onNavigationBackground = { navigationBackground = it; preferences.edit { putBoolean("navigation_background", it) } },
            themeMode = themeMode,
            onThemeMode = { themeMode = it; preferences.edit { putString("theme", it) } },
            language = language,
            onLanguage = { choice ->
                language = choice
                preferences.edit { putString("language", choice.key) }
                if (ChargingNotification.enabled(context)) runCatching { ChargingNotification.createChannel(context) }
                val snapshot = state.snapshot
                settingsScope.launch(Dispatchers.IO) { runCatching { WidgetUpdates.publishAll(context.applicationContext, snapshot) } }
            },
            temperatureUnit = temperatureUnit,
            onTemperatureUnit = { temperatureUnit = it; preferences.edit { putString("temperature_unit", it.key) } },
            backgroundState = backgroundState,
            onBackgroundState = { backgroundState = it; BackgroundStore.save(context, it) },
            backgroundMedia = backgroundMedia,
        )
      }
    }
}
}

@Composable fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixGroupedSection(title = title, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GalaxyText(title, color = GalaxyPalette.onSurface,
                    style = GalaxyTypography.titleMedium, fontWeight = FontWeight.SemiBold)
                content()
            }
        }
    }
}
@Composable private fun Notice(text: String) { GalaxyText(text, color = Muted, style = GalaxyTypography.bodyMedium) }
@Composable internal fun ReadingCard(reading: Reading) {
    InfoCard(reading.label) {
        GalaxyText(reading.value ?: "未知", style = GalaxyTypography.titleLarge,
            color = if (reading.value == null) Muted else GalaxyPalette.onSurface)
        if (reading.detail.isNotEmpty()) GalaxyText(reading.detail, style = GalaxyTypography.bodySmall, color = Muted)
    }
}
@Composable internal fun Sparkline(values: List<Float>) {
    if (values.size < 2) GalaxyText("收集樣本中…", color = Muted)
    val lineColor = Mint
    val gridColor = GalaxyPalette.onSurfaceVariant.copy(alpha = .24f)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        (0..4).forEach { i -> drawLine(gridColor, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4)) }
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
    GalaxyText("縱軸 0–100% · ${values.size} 筆", style = GalaxyTypography.labelSmall, color = Muted)
}

