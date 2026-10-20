package dev.galaxy.toolbox.ui

import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.galaxy.toolbox.data.BatteryReadOptions
import dev.galaxy.toolbox.data.BatteryVendor
import dev.galaxy.toolbox.model.ChargingPowerReading

@Composable internal fun BatteryReadOptionsCard(reading: ChargingPowerReading) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(BatteryReadOptions.STORE, 0) }
    var invert by remember(reading) { mutableStateOf(reading.invertCurrent) }
    var estimate by remember(reading) { mutableStateOf(reading.estimateVoltageEnabled) }
    var dualCell by remember(reading) { mutableStateOf(reading.dualCell) }
    var hasOverrides by remember(reading) { mutableStateOf(listOf(BatteryReadOptions.INVERT,
        BatteryReadOptions.ESTIMATE, BatteryReadOptions.DUAL_CELL).any(prefs::contains)) }
    val vendor = BatteryVendor.of(Build.MANUFACTURER, Build.BRAND)
    val raw = reading.currentMicroAmps
    val mismatch = raw != null && kotlin.math.abs(raw.toLong()) >= 5_000 && when (reading.statusCode) {
        BatteryManager.BATTERY_STATUS_CHARGING -> raw < 0
        BatteryManager.BATTERY_STATUS_DISCHARGING -> raw > 0
        else -> false
    }
    InfoCard("電池讀值校正") {
        Text("電壓估算與雙電芯條件可依讀值自動啟用；手動切換後維持你的選擇。電流正負仍須人工確認。", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (mismatch && vendor in setOf(BatteryVendor.XIAOMI, BatteryVendor.OPPO)) {
            Text("${vendor.title}：電流方向與系統充放電狀態不一致，可嘗試開啟反轉並比對原始值。不同機型／韌體不一定相同。",
                color = Mint, style = MaterialTheme.typography.bodySmall)
        }
        OptionRow("反轉瞬時電流正負", "只反轉顯示與電池淨功率方向；不修改系統讀值。", invert) {
            invert = it; hasOverrides = true; prefs.edit { putBoolean(BatteryReadOptions.INVERT, it) }
        }
        OptionRow("缺少電壓時估算", "僅在能同時讀到剩餘能量與電荷時取比值；標為估算，無資料仍顯示未知。", estimate) {
            estimate = it; hasOverrides = true; prefs.edit { putBoolean(BatteryReadOptions.ESTIMATE, it) }
        }
        OptionRow("雙電芯條件判讀", "僅當來源電壓 6–10 V 時，額外顯示假設兩芯串聯的每芯電壓；不自動倍乘容量或功率。", dualCell) {
            dualCell = it; hasOverrides = true; prefs.edit { putBoolean(BatteryReadOptions.DUAL_CELL, it) }
        }
        if (hasOverrides) {
            TextButton(onClick = {
                prefs.edit { remove(BatteryReadOptions.INVERT); remove(BatteryReadOptions.ESTIMATE); remove(BatteryReadOptions.DUAL_CELL) }
                hasOverrides = false
                invert = reading.invertCurrent; estimate = reading.voltageEstimated; dualCell = reading.possiblePerCellMilliVolts != null
            }) { Text("恢復自動判斷") }
        }
    }
}

@Composable private fun OptionRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
