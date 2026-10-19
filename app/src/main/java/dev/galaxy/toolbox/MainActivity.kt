package dev.galaxy.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import dev.galaxy.toolbox.ui.ToolboxApp

class MainActivity : ComponentActivity() {
    private val model: ToolboxViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { ToolboxApp(model) }
    }
    override fun onStart() { super.onStart(); model.startMonitoring(); dev.galaxy.toolbox.charging.ChargingNotification.start(this) }
    override fun onStop() { model.stopMonitoring(); super.onStop() }
}
