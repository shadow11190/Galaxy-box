package dev.galaxy.toolbox.data

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.*
import dev.galaxy.toolbox.R
import dev.galaxy.toolbox.model.*
import dev.galaxy.toolbox.widgets.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderingTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val data = WidgetPresentation.from(Snapshot(batteryPercent = 0, batteryTemperature = 0f,
        memory = ByteUsage.of(40, 100), storage = ByteUsage.of(65, 100), sampledAtMillis = 1792380000000L), model = "Galaxy box · 渲染測試")

    @Test fun remoteViewsAcceptsOnlySupportedViewsAndShowsEverySelectedField() {
        val view = WidgetRenderer.create(context, WidgetKind.OVERVIEW, 71, data, 320, 280, preview = true).apply(context, FrameLayout(context))
        val texts = descendants(view).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.containsAll(listOf("電量", "電池溫度", "記憶體", "儲存空間", "0%", "0.0 °C")))
        assertTrue(view.findViewById<TextClock>(R.id.widget_clock).visibility == View.VISIBLE)
    }
    @Test fun unknownDataHasDashAndNoProgressImage() {
        val view = WidgetRenderer.create(context, WidgetKind.BATTERY, 72, WidgetPresentation.from(Snapshot()), preview = true).apply(context, FrameLayout(context))
        val values = descendants(view).filterIsInstance<TextView>().filter { it.id == R.id.widget_metric_value }
        assertEquals(listOf("—", "—"), values.map { it.text.toString() })
        assertTrue(descendants(view).filter { it.id == R.id.widget_metric_progress }.all { it.visibility == View.GONE })
    }
    @Test fun switchingFromGlassToTransparentRemovesAllBackgroundsAndDecorations() {
        val view = WidgetRenderer.create(context, WidgetKind.OVERVIEW, 73, data, preview = true).apply(context, FrameLayout(context))
        assertNotNull(view.findViewById<ImageView>(R.id.widget_background_layer).drawable)
        val transparent = WidgetSettings(background = WidgetBackground.TRANSPARENT, opacity = 100, click = WidgetClick.NONE)
        WidgetRenderer.create(context, WidgetKind.OVERVIEW, 73, data, settings = transparent, preview = true).reapply(context, view)
        assertNull(view.findViewById<ImageView>(R.id.widget_background_layer).drawable)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_background_layer).visibility)
        descendants(view).forEach { assertNull("Background at ${it.id}", it.background); assertEquals(0f, it.elevation) }
        assertNull(WidgetBackgroundPainter.create(transparent, 320, 280))
    }
    @Test fun eachWidgetHasIndependentSettingsAndDeletionIsIsolated() {
        val store = WidgetSettingsStore(context)
        store.save(901, WidgetSettings(background = WidgetBackground.TRANSPARENT, click = WidgetClick.NONE))
        store.save(902, WidgetSettings(background = WidgetBackground.SOLID, showClock = false))
        assertEquals(WidgetBackground.TRANSPARENT, store.load(901, WidgetKind.OVERVIEW).background)
        assertEquals(WidgetBackground.SOLID, store.load(902, WidgetKind.BATTERY).background)
        store.delete(901)
        assertEquals(WidgetSettings.defaults(WidgetKind.OVERVIEW), store.load(901, WidgetKind.OVERVIEW))
        assertFalse(store.load(902, WidgetKind.BATTERY).showClock)
        store.delete(902)
    }
    @Test fun backgroundBitmapHasTransparentCornersAndNoPaintForZeroOpacity() {
        val glass = WidgetBackgroundPainter.create(WidgetSettings(), 320, 280)!!
        assertEquals(0, android.graphics.Color.alpha(glass.getPixel(0, 0)))
        assertTrue(android.graphics.Color.alpha(glass.getPixel(160, 140)) > 0)
        assertNull(WidgetBackgroundPainter.create(WidgetSettings(opacity = 0), 320, 280))
    }
    @Test fun lightDarkResizeAndFontScaleRenderMatrix() {
        for (mode in WidgetBackground.entries) for (dark in listOf(false, true)) for (font in listOf(1f, 1.6f)) {
            val config = Configuration(context.resources.configuration).apply {
                fontScale = font
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(config)
            val settings = WidgetSettings(background = mode, backgroundColor = if (dark) 0xFF162237.toInt() else 0xFFF8FBFD.toInt(), textColor = if (dark) 0xFFEDF3FF.toInt() else 0xFF142537.toInt(),
                accentColor = if (dark) 0xFF76E1C3.toInt() else 0xFF096B56.toInt())
            for (width in listOf(200, 320)) {
                val height = if (font > 1f || width < 260) 420 else 280
                val view = WidgetRenderer.create(themed, WidgetKind.OVERVIEW, 74, data, width, height, settings, preview = true).apply(themed, FrameLayout(themed))
                val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
                activity.get().setContentView(view)
                assertTrue(view.findViewById<TextClock>(R.id.widget_clock).text.isNotBlank())
                val density = themed.resources.displayMetrics.density
                val w = (width * density).toInt(); val h = (height * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                descendants(view).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE && it.id == R.id.widget_metric_value }.forEach {
                    assertTrue("Value clipped at $mode/$width/$font", it.height >= it.layout.height)
                }
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/reports/visual-checks/widget-${mode.name}-${if (dark) "dark" else "light"}-$width-$font.png")
                file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (mode == WidgetBackground.TRANSPARENT) assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(w / 2, 0)))
                bitmap.recycle()
                activity.pause().stop().destroy()
            }
        }
    }
    @Test fun glassMaterialIsTranslucentAndWholeLayerRespectsOpacity() {
        val glass = WidgetBackgroundPainter.create(WidgetSettings(opacity = 40), 320, 280)!!
        val solid = WidgetBackgroundPainter.create(WidgetSettings(background = WidgetBackground.SOLID, opacity = 40), 320, 280)!!
        assertTrue(android.graphics.Color.alpha(glass.getPixel(160, 140)) < android.graphics.Color.alpha(solid.getPixel(160, 140)))
        val pixels = IntArray(glass.width * glass.height)
        glass.getPixels(pixels, 0, glass.width, 0, 0, glass.width, glass.height)
        assertTrue(pixels.all { android.graphics.Color.alpha(it) <= 102 })
        val large = WidgetBackgroundPainter.create(WidgetSettings(), 1500, 900)!!
        assertTrue(large.width <= 320 && large.height <= 320)
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
