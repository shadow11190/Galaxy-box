package dev.galaxy.toolbox.diagnostics

import kotlin.math.*

object AudioSignal {
    fun tone(channel: Int, rate: Int = 44100): ShortArray {
        require(channel in 0..2 && rate > 0)
        val frames = rate / 2
        return ShortArray(frames * 2).also { output ->
            repeat(frames) { frame ->
                val edge = minOf(frame / (rate * .01), (frames - frame - 1) / (rate * .01), 1.0).coerceAtLeast(0.0)
                val sample = (sin(2.0 * PI * 660 * frame / rate) * Short.MAX_VALUE * .12 * edge).toInt().toShort()
                output[frame * 2] = if (channel == 1) 0 else sample
                output[frame * 2 + 1] = if (channel == 0) 0 else sample
            }
        }
    }
    fun dbfs(samples: ShortArray, count: Int): Double? {
        if (count !in 1..samples.size) return null
        val rms = sqrt((0 until count).sumOf { val n = samples[it] / 32768.0; n * n } / count)
        return if (rms == 0.0) Double.NEGATIVE_INFINITY else 20 * log10(rms)
    }
}
