package dev.galaxy.toolbox.data

import android.media.MediaDrm
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrmDetailsTest {
    @Test fun hdcpCodesAreNotTreatedAsPlaybackQuality() {
        assertEquals("2.2", DrmDetails.hdcpLabel(MediaDrm.HDCP_V2_2))
        assertEquals("未知", DrmDetails.hdcpLabel(MediaDrm.HDCP_LEVEL_UNKNOWN))
        assertEquals("原始代碼 987", DrmDetails.hdcpLabel(987))
    }

    @Test fun pluginTextMustBeShortAndPrintable() {
        assertEquals("L1", DrmDetails.cleanText(" L1 "))
        assertNull(DrmDetails.cleanText("\u0000L1"))
        assertNull(DrmDetails.cleanText(""))
    }
}
