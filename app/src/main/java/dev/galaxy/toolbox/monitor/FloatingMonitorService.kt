package dev.galaxy.toolbox.monitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.galaxy.toolbox.MainActivity
import dev.galaxy.toolbox.R
import dev.galaxy.toolbox.ui.AppDisplayPreferences
import dev.galaxy.toolbox.ui.AppText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class FloatingMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private var panel: TextView? = null
    private val language by lazy { AppDisplayPreferences.language(this) }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (panel != null) return START_NOT_STICKY
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("浮動監控"), NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_charging_status)
                .setContentTitle(tr("浮動監控")).setContentText(tr("浮動監控已開啟。"))
                .setContentIntent(open).setOngoing(true).addAction(0, tr("停止浮動監控"), stop).build()
            ServiceCompat.startForeground(this, 4611, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            showPanel()
            active.value = true
        }.onFailure {
            error.value = "無法開啟浮動監控。"
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            val power = getSystemService(PowerManager::class.java)
            while (isActive && Settings.canDrawOverlays(this@FloatingMonitorService)) {
                if (power.isInteractive) {
                    val text = withContext(Dispatchers.IO) { MonitorReadings.read(this@FloatingMonitorService).floatingText(this@FloatingMonitorService) }
                    panel?.text = text
                }
                delay(if (power.isInteractive) 2_000 else 8_000)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun tr(value: String) = AppText.translate(value, language)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun showPanel() {
        val layout = WindowManager.LayoutParams(dp(192), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = dp(16)
            y = dp(72)
        }
        val view = TextView(this).apply {
            text = tr("等待資料")
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xEC15232F.toInt())
                setStroke(dp(1), 0x559BD9FF)
            }
        }
        var startX = 0f
        var startY = 0f
        var windowX = 0
        var windowY = 0
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    windowX = layout.x; windowY = layout.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val metrics = resources.displayMetrics
                    layout.x = (windowX + (event.rawX - startX).roundToInt())
                        .coerceIn(0, (metrics.widthPixels - view.width).coerceAtLeast(0))
                    layout.y = (windowY + (event.rawY - startY).roundToInt())
                        .coerceIn(0, (metrics.heightPixels - view.height).coerceAtLeast(0))
                    runCatching { windows.updateViewLayout(view, layout) }
                }
            }
            true
        }
        windows.addView(view, layout)
        panel = view
    }

    override fun onDestroy() {
        scope.cancel()
        panel?.let { runCatching { windows.removeView(it) } }
        panel = null
        active.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "floating_monitor"
        private const val STOP = "dev.galaxy.toolbox.STOP_FLOATING_MONITOR"
        private val active = MutableStateFlow(false)
        val running = active.asStateFlow()
        internal val error = MutableStateFlow<String?>(null)

        fun start(context: Context) {
            error.value = null
            runCatching { ContextCompat.startForegroundService(context, Intent(context, FloatingMonitorService::class.java)) }
                .onFailure { error.value = "無法開啟浮動監控。" }
        }
        fun stop(context: Context) { context.stopService(Intent(context, FloatingMonitorService::class.java)) }
    }
}
