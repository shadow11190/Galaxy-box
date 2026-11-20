package dev.galaxy.toolbox.data

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.galaxy.toolbox.ui.AppLanguage
import dev.galaxy.toolbox.ui.AppStyle
import dev.galaxy.toolbox.ui.AppText
import dev.galaxy.toolbox.ui.GalaxyBoxTheme
import dev.galaxy.toolbox.ui.GalaxyText
import dev.galaxy.toolbox.ui.LocalAppLanguage
import dev.galaxy.toolbox.ui.MiuixSectionHeader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnglishLocalizationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersEnglishInBothStyles() {
        AppText.install(RuntimeEnvironment.getApplication())
        val style = mutableStateOf(AppStyle.LIQUID_GLASS)
        compose.setContent {
            GalaxyBoxTheme(style = style.value) {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    Column {
                        if (style.value == AppStyle.MIUIX) MiuixSectionHeader("相機清單")
                        else GalaxyText("相機清單")
                        GalaxyText("CPU 7 當前頻率")
                    }
                }
            }
        }
        compose.onNodeWithText("Camera list").assertIsDisplayed()
        compose.onNodeWithText("CPU 7 current frequency").assertIsDisplayed()
        compose.runOnIdle { style.value = AppStyle.MIUIX }
        compose.onNodeWithText("Camera list").assertIsDisplayed()
        compose.onNodeWithText("CPU 7 current frequency").assertIsDisplayed()
    }

    @Test fun localizesListsAndExpandableGroupsWithoutChangingSystemIdentifiers() {
        AppText.install(RuntimeEnvironment.getApplication())
        fun english(text: String) = AppText.translate(text, AppLanguage.EN)
        assertEquals("Built-in microphone, Bluetooth A2DP", english("內建麥克風、藍牙 A2DP"))
        assertEquals("Expand Camera list (2 items)", english("展開 相機清單（共 2 項）"))
        assertEquals("/sys/class/power_supply/battery/voltage_now",
            english("/sys/class/power_supply/battery/voltage_now"))
        assertEquals("小米 Buds、手錶 X18", english("小米 Buds、手錶 X18"))
    }
}
