package dev.galaxy.toolbox.data

import android.content.Intent
import dev.galaxy.toolbox.diagnostics.DeviceTest
import dev.galaxy.toolbox.diagnostics.SamsungServiceCode
import dev.galaxy.toolbox.diagnostics.SamsungServiceCodes
import dev.galaxy.toolbox.diagnostics.ServiceCodeAccess
import dev.galaxy.toolbox.diagnostics.ServiceCodeCategory
import dev.galaxy.toolbox.diagnostics.TestCapabilities
import dev.galaxy.toolbox.diagnostics.TestCatalogue
import dev.galaxy.toolbox.ui.pressureStats
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SamsungDiagnosticsShortcutsTest {
    @Test fun sourceCatalogueIsCompleteAndUnique() {
        val expected = setOf(
            "*#0*#", "*#2664#", "*#2663#", "*#0842#", "*#0589#", "*#0289#", "*#0673#",
            "*#06#", "*#1234#", "*#12580*369#", "*#2222#", "*#1111#",
            "*#0011#", "*#2263#", "*#7465625#", "*#9090#",
            "*#0228#", "*#9998*228#", "*#9900#", "*#0808#", "*#7284#",
            "*#7780#", "*#7594#", "*2767*3855#", "*2767*2878#", "*#32489#"
        )
        assertEquals(26, SamsungServiceCodes.entries.size)
        assertEquals(expected, SamsungServiceCodes.entries.map { it.code }.toSet())
    }

    @Test fun onlyReviewedReadOnlyCodesOpenTheDialer() {
        SamsungServiceCodes.entries.filter { it.access == ServiceCodeAccess.DIAL }.forEach { entry ->
            val intent = SamsungServiceCodes.dialIntent(entry)
            assertEquals(Intent.ACTION_DIAL, intent.action)
            assertEquals("tel", intent.data?.scheme)
            assertEquals(entry.code, intent.data?.schemeSpecificPart)
        }
        SamsungServiceCodes.entries.filter { it.access != ServiceCodeAccess.DIAL }.forEach { entry ->
            assertThrows(IllegalArgumentException::class.java) { SamsungServiceCodes.dialIntent(entry) }
        }
        assertEquals(ServiceCodeAccess.RESET, SamsungServiceCodes.entries.first { it.code == "*2767*3855#" }.access)
        assertEquals(ServiceCodeAccess.COPY_ONLY, SamsungServiceCodes.entries.first { it.code == "*#2263#" }.access)
        assertThrows(IllegalArgumentException::class.java) {
            SamsungServiceCodes.dialIntent(SamsungServiceCode("未知", "*#0*#", "", ServiceCodeCategory.HARDWARE))
        }
    }

    @Test fun pressureCheckAppearsOnlyWithAReportedSensor() {
        assertFalse(TestCatalogue.available(TestCapabilities()).contains(DeviceTest.PRESSURE))
        assertTrue(TestCatalogue.available(TestCapabilities(pressure = true)).contains(DeviceTest.PRESSURE))
    }

    @Test fun pressureSummaryKeepsRawChangesWithoutWaterproofThresholds() {
        val stats = pressureStats(listOf(Float.NaN, 1001f, 999.5f, 1002f), 1001f)!!
        assertEquals(1f, stats.change, 0.001f)
        assertEquals(2.5f, stats.range, 0.001f)
        assertNull(pressureStats(listOf(Float.NaN), null))
        assertEquals(0f, pressureStats(listOf(0f), null)!!.current, 0.001f)
    }
}
