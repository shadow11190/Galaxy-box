package dev.galaxy.toolbox.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.galaxy.toolbox.model.ChargingPoint
import dev.galaxy.toolbox.model.ChargingTestResult
import dev.galaxy.toolbox.model.ChargingTestState
import dev.galaxy.toolbox.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChargingTestPreview {
    @get:Rule val compose = createComposeRule()

    @Test fun renderChargingTestStates() {
        val points = List(61) { index ->
            ChargingPoint(index * 2_000L, 18.0 - index * .107 + 1.55 * sin(index * .29))
        }
        val stats = ChargingTestResult(
            startedAt = 1_795_179_600_000L, durationMs = 120_000L, samples = points.size,
            peak = points.maxOf { requireNotNull(it.watts) },
            integratedWattMs = points.zipWithNext().sumOf { (a, b) ->
                (requireNotNull(a.watts) + requireNotNull(b.watts)) * 1_000
            },
            measuredMs = 120_000L, startPercent = 41, endPercent = 43,
        )
        var finished by mutableStateOf(false)
        lateinit var drawingView: View
        compose.setContent {
            val view = LocalView.current
            val context = LocalContext.current
            SideEffect { drawingView = view }
            remember(context) { AppText.install(context) }
            val language = if (finished) AppLanguage.EN else AppLanguage.ZH_TW
            GalaxyBoxTheme(dark = !finished, style = AppStyle.LIQUID_GLASS, themeMode = if (finished) "light" else "dark") {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    val backdrop = rememberAppBackdrops()
                    val scroll = rememberLazyListState()
                    val pages = listOf("總覽", "硬體", "電池", "更新", "檢測").map { AppText.translate(it, language) }
                    Box(Modifier.fillMaxSize()) {
                        GalaxyBackdrop()
                        AppScaffold(pages, 2, {}, backdrop.controls, rememberNavigationMotion(2, pages.size),
                            true, scroll, {}, {}) { inset ->
                            AppScrollContent(inset, state = scroll) {
                                item(key = "title") { AppLargeTitle("電池", scroll) }
                                item(key = "charging-test") {
                                    ChargingTestCard(
                                        ChargingTestState(
                                            active = !finished, result = stats, points = points,
                                            message = if (finished) "檢測已結束。" else null,
                                            history = if (finished) listOf(stats) else emptyList(),
                                        ), {}, {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("停止檢測").assertIsDisplayed()
        capture(drawingView, "charging-test-running-zh-TW")
        compose.runOnIdle { finished = true }
        compose.onNodeWithText("Show charging history").assertIsDisplayed()
        capture(drawingView, "charging-test-finished-en")
    }

    private fun capture(view: View, name: String) {
        val bitmap = compose.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file = File("build/reports/visual-checks/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
