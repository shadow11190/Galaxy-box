package dev.galaxy.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import dev.galaxy.toolbox.ui.ToolboxApp
import dev.galaxy.toolbox.ui.LocalRefreshSnapshot
import dev.galaxy.toolbox.performance.HighRefreshController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.preferredFrameRate
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val model: ToolboxViewModel by viewModels()
    private lateinit var highRefresh: HighRefreshController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        highRefresh = HighRefreshController(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val rate by highRefresh.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalRefreshSnapshot provides rate) {
                ToolboxApp(model, Modifier.preferredFrameRate(if (rate.requestedHz != null) FrameRateCategory.High else FrameRateCategory.Default))
            }
        }
    }
    override fun onStart() { super.onStart(); dev.galaxy.toolbox.charging.ChargingNotification.start(this) }
    override fun onResume() { super.onResume(); highRefresh.start(); model.startMonitoring() }
    override fun onPause() { highRefresh.stop(); model.stopMonitoring(); super.onPause() }
    override fun onStop() { model.stopMonitoring(); super.onStop() }
}
