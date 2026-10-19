package dev.galaxy.toolbox.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.galaxy.toolbox.model.Snapshot
import dev.galaxy.toolbox.ui.GalaxyBoxTheme
import dev.galaxy.toolbox.ui.InfoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        enableEdgeToEdge()
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val kind = WidgetUpdates.kindFor(this, id)
        setContent {
            GalaxyBoxTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (kind != null) WidgetConfiguration(id, kind, onSave = {
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                    }, onCancel = ::finish) else WidgetChooser { finish() }
                }
            }
        }
    }
}

@Composable private fun WidgetChooser(onClose: () -> Unit) {
    val context = LocalContext.current
    val widgets = WidgetUpdates.installed(context)
    Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("小工具設定", style = MaterialTheme.typography.headlineMedium)
        Text("選擇要調整的桌面小工具。每個小工具分別保存設定。")
        if (widgets.isEmpty()) Text("請先長按桌面 → 小工具 → Galaxy box，加入完整版或精簡版。")
        widgets.forEach { (id, kind) ->
            OutlinedButton(onClick = { context.startActivity(WidgetRenderer.configurationIntent(context, id)) }) {
                Text("${kind.title()} · #$id")
            }
        }
        TextButton(onClick = onClose) { Text("返回") }
    }
}

internal fun WidgetKind.title() = when (this) { WidgetKind.OVERVIEW -> "完整版"; WidgetKind.BATTERY -> "精簡版"; WidgetKind.RESOURCES -> "資源版" }

@Composable private fun WidgetConfiguration(id: Int, kind: WidgetKind, onSave: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WidgetSettingsStore(context) }
    val saver = remember(kind) { mapSaver(save = { it: WidgetSettings -> it.encode() },
        restore = { WidgetSettings.decode(it.mapValues { entry -> entry.value as String }, kind) }) }
    var settings by rememberSaveable(id, stateSaver = saver) { mutableStateOf(store.load(id, kind)) }
    var data by remember { mutableStateOf(WidgetPresentation.from(Snapshot())) }
    var previewHeight by rememberSaveable { mutableIntStateOf(if (kind == WidgetKind.OVERVIEW) 280 else 180) }
    var previewDark by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { data = withContext(Dispatchers.IO) { WidgetDataReader.read(context.applicationContext) } }
    Column(Modifier.safeDrawingPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(6.dp))
        Text("${kind.title()} · #$id", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("即時設定預覽 · 本機快照", color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth.value.toInt()
            Box(Modifier.fillMaxWidth().height(previewHeight.dp)) {
                PreviewChecks(previewDark)
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { FrameLayout(it) }, update = { container ->
                    container.removeAllViews()
                    val remote = WidgetRenderer.create(context, kind, id, data, width, previewHeight, settings, preview = true)
                    val view = remote.apply(context, container)
                    container.addView(view, FrameLayout.LayoutParams(-1, -1))
                })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(180, 280, 380).forEach { height -> FilterChip(selected = previewHeight == height, onClick = { previewHeight = height }, label = { Text("${height}dp 高") }) }
            FilterChip(selected = previewDark, onClick = { previewDark = !previewDark }, label = { Text(if (previewDark) "深色底" else "淺色底") })
        }
        Text("方格只用於檢查透明度，不會寫入小工具。預覽套用實際 RemoteViews 版面；桌面可拉寬、拉高。", style = MaterialTheme.typography.bodySmall)
        InfoCard("背景") {
            WidgetBackground.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.background == mode, onClick = { settings = settings.copy(background = mode) })
                    Text(mode.title)
                }
            }
            Text(when (settings.background) {
                WidgetBackground.GLASS -> "桌面使用靜態玻璃漸層與柔和高光，不會即時取樣桌布；App 內才有 Backdrop 模糊與折射。"
                WidgetBackground.SOLID -> "單一底色，可調背景不透明度。"
                WidgetBackground.TRANSPARENT -> "完整移除底色、漸層、邊框、高光與背景陰影；僅保留文字、數值與資訊進度條。"
            }, style = MaterialTheme.typography.bodySmall)
            Text("背景不透明度 ${settings.opacity}%")
            Slider(value = settings.opacity.toFloat(), onValueChange = { settings = settings.copy(opacity = it.toInt()) },
                valueRange = 0f..100f, enabled = settings.background != WidgetBackground.TRANSPARENT)
            ColorEntry("背景色", settings.backgroundColor) { settings = settings.copy(backgroundColor = it) }
            ColorEntry("文字色", settings.textColor) { settings = settings.copy(textColor = it) }
            ColorEntry("強調色", settings.accentColor) { settings = settings.copy(accentColor = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { settings = settings.copy(backgroundColor = 0xFF162237.toInt(), textColor = 0xFFEDF3FF.toInt(), accentColor = 0xFF76E1C3.toInt()) }) { Text("深底配色") }
                OutlinedButton(onClick = { settings = settings.copy(backgroundColor = 0xFFF8FBFD.toInt(), textColor = 0xFF142537.toInt(), accentColor = 0xFF096B56.toInt()) }) { Text("淺底配色") }
            }
        }
        InfoCard("顯示內容") {
            SettingCheck("日期與時鐘", settings.showClock) { settings = settings.copy(showClock = it) }
            SettingCheck("機型", settings.showModel) { settings = settings.copy(showModel = it) }
            WidgetField.entries.forEach { field -> SettingCheck(field.title, field in settings.fields) {
                settings = settings.copy(fields = if (it) settings.fields + field else settings.fields - field)
            } }
            Text("流量為 TrafficStats 可讀網路介面的收送合計，自開機累計，非每月帳單。受系統支援與存取限制影響，無資料顯示 —。", style = MaterialTheme.typography.bodySmall)
        }
        InfoCard("點擊動作") {
            WidgetClick.entries.forEach { action -> Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.click == action, onClick = { settings = settings.copy(click = action) })
                Text(action.title)
            } }
            Text("↻ 按鈕仍可手動取樣；桌面長按或 App 總覽可再次開啟設定。", style = MaterialTheme.typography.bodySmall)
        }
        Text("數據是有時間標記的快照。系統背景約每 30 分鐘更新，可能延後；時鐘由桌面更新，不持續喚醒 App。", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
            saving = true
            scope.launch {
                val outcome = withContext(Dispatchers.IO) { runCatching { store.save(id, settings); WidgetUpdates.refresh(context.applicationContext, kind) } }
                saving = false
                outcome.fold(onSuccess = { onSave() }, onFailure = { error = "更新失敗，請重試。" })
            }
        }) { Text(if (saving) "套用中…" else "儲存並套用") }
        OutlinedButton(enabled = !saving, onClick = { scope.launch { data = withContext(Dispatchers.IO) { WidgetDataReader.read(context.applicationContext) } } }) { Text("重新取樣預覽資料") }
        TextButton(onClick = onCancel, enabled = !saving) { Text("取消") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable private fun SettingCheck(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(title, Modifier.weight(1f))
    }
}

@Composable private fun ColorEntry(title: String, color: Int, onChange: (Int) -> Unit) {
    var text by rememberSaveable(color) { mutableStateOf("#%06X".format(color and 0xFFFFFF)) }
    OutlinedTextField(text, onValueChange = { value ->
        text = value.take(7)
        WidgetSettings.parseColor(text)?.let(onChange)
    }, label = { Text("$title · #RRGGBB") }, singleLine = true,
        isError = WidgetSettings.parseColor(text) == null, modifier = Modifier.fillMaxWidth())
}

@Composable private fun PreviewChecks(dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val cell = 20.dp.toPx()
        for (x in 0..(size.width / cell).toInt()) for (y in 0..(size.height / cell).toInt()) {
            val shade = if (dark) { if ((x + y) % 2 == 0) 0xFF253347 else 0xFF2D3D52 } else { if ((x + y) % 2 == 0) 0xFFE0E5E9 else 0xFFF1F4F7 }
            drawRect(Color(shade), Offset(x * cell, y * cell), Size(cell, cell))
        }
    }
}
