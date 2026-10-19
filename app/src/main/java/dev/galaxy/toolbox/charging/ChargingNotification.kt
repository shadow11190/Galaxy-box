package dev.galaxy.toolbox.charging

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.galaxy.toolbox.MainActivity
import dev.galaxy.toolbox.R
import dev.galaxy.toolbox.model.ChargingPowerReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object ChargingNotification {
    const val CHANNEL = "charging_status"
    const val ID = 207
    const val STOP = "dev.galaxy.toolbox.STOP_CHARGING_NOTICE"
    private val runningMutable = MutableStateFlow(false)
    val running = runningMutable.asStateFlow()
    private val errorMutable = MutableStateFlow<String?>(null)
    val error = errorMutable.asStateFlow()
    internal fun failed(message: String) { errorMutable.value = message; runningMutable.value = false }
    internal fun setRunning(value: Boolean) { runningMutable.value = value }
    fun enabled(context: Context) = context.getSharedPreferences("charging_notice", 0).getBoolean("enabled", false)
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun enable(context: Context) {
        if (!allowed(context)) { errorMutable.value = "請先允許充電通知"; return }
        context.getSharedPreferences("charging_notice", 0).edit { putBoolean("enabled", true) }; start(context)
    }
    fun start(context: Context) {
        if (!enabled(context) || !allowed(context) || runningMutable.value) return
        errorMutable.value = null
        runCatching { ContextCompat.startForegroundService(context, Intent(context, ChargingStatusService::class.java)) }
            .onFailure { errorMutable.value = "充電通知未能啟動，請在 App 前景重試。" }
    }
    fun disable(context: Context) {
        errorMutable.value = null
        context.getSharedPreferences("charging_notice", 0).edit { putBoolean("enabled", false) }
        context.stopService(Intent(context, ChargingStatusService::class.java)); runningMutable.value = false
        NotificationManagerCompat.from(context).cancel(ID)
    }
    internal fun turnOffPreference(context: Context) { context.getSharedPreferences("charging_notice", 0).edit { putBoolean("enabled", false) } }
    internal fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "充電狀態", NotificationManager.IMPORTANCE_LOW).apply { description = "充電狀態、電量與電池淨功率"; setShowBadge(false) })
    }
    internal fun interval(connected: Boolean?, interactive: Boolean): Long = when { connected != true -> 60_000; interactive -> 5_000; else -> 30_000 }
    internal fun title(r: ChargingPowerReading, now: Long): String {
        if (!r.fresh(now)) return "充電狀態 · —"
        val state = when (r.statusCode) {
            android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "充電中"
            android.os.BatteryManager.BATTERY_STATUS_FULL -> "已充滿"
            android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "暫停充電"
            android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "放電中"
            else -> if (r.connected == false) "未接電源" else if (r.connected == true) "已接電源" else "充電狀態"
        }
        return "$state · ${r.batteryPercent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"}"
    }
    internal fun content(r: ChargingPowerReading, now: Long): String = "電池淨功率 " +
        (r.watts?.takeIf { r.fresh(now) }?.let { String.format(Locale.ROOT, "%+.1f W", it) } ?: "—")
    internal fun build(context: Context, reading: ChargingPowerReading, now: Long): Notification {
        val open = PendingIntent.getActivity(context, 207, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(context, 208, Intent(context, ChargingStatusService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_charging_status)
            .setContentTitle(title(reading, now)).setContentText(content(reading, now)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS).setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "停止監控", stop).build()
    }
}
