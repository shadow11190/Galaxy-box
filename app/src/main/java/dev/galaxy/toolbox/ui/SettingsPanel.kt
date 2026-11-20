package dev.galaxy.toolbox.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen in-app overlay so Backdrop can sample the same Compose background layer. */
@Composable
internal fun SettingsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    visualStyle: AppStyle,
    onVisualStyle: (AppStyle) -> Unit,
    glassEnabled: Boolean,
    glassStatus: String,
    onGlass: (Boolean) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparency: (Boolean) -> Unit,
    navigationBackground: Boolean,
    onNavigationBackground: (Boolean) -> Unit,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    temperatureUnit: TemperatureDisplayUnit,
    onTemperatureUnit: (TemperatureDisplayUnit) -> Unit,
    backgroundState: CustomBackgroundState,
    onBackgroundState: (CustomBackgroundState) -> Unit,
    backgroundMedia: BackgroundMedia?,
) {
    val context = LocalContext.current
    val displayLanguage = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val currentVisible by rememberUpdatedState(visible)
    var pending by remember { mutableStateOf<BackgroundPreset?>(null) }
    var editingExisting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var restoring by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    fun import(uri: Uri) {
        scope.launch {
            importError = null
            runCatching { BackgroundStore.import(context.applicationContext, uri) }
                .onSuccess { if (currentVisible) { editingExisting = false; pending = it } else BackgroundStore.file(context, it).delete() }
                .onFailure { importError = it.message ?: "無法讀取圖片。" }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) import(uri) }
    val activity = context as? Activity
    val dropTarget = remember(activity) { object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val androidEvent = event.toAndroidDragEvent()
            val uri = androidEvent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return false
            val grant = activity?.requestDragAndDropPermissions(androidEvent)
            scope.launch {
                try {
                    importError = null
                    val imported = BackgroundStore.import(context.applicationContext, uri)
                    if (currentVisible) { editingExisting = false; pending = imported } else BackgroundStore.file(context, imported).delete()
                }
                catch (error: Exception) { importError = error.message ?: "無法讀取圖片。" }
                finally { grant?.release() }
            }
            return true
        }
    } }
    fun dismissTop() {
        when {
            pending != null -> { if (!editingExisting) pending?.let { BackgroundStore.file(context, it).delete() }; pending = null; editingExisting = false }
            restoring -> restoring = false
            deletingId != null -> deletingId = null
            renameId != null -> renameId = null
            else -> onDismiss()
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            if (!editingExisting) pending?.let { BackgroundStore.file(context, it).delete() }
            pending = null
            editingExisting = false
            restoring = false
            deletingId = null
            renameId = null
        }
    }
    BackHandler(enabled = visible) { dismissTop() }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.key == Key.Escape) { dismissTop(); true } else false }) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .60f)).clickable(onClick = ::dismissTop))
            val wide = maxWidth >= 600.dp
            val shape = if (visualStyle == AppStyle.MIUIX) GalaxySquircleShape(30.dp) else RoundedCornerShape(26.dp)
            AnimatedVisibility(visible, modifier = Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it / 4 }, exit = fadeOut() + slideOutVertically { it / 4 }) {
                val glass = LocalGlass.current.enabled
                val settingsScroll = rememberScrollState()
                LaunchedEffect(visible) { if (visible) settingsScroll.scrollTo(0) }
                Surface(
                    modifier = Modifier.then(if (wide) Modifier.widthIn(max = 500.dp).fillMaxHeight() else Modifier.fillMaxWidth().fillMaxHeight(.88f))
                        .galaxyGlass(shape, floating = true),
                    shape = shape, color = if (glass) Color.Transparent else Panel,
                ) {
                  CompositionLocalProvider(LocalReadableGlass provides true) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            GalaxyText("設定", style = GalaxyTypography.headlineMedium, fontWeight = FontWeight.Bold)
                            HeaderIconButton(HeaderIcon.CLOSE, "關閉", grouped = true) { dismissTop() }
                        }
                        Column(Modifier.weight(1f).verticalScroll(settingsScroll)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCard("外觀") {
                            GalaxyText("介面風格", style = GalaxyTypography.titleSmall)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppStyle.entries.forEach { GalaxyChoiceChip(visualStyle == it, { onVisualStyle(it) }, it.label) }
                            }
                            AppSwitchRow("液態玻璃", glassEnabled, onGlass)
                            GalaxyText(glassStatus, color = Muted, style = GalaxyTypography.bodySmall)
                            AppSwitchRow("降低透明度", reduceTransparency, onReduceTransparency)
                            AppSwitchRow("導覽列背景", navigationBackground, onNavigationBackground)
                            GalaxyText("主題", style = GalaxyTypography.titleSmall)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (listOf("system" to "跟隨系統", "light" to "淺色", "dark" to "深色") +
                                    if (android.os.Build.VERSION.SDK_INT >= 31) listOf("monet" to "動態取色") else emptyList()).forEach { (key, label) ->
                                    GalaxyChoiceChip(themeMode == key, { onThemeMode(key) }, label)
                                }
                            }
                        }
                        InfoCard("背景") {
                            AppSwitchRow("自訂背景", backgroundState.enabled && backgroundState.active != null,
                                { onBackgroundState(backgroundState.copy(enabled = it && backgroundState.active != null)) })
                            val active = backgroundState.active
                            if (active != null) {
                                Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(18.dp)).then(if (visualStyle == AppStyle.MIUIX)
                                    Modifier.miuixGroupedSurface() else Modifier.background(Panel, RoundedCornerShape(18.dp)))) {
                                    CustomBackgroundLayer(active, backgroundMedia)
                                    Box(Modifier.align(Alignment.Center).padding(10.dp)) {
                                        GlassCard { GalaxyText("Galaxy Box · 即時預覽", Modifier.padding(12.dp)) }
                                    }
                                }
                                GalaxyText(active.name, style = GalaxyTypography.bodyMedium)
                                GalaxyTextButton(text = "調整裁切", onClick = { editingExisting = true; pending = active })
                            } else GalaxyText("無自訂背景", color = Muted)
                            GalaxyTextButton(text = "選擇圖片", onClick = { picker.launch(arrayOf("image/*", "application/octet-stream")) },
                                modifier = Modifier.fillMaxWidth().dragAndDropTarget(
                                    shouldStartDragAndDrop = { true },
                                    target = dropTarget).semantics { contentDescription = AppText.translate("選擇或拖曳圖片", displayLanguage) })
                            GalaxyText("可選 JPG、PNG、WebP、AVIF、BMP、SVG、HEIC、GIF；檔案上限 20 MB。", color = Muted, style = GalaxyTypography.bodySmall)
                            importError?.let { GalaxyText(it, color = GalaxyPalette.error) }
                            if (active != null) {
                                GalaxyText("填滿方式", style = GalaxyTypography.titleSmall)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BackgroundFill.entries.forEach { mode -> GalaxyChoiceChip(active.fill == mode, { onBackgroundState(backgroundState.replace(active.copy(fill = mode))) }, mode.label) }
                                }
                                SettingSlider("模糊度", active.blur, 0f..24f) { onBackgroundState(backgroundState.replace(active.copy(blur = it))) }
                                SettingSlider("亮度", active.brightness, .25f..1.5f) { onBackgroundState(backgroundState.replace(active.copy(brightness = it))) }
                                SettingSlider("遮罩不透明度", active.overlay, 0f..0.8f) { onBackgroundState(backgroundState.replace(active.copy(overlay = it))) }
                                GalaxyTextButton(text = "儲存目前組合", onClick = { renameId = "new"; editName = "${active.name} 副本" })
                            }
                            GalaxyText("背景組合", style = GalaxyTypography.titleSmall)
                            if (backgroundState.presets.isEmpty()) GalaxyText("尚未儲存背景組合", color = Muted)
                            backgroundState.presets.forEach { preset ->
                                val thumbnail by produceState<BackgroundMedia?>(null, preset.fileName) {
                                    value = withContext(Dispatchers.IO) { BackgroundStore.thumbnail(context, preset) }
                                }
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) { thumbnail?.let { CustomBackgroundLayer(preset, it) } }
                                        GalaxyText(preset.name, Modifier.weight(1f), maxLines = 1)
                                    }
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        GalaxyTextButton(text = "套用", onClick = { onBackgroundState(backgroundState.copy(activeId = preset.id, enabled = true)) })
                                        GalaxyTextButton(text = "重新命名", onClick = { renameId = preset.id; editName = preset.name })
                                        GalaxyTextButton(text = "刪除", onClick = { deletingId = preset.id })
                                    }
                                }
                            }
                            GalaxyTextButton(text = "恢復預設背景", onClick = { restoring = true })
                        }
                        InfoCard("語言") {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AppLanguage.entries.forEach { GalaxyChoiceChip(language == it, { onLanguage(it) }, it.title) }
                            }
                        }
                        InfoCard("單位") {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TemperatureDisplayUnit.entries.forEach { GalaxyChoiceChip(temperatureUnit == it, { onTemperatureUnit(it) }, it.title) }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        }
                    }
                  }
                }
            }
            if (pending != null) CropBackgroundPanel(pending!!, onChange = { pending = it }, onCancel = {
                if (!editingExisting) pending?.let { BackgroundStore.file(context, it).delete() }
                pending = null; editingExisting = false
            }, onApply = { chosen ->
                onBackgroundState(if (editingExisting) backgroundState.replace(chosen).copy(enabled = true, activeId = chosen.id)
                    else backgroundState.copy(enabled = true, activeId = chosen.id, presets = backgroundState.presets + chosen))
                pending = null; editingExisting = false
            })
            if (restoring) ConfirmationPanel("恢復預設背景？", "已儲存的背景組合會保留。", onCancel = { restoring = false }) {
                onBackgroundState(backgroundState.copy(enabled = false, activeId = null)); restoring = false
            }
            deletingId?.let { id -> ConfirmationPanel("刪除背景組合？", "此操作會移除圖片檔案。", onCancel = { deletingId = null }) {
                onBackgroundState(BackgroundStore.delete(context, backgroundState, id)); deletingId = null
            } }
            renameId?.let { id -> NamePanel(editName, { editName = it }, { renameId = null }) {
                val name = editName.trim().take(40).ifBlank { "背景組合" }
                if (id == "new") {
                    val source = backgroundState.active
                    if (source != null) scope.launch {
                        runCatching { BackgroundStore.duplicate(context, source, name) }
                            .onSuccess { onBackgroundState(backgroundState.copy(presets = backgroundState.presets + it)) }
                            .onFailure { importError = it.message ?: "無法儲存背景組合。" }
                    }
                } else backgroundState.presets.firstOrNull { it.id == id }?.let { onBackgroundState(backgroundState.replace(it.copy(name = name))) }
                renameId = null
            } }
        }
    }
}

@Composable private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val displayLabel = AppText.translate(label, LocalAppLanguage.current)
    GalaxyText("$displayLabel · ${"%.0f".format(if (label == "模糊度") value else value * 100)}${if (label == "模糊度") "dp" else "%"}")
    GalaxySlider(value, onChange, Modifier.fillMaxWidth(), valueRange = range)
}

@Composable private fun ConfirmationPanel(title: String, message: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AppDialog(show = true, title = title, onDismiss = onCancel) {
        GalaxyText(message)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GalaxyTextButton(text = "取消", onClick = onCancel)
            AppButton(text = "確認", onClick = onConfirm)
        }
    }
}

@Composable private fun NamePanel(name: String, onName: (String) -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AppDialog(show = true, title = "背景名稱", onDismiss = onCancel) {
        GalaxyTextField(name, onName, "背景名稱", Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GalaxyTextButton(text = "取消", onClick = onCancel)
            AppButton(text = "確認", onClick = onConfirm)
        }
    }
}

@Composable private fun CropBackgroundPanel(preset: BackgroundPreset, onChange: (BackgroundPreset) -> Unit,
    onCancel: () -> Unit, onApply: (BackgroundPreset) -> Unit) {
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    val currentPreset by rememberUpdatedState(preset)
    val media by produceState<BackgroundMedia?>(null, preset.fileName) {
        value = withContext(Dispatchers.IO) { BackgroundStore.decode(BackgroundStore.file(context, preset), preset.format, 1024) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .76f)), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.fillMaxWidth().widthIn(max = 620.dp).padding(16.dp)) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GalaxyText("裁切圖片", style = GalaxyTypography.titleLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BackgroundAspect.entries.forEach { aspect -> GalaxyChoiceChip(preset.aspect == aspect, { onChange(preset.copy(aspect = aspect)) }, aspect.label) }
                }
                val aspect = when (preset.aspect) {
                    BackgroundAspect.FREE -> media?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1f
                    BackgroundAspect.SCREEN -> windowSize.width.toFloat() / windowSize.height.coerceAtLeast(1)
                    else -> preset.aspect.ratio ?: 1f
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 250.dp).aspectRatio(aspect.coerceIn(.75f, 2.2f))
                    .pointerInput(preset.id) { detectDragGestures { change, drag ->
                        change.consume()
                        val current = currentPreset
                        onChange(current.copy(panX = (current.panX - drag.x / size.width * 2).coerceIn(-1f, 1f),
                            panY = (current.panY - drag.y / size.height * 2).coerceIn(-1f, 1f)))
                    } }) {
                    CustomBackgroundLayer(preset, media)
                    if (media == null) GalaxyText("圖片載入中…", Modifier.align(Alignment.Center), color = Muted)
                    else Box(Modifier.align(Alignment.Center).padding(14.dp)) {
                        GlassCard { GalaxyText("Galaxy Box · 即時預覽", Modifier.padding(14.dp)) }
                    }
                }
                SettingSlider("縮放", preset.zoom, 1f..4f) { onChange(preset.copy(zoom = it)) }
                SettingSlider("旋轉", preset.rotation, -180f..180f) { onChange(preset.copy(rotation = it)) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GalaxyTextButton(text = "取消", onClick = onCancel)
                    GalaxyTextButton(text = "跳過裁切", onClick = { onApply(preset.copy(aspect = BackgroundAspect.FREE, zoom = 1f, panX = 0f, panY = 0f, rotation = 0f)) })
                    GalaxyPrimaryTextButton(text = "確認", onClick = { onApply(preset) })
                }
            }
        }
    }
}
