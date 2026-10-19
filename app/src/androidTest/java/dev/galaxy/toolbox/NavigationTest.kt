package dev.galaxy.toolbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun launchesAndNavigates() {
        rule.onNodeWithText("掌握你的 Galaxy").assertIsDisplayed()
        rule.onNodeWithText("硬體").performClick()
        rule.onNodeWithText("處理器").assertIsDisplayed()
        rule.onNodeWithText("圖形").performClick()
        rule.onNodeWithText("圖形 ·", substring = true).assertIsDisplayed()
        rule.onNodeWithText("更新").performClick()
        rule.onNodeWithText("Android / API").assertIsDisplayed()
        rule.onNodeWithText("檢測").performClick()
        rule.onNodeWithText("開啟貼膜輔助").performClick()
        rule.onNodeWithText("貼膜輔助", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("結束").performClick()
        rule.onNodeWithText("開啟貼膜輔助").assertIsDisplayed()
    }
}
