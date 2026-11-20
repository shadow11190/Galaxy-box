package dev.galaxy.toolbox.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.galaxy.toolbox.diagnostics.HapticPattern
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun HapticMotorCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }
    val available = vibrator?.hasVibrator() == true
    val amplitudeControl = available && vibrator?.hasAmplitudeControl() == true
    var intensity by remember { mutableIntStateOf(60) }
    var duration by remember { mutableIntStateOf(500) }
    var damping by remember { mutableIntStateOf(24) }
    var interval by remember { mutableIntStateOf(70) }
    var result by remember { mutableStateOf("按播放後確認實際震感；離開此頁會停止。") }
    val baselineColor = Muted
    val waveColor = Mint

    DisposableEffect(vibrator, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vibrator?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vibrator?.cancel() }
    }

    fun play(effect: VibrationEffect, label: String) {
        val motor = vibrator ?: return
        runCatching {
            motor.cancel()
            motor.vibrate(effect)
        }.onSuccess { result = "已送出" + label + "指令；實際力度可能受系統限制。" }
            .onFailure { result = "無法啟動震動：" + it.javaClass.simpleName }
    }

    InfoCard("震動馬達 · 波形測試") {
        GalaxyText(result, color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyText(if (amplitudeControl) "裝置回報支援幅度控制" else "裝置未回報幅度控制；僅能測試開／關與最大指令幅度",
            color = Muted, style = GalaxyTypography.bodySmall)
        Canvas(Modifier.fillMaxWidth().height(110.dp).background(Navy.copy(alpha = .5f), GalaxyShapes.medium)) {
            val mid = size.height / 2f
            drawLine(baselineColor.copy(alpha = .35f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            val line = Path()
            val strength = if (amplitudeControl) intensity / 100f else 1f
            for (point in 0..240) {
                val position = point / 240f
                val cycle = position * 10f
                val envelope = (1f - damping / 100f).toDouble().pow(cycle.toDouble()).toFloat()
                val wave = sin(position * 20f * PI).toFloat()
                val y = mid - wave * envelope * strength * size.height * .43f
                val x = position * size.width
                if (point == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            drawPath(line, waveColor, style = Stroke(2.dp.toPx()))
        }
        GalaxyText("力度 · " + if (amplitudeControl) intensity.toString() + "%" else "裝置不支援調節")
        GalaxySlider(value = intensity.toFloat(), onValueChange = { intensity = it.roundToInt() },
            valueRange = 1f..100f, enabled = amplitudeControl)
        GalaxyText("持續測試時間 · " + duration + " ms")
        GalaxySlider(value = duration.toFloat(), onValueChange = { duration = it.roundToInt() },
            valueRange = 100f..3000f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalaxyPrimaryButton(enabled = available, onClick = {
                play(VibrationEffect.createOneShot(duration.toLong(),
                    if (amplitudeControl) HapticPattern.amplitude(intensity) else VibrationEffect.DEFAULT_AMPLITUDE),
                    "持續震動")
            }) { GalaxyText("持續測試") }
            GalaxyOutlinedButton(enabled = available, onClick = {
                play(VibrationEffect.createOneShot(1000, 255), "API 最大幅度")
            }) { GalaxyText("頂滿 1 秒") }
        }
        GalaxyText("回彈波形：以下參數控制 App 送出的脈衝節奏與衰減，不會改變馬達硬體剛度。",
            color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyText("阻尼 · " + damping + "%")
        GalaxySlider(value = damping.toFloat(), onValueChange = { damping = it.roundToInt() },
            valueRange = 0f..80f, enabled = amplitudeControl)
        GalaxyText("脈衝間隔 · " + interval + " ms")
        GalaxySlider(value = interval.toFloat(), onValueChange = { interval = it.roundToInt() },
            valueRange = 30f..140f, enabled = amplitudeControl)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalaxyPrimaryButton(enabled = available, onClick = {
                val effect = if (amplitudeControl) {
                    val pattern = HapticPattern.elastic(intensity, damping, interval)
                    VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
                } else VibrationEffect.createWaveform(longArrayOf(0, 70, 55, 45, 80, 25), -1)
                play(effect, "回彈波形")
            }) { GalaxyText("播放回彈") }
            GalaxyOutlinedButton(enabled = available, onClick = {
                vibrator?.cancel()
                result = "已停止震動。"
            }) { GalaxyText("停止") }
        }
    }
}
