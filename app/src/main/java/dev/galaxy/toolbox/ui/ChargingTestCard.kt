package dev.galaxy.toolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.model.ChargingPoint
import dev.galaxy.toolbox.model.ChargingTestResult
import dev.galaxy.toolbox.model.ChargingTestState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Composable internal fun ChargingTestCard(test: ChargingTestState, onStart: () -> Unit, onStop: () -> Unit) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    InfoCard("充電檢測") {
        GalaxyText("測量電池端淨功率，與充電器輸出功率不同。", color = Muted, style = GalaxyTypography.bodySmall)
        if (test.active) {
            GalaxyText("檢測中", color = Mint, style = GalaxyTypography.labelLarge)
            GalaxyText(power(test.points.lastOrNull()?.watts), style = GalaxyTypography.headlineLarge)
        }
        test.message?.let { GalaxyText(it, color = Muted) }
        test.result?.let { result ->
            ChargingTestSummary(result)
            if (test.points.count { it.watts != null } >= 2) ChargingPowerTrend(test.points)
            else GalaxyText(if (test.active) "等待有效功率樣本。" else "有效樣本不足，無法繪製曲線。",
                color = Muted, style = GalaxyTypography.bodySmall)
        }
        if (test.active) GalaxyOutlinedButton("停止檢測", onStop)
        else GalaxyPrimaryTextButton("開始檢測", onStart)
        if (test.history.isNotEmpty()) {
            GalaxyTextButton(if (showHistory) "收合充電紀錄" else "查看充電紀錄", { showHistory = !showHistory })
            if (showHistory) {
                val locale = Locale.forLanguageTag(LocalAppLanguage.current.key)
                val date = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
                var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
                test.history.forEach { record ->
                    GalaxyDivider()
                    val stamp = remember(record.startedAt, date) { date.format(Date(record.startedAt)) }
                    GalaxyTextButton(stamp, { expanded = if (expanded == record.startedAt) null else record.startedAt })
                    if (expanded == record.startedAt) ChargingTestSummary(record)
                }
            }
        }
    }
}

@Composable private fun ChargingTestSummary(result: ChargingTestResult) {
    val age = result.durationMs / 1000
    val duration = String.format(Locale.ROOT, "%02d:%02d:%02d", age / 3600, age / 60 % 60, age % 60)
    val start = result.startPercent?.let { "$it%" } ?: "—"
    val end = result.endPercent?.let { "$it%" } ?: "—"
    GalaxyDivider()
    TestMetric("平均功率", power(result.average))
    TestMetric("峰值功率", power(result.peak))
    TestMetric("估計淨充入能量", result.energyWh?.let { String.format(Locale.ROOT, "%+.3f Wh", it) } ?: "—")
    TestMetric("檢測時間", duration)
    TestMetric("電量變化", "$start → $end")
    TestMetric("有效樣本", result.samples.toString())
    if ((result.samples >= 2 && result.measuredMs == 0L) || result.measuredMs + 6_000 < result.durationMs) {
        GalaxyText("統計只計入有效且連續的取樣。", color = Muted, style = GalaxyTypography.bodySmall)
    }
    if (result.estimatedVoltage) GalaxyText("此紀錄包含估算電壓。", color = Muted, style = GalaxyTypography.bodySmall)
}

@Composable private fun TestMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GalaxyText(label, Modifier.weight(1f), color = Muted)
        GalaxyText(value, style = GalaxyTypography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun ChargingPowerTrend(points: List<ChargingPoint>) {
    val values = points.mapNotNull { it.watts }
    val low = floor(minOf(0.0, values.min()))
    val high = ceil(maxOf(1.0, values.max()))
    val first = points.first().elapsedMs
    val span = (points.last().elapsedMs - first).coerceAtLeast(1)
    val ink = GalaxyPalette.readableAccent(GalaxyPalette.telemetry.battery)
    val grid = Muted.copy(alpha = .26f)
    val description = AppText.translate("電池淨功率趨勢", LocalAppLanguage.current)
    GalaxyText("電池淨功率趨勢", style = GalaxyTypography.titleSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GalaxyText(power(low), color = Muted, style = GalaxyTypography.labelSmall)
        GalaxyText(power(high), color = Muted, style = GalaxyTypography.labelSmall)
    }
    Canvas(Modifier.fillMaxWidth().height(92.dp).semantics { contentDescription = description }) {
        fun x(point: ChargingPoint) = ((point.elapsedMs - first).toDouble() / span * size.width).toFloat()
        fun y(value: Double) = ((high - value) / (high - low) * size.height).toFloat()
        drawLine(grid, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.dp.toPx())
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val next = points[index]
            val previousWatts = previous.watts ?: continue
            val nextWatts = next.watts ?: continue
            if (next.elapsedMs - previous.elapsedMs > 6_000) continue
            drawLine(ink, Offset(x(previous), y(previousWatts)), Offset(x(next), y(nextWatts)),
                2.dp.toPx(), StrokeCap.Round)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GalaxyText(elapsedLabel(first), color = Muted, style = GalaxyTypography.labelSmall)
        GalaxyText(elapsedLabel(points.last().elapsedMs), color = Muted, style = GalaxyTypography.labelSmall)
    }
}

private fun power(value: Double?) = value?.let { String.format(Locale.ROOT, "%+.1f W", it) } ?: "—"
private fun elapsedLabel(milliseconds: Long) = String.format(Locale.ROOT, "%d:%02d", milliseconds / 60_000, milliseconds / 1000 % 60)
