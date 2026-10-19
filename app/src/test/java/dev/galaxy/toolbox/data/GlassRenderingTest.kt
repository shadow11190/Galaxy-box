package dev.galaxy.toolbox.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
class GlassRenderingTest {
    @get:Rule val rule = createComposeRule()
    @Test fun capabilityPolicyRejectsUnsupportedAndPowerSavingDevices() {
        assertTrue(GlassCompatibility.canUse(33, true, false, false, true))
        assertFalse(GlassCompatibility.canUse(32, true, false, false, true))
        assertFalse(GlassCompatibility.canUse(34, false, false, false, true))
        assertFalse(GlassCompatibility.canUse(34, true, true, false, true))
        assertFalse(GlassCompatibility.canUse(34, true, false, true, true))
        assertFalse(GlassCompatibility.canUse(34, true, false, false, false))
    }

    @Test fun sharedCardsRenderInLightDarkLargeTextWithCapabilityFallback() {
        var drawingView: View? = null
        var dark by mutableStateOf(true)
        var glass by mutableStateOf(true)
        var large by mutableStateOf(false)
        rule.setContent {
            val view = LocalView.current
            SideEffect { drawingView = view }
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (large) 1.6f else 1f)) {
                GalaxyBoxTheme(dark) {
                    val colors = MaterialTheme.colorScheme
                    assertTrue(ColorUtils.calculateContrast(colors.onSurface.toArgb(), colors.surface.toArgb()) >= 4.5)
                    assertTrue(ColorUtils.calculateContrast(colors.onSurfaceVariant.toArgb(), colors.surface.toArgb()) >= 4.5)
                    assertTrue(ColorUtils.calculateContrast(colors.primary.toArgb(), colors.surface.toArgb()) >= 4.5)
                    val source = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        GalaxyBackdrop(Modifier.layerBackdrop(source))
                        CompositionLocalProvider(LocalGlass provides GlassEnvironment(glass && GlassCompatibility.refractionSupported, source)) {
                            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                Text("Galaxy box", color = colors.primary, style = MaterialTheme.typography.headlineLarge)
                                InfoCard("總覽") { Text("本機渲染測試 · 不含手機量測") }
                                InfoCard("CPU 測點最高溫") { Text("—", style = MaterialTheme.typography.headlineMedium); Text("無可用資料") }
                                Spacer(Modifier.weight(1f))
                                GlassNavigation(listOf("總覽", "硬體", "電池", "更新", "檢測"), 0, {}, source, glass && GlassCompatibility.refractionSupported)
                            }
                        }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) for (isGlass in listOf(false, true)) for (isLarge in listOf(false, true)) {
            rule.runOnIdle { dark = isDark; glass = isGlass; large = isLarge }
            rule.onNodeWithText("CPU 測點最高溫").assertIsDisplayed()
            rule.onNodeWithText("無可用資料").assertIsDisplayed()
            val bitmap = rule.runOnIdle {
                val view = drawingView!!
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val file = File("build/reports/visual-checks/app-${if (isDark) "dark" else "light"}-${if (isGlass && GlassCompatibility.refractionSupported) "glass" else "plain"}-${if (isLarge) "large" else "standard"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
