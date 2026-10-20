package dev.galaxy.toolbox.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.galaxy.toolbox.model.ChargingPowerReading
import java.util.Locale

@Composable internal fun ChargingPowerCard(reading: ChargingPowerReading, nowMs: Long, status: String? = null) {
    InfoCard("充電狀態") {
        Text(if (reading.fresh(nowMs)) status ?: "—" else "—", color = Mint)
        Text(reading.powerText(nowMs), style = MaterialTheme.typography.headlineLarge)
        Text("電池端來源乘積", color = Muted)
        Text("${reading.adjustedCurrentMicroAmps?.takeIf { reading.fresh(nowMs) }?.let { String.format(Locale.ROOT, "%.0f mA", it / 1000.0) } ?: "電流未知"} · ${reading.voltageMilliVolts?.takeIf { reading.fresh(nowMs) }?.let { String.format(Locale.ROOT, "%.3f V", it / 1000.0) } ?: "電壓未知"}", color = Muted)
        if (reading.invertCurrent) Text("電流正負已反轉 · 原始 ${reading.currentMicroAmps?.let { "${it / 1000.0} mA" } ?: "未知"}", color = Muted)
        if (reading.voltageEstimated) Text("電壓來自剩餘能量／電荷比，非瞬時量測。", color = Muted)
        reading.possiblePerCellMilliVolts?.let { Text("若為兩芯串聯，每芯約 ${String.format(Locale.ROOT, "%.3f V", it / 1000.0)}；接線未確認。", color = Muted) }
    }
}
