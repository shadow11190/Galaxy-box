package dev.galaxy.toolbox.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dev.galaxy.toolbox.data.ChargingPowerReader
import dev.galaxy.toolbox.ui.AppDisplayPreferences
import dev.galaxy.toolbox.ui.AppText
import dev.galaxy.toolbox.ui.TemperatureText
import java.io.File
import java.util.Locale

internal data class MonitorReadings(val power: String, val temperature: String, val cpu: String, val memory: String) {
    fun floatingText(context: Context): String {
        val language = AppDisplayPreferences.language(context)
        return listOf("電池淨功率" to power, "電池溫度" to temperature, "CPU 最高頻率" to cpu)
            .joinToString("\n") { (label, value) -> "${AppText.translate(label, language)}  $value" }
    }

    fun notificationText(context: Context): String =
        "CPU $cpu · ${AppText.forContext(context, "記憶體")} $memory · ${AppText.forContext(context, "電池溫度")} $temperature"

    companion object {
        fun read(context: Context, forNotification: Boolean = false): MonitorReadings {
            val watts = if (forNotification) null else runCatching { ChargingPowerReader.read(context).watts }.getOrNull()
            val celsius = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                    ?.takeUnless { it == Int.MIN_VALUE }?.div(10.0)
            }.getOrNull()
            val cpu = (0 until Runtime.getRuntime().availableProcessors().coerceAtMost(32)).mapNotNull { core ->
                runCatching { File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
                    .bufferedReader().use { it.readLine()?.trim()?.toLongOrNull() } }
                    .getOrNull()?.takeIf { it > 0 }
            }.maxOrNull()
            val memory = if (forNotification) runCatching {
                val info = ActivityManager.MemoryInfo()
                context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
                if (info.totalMem <= 0 || info.availMem !in 0..info.totalMem) "—"
                else "${(100.0 * (info.totalMem - info.availMem) / info.totalMem).toInt()}%"
            }.getOrDefault("—") else "—"
            val temp = celsius?.let {
                TemperatureText.display(String.format(Locale.ROOT, "%.1f °C", it), AppDisplayPreferences.temperatureUnit(context))
            } ?: "—"
            return MonitorReadings(watts?.let { String.format(Locale.ROOT, "%+.1f W", it) } ?: "—", temp,
                cpu?.let { String.format(Locale.ROOT, "%.2f GHz", it / 1_000_000.0) } ?: "—", memory)
        }
    }
}
