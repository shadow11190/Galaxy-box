package dev.galaxy.toolbox.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
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
class NavigationInteractionTest {
    @get:Rule val rule = createComposeRule()
    private var selected by mutableIntStateOf(0)
    private var width by mutableStateOf(360.dp)
    private var rtl by mutableStateOf(false)
    private var dark by mutableStateOf(true)
    private var fontScale by mutableFloatStateOf(1f)
    private var drawingView: View? = null
    private val changes = mutableListOf<Int>()
    private var opticalFailures = 0
    private val pages = listOf("總覽", "硬體", "電池", "更新", "檢測")
    private fun setup(requestGlass: Boolean = false) {
        rule.setContent {
            val original = LocalDensity.current
            val view = LocalView.current
            SideEffect { drawingView = view }
            CompositionLocalProvider(LocalDensity provides Density(original.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                GalaxyBoxTheme(dark) {
                    val source = rememberLayerBackdrop()
                    Box(Modifier.width(width).fillMaxHeight()) {
                        GalaxyBackdrop(if (requestGlass) Modifier.layerBackdrop(source) else Modifier)
                        CompositionLocalProvider(LocalGlass provides GlassEnvironment(requestGlass, source, { opticalFailures++ })) {
                        Column(Modifier.fillMaxSize()) {
                            Text("導覽互動測試 · 無手機資料", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.weight(1f))
                            GlassNavigation(pages, selected, { changes += it; selected = it }, source, requestGlass)
                        }
                        }
                    }
                }
            }
        }
    }
    private fun nav() = rule.onNodeWithTag("bottom-navigation")
    private fun page(index: Int) = rule.onNodeWithTag("navigation-page-$index")

    @Test fun restingGlassBubbleCoversOneTabAndKeepsTouchAccessibility() {
        setup()
        rule.onNodeWithTag("navigation-indicator").assertHeightIsEqualTo(56.dp)
        val bubble = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        assertEquals(page(0).fetchSemanticsNode().boundsInRoot.width, bubble.width, 1f)
        page(0).assertIsSelected().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        page(3).performClick().assertIsSelected()
        page(0).assertIsNotSelected()
        rule.runOnIdle { assertEquals(listOf(3), changes) }
    }

    @Test fun dragFollowsFingerButChangesPageOnlyAfterRelease() {
        setup()
        nav().performTouchInput {
            down(Offset(width * .1f, center.y))
            moveTo(Offset(width * .5f, center.y), delayMillis = 300)
        }
        rule.runOnIdle { assertTrue(changes.isEmpty()); assertEquals(0, selected) }
        val bar = nav().fetchSemanticsNode().boundsInRoot
        val frame = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        assertEquals(bar.center.x, frame.center.x, 3f)
        nav().performTouchInput { up() }
        page(2).assertIsSelected()
        rule.runOnIdle { assertEquals(listOf(2), changes) }
    }

    @Test fun cancelledDragReturnsToCurrentPageWithoutNavigation() {
        setup()
        nav().performTouchInput {
            down(Offset(width * .1f, center.y))
            moveTo(Offset(width * .9f, center.y), delayMillis = 250)
            cancel()
        }
        page(0).assertIsSelected()
        rule.runOnIdle { assertTrue(changes.isEmpty()) }
        val frame = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        val first = page(0).fetchSemanticsNode().boundsInRoot
        assertEquals(first.center.x, frame.center.x, 3f)
    }

    @Test fun verticalGestureDoesNotTurnIntoPageSelection() {
        setup()
        nav().performTouchInput {
            down(Offset(width * .5f, height * .8f))
            moveTo(Offset(width * .5f, -100f), delayMillis = 250)
            up()
        }
        page(0).assertIsSelected()
        rule.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test fun rtlDragAndBothEdgesUseTheCorrectPage() {
        rtl = true
        setup()
        nav().performTouchInput {
            down(Offset(width * .9f, center.y))
            moveTo(Offset(-40f, center.y), delayMillis = 300)
            up()
        }
        page(4).assertIsSelected()
        nav().performTouchInput {
            down(Offset(width * .1f, center.y))
            moveTo(Offset(width + 40f, center.y), delayMillis = 300)
            up()
        }
        page(0).assertIsSelected()
        rule.runOnIdle { assertEquals(listOf(4, 0), changes) }
    }

    @Test fun resizeDuringDragCancelsAndResizedBarStillWorks() {
        setup()
        nav().performTouchInput {
            down(Offset(width * .1f, center.y))
            moveTo(Offset(width * .9f, center.y), delayMillis = 300)
        }
        rule.runOnIdle { width = 320.dp }
        nav().performTouchInput { up() }
        rule.runOnIdle { assertTrue(changes.isEmpty()) }
        page(0).assertIsSelected()
        val frame = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        assertEquals(page(0).fetchSemanticsNode().boundsInRoot.center.x, frame.center.x, 3f)
        nav().performTouchInput {
            down(Offset(width * .1f, center.y))
            moveTo(Offset(width * .7f, center.y), delayMillis = 300)
            up()
        }
        page(3).assertIsSelected()
    }

    @Test fun compactBarFitsLightDarkLargeTextAndNarrowWidth() {
        setup()
        for (isDark in listOf(false, true)) for (scale in listOf(1f, 1.6f)) {
            rule.runOnIdle { dark = isDark; fontScale = scale; width = 320.dp }
            pages.indices.forEach { page(it).assertIsDisplayed().assertHasClickAction().assertWidthIsAtLeast(48.dp) }
            val bitmap = rule.runOnIdle {
                val view = drawingView!!
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val file = File("build/reports/visual-checks/navigation-${if (isDark) "dark" else "light"}-${if (scale > 1f) "large" else "standard"}.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun pressExpandsBubbleAndCancellationReturnsToRestWithoutSwitching() {
        setup()
        val rest = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        nav().performTouchInput { down(Offset(width * .1f, center.y)) }
        rule.mainClock.advanceTimeBy(400)
        val pressed = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        assertTrue(pressed.width > rest.width * 1.04f)
        rule.runOnIdle { assertTrue(changes.isEmpty()) }
        nav().performTouchInput { cancel() }
        val released = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
        assertEquals(rest.width, released.width, 1f)
        page(0).assertIsSelected()
    }

    @Test fun opticalBubbleExtendsBeyondPanelAndStaysInsideReservedSpace() {
        width = 320.dp
        setup(requestGlass = true)
        for (scale in listOf(1f, 1.6f, 2f)) {
            rule.runOnIdle { fontScale = scale }
            rule.mainClock.advanceTimeBy(500)
            val panel = rule.onNodeWithTag("navigation-panel").fetchSemanticsNode().boundsInRoot
            val reserve = rule.onNodeWithTag("navigation-optical-container").fetchSemanticsNode().boundsInRoot
            val bubble = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
            assertTrue("Resting glass is taller than the original frame", bubble.top < panel.top && bubble.bottom > panel.bottom)
            assertTrue(bubble.top >= reserve.top && bubble.bottom <= reserve.bottom)
            assertTrue(rule.onNodeWithTag("navigation-panel").fetchSemanticsNode().children.isEmpty())
            for (index in listOf(0, 4)) {
                page(index).performClick(); rule.mainClock.advanceTimeBy(600)
                val bar = nav().fetchSemanticsNode().boundsInRoot
                nav().performTouchInput { down(Offset(if (index == 0) width * .1f else width * .9f, center.y)) }
                rule.mainClock.advanceTimeBy(400)
                val pressed = rule.onNodeWithTag("navigation-indicator").fetchSemanticsNode().boundsInRoot
                assertTrue(pressed.height > panel.height)
                assertTrue(pressed.top >= reserve.top && pressed.bottom <= reserve.bottom)
                assertTrue(pressed.left >= 0 && pressed.right <= with(rule.density) { 320.dp.toPx() })
                val label = rule.onNodeWithText(pages[index]).fetchSemanticsNode().boundsInRoot
                assertTrue(label.top >= pressed.top && label.bottom <= pressed.bottom)
                nav().performTouchInput { cancel() }
            }
        }
    }

    @Test fun navigationFallbackLayoutRendersBothThemesAndLargeLabels() {
        // Software Canvas cannot draw Backdrop RuntimeShader highlights. Optical overflow is
        // verified separately by the real component geometry test; these are fallback images.
        setup(requestGlass = false)
        for (isDark in listOf(false, true)) for (scale in listOf(1f, 1.6f)) {
            rule.runOnIdle { dark = isDark; fontScale = scale; width = 320.dp; selected = 2 }
            rule.mainClock.advanceTimeBy(600)
            pages.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
            val bitmap = rule.runOnIdle { val v = drawingView!!; Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) } }
            val file = File("build/reports/visual-checks/navigation-overflow-${if (isDark) "dark" else "light"}-${if (scale > 1f) "large" else "standard"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun opticalCaptureLayerKeepsOneAccessibleTabSetAndHandlesShaderFailure() {
        // Deliberately request optics on the limited Windows renderer: exercise failure
        // handling and sibling capture semantics, not a claim of validated refraction.
        setup(requestGlass = true)
        pages.forEach { rule.onAllNodesWithText(it).assertCountEquals(1) }
        page(2).performClick().assertIsSelected()
        pages.forEach { rule.onAllNodesWithText(it).assertCountEquals(1) }
        if (!GlassCompatibility.refractionSupported) rule.runOnIdle { assertTrue(opticalFailures > 0) }
    }
}
