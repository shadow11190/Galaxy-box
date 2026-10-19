package dev.galaxy.toolbox.model

import java.util.Locale

data class ChargingPowerReading(
    val currentMicroAmps: Int? = null, val voltageMilliVolts: Int? = null, val batteryPercent: Int? = null,
    val connected: Boolean? = null, val full: Boolean = false, val atMs: Long? = null, val statusCode: Int? = null
) {
    // Signed net power at the battery. Device consumption and conversion losses are excluded.
    val watts: Double? get() = if (currentMicroAmps != null && currentMicroAmps != Int.MIN_VALUE && voltageMilliVolts != null && voltageMilliVolts > 0)
        currentMicroAmps.toDouble() * voltageMilliVolts / 1_000_000_000.0 else null
    fun fresh(nowMs: Long) = atMs != null && nowMs >= atMs && nowMs - atMs <= 15_000
    fun powerText(nowMs: Long): String = when {
        !fresh(nowMs) -> "無最新資料"
        watts == null -> "無可用功率"
        else -> String.format(Locale.ROOT, "%+.1f W", watts)
    }
    companion object {
        const val EXPLANATION = "電池端淨功率＝電池電流 × 電池電壓。正值流入、負值流出，包含裝置自身耗電的影響；不等於充電器／USB 輸入瓦數，也不是充電器額定功率。"
        const val SOURCE = "BatteryManager.CURRENT_NOW（µA）× ACTION_BATTERY_CHANGED.voltage（mV）÷ 10⁹"
    }
}
