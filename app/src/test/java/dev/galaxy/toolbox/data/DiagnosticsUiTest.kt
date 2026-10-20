package dev.galaxy.toolbox.data

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.galaxy.toolbox.diagnostics.*
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
class DiagnosticsUiTest {
    @get:Rule val rule = createComposeRule()
    private var dark by mutableStateOf(true)
    private var scale by mutableFloatStateOf(1f)
    private var view: View? = null
    private val pages = listOf("總覽", "硬體", "電池", "更新", "檢測")
    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current; val currentView = LocalView.current
            SideEffect { view = currentView }
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) { GalaxyBoxTheme(dark) { content() } }
        }
    }
    @Test fun batteryUnknownRowsAreRemovedButSourceReportedZeroRemains() {
        show { AppScrollContent(PaddingValues()) { batteryInformation(listOf(Reading("SOH", null, "API"), Reading("設計容量", null, "API"), BatteryPresentation.cycleCount(0))) } }
        rule.onNodeWithText("SOH").assertDoesNotExist(); rule.onNodeWithText("設計容量").assertDoesNotExist()
        rule.onNodeWithText("系統回報 0").assertIsDisplayed()
    }
    @Test fun lastButtonClearsNavigationWhileBackdropExtendsBehindIt() {
        var clicks = 0
        show {
            val source = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                GalaxyBackdrop()
                Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    bottomBar = { GlassNavigation(pages, 2, {}, source, false) }) { insets ->
                    AppScrollContent(insets) {
                        repeat(12) { item { InfoCard("電池測試資料 $it") { Text("真實頁面容器的排版測試") } } }
                        item { Button(onClick = { clicks++ }, modifier = Modifier.fillMaxWidth()) { Text("匯入完整 ZIP／TXT") } }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) for (font in listOf(1f, 1.6f)) {
            rule.runOnIdle { dark = isDark; scale = font }
            val button = rule.onNodeWithText("匯入完整 ZIP／TXT")
            rule.onNodeWithTag("app-scroll-content").performScrollToIndex(12)
            button.assertIsDisplayed().performClick()
            val bar = rule.onNodeWithTag("bottom-navigation").fetchSemanticsNode().boundsInRoot
            val viewport = rule.onNodeWithTag("app-scroll-content").fetchSemanticsNode().boundsInRoot
            assertTrue("Capture must cover content behind transparent nav", viewport.bottom >= bar.bottom)
            assertTrue(button.fetchSemanticsNode().boundsInRoot.bottom <= bar.top)
            val bitmap = rule.runOnIdle { val v = view!!; Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) } }
            val file = File("build/reports/visual-checks/battery-nav-${if (isDark) "dark" else "light"}-${if (font > 1f) "large" else "standard"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(4, clicks)
    }
    @Test fun firmwarePageNeedsNoManualInputOrCsc() {
        show { FirmwareLinksCard("SM-S9480") }
        rule.onNodeWithText("本機型號 · SM-S9480").assertIsDisplayed()
        rule.onNodeWithText("查看此型號公開韌體").assertHasClickAction()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun gridOpensOnlyOnExplicitTapAndQueuesCapabilityFilteredTests() {
        val tests = TestCatalogue.available(TestCapabilities())
        val opened = mutableListOf<List<DeviceTest>>()
        scale = 1.6f
        show { AppScrollContent(PaddingValues()) { deviceTestGrid(tests, emptyMap(), { opened += it }) } }
        rule.runOnIdle { assertTrue(opened.isEmpty()) }
        rule.onNodeWithText("逐項檢查全部 4 項").performClick()
        assertEquals(tests, opened.single())
        rule.onNodeWithText(DeviceTest.CHARGING.title).performScrollTo().performClick()
        assertEquals(listOf(DeviceTest.CHARGING), opened.last())
        rule.onNodeWithText(DeviceTest.NFC.title).assertDoesNotExist()
    }
    @Test fun fullGridRemainsReachableInLightDarkAndLargeText() {
        val tests = TestCatalogue.available(TestCapabilities(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true))
        show { Box(Modifier.fillMaxSize()) { GalaxyBackdrop(); AppScrollContent(PaddingValues()) { deviceTestGrid(tests, emptyMap(), {}) } } }
        for (isDark in listOf(false, true)) for (font in listOf(1f, 1.6f)) {
            rule.runOnIdle { dark = isDark; scale = font }
            rule.onNodeWithTag("app-scroll-content").performScrollToIndex(1)
            tests.forEach { rule.onNodeWithText(it.title).performScrollTo().assertIsDisplayed().assertHasClickAction() }
            rule.onNodeWithTag("app-scroll-content").performScrollToIndex(0)
            val bitmap = rule.runOnIdle { val v = view!!; Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) } }
            val file = File("build/reports/visual-checks/diagnostics-${if (isDark) "dark" else "light"}-${if (font > 1f) "large" else "standard"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun microphonePermissionDoesNotAutomaticallyStartCapture() {
        var entered = false
        show { PermissionGate(listOf(Manifest.permission.RECORD_AUDIO), "麥克風") { entered = true; Text("已進入錄音區") } }
        rule.onNodeWithText("允許 麥克風 權限").assertIsDisplayed()
        rule.onNodeWithText("已進入錄音區").assertDoesNotExist()
        assertFalse(entered)
    }
    @Test fun screenTestKeepsOnlyItsDialogAwakeAndRestoresItOnExit() {
        var active by mutableStateOf(true)
        var dialog: android.view.Window? = null
        var activity: android.view.Window? = null
        var baseline = 0
        show {
            val host = androidx.activity.compose.LocalActivity.current!!
            SideEffect { activity = host.window; baseline = host.window.attributes.flags }
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                val child = LocalView.current
                SideEffect { dialog = (child.parent as androidx.compose.ui.window.DialogWindowProvider).window }
                if (active) ScreenWindowEffect()
                Text("螢幕檢查視窗")
            }
        }
        val keep = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        rule.runOnIdle {
            assertNotSame(activity, dialog)
            assertTrue(dialog!!.attributes.flags and keep != 0)
            assertEquals(baseline and keep, activity!!.attributes.flags and keep)
            active = false
        }
        rule.runOnIdle { assertEquals(0, dialog!!.attributes.flags and keep) }
    }
    @Test fun shizukuReadIsManualAndBusyStateOffersCancellation() {
        var busy by mutableStateOf(false); var reads = 0; var cancels = 0
        show { ShizukuDiagnosticsCard(ProbeKind.BATTERY, ShizukuAvailability(ShizukuConnection.READY), busy, null, null,
            { reads++; busy = true }, { cancels++; busy = false }, {}, {}) }
        rule.runOnIdle { assertEquals(0, reads) }
        rule.onNodeWithText("讀取電池原始資料").performClick()
        rule.onNodeWithText("取消讀取").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, reads); assertEquals(1, cancels) }
    }
}
