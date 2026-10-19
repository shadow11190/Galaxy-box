package dev.galaxy.toolbox.charging

import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.galaxy.toolbox.data.ChargingPowerReader
import dev.galaxy.toolbox.model.ChargingPowerReading
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Explicit user-started battery-only foreground monitor. Never starts from boot/power broadcasts. */
class ChargingStatusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { signals.trySend(Unit) }
    }
    override fun onBind(intent: Intent?) = null
    @android.annotation.SuppressLint("MissingPermission") // allowed() explicitly checks API 33 POST_NOTIFICATIONS before start and each update.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ChargingNotification.STOP) {
            ChargingNotification.turnOffPreference(this); stopSelf(); return START_NOT_STICKY
        }
        if (!ChargingNotification.enabled(this) || !ChargingNotification.allowed(this)) { stopSelf(); return START_NOT_STICKY }
        if (job?.isActive == true) return START_NOT_STICKY
        try {
            ChargingNotification.createChannel(this)
            ServiceCompat.startForeground(this, ChargingNotification.ID, ChargingNotification.build(this, ChargingPowerReading(), System.currentTimeMillis()),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (_: Exception) { ChargingNotification.failed("系統未允許啟動充電通知，請在 App 前景重試。"); stopSelf(); return START_NOT_STICKY }
        ChargingNotification.setRunning(true)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }
        try { ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED); registered = true }
        catch (_: Exception) { ChargingNotification.failed("無法監聽電源狀態"); stopSelf(); return START_NOT_STICKY }
        job = scope.launch {
            while (isActive) {
                if (!ChargingNotification.enabled(this@ChargingStatusService) || !ChargingNotification.allowed(this@ChargingStatusService)) { stopSelf(); break }
                val reading = runCatching { ChargingPowerReader.read(this@ChargingStatusService) }.getOrElse { ChargingPowerReading() }
                // This task never samples GPU/thermal zones or uses Shizuku.
                val manager = getSystemService(android.app.NotificationManager::class.java)
                runCatching { manager.notify(ChargingNotification.ID, ChargingNotification.build(this@ChargingStatusService, reading, System.currentTimeMillis())) }
                val interactive = getSystemService(PowerManager::class.java).isInteractive
                withTimeoutOrNull(ChargingNotification.interval(reading.connected, interactive)) { signals.receive() }
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        job?.cancel(); scope.cancel(); signals.close()
        if (registered) { unregisterReceiver(receiver); registered = false }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        ChargingNotification.setRunning(false)
        super.onDestroy()
    }
}
