package dev.galaxy.toolbox.data

import android.media.AudioDeviceInfo as A
import org.junit.Assert.*
import org.junit.Test

class AudioInventoryTest {
    @Test fun speakerSafeRouteAndEarpieceAreNotAddedAsPhysicalSpeakerDrivers() {
        val p = listOf(AudioPort(1,A.TYPE_BUILTIN_SPEAKER,false,true), AudioPort(1,A.TYPE_BUILTIN_SPEAKER,false,true),
            AudioPort(2,A.TYPE_BUILTIN_EARPIECE,false,true), AudioPort(3,A.TYPE_BUILTIN_SPEAKER_SAFE,false,true), AudioPort(4,A.TYPE_BLUETOOTH_A2DP,false,true))
        val rows = AudioInventory.summary(p, null, "unknown")
        assertEquals("1", rows.first { it.label == "內建喇叭端點" }.value)
        assertEquals("4", rows.first { it.label == "全部輸出端點" }.value)
        assertFalse(rows.any { it.label.contains("實體喇叭") })
    }
    @Test fun microphoneCharacteristicIdsAreDeduplicatedAndExternalMicsExcluded() {
        val m = listOf(AudioMic("bottom",A.TYPE_BUILTIN_MIC,0,0), AudioMic("bottom",A.TYPE_BUILTIN_MIC,0,0),
            AudioMic("back",A.TYPE_BUILTIN_MIC,0,1),AudioMic("USB",A.TYPE_USB_HEADSET,1,0))
        assertEquals("2", AudioInventory.summary(emptyList(),m,"").first { it.label == "內建麥克風（系統列出）" }.value)
    }
    @Test fun failedOrEmptyMicEnumerationIsUnknownRatherThanInventedZero() {
        for (m in listOf(null,emptyList(),listOf(AudioMic("",A.TYPE_BUILTIN_MIC,-1,-1))))
            assertNull(AudioInventory.summary(null,m,"權限不足").first { it.label == "內建麥克風（系統列出）" }.value)
        assertEquals("0",AudioInventory.summary(emptyList(),null,"").first { it.label == "全部輸入端點" }.value)
        assertTrue(AudioInventory.summary(null,null,"權限不足").first { it.label == "內建麥克風（系統列出）" }.detail.contains("權限不足"))
    }
}
