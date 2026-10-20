package dev.galaxy.toolbox.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FilmBackgrounds = listOf("白" to Color.White, "黑" to Color.Black, "灰" to Color(0xFF808080), "深藍" to Color(0xFF0C1424))
private val FilmInks = listOf("黑" to Color.Black, "白" to Color.White, "薄荷" to Color(0xFF76E1C3), "藍" to Color(0xFF4C90FF))

@Composable
internal fun FilmAssistant(onClose: () -> Unit) {
    var background by rememberSaveable { mutableIntStateOf(0) }
    var ink by rememberSaveable { mutableIntStateOf(0) }
    var border by rememberSaveable { mutableFloatStateOf(4f) }
    var insetX by rememberSaveable { mutableFloatStateOf(0f) }
    var insetY by rememberSaveable { mutableFloatStateOf(0f) }
    var topRadius by rememberSaveable { mutableFloatStateOf(0f) }
    var bottomRadius by rememberSaveable { mutableFloatStateOf(0f) }
    var systemCorners by rememberSaveable { mutableStateOf(true) }
    var reportedCorners by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var cross by rememberSaveable { mutableStateOf(true) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf("Galaxy box") }
    var showLabel by rememberSaveable { mutableStateOf(true) }
    var controls by rememberSaveable { mutableStateOf(true) }
    var useSystemBrightness by rememberSaveable { mutableStateOf(true) }
    var brightness by rememberSaveable { mutableFloatStateOf(.7f) }
    var tip by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(view, size) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            withFrameNanos { }
            val insets = view.rootWindowInsets
            fun radius(position: Int) = insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
            val top = maxOf(radius(android.view.RoundedCorner.POSITION_TOP_LEFT), radius(android.view.RoundedCorner.POSITION_TOP_RIGHT))
            val bottom = maxOf(radius(android.view.RoundedCorner.POSITION_BOTTOM_LEFT), radius(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT))
            reportedCorners = (top to bottom).takeIf { top > 0 || bottom > 0 }
        }
    }
    val rTop = if (systemCorners) reportedCorners?.first ?: topRadius else topRadius
    val rBottom = if (systemCorners) reportedCorners?.second ?: bottomRadius else bottomRadius
    val panelSidePadding = maxOf(20f, maxOf(rTop, rBottom) * .3f / LocalDensity.current.density).dp
    val scope = rememberCoroutineScope()
    val pattern = FilmPattern(FilmBackgrounds[background].second.toArgb(), FilmInks[ink].second.toArgb(),
        border.toInt(), cross, grid, if (showLabel) label else "", insetX, insetY, rTop, rBottom)
    val latestPattern by rememberUpdatedState(pattern)
    val latestSize by rememberUpdatedState(size)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            // Take a snapshot; subsequent edits cannot alter an export in progress.
            val config = latestPattern
            val dimensions = latestSize
            try {
                require(dimensions.width > 0 && dimensions.height > 0 && dimensions.width.toLong() * dimensions.height <= 24_000_000) { "畫面尺寸無法匯出" }
                withContext(Dispatchers.IO) {
                    val bitmap = createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888)
                    try {
                        config.draw(android.graphics.Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat())
                        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG 編碼失敗" }
                        }
                    } finally { bitmap.recycle() }
                }
                exportMessage = "已儲存 ${dimensions.width} × ${dimensions.height} PNG"
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { exportMessage = "無法儲存圖片：${error.message ?: "來源無法使用"}" }
            finally { exporting = false }
        }
    }
    ScreenWindowEffect(if (useSystemBrightness) null else brightness)
    BackHandler { if (!controls) controls = true else onClose() }
    LaunchedEffect(controls) { if (!controls) { tip = true; delay(3000); tip = false } }
    Box(Modifier.fillMaxSize().background(FilmBackgrounds[background].second)) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }.testTag("film-preview")
            .semantics {
                contentDescription = "貼膜測試圖。雙擊或長按開啟設定，也可按返回鍵。"
                customActions = listOf(CustomAccessibilityAction("開啟貼膜設定") { controls = true; true })
            }.pointerInput(Unit) { detectTapGestures(onDoubleTap = { controls = true }, onLongPress = { controls = true }) }) {
            pattern.draw(drawContext.canvas.nativeCanvas, this.size.width, this.size.height)
        }
        if (!controls && tip) Text("雙擊或長按開啟設定 · 返回鍵也可開啟", color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp).background(Panel).padding(12.dp),
            style = MaterialTheme.typography.bodySmall)
        if (controls) {
            Surface(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = panelSidePadding, vertical = 12.dp).fillMaxWidth().fillMaxHeight(.83f),
                shape = MaterialTheme.shapes.extraLarge, color = Panel) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("貼膜輔助", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onClose) { Text("結束") }
                    }
                    Button(onClick = { controls = false }, modifier = Modifier.fillMaxWidth()) { Text("全螢幕開始") }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("先選圖樣，再隱藏面板；檢視灰塵、黑邊遮擋與視覺對位。", color = Muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { background = 0; ink = 0; border = 0f; cross = false; grid = false; showLabel = false }) { Text("除塵白底") }
                        OutlinedButton(onClick = { background = 0; ink = 0; border = 4f; cross = false; grid = false; showLabel = true }) { Text("邊緣檢視") }
                        OutlinedButton(onClick = { background = 0; ink = 0; border = 4f; cross = true; grid = true; showLabel = false }) { Text("格線對位") }
                        OutlinedButton(onClick = { systemCorners = false; topRadius = 80f; bottomRadius = 80f; insetX = 12f; insetY = 12f; border = 4f }) { Text("圓角邊框") }
                    }
                    FilmColorChoice("背景", FilmBackgrounds, background) { background = it }
                    FilmColorChoice("邊框與文字", FilmInks, ink) { ink = it }
                    Text("邊框寬度 ${border.toInt()} px", color = Mint)
                    Slider(border, { border = it }, valueRange = 0f..24f, steps = 23,
                        modifier = Modifier.semantics { contentDescription = "邊框寬度" })
                    FilmToggle("使用系統回報 R 角", systemCorners) { systemCorners = it }
                    Text(if (reportedCorners == null) "系統未提供 R 角，可手動調整。" else "系統 R 角：上 ${reportedCorners!!.first.toInt()}／下 ${reportedCorners!!.second.toInt()} px", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (!systemCorners || reportedCorners == null) {
                        FilmDimensionSlider("上方 R 角", topRadius, 300f) { topRadius = it }
                        FilmDimensionSlider("下方 R 角", bottomRadius, 300f) { bottomRadius = it }
                    }
                    FilmDimensionSlider("水平內縮", insetX, 120f) { insetX = it }
                    FilmDimensionSlider("垂直內縮", insetY, 160f) { insetY = it }
                    TextButton(onClick = { insetX = 0f; insetY = 0f; topRadius = 0f; bottomRadius = 0f; systemCorners = true }) { Text("重設圓角與內縮") }
                    FilmToggle("中央十字", cross) { cross = it }
                    FilmToggle("對位格線", grid) { grid = it }
                    FilmToggle("中央文字", showLabel) { showLabel = it }
                    if (showLabel) OutlinedTextField(label, { label = it.take(120) }, label = { Text("自訂中央文字") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                    FilmToggle("跟隨系統亮度", useSystemBrightness) { useSystemBrightness = it }
                    if (!useSystemBrightness) {
                        Text("此畫面亮度 ${(brightness * 100).toInt()}%", color = Mint)
                        Slider(brightness, { brightness = it }, valueRange = .05f..1f,
                            modifier = Modifier.semantics { contentDescription = "此畫面亮度" })
                    }
                    Text("px 是目前畫面的繪圖像素，非毫米或面板原生像素；圖樣不代表保護貼裁切尺寸。離開或切到背景會還原亮度。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !exporting, onClick = { export.launch("Galaxy-box-film.png") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (exporting) "儲存中…" else "儲存測試圖 PNG")
                    }
                    exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable private fun FilmColorChoice(title: String, colors: List<Pair<String, Color>>, selected: Int, onSelect: (Int) -> Unit) {
    Text(title, color = Mint)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { index, choice ->
            FilterChip(selected == index, { onSelect(index) }, label = { Text(choice.first) },
                leadingIcon = { Box(Modifier.size(14.dp).background(choice.second, MaterialTheme.shapes.small)) })
        }
    }
}
@Composable private fun FilmToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable private fun FilmDimensionSlider(title: String, value: Float, maximum: Float, change: (Float) -> Unit) {
    Text("$title ${value.toInt()} px", color = Mint)
    Slider(value, change, valueRange = 0f..maximum, modifier = Modifier.semantics { contentDescription = title })
}

/** Original renderer shared by the preview and PNG export; no Xiaomi code or assets. */
internal data class FilmPattern(val background: Int, val ink: Int, val borderPx: Int, val cross: Boolean, val grid: Boolean, val text: String,
    val insetXPx: Float = 0f, val insetYPx: Float = 0f, val topRadiusPx: Float = 0f, val bottomRadiusPx: Float = 0f) {
    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float) {
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        fun safe(value: Float, maximum: Float) = (value.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, maximum.coerceAtLeast(0f))
        val x = safe(insetXPx, width / 2 - 1f); val y = safe(insetYPx, height / 2 - 1f)
        val frame = android.graphics.RectF(x, y, width - x, height - y)
        val limit = minOf(frame.width(), frame.height()) / 2
        val top = safe(topRadiusPx, limit); val bottom = safe(bottomRadiusPx, limit)
        fun path(rect: android.graphics.RectF, inset: Float = 0f) = android.graphics.Path().apply {
            val t = (top - inset).coerceAtLeast(0f); val b = (bottom - inset).coerceAtLeast(0f)
            addRoundRect(rect, floatArrayOf(t,t,t,t,b,b,b,b), android.graphics.Path.Direction.CW)
        }
        val outline = path(frame)
        val border = borderPx.toFloat().coerceIn(0f, limit)
        if (border > 0) {
            val ring = android.graphics.Path(outline).apply {
                fillType = android.graphics.Path.FillType.EVEN_ODD
                val inner = android.graphics.RectF(frame).apply { inset(border, border) }
                if (inner.width() > 0 && inner.height() > 0) addPath(path(inner, border))
            }
            canvas.drawPath(ring, paint)
        }
        val clipped = canvas.save()
        canvas.clipPath(outline)
        paint.strokeWidth = 1f
        if (grid) {
            paint.alpha = 100
            (1..5).forEach { i ->
                canvas.drawLine(frame.left + frame.width() * i / 6, frame.top, frame.left + frame.width() * i / 6, frame.bottom, paint)
                canvas.drawLine(frame.left, frame.top + frame.height() * i / 6, frame.right, frame.top + frame.height() * i / 6, paint)
            }
        }
        paint.alpha = 220
        if (cross) {
            val arm = minOf(width, height) * .045f
            canvas.drawLine(width / 2 - arm, height / 2, width / 2 + arm, height / 2, paint)
            canvas.drawLine(width / 2, height / 2 - arm, width / 2, height / 2 + arm, paint)
        }
        if (text.isNotBlank()) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = minOf(width, height) * .045f
            val lines = text.take(120).lines().take(3)
            val longest = lines.maxOf { paint.measureText(it) }.coerceAtLeast(1f)
            if (longest > width * .8f) paint.textSize *= width * .8f / longest
            val lineHeight = paint.fontSpacing
            val start = height / 2 - (paint.ascent() + paint.descent()) / 2 - (lines.size - 1) * lineHeight / 2 + if (cross) minOf(width, height) * .10f else 0f
            lines.forEachIndexed { i, line -> canvas.drawText(line, width / 2, start + i * lineHeight, paint) }
        }
        canvas.restoreToCount(clipped)
    }
}
