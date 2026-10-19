package dev.galaxy.toolbox.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.model.*
import dev.galaxy.toolbox.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GpuUiTest {
    @get:Rule val rule = createComposeRule()
    private var dark by mutableStateOf(true); private var scale by mutableFloatStateOf(1f)
    private var view: View? = null
    private fun snapshot() = GpuSnapshot(1000, listOf(GpuAttempt(GpuMetric.CURRENT, "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq", GpuUnit.HZ, null, TemperatureReadStatus.PERMISSION_DENIED, 1000)), emptyList())
    private fun show() { rule.setContent {
        val density = LocalDensity.current; val v = LocalView.current; SideEffect { view = v }
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) { GalaxyBoxTheme(dark) {
            Box(Modifier.fillMaxSize()) { GalaxyBackdrop(); Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                GpuSourcesCard(snapshot(), 1000, null, {}, {}, {})
                ChargingNotificationCard()
            } }
        } }
    } }
    @Test fun diagnosticDoesNotTurnPermissionFailureIntoZeroOrRequireAdb() {
        show()
        rule.onNodeWithText("GPU 一般讀取 · 免 Root／ADB").assertIsDisplayed()
        rule.onNodeWithText("查看 GPU 來源診斷").performClick()
        rule.onNodeWithText("權限不足", substring = true).assertIsDisplayed()
        rule.onNodeWithText("0.0 MHz").assertDoesNotExist()
        rule.onNodeWithText("狀態列充電通知").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("開啟充電通知").performScrollTo().assertIsDisplayed()
        assertFalse(dev.galaxy.toolbox.charging.ChargingNotification.running.value)
    }
    @Test fun gpuAndChargingControlsFitBothThemesAndLargeText() {
        show()
        for (isDark in listOf(false, true)) for (font in listOf(1f, 1.6f)) {
            rule.runOnIdle { dark = isDark; scale = font }
            rule.onNodeWithText("開啟充電通知").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("GPU 一般讀取 · 免 Root／ADB").performScrollTo().assertIsDisplayed()
            val bitmap = rule.runOnIdle { val v = view!!; Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) } }
            val file = File("build/reports/visual-checks/gpu-charging-${if (isDark) "dark" else "light"}-${if (font > 1f) "large" else "standard"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
