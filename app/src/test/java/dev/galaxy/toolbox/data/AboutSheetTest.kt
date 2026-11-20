package dev.galaxy.toolbox.data

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.galaxy.toolbox.ui.AboutApp
import dev.galaxy.toolbox.ui.GalaxyBoxTheme
import dev.galaxy.toolbox.ui.AppStyle
import top.yukonga.miuix.kmp.basic.Scaffold
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AboutSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun miuixAboutOpensInsideScaffold() {
        rule.setContent {
            GalaxyBoxTheme(style = AppStyle.MIUIX) {
                Scaffold { AboutApp {} }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        rule.onNodeWithText("Galaxy Box").assertIsDisplayed()
        rule.onNodeWithText("可以做什麼").assertIsDisplayed()
    }
}
