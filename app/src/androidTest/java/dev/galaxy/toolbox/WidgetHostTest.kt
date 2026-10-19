package dev.galaxy.toolbox

import android.appwidget.AppWidgetManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.galaxy.toolbox.model.*
import dev.galaxy.toolbox.widgets.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetHostTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Test fun unknownValuesAndTransparentLayerAreSafeInRealRemoteViewsHost() {
        instrumentation.runOnMainSync {
            val views = WidgetRenderer.create(context, WidgetKind.BATTERY, 77, WidgetPresentation.from(Snapshot()),
                settings = WidgetSettings.defaults(WidgetKind.BATTERY).copy(background = WidgetBackground.TRANSPARENT))
            val view = views.apply(context, FrameLayout(context))
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_background_layer).visibility)
            assertNull(view.background)
            val metrics = view.findViewById<android.view.ViewGroup>(R.id.widget_metrics)
            val cell = (metrics.getChildAt(0) as android.view.ViewGroup).getChildAt(0)
            assertEquals("—", cell.findViewById<TextView>(R.id.widget_metric_value).text.toString())
            assertEquals(View.GONE, cell.findViewById<View>(R.id.widget_metric_progress).visibility)
        }
    }
    @Test fun resourceWidgetPreservesAllFieldsWhenResized() {
        instrumentation.runOnMainSync {
            val data = WidgetPresentation.from(Snapshot(memory = ByteUsage.of(50, 100)))
            for (width in listOf(200, 320)) {
                val view = WidgetRenderer.create(context, WidgetKind.RESOURCES, 78, data, width).apply(context, FrameLayout(context))
                val rows = view.findViewById<android.view.ViewGroup>(R.id.widget_metrics)
                val cells = (0 until rows.childCount).sumOf { (rows.getChildAt(it) as android.view.ViewGroup).childCount }
                assertEquals(3, cells)
            }
        }
    }
    @Test fun androidRecognizesAllThreeWidgetProviders() {
        val providers = AppWidgetManager.getInstance(context).installedProviders.associateBy { it.provider }
        WidgetKind.entries.forEach { kind ->
            val info = providers[WidgetUpdates.component(context, kind)]
            assertNotNull("Missing widget: $kind", info)
            assertEquals(1800000, info!!.updatePeriodMillis)
        }
    }
}
