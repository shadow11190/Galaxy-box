package dev.galaxy.toolbox.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.model.ImportedReport
import dev.galaxy.toolbox.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatteryImportRenderingTest {
    @get:Rule val rule = createComposeRule()
    private val instant = "2026-10-19T11:15:51.553062Z"
    private var dark by mutableStateOf(true)
    private var large by mutableStateOf(false)
    private var drawingView: View? = null
    private fun report(text: String): ImportedReport {
        val parsed = BugreportParser.parseReport(text.byteInputStream())
        return ImportedReport("test", parsed.fields, instant, parsed.diagnostics)
    }
    private fun show(report: ImportedReport) {
        rule.setContent {
            val original = LocalDensity.current
            val view = LocalView.current
            SideEffect { drawingView = view }
            CompositionLocalProvider(LocalDensity provides Density(original.density, if (large) 1.6f else 1f)) {
                GalaxyBoxTheme(dark) {
                    Box(Modifier.fillMaxSize()) {
                        GalaxyBackdrop()
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                            InfoCard("電池匯入測試 · 非手機量測") { ImportedBatteryResults(report) }
                        }
                    }
                }
            }
        }
    }
    private fun valid() = report("DUMP OF SERVICE battery:\ncycle count: [0]\nmSavedBatteryAsoc: [96]\nmSavedBatteryUsage: [12345]")

    @Test fun arraysAreVisibleAndTimeIsReadableInLocalZone() {
        show(valid())
        rule.onNodeWithText("已取得 3 項電池資料").assertIsDisplayed()
        for (value in listOf("0", "96", "12345")) rule.onNodeWithText(value).performScrollTo().assertIsDisplayed()
        assertEquals("2026/10/19 19:15:51", formatImportTime(instant, ZoneId.of("Asia/Taipei")))
        rule.onNodeWithText(instant, substring = true).assertDoesNotExist()
    }
    @Test fun skippedLinesAreShownOnceOnlyWhenDiagnosticsAreOpened() {
        val unknown = report(("x".repeat(17000) + "\n").repeat(22) + "DUMP OF SERVICE battery:\nlevel: 42\n")
        show(unknown)
        rule.onAllNodesWithText("未知").assertCountEquals(3)
        rule.onNodeWithText("超長行", substring = true).assertDoesNotExist()
        rule.onNodeWithText("查看解析詳細資訊").performScrollTo().performClick()
        rule.onAllNodesWithText("已略過 22 條", substring = true).assertCountEquals(1)
        rule.onNodeWithText("韌體可能未提供", substring = true).assertDoesNotExist()
    }
    @Test fun conflictingArrayStaysUnknownAndRawEvidenceIsExpandable() {
        val conflict = report("DUMP OF SERVICE battery:\nmSavedBatteryAsoc: [96, 94]")
        show(conflict)
        rule.onNodeWithText("數值衝突").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("[96, 94]", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("查看 Samsung ASOC 原始依據").performScrollTo().performClick()
        rule.onNodeWithText("mSavedBatteryAsoc: [96, 94]", substring = true).performScrollTo().assertIsDisplayed()
        assertTrue(batteryImportSummary(conflict).contains("[96, 94]"))
        assertFalse(batteryImportSummary(conflict).contains("level: 42"))
    }
    @Test fun resultsFitLightDarkAndLargeTextWithScrolling() {
        show(valid())
        for (isDark in listOf(false, true)) for (isLarge in listOf(false, true)) {
            rule.runOnIdle { dark = isDark; large = isLarge }
            for (value in listOf("0", "96", "12345")) rule.onNodeWithText(value).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("已取得 3 項電池資料").performScrollTo()
            val bitmap = rule.runOnIdle {
                val view = drawingView!!
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val file = File("build/reports/visual-checks/import-${if (isDark) "dark" else "light"}-${if (isLarge) "large" else "standard"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
