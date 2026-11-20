package dev.galaxy.toolbox.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.galaxy.toolbox.model.ChargingPowerReading
import java.util.Locale

@Composable internal fun ChargingPowerCard(reading: ChargingPowerReading, nowMs: Long, status: String? = null) {
    InfoCard("充電狀態") {
        GalaxyText(if (reading.fresh(nowMs)) status ?: "—" else "—", color = Mint)
        GalaxyText(reading.powerText(nowMs), style = GalaxyTypography.headlineLarge)
        GalaxyText("電池淨功率", color = Muted)
        GalaxyText("${reading.adjustedCurrentMicroAmps?.takeIf { reading.fresh(nowMs) }?.let { String.format(Locale.ROOT, "%.0f mA", it / 1000.0) } ?: "— mA"} · ${reading.voltageMilliVolts?.takeIf { reading.fresh(nowMs) }?.let { String.format(Locale.ROOT, "%.3f V", it / 1000.0) } ?: "— V"}", color = Muted)
        if (reading.invertCurrent) GalaxyText("電流正負已反轉", color = Muted, style = GalaxyTypography.bodySmall)
        if (reading.voltageEstimated) GalaxyText("電壓來自剩餘能量／電荷比，非瞬時量測。", color = Muted)
        reading.possiblePerCellMilliVolts?.let { GalaxyText("若為兩芯串聯，每芯約 ${String.format(Locale.ROOT, "%.3f V", it / 1000.0)}；接線未確認。", color = Muted) }
    }
}
