package dev.galaxy.toolbox

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.galaxy.toolbox.ui.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Runs on a real Android renderer; Windows API 34 Robolectric cannot validate child shaders. */
class GlassDeviceTest {
    @get:Rule val rule = createComposeRule()
    @Test fun backdropCardsCaptureBackgroundAndCanSwitchToOrdinaryCards() {
        assumeTrue(Build.VERSION.SDK_INT >= 33 && GlassCompatibility.refractionSupported)
        var enabled by mutableStateOf(true)
        rule.setContent {
            GalaxyBoxTheme(dark = true) {
                val source = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    GalaxyBackdrop(Modifier.layerBackdrop(source))
                    CompositionLocalProvider(LocalGlass provides GlassEnvironment(enabled, source)) {
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            InfoCard("背景捕捉驗證") { Text("—"); Text("測試版面，不含硬體示範值") }
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("背景捕捉驗證").assertIsDisplayed()
        val glass = rule.onRoot().captureToImage().asAndroidBitmap()
        rule.runOnIdle { enabled = false }
        val plain = rule.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("Backdrop and ordinary surface should differ", glass.sameAs(plain))
    }

    @Test fun glassNavigationSelectionMovesUnderFingerAndCommitsOnRelease() {
        assumeTrue(Build.VERSION.SDK_INT >= 33 && GlassCompatibility.refractionSupported)
        var selected by mutableIntStateOf(0)
        rule.setContent {
            GalaxyBoxTheme(dark = true) {
                val source = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    GalaxyBackdrop(Modifier.layerBackdrop(source))
                    CompositionLocalProvider(LocalGlass provides GlassEnvironment(true, source)) {
                        Column(Modifier.fillMaxSize()) {
                            Spacer(Modifier.weight(1f))
                            GlassNavigation(listOf("總覽", "硬體", "電池", "更新", "檢測"), selected, { selected = it }, source, true)
                        }
                    }
                }
            }
        }
        val bar = rule.onNodeWithTag("bottom-navigation")
        val rest = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag("navigation-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Raised glass remains visible without pressing", rest.top < panel.top && rest.bottom > panel.bottom)
        val label = rule.onNodeWithText("總覽").fetchSemanticsNode().boundsInRoot
        assertTrue("The optical bubble must include the label, not only the icon", rest.bottom >= label.bottom)
        rule.onAllNodesWithText("總覽").assertCountEquals(1)
        val before = rule.onRoot().captureToImage().asAndroidBitmap()
        bar.performTouchInput {
            down(Offset(width * .1f, center.y))
            moveTo(Offset(width * .5f, center.y), delayMillis = 300)
        }
        rule.runOnIdle { assertEquals(0, selected) }
        val frame = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        assertTrue("Pressed bubble should expand like the upstream sample", frame.width > rest.width * 1.08f)
        assertEquals(bar.fetchSemanticsNode().boundsInRoot.center.x, frame.center.x, 3f)
        val during = rule.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("The moving glass selection should produce a different image", before.sameAs(during))
        bar.performTouchInput { up() }
        rule.onNodeWithTag("navigation-page-2").assertIsSelected()
    }
}

