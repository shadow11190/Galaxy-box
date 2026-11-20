package dev.galaxy.toolbox.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

internal data class PressureStats(val current: Float, val baseline: Float, val minimum: Float, val maximum: Float) {
    val change: Float get() = current - baseline
    val range: Float get() = maximum - minimum
}

internal fun pressureStats(samples: List<Float>, baseline: Float?): PressureStats? {
    val valid = samples.filter { it.isFinite() }
    val current = valid.lastOrNull() ?: return null
    return PressureStats(current, baseline?.takeIf { it.isFinite() } ?: valid.first(), valid.min(), valid.max())
}

@Composable
internal fun PressureObservation() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    val samples = remember { mutableStateListOf<Float>() }
    var baseline by remember { mutableStateOf<Float?>(null) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("按開始後觀察氣壓讀值。") }

    DisposableEffect(owner, manager, sensor, running) {
        if (!running || manager == null || sensor == null) {
            onDispose { }
        } else {
            var lastAccepted = 0L
            var registered = false
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                override fun onSensorChanged(event: SensorEvent) {
                    val value = event.values.getOrNull(0) ?: return
                    val now = SystemClock.elapsedRealtime()
                    if (!value.isFinite() || now - lastAccepted < 400L) return
                    lastAccepted = now
                    if (samples.isEmpty()) baseline = value
                    samples.add(value)
                    if (samples.size > 120) samples.removeAt(0)
                    status = "正在接收氣壓感測器讀值"
                }
            }
            fun stop() {
                if (registered) runCatching { manager.unregisterListener(listener) }
                registered = false
            }
            fun start() {
                if (!registered) {
                    registered = runCatching { manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
                        .getOrDefault(false)
                    if (!registered) status = "感測器暫時無法提供讀值"
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> start()
                    Lifecycle.Event.ON_STOP -> { stop(); running = false; status = "已暫停（離開前景）" }
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
            onDispose { stop(); owner.lifecycle.removeObserver(observer) }
        }
    }

    if (sensor == null) {
        GalaxyText("這台裝置沒有提供 Android 氣壓感測器。")
        return
    }
    val stats = pressureStats(samples, baseline)
    GalaxyText(status, color = Muted)
    GalaxyText("${sensor.name} · Android TYPE_PRESSURE", color = Muted, style = GalaxyTypography.bodySmall)
    GalaxyText(stats?.let { String.format(Locale.ROOT, "%.2f hPa", it.current) } ?: "—", style = GalaxyTypography.headlineMedium)
    stats?.let {
        GalaxyText(String.format(Locale.ROOT, "相對基準 %+.2f hPa · 最近讀值範圍 %.2f hPa", it.change, it.range))
        GalaxyText(String.format(Locale.ROOT, "最低 %.2f · 最高 %.2f hPa", it.minimum, it.maximum), color = Muted)
    }
    if (samples.size >= 2) {
        val lineColor = Mint
        val guideColor = Muted.copy(alpha = .35f)
        Canvas(Modifier.fillMaxWidth().height(116.dp)) {
            val low = samples.min()
            val high = samples.max()
            val span = (high - low).coerceAtLeast(0.01f)
            drawLine(guideColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            val path = Path()
            samples.forEachIndexed { index, value ->
                val x = size.width * index / (samples.size - 1)
                val y = size.height * (.15f + .7f * (high - value) / span)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GalaxyPrimaryButton(onClick = { running = !running; status = if (running) "等待感測器回報…" else "已暫停讀取" }) {
            GalaxyText(if (running) "暫停" else if (samples.isEmpty()) "開始" else "繼續")
        }
        GalaxyOutlinedButton(onClick = { baseline = samples.lastOrNull(); status = "已重設基準" }, enabled = samples.isNotEmpty()) {
            GalaxyText("設為基準")
        }
    }
    GalaxyText("僅顯示環境氣壓及變化；持握、天氣與移動都可能影響讀值。不能以此判定防水等級或保證密封。", color = Muted,
        style = GalaxyTypography.bodySmall)
}
