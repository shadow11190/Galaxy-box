package dev.galaxy.toolbox.data

import android.media.AudioFormat
import dev.galaxy.toolbox.diagnostics.HapticPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioAndHapticsTest {
    @Test fun elasticEnvelopeIsFiniteAndDecays() {
        val waveform = HapticPattern.elastic(100, 25, 70)
        assertEquals(21, waveform.timings.size)
        assertEquals(21, waveform.amplitudes.size)
        assertEquals(255, waveform.amplitudes[1])
        assertTrue(waveform.amplitudes[19] < waveform.amplitudes[1])
        assertTrue(waveform.amplitudes.indices.filter { it % 2 == 0 }.all { waveform.amplitudes[it] == 0 })
        assertEquals(700L, waveform.timings.sum())
        assertEquals(255, HapticPattern.amplitude(100))
        assertTrue(HapticPattern.amplitude(1) > 0)
    }

    @Test fun dsdLabelsRequireAnAdvertisedDsdRate() {
        assertEquals("DSD64 (2822400 Hz)", LiveAudioInspector.dsdRateLabel(2_822_400))
        assertEquals("176400 Hz", LiveAudioInspector.dsdRateLabel(176_400))
        assertEquals("PCM 16-bit", LiveAudioInspector.encoding(AudioFormat.ENCODING_PCM_16BIT))
        val pcm = AudioFormat.Builder().setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        assertEquals("256 kbit/s", LiveAudioInspector.pcmDataRate(pcm))
    }
}
