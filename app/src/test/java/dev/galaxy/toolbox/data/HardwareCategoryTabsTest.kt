package dev.galaxy.toolbox.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.model.HardwareCategory
import dev.galaxy.toolbox.ui.GalaxyBoxTheme
import dev.galaxy.toolbox.ui.HardwareCategoryTabs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardwareCategoryTabsTest {
    @get:Rule val rule = createComposeRule()
    @Test fun audioAndNetworkHeaderInsertionDoesNotResetTabPosition() {
        var category by mutableStateOf(HardwareCategory.CPU)
        var visible by mutableStateOf(true)
        var tick by mutableIntStateOf(0)
        lateinit var state: LazyListState
        rule.setContent {
            state = rememberLazyListState()
            GalaxyBoxTheme(true) {
                if (visible) LazyColumn(Modifier.width(280.dp).height(600.dp)) {
                    item { Text("硬體 · $tick") }
                    if (category in listOf(HardwareCategory.AUDIO, HardwareCategory.NETWORK)) item { Text("重新讀取連線與裝置") }
                    item(key = "hardware-category-tabs") { HardwareCategoryTabs(category, { category = it }, state) }
                    item { Text("內容 ${category.title}") }
                }
            }
        }
        rule.onNodeWithTag("hardware-category-tabs").performScrollToNode(hasText("音訊"))
        rule.onNodeWithText("音訊").performClick()
        rule.onNodeWithText("音訊").assertIsDisplayed().assertIsSelected()
        val position = rule.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        assertTrue(position.first > 0)
        rule.runOnIdle { tick++ }
        rule.runOnIdle { assertEquals(position, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset); visible = false }
        rule.runOnIdle { visible = true }
        rule.onNodeWithText("音訊").assertIsDisplayed().assertIsSelected()
        rule.runOnIdle { assertEquals(position, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset) }
        rule.onNodeWithTag("hardware-category-tabs").performScrollToNode(hasText("編解碼"))
        rule.onNodeWithText("編解碼").performClick()
        rule.onNodeWithText("編解碼").assertIsDisplayed().assertIsSelected()
        rule.runOnIdle { assertTrue(state.firstVisibleItemIndex > 0) }
    }
}
