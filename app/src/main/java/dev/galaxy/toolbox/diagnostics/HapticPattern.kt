package dev.galaxy.toolbox.diagnostics

import kotlin.math.roundToInt

internal data class HapticWaveform(val timings: LongArray, val amplitudes: IntArray)

internal object HapticPattern {
    fun amplitude(percent: Int): Int = (255 * percent.coerceIn(1, 100) / 100f)
        .roundToInt().coerceIn(1, 255)

    /** A finite software envelope, not a motor stiffness or braking adjustment. */
    fun elastic(intensityPercent: Int, dampingPercent: Int, intervalMs: Int): HapticWaveform {
        val intensity = amplitude(intensityPercent)
        val decay = 1f - dampingPercent.coerceIn(0, 90) / 100f
        val interval = intervalMs.coerceIn(30, 140)
        val on = (interval / 2).toLong()
        val off = (interval - interval / 2).toLong()
        val timings = LongArray(21)
        val amplitudes = IntArray(21)
        timings[0] = 0
        for (pulse in 0 until 10) {
            val index = pulse * 2 + 1
            timings[index] = on
            timings[index + 1] = off
            amplitudes[index] = (intensity * Math.pow(decay.toDouble(), pulse.toDouble()))
                .roundToInt().coerceAtLeast(1)
        }
        return HapticWaveform(timings, amplitudes)
    }
}
