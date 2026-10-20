package dev.galaxy.toolbox

import android.graphics.Rect
import android.os.*
import android.view.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Collections

/** Real Choreographer/renderer; intentionally does NOT use Compose's virtual test clock. */
class FramePacingDeviceTest {
    @Test fun recordsRealWindowFramesDuringRepeatedNavigationGestures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val records = Collections.synchronizedList(mutableListOf<Triple<Long,Long,Long>>())
        val dropped = java.util.concurrent.atomic.AtomicInteger()
        val worker = HandlerThread("GalaxyBox-FrameMetrics").apply { start() }
        var window: Window? = null
        var reportedHz = 0f
        var requestedHz = 0f
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, lost ->
            dropped.addAndGet(lost)
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val deadline = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L
            val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            if (duration > 0 && records.size < 10_000) records.add(Triple(vsync,duration,deadline))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            SystemClock.sleep(1800) // test worker only; let the first static hardware snapshot settle
            scenario.onActivity { activity ->
                window = activity.window
                reportedHz = activity.window.decorView.display?.refreshRate ?: 0f
                requestedHz = activity.window.attributes.preferredRefreshRate
                activity.window.addOnFrameMetricsAvailableListener(listener,Handler(worker.looper))
            }
            fun tab(text: String): Rect {
                val root = instrumentation.uiAutomation.rootInActiveWindow ?: error("No accessibility window")
                val height = instrumentation.targetContext.resources.displayMetrics.heightPixels
                return root.findAccessibilityNodeInfosByText(text).map { node ->
                    Rect().also { node.getBoundsInScreen(it) }
                }.filter { it.centerY() > height * .65f && !it.isEmpty }.maxByOrNull { it.centerY() }
                    ?: error("Bottom navigation tab not found: $text")
            }
            val first = tab("總覽"); val last = tab("檢測")
            val y = first.exactCenterY()
            // FrameMetrics timestamps use CLOCK_MONOTONIC (suspend time excluded).
            val startNs = System.nanoTime()
            fun drag(from: Float, to: Float) {
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, x: Float) {
                    val event = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
                send(MotionEvent.ACTION_DOWN,from)
                val cadence = (1000f / reportedHz.coerceAtLeast(30f)).toLong().coerceAtLeast(2L)
                val steps = (320L / cadence).toInt().coerceAtLeast(12)
                for (i in 1..steps) {
                    SystemClock.sleep(cadence)
                    send(MotionEvent.ACTION_MOVE,from+(to-from)*i/steps)
                }
                send(MotionEvent.ACTION_UP,to)
                SystemClock.sleep(90) // interrupt the settling spring with the next drag
            }
            repeat(6) { drag(first.exactCenterX(),last.exactCenterX()); drag(last.exactCenterX(),first.exactCenterX()) }
            val endNs = System.nanoTime()
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            val frames = synchronized(records) { records.filter { it.first in startNs..endNs }.toList() }
            val durations = frames.map { it.second / 1_000_000.0 }.sorted()
            fun percentile(p: Double) = durations.takeIf { it.isNotEmpty() }?.let { it[((it.size-1)*p).toInt()] }
            val seconds = (endNs-startNs) / 1_000_000_000.0
            val result = JSONObject().apply {
                put("model",Build.MODEL); put("sdk",Build.VERSION.SDK_INT)
                put("reportedDisplayHz",reportedHz); put("requestedWindowHz",requestedHz)
                put("measurementSeconds",seconds); put("windowFrameCallbacks",frames.size)
                put("observedDrawCallbacksPerSecond",frames.size/seconds)
                put("p50FrameWorkMs",percentile(.5)); put("p95FrameWorkMs",percentile(.95))
                put("framesOverOwnDeadline",frames.count { it.third > 0 && it.second > it.third })
                put("droppedMetricReports",dropped.get())
                put("glassRequested",instrumentation.targetContext.getSharedPreferences("display",0).getBoolean("liquid_glass",false))
                put("note","Window draw callbacks include UI scheduling gaps and are not guaranteed presented frames. Use Perfetto FrameTimeline for actual presentation FPS and missed deadlines. No virtual Compose test clock is used.")
                put("frames",JSONArray(frames.map { JSONObject().put("intendedVsyncNs",it.first).put("frameWorkNs",it.second).put("deadlineNs",it.third) }))
            }
            File(instrumentation.targetContext.filesDir,"navigation-frame-metrics.json").writeText(result.toString(2))
            assertTrue("No rendered window frames captured; this is not an FPS result",frames.isNotEmpty())
        } finally {
            instrumentation.runOnMainSync { window?.removeOnFrameMetricsAvailableListener(listener) }
            worker.quitSafely(); worker.join(1000); scenario.close()
        }
    }
}
