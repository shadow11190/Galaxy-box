package dev.galaxy.toolbox.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.galaxy.toolbox.ToolboxState
import dev.galaxy.toolbox.model.*
import dev.galaxy.toolbox.ui.GalaxyBoxTheme
import dev.galaxy.toolbox.ui.TemperatureSummaryCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SocSourceRenderingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sourceSheetShowsActualVideoNameAndOpensItsRawDetails() {
        val now = 100_000L
        val result = TemperatureClassifier().classify("video", "/sys/class/thermal/thermal_zone77/temp",
            TemperatureSource.THERMAL, TemperatureUnit.MILLI_C,
            mapOf("SoC manufacturer" to "Qualcomm"), "SM-S9480")
        val descriptor = TemperatureDescriptor("local-video", "video", "/sys/class/thermal/thermal_zone77/temp",
            TemperatureSource.THERMAL, result.unit, result.classification)
        val reading = TemperatureConversion.observation(descriptor, TemperatureFileResult("41000", TemperatureReadStatus.OK), now)
        var opened: String? = null
        rule.setContent {
            GalaxyBoxTheme(dark = true) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    TemperatureSummaryCard(ToolboxState(temperatures = listOf(reading), temperatureNowMs = now), { opened = it })
                }
            }
        }
        rule.onNodeWithText("查看 SoC 來源與讀取狀態").performScrollTo().performClick()
        rule.onNodeWithText("SoC 來源診斷").assertIsDisplayed()
        rule.onNodeWithText("已辨識 SoC 來源 · 1").assertIsDisplayed()
        rule.onNodeWithText("video").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("local-video", opened) }
        rule.onNodeWithText("SoC 來源診斷").assertDoesNotExist()
    }
}
