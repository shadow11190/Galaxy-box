package dev.galaxy.toolbox.data

import android.app.*
import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import dev.galaxy.toolbox.charging.*
import dev.galaxy.toolbox.model.ChargingPowerReading
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChargingNotificationTest {
    private fun reading() = ChargingPowerReading(2_000_000, 4000, 62, true, false, 1000, BatteryManager.BATTERY_STATUS_CHARGING)
    @Test fun notificationContainsOnlyStatusPercentAndNetPower() {
        val context = ApplicationProvider.getApplicationContext<Context>(); ChargingNotification.createChannel(context)
        val n = ChargingNotification.build(context, reading(), 1000)
        assertEquals("充電中 · 62%", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("電池淨功率 +8.0 W", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(n.extras.toString().contains("10⁹")); assertEquals("停止監控", n.actions.single().title)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
    @Test fun missingOrStaleValuesNeverBecomeZero() {
        assertEquals("電池淨功率 —", ChargingNotification.content(ChargingPowerReading(), 1000))
        assertEquals("充電狀態 · —", ChargingNotification.title(reading(), 16001))
        assertEquals("電池淨功率 —", ChargingNotification.content(reading(), 16001))
        assertEquals("電池淨功率 +0.0 W", ChargingNotification.content(reading().copy(currentMicroAmps = 0), 1000))
    }
    @Test fun pluggedDoesNotInventChargingAndFullIsExplicit() {
        assertEquals("暫停充電 · 62%", ChargingNotification.title(reading().copy(statusCode = BatteryManager.BATTERY_STATUS_NOT_CHARGING), 1000))
        assertEquals("已充滿 · 62%", ChargingNotification.title(reading().copy(statusCode = BatteryManager.BATTERY_STATUS_FULL), 1000))
        assertEquals("已接電源 · 62%", ChargingNotification.title(reading().copy(statusCode = null), 1000))
    }
    @Test fun lowImportanceChannelAndIdleIntervalsAvoidHighRateBackgroundPolling() {
        val c = ApplicationProvider.getApplicationContext<Context>(); ChargingNotification.createChannel(c)
        assertEquals(NotificationManager.IMPORTANCE_LOW, c.getSystemService(NotificationManager::class.java).getNotificationChannel(ChargingNotification.CHANNEL).importance)
        assertEquals(5000L, ChargingNotification.interval(true, true)); assertEquals(30000L, ChargingNotification.interval(true, false)); assertEquals(60000L, ChargingNotification.interval(false, true))
    }
    @Test fun serviceIsOptInAndStopActionDoesNotStartMonitoring() {
        val c = ApplicationProvider.getApplicationContext<Context>(); ChargingNotification.turnOffPreference(c)
        assertFalse(ChargingNotification.enabled(c))
        val controller = org.robolectric.Robolectric.buildService(ChargingStatusService::class.java).create()
        controller.get().onStartCommand(android.content.Intent().setAction(ChargingNotification.STOP), 0, 1)
        assertFalse(ChargingNotification.running.value); assertFalse(ChargingNotification.enabled(c)); controller.destroy()
    }
    @Test @Config(sdk = [26]) fun ordinaryNotificationWorksOnMinimumApiWithoutModernPromotedFlags() {
        val c = ApplicationProvider.getApplicationContext<Context>(); ChargingNotification.createChannel(c)
        val n = ChargingNotification.build(c, reading(), 1000)
        assertEquals(ChargingNotification.CHANNEL, n.channelId); assertNotNull(n.smallIcon)
        assertEquals(1, n.actions.size)
    }
}
