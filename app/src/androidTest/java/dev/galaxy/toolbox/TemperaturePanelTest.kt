package dev.galaxy.toolbox

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.galaxy.toolbox.data.TemperatureClassifier
import dev.galaxy.toolbox.data.TemperatureConversion
import dev.galaxy.toolbox.data.TemperatureFileResult
import dev.galaxy.toolbox.model.*
import dev.galaxy.toolbox.ui.TemperatureDetailSheet
import dev.galaxy.toolbox.ui.temperatureItems
import org.junit.Rule
import org.junit.Test

class TemperaturePanelTest {
    @get:Rule val rule = createComposeRule()
    private val at = 1_000_000L
    private fun sensor(name: String, raw: String): TemperatureReading {
        val result = TemperatureClassifier().classify(name, "/sys/$name/temp", TemperatureSource.THERMAL, TemperatureUnit.MILLI_C)
        val descriptor = TemperatureDescriptor(name, name, "/sys/$name/temp", TemperatureSource.THERMAL, result.unit, result.classification)
        return TemperatureConversion.observation(descriptor, TemperatureFileResult(raw, TemperatureReadStatus.OK), at)
    }
    private fun content() {
        val values = listOf(sensor("cpu0", "42000"), sensor("cpu-hw-trip-0", "105000"), sensor("pm8550b-bcl-lvl0", "0"))
        rule.setContent {
            var expanded by remember { mutableStateOf(emptyList<String>()) }
            var selected by remember { mutableStateOf<String?>(null) }
            val state = ToolboxState(temperatures = values, temperatureNowMs = at)
            MaterialTheme(colorScheme = darkColorScheme()) {
                LazyColumn(Modifier.testTag("temperatures")) {
                    temperatureItems(state, expanded, { id -> expanded = if (id in expanded) expanded - id else expanded + id }, { selected = it }, {})
                }
                selected?.let { id -> values.firstOrNull { it.sensor.id == id }?.let { reading ->
                    TemperatureDetailSheet(reading, emptyList(), at) { selected = null }
                } }
            }
        }
    }
    @Test fun summaryExcludesHwTripAndBclFromTemperatures() {
        content()
        rule.onNodeWithText("CPU 測點最高溫").assertIsDisplayed()
        rule.onNodeWithText("42.0 °C").assertIsDisplayed()
        rule.onNodeWithText("105.0 °C").assertDoesNotExist()
        rule.onNodeWithText("0.0 °C").assertDoesNotExist()
        rule.onNodeWithText("溫度資料 · 3").assertDoesNotExist()
    }
    @Test fun bclGroupExpandsAsRawStateAndOpensOriginalData() {
        content()
        rule.onNodeWithTag("temperatures").performScrollToNode(hasText("保護狀態候選"))
        rule.onNodeWithText("保護狀態候選").performClick()
        rule.onNodeWithTag("temperatures").performScrollToNode(hasText("0 · 原始值"))
        rule.onNodeWithText("0 · 原始值").assertIsDisplayed().performClick()
        rule.onNodeWithText("原始名稱").assertIsDisplayed()
        rule.onNodeWithText("105.0 °C").assertDoesNotExist()
    }
    @Test fun unconfirmedHwTripShowsRawValueAfterExpansion() {
        content()
        rule.onNodeWithTag("temperatures").performScrollToNode(hasText("疑似門檻／用途待確認"))
        rule.onNodeWithText("疑似門檻／用途待確認").performClick()
        rule.onNodeWithTag("temperatures").performScrollToNode(hasText("105000 · 原始值"))
        rule.onNodeWithText("105000 · 原始值").assertIsDisplayed()
        rule.onNodeWithText("105.0 °C").assertDoesNotExist()
    }
}
