package dev.galaxy.toolbox.data

import android.bluetooth.BluetoothCodecConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class A2dpCodecAccessTest {
    @Test
    fun selectableCapabilityExposesOnlyAdvertisedAudioParameters() {
        val capability = BluetoothCodecConfig.Builder()
            .setCodecType(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC)
            .setSampleRate(
                BluetoothCodecConfig.SAMPLE_RATE_44100 or BluetoothCodecConfig.SAMPLE_RATE_48000,
            )
            .setBitsPerSample(BluetoothCodecConfig.BITS_PER_SAMPLE_16)
            .setChannelMode(BluetoothCodecConfig.CHANNEL_MODE_STEREO)
            .build()

        assertEquals(
            listOf(
                BluetoothCodecConfig.SAMPLE_RATE_44100 to "44.1 kHz",
                BluetoothCodecConfig.SAMPLE_RATE_48000 to "48 kHz",
            ),
            A2dpCodecAccess.sampleRateOptions(capability),
        )
        assertEquals(
            listOf(BluetoothCodecConfig.BITS_PER_SAMPLE_16 to "16 bit"),
            A2dpCodecAccess.bitDepthOptions(capability),
        )
        assertEquals(
            listOf(BluetoothCodecConfig.CHANNEL_MODE_STEREO to "立體聲"),
            A2dpCodecAccess.channelModeOptions(capability),
        )
    }

    @Test
    fun explicitPreferenceMustBeOneSupportedFlagAndNoneRemainsWildcard() {
        val flags = listOf(1 to "first", 2 to "second", 4 to "third")
        A2dpCodecAccess.validateExactChoice(0, 3, flags, "測試欄位")
        A2dpCodecAccess.validateExactChoice(1, 3, flags, "測試欄位")
        A2dpCodecAccess.validateExactChoice(2, 3, flags, "測試欄位")
        assertThrows(IllegalArgumentException::class.java) {
            A2dpCodecAccess.validateExactChoice(4, 3, flags, "測試欄位")
        }
        assertThrows(IllegalArgumentException::class.java) {
            A2dpCodecAccess.validateExactChoice(3, 3, flags, "測試欄位")
        }
    }
}
