package dev.galaxy.toolbox.data


/** Exact KGSL/devfreq interfaces only. Unknown vendor files are never assigned guessed units. */
object GpuMetrics {
    internal fun frequencyHz(raw: String?): Long? = raw?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
    internal fun busyFraction(raw: String?): Double? {
        val tokens = raw?.trim()?.split(Regex("\\s+")) ?: return null
        if (tokens.size != 2) return null
        val busy = tokens[0].toLongOrNull() ?: return null
        val total = tokens[1].toLongOrNull() ?: return null
        return if (total > 0 && busy in 0..total) busy.toDouble() / total else null
    }
}
