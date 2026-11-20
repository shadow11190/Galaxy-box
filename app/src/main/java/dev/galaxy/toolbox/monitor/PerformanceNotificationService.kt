package dev.galaxy.toolbox.monitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.galaxy.toolbox.MainActivity
import dev.galaxy.toolbox.R
import dev.galaxy.toolbox.ui.AppText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PerformanceNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitor: Job? = null
    override fun onBind(intent: Intent?) = null

    @android.annotation.SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || !allowed(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitor?.isActive == true) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        try {
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                AppText.forContext(this, "效能通知"), NotificationManager.IMPORTANCE_LOW))
            ServiceCompat.startForeground(this, ID, notification(AppText.forContext(this, "等待資料")),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (_: RuntimeException) {
            error.value = "無法開啟效能通知。"
            stopSelf()
            return START_NOT_STICKY
        }
        active.value = true
        monitor = scope.launch {
            val power = getSystemService(PowerManager::class.java)
            while (isActive && allowed(this@PerformanceNotificationService)) {
                if (power.isInteractive) {
                    val context = this@PerformanceNotificationService
                    val text = MonitorReadings.read(context, forNotification = true).notificationText(context)
                    runCatching { manager.notify(ID, notification(text)) }
                }
                delay(if (power.isInteractive) 5_000 else 15_000)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, ID, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, ID, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_charging_status)
            .setContentTitle(AppText.forContext(this, "效能通知")).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, AppText.forContext(this, "停止效能通知"), stop).build()
    }

    override fun onDestroy() {
        scope.cancel()
        active.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val ID = 4612
        private const val CHANNEL = "performance_status"
        private const val STOP = "dev.galaxy.toolbox.STOP_PERFORMANCE_MONITOR"
        private val active = MutableStateFlow(false)
        val running = active.asStateFlow()
        internal val error = MutableStateFlow<String?>(null)

        fun allowed(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java)
            return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        }
        fun start(context: Context) {
            error.value = null
            if (!allowed(context)) {
                error.value = "請到通知設定允許顯示。"
                return
            }
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PerformanceNotificationService::class.java)) }
                .onFailure { error.value = "無法開啟效能通知。" }
        }
        fun stop(context: Context) { context.stopService(Intent(context, PerformanceNotificationService::class.java)) }
    }
}
