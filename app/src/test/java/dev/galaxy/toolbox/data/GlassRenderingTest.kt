package dev.galaxy.toolbox.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
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
    @Test fun originalDynamicThemeSetsReadablePageTextColor() {
        rule.setContent {
            GalaxyBoxTheme(dark = true, style = AppStyle.LIQUID_GLASS, themeMode = "monet") {
                assertEquals(MaterialTheme.colorScheme.onBackground, LocalContentColor.current)
            }
        }
    }
    @Test fun miuixSquircleExposesCornersForBackdropLens() {
        val squircle: Shape = GalaxySquircleShape(22.dp)
        assertTrue(squircle is CornerBasedShape)
        assertTrue(squircle.createOutline(Size(300f, 180f), LayoutDirection.Ltr, Density(2f)) is Outline.Generic)
    }
    // Robolectric's native shader compiler cannot parse MIUIX's AGSL shader;
    // use the same path silhouette as the app's older-Android fallback.
    @Test fun visualStylesProduceComparableLightAndDarkPreviews() {
        var drawingView: View? = null
        var style by mutableStateOf(AppStyle.LIQUID_GLASS)
        var dark by mutableStateOf(true)
        rule.setContent {
            val view = LocalView.current
            SideEffect { drawingView = view }
            GalaxyBoxTheme(dark, style) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    GalaxyBackdrop(Modifier.layerBackdrop(backdrop))
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GalaxyText("Galaxy box", color = Mint, style = GalaxyTypography.titleMedium)
                        GalaxyText("總覽", color = GalaxyPalette.onBackground,
                            style = GalaxyTypography.headlineLarge)
                        GalaxyText("介面預覽 · 非裝置讀值", color = Muted)
                        CompositionLocalProvider(LocalNativeMiuixCard provides false) {
                            InfoCard("目前裝置") {
                                GalaxyText("Galaxy Box", style = GalaxyTypography.headlineSmall)
                                GalaxyText("裝置與處理器資料以實機讀值顯示", color = Muted)
                            }
                            InfoCard("溫度摘要") {
                                GalaxyText("CPU 測點最高溫　—")
                                GalaxyText("沒有可靠來源時顯示無可用資料", color = Muted)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        GlassNavigation(listOf("總覽", "硬體", "電池", "更新", "檢測"), 0, {}, backdrop, false)
                    }
                }
            }
        }
        for (isDark in listOf(true, false)) for (choice in AppStyle.entries) {
            rule.runOnIdle { dark = isDark; style = choice }
            rule.onNodeWithText("介面預覽 · 非裝置讀值").assertIsDisplayed()
            rule.onNodeWithText("CPU 測點最高溫　—").assertIsDisplayed()
            val bitmap = rule.runOnIdle {
                val view = drawingView!!
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val file = File("build/reports/visual-checks/style-${choice.preferenceValue}-${if (isDark) "dark" else "light"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun capabilityPolicyUsesBlurOnAndroid12WithoutRequiringLens() {
        assertTrue(GlassCompatibility.canUse(33, true, false))
        assertTrue(GlassCompatibility.canUse(31, true, false))
        assertFalse(GlassCompatibility.canUse(30, true, false))
        assertFalse(GlassCompatibility.canUse(34, false, false))
        assertFalse(GlassCompatibility.canUse(34, true, true))
        assertTrue(GlassCompatibility.tier(AppStyle.LIQUID_GLASS, true, false, false,
            33, true, false, true) == dev.galaxy.toolbox.ui.GlassTier.REFRACTIVE)
        assertTrue(GlassCompatibility.tier(AppStyle.LIQUID_GLASS, true, false, false,
            31, true, false, false) == dev.galaxy.toolbox.ui.GlassTier.BLUR)
        assertTrue(GlassCompatibility.tier(AppStyle.LIQUID_GLASS, true, false, false,
            30, true, false, false) == dev.galaxy.toolbox.ui.GlassTier.STATIC)
        assertTrue(GlassCompatibility.tier(AppStyle.LIQUID_GLASS, true, true, false,
            33, true, false, true) == dev.galaxy.toolbox.ui.GlassTier.REDUCED)
        assertTrue(GlassCompatibility.tier(AppStyle.MIUIX, true, false, false,
            33, true, false, true) == dev.galaxy.toolbox.ui.GlassTier.REFRACTIVE)
        assertTrue(GlassCompatibility.tier(AppStyle.LIQUID_GLASS, false, false, false,
            33, true, false, true) == dev.galaxy.toolbox.ui.GlassTier.OFF)
    }

    @Test fun navigationAdaptsToCompactAndUnfoldedWidths() {
        var width by mutableIntStateOf(320)
        rule.setContent {
            GalaxyBoxTheme(style = AppStyle.LIQUID_GLASS) {
                val backdrops = rememberAppBackdrops()
                val pages = listOf("總覽", "硬體", "電池", "更新", "檢測")
                Box(Modifier.requiredWidth(width.dp).requiredHeight(700.dp)) {
                    AppScaffold(pages, 0, {}, backdrops.controls,
                        rememberNavigationMotion(0, pages.size), true,
                        rememberLazyListState(), {}, {}) {
                        Text("頁面內容")
                    }
                }
            }
        }
        rule.onNodeWithTag("bottom-navigation").assertExists()
        rule.runOnIdle { width = 673 }
        rule.onNodeWithTag("bottom-navigation").assertDoesNotExist()
        rule.onNodeWithText("頁面內容").assertExists()
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
