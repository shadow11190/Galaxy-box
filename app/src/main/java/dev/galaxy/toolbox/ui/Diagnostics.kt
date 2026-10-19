package dev.galaxy.toolbox.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Composable
fun ScreenTool(tool: String, onClose: () -> Unit) {
    val activity = LocalActivity.current ?: return
    var mode by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(tool == "貼膜") }
    var touches by remember { mutableStateOf(emptyList<Offset>()) }
    var trail by remember { mutableStateOf(emptyList<Offset>()) }
    var maxTouches by remember { mutableIntStateOf(0) }
    val colors = listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color(0xFF808080))
    val names = listOf("白", "黑", "紅", "綠", "藍", "灰")
    BackHandler(onBack = onClose)
    ScreenWindowEffect()
    Box(Modifier.fillMaxSize().background(if (tool == "觸控") Color(0xFF0C1424) else colors[mode])) {
        Canvas(Modifier.fillMaxSize().then(if (tool == "觸控") Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                touches = listOf(first.position)
                trail = (trail + first.position).takeLast(1500)
                maxTouches = maxOf(maxTouches, 1)
                do {
                    val event = awaitPointerEvent()
                    val active = event.changes.filter { it.pressed }
                    touches = active.map { it.position }
                    maxTouches = maxOf(maxTouches, touches.size)
                    trail = (trail + touches).takeLast(1500)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                touches = emptyList()
            }
        } else Modifier)) {
            if (grid) {
                val ink = if (mode == 1) Color.White.copy(alpha = .45f) else Color.Black.copy(alpha = .35f)
                (1..5).forEach { i ->
                    drawLine(ink, Offset(size.width * i / 6, 0f), Offset(size.width * i / 6, size.height), 1.dp.toPx())
                    drawLine(ink, Offset(0f, size.height * i / 6), Offset(size.width, size.height * i / 6), 1.dp.toPx())
                }
                drawCircle(ink, 16.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            trail.forEach { drawCircle(Color(0xFF76E1C3).copy(alpha = .5f), 3.dp.toPx(), it) }
            touches.forEach { drawCircle(Color(0xFF76E1C3), 25.dp.toPx(), it, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())) }
        }
        Column(Modifier.align(androidx.compose.ui.Alignment.TopCenter).statusBarsPadding().padding(12.dp)
            .background(Color(0xE6162237), MaterialTheme.shapes.medium).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (tool == "觸控") "觸控 ${touches.size} 指 · 本次最多 $maxTouches 指" else "$tool · ${names[mode]}色", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tool != "觸控") {
                    TextButton(onClick = { mode = (mode + 1) % colors.size }) { Text("切換顏色") }
                    TextButton(onClick = { grid = !grid }) { Text(if (grid) "隱藏格線" else "顯示格線") }
                } else TextButton(onClick = { trail = emptyList(); maxTouches = 0 }) { Text("清除軌跡") }
                Button(onClick = onClose) { Text("結束") }
            }
            Text("返回鍵可離開 · 畫面使用目前系統亮度", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable fun VibrationCard() {
    val context = LocalContext.current
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    }
    var result by remember { mutableStateOf("按下後請自行確認是否感受到震動。") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(vibrator, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vibrator.cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vibrator.cancel() }
    }
    InfoCard("震動馬達") {
        Text(result)
        Button(enabled = vibrator.hasVibrator(), onClick = {
            runCatching { vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)) }
                .onSuccess { result = "已送出 300 ms 震動指令；請自行確認結果。" }
                .onFailure { result = "無法啟動震動：${it.message}" }
        }) { Text(if (vibrator.hasVibrator()) "測試震動" else "裝置未回報震動馬達") }
    }
}

@Composable fun SensorCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember { context.getSystemService(SensorManager::class.java) }
    val supported = listOf(Sensor.TYPE_ACCELEROMETER to "加速度 · m/s²", Sensor.TYPE_GYROSCOPE to "陀螺儀 · rad/s",
        Sensor.TYPE_LIGHT to "環境光 · lx", Sensor.TYPE_PROXIMITY to "距離 · cm").filter { manager.getDefaultSensor(it.first) != null }
    val types = supported.map { it.first }
    val readings = remember { mutableStateMapOf<Int, String>() }
    DisposableEffect(manager, lifecycle) {
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                readings[event.sensor.type] = event.values.take(3).joinToString(" / ") { String.format(Locale.ROOT, "%.2f", it) }
            }
        }
        fun register() {
            types.forEach { type ->
                val sensor = manager.getDefaultSensor(type)
                readings[type] = if (sensor == null) "未知 · 無感測器" else "等待事件"
                if (sensor != null && !manager.registerListener(listener, sensor, 200000)) readings[type] = "未知 · 註冊失敗"
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) register()
            if (event == Lifecycle.Event.ON_STOP) manager.unregisterListener(listener)
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose { lifecycle.removeObserver(observer); manager.unregisterListener(listener) }
    }
    InfoCard("即時感測器") {
        supported.forEachIndexed { i, (_, title) ->
            Text(title, color = MaterialTheme.colorScheme.primary)
            Text(readings[types[i]] ?: "未知")
        }
        Text("來源：SensorManager · 約 5 Hz；僅在此頁與 App 前景註冊。距離值可能只提供遠／近。", style = MaterialTheme.typography.bodySmall)
    }
}
