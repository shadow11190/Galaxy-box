package dev.galaxy.toolbox.performance

import android.app.Activity
import android.content.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.View
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Event-driven foreground preference. No frame loop, redraw request, resolution or settings write. */
class HighRefreshController(private val activity: Activity) {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private val power = activity.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(RefreshSnapshot())
    val state = mutable.asStateFlow()
    private var active = false
    private var originalRate = 0f
    private var originalMode = 0
    private var displayRegistered = false
    private var receiverRegistered = false
    private var thermalRegistered = false
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private val refreshTask = Runnable { refresh() }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) { view.post(refreshTask) }
        override fun onViewDetachedFromWindow(view: View) { clearPreference(); mutable.value = RefreshSnapshot(reason = "螢幕視窗已離開") }
    }
    fun start() {
        if (active) return
        active = true
        originalRate = activity.window.attributes.preferredRefreshRate
        originalMode = activity.window.attributes.preferredDisplayModeId
        displayRegistered = runCatching { displayManager.registerDisplayListener(displayListener, handler); true }.getOrDefault(false)
        receiverRegistered = runCatching {
            ContextCompat.registerReceiver(activity, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED); true
        }.getOrDefault(false)
        if (Build.VERSION.SDK_INT >= 29) {
            val listener = PowerManager.OnThermalStatusChangedListener { refresh() }
            thermalRegistered = runCatching { power.addThermalStatusListener(activity.mainExecutor, listener); true }.getOrDefault(false)
            thermalListener = listener
        }
        activity.window.decorView.addOnAttachStateChangeListener(attachment)
        refresh()
    }
    @Suppress("DEPRECATION")
    private fun refresh() {
        if (!active) return
        val display = activity.window.decorView.display ?: activity.windowManager.defaultDisplay
        val current = runCatching { display.mode }.getOrNull()
        val modes = runCatching { display.supportedModes.map { RefreshMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) } }.getOrDefault(emptyList())
        val currentMode = current?.let { RefreshMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val severe = Build.VERSION.SDK_INT >= 29 && runCatching { power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE }.getOrDefault(false)
        val decision = RefreshPolicy.decide(currentMode, modes, true, power.isPowerSaveMode, severe)
        val applied = runCatching {
            val params = activity.window.attributes
            val rate = decision.hz ?: originalRate
            // Keep resolution under system control. Legacy devices use an exact same-resolution mode.
            val mode = if (Build.VERSION.SDK_INT < 30 && decision.hz != null) decision.modeId ?: originalMode else originalMode
            if (params.preferredRefreshRate != rate || params.preferredDisplayModeId != mode) {
                params.preferredRefreshRate = rate; params.preferredDisplayModeId = mode; activity.window.attributes = params
            }
        }.isSuccess
        mutable.value = RefreshSnapshot(if (applied) decision.hz else null,
            runCatching { display.refreshRate.takeIf { it.isFinite() && it > 0f } }.getOrNull(),
            current?.physicalWidth, current?.physicalHeight, if (applied) decision.reason else "系統未接受刷新率偏好")
    }
    private fun clearPreference() {
        runCatching {
            val params = activity.window.attributes
            if (params.preferredRefreshRate != originalRate || params.preferredDisplayModeId != originalMode) {
                params.preferredRefreshRate = originalRate; params.preferredDisplayModeId = originalMode; activity.window.attributes = params
            }
        }
    }
    fun stop() {
        if (!active) return
        active = false
        activity.window.decorView.removeCallbacks(refreshTask)
        activity.window.decorView.removeOnAttachStateChangeListener(attachment)
        if (displayRegistered) runCatching { displayManager.unregisterDisplayListener(displayListener) }
        if (receiverRegistered) runCatching { activity.unregisterReceiver(receiver) }
        if (Build.VERSION.SDK_INT >= 29 && thermalRegistered) thermalListener?.let { runCatching { power.removeThermalStatusListener(it) } }
        displayRegistered = false; receiverRegistered = false; thermalRegistered = false; thermalListener = null
        clearPreference()
        mutable.value = mutable.value.copy(requestedHz = null, reason = "背景中 · 已釋放高刷新率請求")
    }
}
