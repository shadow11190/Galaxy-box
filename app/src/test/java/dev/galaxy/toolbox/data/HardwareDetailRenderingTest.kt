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
class HardwareDetailRenderingTest {
    @get:Rule val rule = createComposeRule()
    private var dark by mutableStateOf(true)
    private var large by mutableStateOf(false)
    private var view: View? = null
    private var selected: Reading? = null
    private val cameraRows get() = CameraDetails.describe("test", org.robolectric.shadows.ShadowCameraCharacteristics.newCameraCharacteristics())
    private val cpuRows = listOf(
        Reading("處理器名稱", "測試資料 · 非實機量測", "Test fixture"),
        Reading("處理器製造商", "測試廠商", "Test fixture"),
        Reading("SoC 原始型號", "TEST", "Test fixture"),
        Reading("CPU 邏輯核心", "8", "Test fixture"),
        Reading("CPU 製程（規格）", null, "Test fixture"),
        Reading("CPU 指令功能", "flag_one\nflag_two\nflag_three\nflag_four\nflag_five", "Test fixture")
    )
    private fun show(title: String, readings: List<Reading>, category: HardwareCategory) {
        rule.setContent {
            val density = LocalDensity.current
            val currentView = LocalView.current
            SideEffect { view = currentView }
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.6f else 1f)) {
                GalaxyBoxTheme(dark) {
                    Box(Modifier.fillMaxSize()) {
                        GalaxyBackdrop()
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                            HardwareDetailCard(title, readings, category) { selected = it }
                        }
                    }
                }
            }
        }
    }
    @Test fun cameraTechnicalRowsExpandAndRetainFullSourceValues() {
        val rows = cameraRows
        show("相機測試", rows, HardwareCategory.CAMERA)
        rule.onNodeWithText("感光元件型號／廠商").assertDoesNotExist()
        rule.onNodeWithText("展開 相機測試（共 ${rows.size} 項）").performScrollTo().performClick()
        rule.onNodeWithText("感光元件型號／廠商").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("Camera2 無通用公開型號欄位", selected?.source); assertNull(selected?.value) }
        rule.onNodeWithText("收合 相機測試").performScrollTo().performClick()
        rule.onNodeWithText("感光元件型號／廠商").assertDoesNotExist()
    }
    @Test fun longValuesOpenUntruncatedReading() {
        show("處理器概覽", cpuRows, HardwareCategory.CPU)
        rule.onNodeWithText("CPU 指令功能").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(selected!!.value!!.contains("flag_five")); assertEquals("Test fixture", selected!!.source) }
    }
    @Test fun cpuCardFitsLightDarkAndLargeText() {
        show("處理器概覽", cpuRows, HardwareCategory.CPU)
        capture("hardware-cpu", "處理器名稱", "CPU 指令功能")
    }
    @Test fun cameraCardFitsLightDarkAndLargeTextWithUnknownFields() {
        show("後置相機 · ID test · 測試資料", cameraRows, HardwareCategory.CAMERA)
        capture("hardware-camera", "最大 JPEG（一般模式）", "Camera2 硬體等級")
    }
    private fun capture(name: String, first: String, last: String) {
        for (isDark in listOf(false, true)) for (isLarge in listOf(false, true)) {
            rule.runOnIdle { dark = isDark; large = isLarge }
            rule.onNodeWithText(last).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(first).performScrollTo().assertIsDisplayed()
            val bitmap = rule.runOnIdle {
                val v = view!!
                Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
            }
            val file = File("build/reports/visual-checks/$name-${if (isDark) "dark" else "light"}-${if (isLarge) "large" else "standard"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
