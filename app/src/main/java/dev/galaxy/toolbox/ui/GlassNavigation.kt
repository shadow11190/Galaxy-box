package dev.galaxy.toolbox.ui

import android.app.ActivityManager
import android.content.*
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Capture only when hardware blur is supported and its power cost is appropriate. */
@Composable
internal fun rememberGlassAvailable(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    fun available() = GlassCompatibility.canUse(Build.VERSION.SDK_INT, view.isHardwareAccelerated,
        context.getSystemService(ActivityManager::class.java).isLowRamDevice,
        context.getSystemService(PowerManager::class.java).isPowerSaveMode,
        Build.VERSION.SDK_INT >= 33 && view.isHardwareAccelerated && GlassCompatibility.refractionSupported)
    var supported by remember(context) { mutableStateOf(available()) }
    DisposableEffect(context, lifecycle) {
        // The first composition can run before the View has an accelerated window.
        val refresh = Runnable { supported = available() }
        val attachment = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { v.post(refresh) }
            override fun onViewDetachedFromWindow(v: android.view.View) { supported = false }
        }
        view.addOnAttachStateChangeListener(attachment)
        view.post(refresh)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { supported = available() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) supported = available()
        }
        lifecycle.addObserver(observer)
        onDispose {
            view.removeCallbacks(refresh); view.removeOnAttachStateChangeListener(attachment)
            context.unregisterReceiver(receiver); lifecycle.removeObserver(observer)
        }
    }
    return supported
}

/** Coordinates are tab-center positions, independent of display density and thermal data. */
internal object NavigationDrag {
    fun positionAt(x: Float, width: Float, count: Int, rtl: Boolean): Float {
        if (width <= 0f || count <= 1 || !x.isFinite()) return 0f
        val logicalX = if (rtl) width - x else x
        return (logicalX / (width / count) - .5f).coerceIn(0f, (count - 1).toFloat())
    }
}
