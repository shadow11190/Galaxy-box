package dev.galaxy.toolbox.data

import android.app.Activity
import android.content.Intent
import android.os.PowerManager
import dev.galaxy.toolbox.performance.HighRefreshController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HighRefreshControllerTest {
    @Test fun foregroundRequestAndStopRestoreTheOriginalWindowPreference() {
        val scenario = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = scenario.get()
        activity.window.attributes = activity.window.attributes.apply { preferredRefreshRate = 77f }
        val controller = HighRefreshController(activity)
        controller.start()
        assertNotNull(controller.state.value.requestedHz)
        assertEquals(controller.state.value.requestedHz!!,activity.window.attributes.preferredRefreshRate,.001f)
        controller.stop(); controller.stop()
        assertNull(controller.state.value.requestedHz)
        assertEquals(77f,activity.window.attributes.preferredRefreshRate,.001f)
        activity.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(77f,activity.window.attributes.preferredRefreshRate,.001f)
        scenario.pause().stop().destroy()
    }
    @Test @Config(sdk = [26]) fun legacyDevicesUseOnlyTheCurrentResolutionAndCanReleaseTheMode() {
        val scenario = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = scenario.get()
        val original = activity.window.attributes.preferredDisplayModeId
        val controller = HighRefreshController(activity)
        controller.start()
        assertNotNull(controller.state.value.requestedHz)
        controller.stop()
        assertEquals(original,activity.window.attributes.preferredDisplayModeId)
        scenario.pause().stop().destroy()
    }
}
