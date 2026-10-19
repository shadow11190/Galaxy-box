package dev.galaxy.toolbox.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.galaxy.toolbox.model.ChargingPowerReading
import java.util.Locale

@Composable internal fun ChargingPowerCard(reading: ChargingPowerReading, nowMs: Long, status: String? = null) {
    InfoCard("充電狀態") {
        Text(if (reading.fresh(nowMs)) status ?: "—" else "—", color = Mint)
        Text(reading.powerText(nowMs), style = MaterialTheme.typography.headlineLarge)
        Text("電池淨功率", color = Muted)
        Text("${reading.currentMicroAmps?.takeIf { reading.fresh(nowMs) }?.let { String.format(Locale.ROOT, "%.0f mA", it / 1000.0) } ?: "電流未知"} · ${reading.voltageMilliVolts?.takeIf { reading.fresh(nowMs) }?.let { String.format(Locale.ROOT, "%.3f V", it / 1000.0) } ?: "電壓未知"}", color = Muted)
    }
}
