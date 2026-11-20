package dev.galaxy.toolbox

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import androidx.activity.viewModels
import dev.galaxy.toolbox.ui.ToolboxApp
import dev.galaxy.toolbox.ui.LocalRefreshSnapshot
import dev.galaxy.toolbox.ui.LocalDisplayModeSetter
import dev.galaxy.toolbox.performance.HighRefreshController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.preferredFrameRate
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN_OVERVIEW = "dev.galaxy.toolbox.OPEN_OVERVIEW" }
    private var overviewRequest by mutableIntStateOf(0)
    private val model: ToolboxViewModel by viewModels()
    private lateinit var highRefresh: HighRefreshController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_OPEN_OVERVIEW, false) == true) overviewRequest++
        highRefresh = HighRefreshController(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContent {
            val rate by highRefresh.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalRefreshSnapshot provides rate,
                LocalDisplayModeSetter provides { id: Int? -> highRefresh.setPreferredMode(id) }) {
                ToolboxApp(model, Modifier.preferredFrameRate(if ((rate.requestedHz ?: 0f) > 60f) FrameRateCategory.High else FrameRateCategory.Default), overviewRequest)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_OVERVIEW, false)) overviewRequest++
    }
    override fun onStart() { super.onStart(); dev.galaxy.toolbox.charging.ChargingNotification.start(this) }
    override fun onResume() { super.onResume(); highRefresh.start(); model.startMonitoring() }
    override fun onPause() { highRefresh.stop(); model.stopMonitoring(); super.onPause() }
    override fun onStop() { model.stopMonitoring(); super.onStop() }
}
