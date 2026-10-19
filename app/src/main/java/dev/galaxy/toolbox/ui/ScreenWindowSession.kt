package dev.galaxy.toolbox.ui

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** All changes are scoped to this window and reversed on pause, exit or recreation. */
internal class ScreenWindowSession(private val window: Window) {
    private val controller = WindowCompat.getInsetsController(window, window.decorView)
    private val brightness = window.attributes.screenBrightness
    private val keptAwake = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    private val behavior = controller.systemBarsBehavior
    private val insets = ViewCompat.getRootWindowInsets(window.decorView)
    private val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
    private val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
    private var active = false
    private var requestedBrightness: Float? = null
    fun setBrightness(value: Float?) {
        requestedBrightness = value?.coerceIn(.05f, 1f)
        if (active) applyBrightness(requestedBrightness ?: brightness)
    }
    fun resume() {
        active = true
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyBrightness(requestedBrightness ?: brightness)
    }
    fun restore() {
        if (!active) return
        active = false
        applyBrightness(brightness)
        if (!keptAwake) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.systemBarsBehavior = behavior
        fun restoreBar(type: Int, visible: Boolean) { if (visible) controller.show(type) else controller.hide(type) }
        restoreBar(WindowInsetsCompat.Type.statusBars(), statusVisible)
        restoreBar(WindowInsetsCompat.Type.navigationBars(), navigationVisible)
    }
    private fun applyBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }
}

@Composable
internal fun ScreenWindowEffect(brightness: Float? = null) {
    val activity = LocalActivity.current ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: activity.window
    val session = remember(window) { ScreenWindowSession(window) }
    SideEffect { session.setBrightness(brightness) }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> session.resume()
                Lifecycle.Event.ON_PAUSE -> session.restore()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.resume()
        onDispose { lifecycle.removeObserver(observer); session.restore() }
    }
}
