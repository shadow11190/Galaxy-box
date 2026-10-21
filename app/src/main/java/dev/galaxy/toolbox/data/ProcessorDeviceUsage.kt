package dev.galaxy.toolbox.data

/** Years refer to verified phone or tablet releases, not chipset announcements. */
class ProcessorDeviceUsage private constructor(private val entries: Map<String, Entry>) {
    data class Entry(val firstDeviceYear: Int, val lastDeviceYear: Int, val source: String)

    fun lookup(name: String): Entry? = entries[name]

    companion object {
        fun parse(lines: Sequence<String>): ProcessorDeviceUsage = ProcessorDeviceUsage(
            lines.filterNot { it.startsWith('#') }.map { it.split('\t') }.mapNotNull { fields ->
                if (fields.size != 4 || fields.any(String::isBlank)) return@mapNotNull null
                val first = fields[1].toIntOrNull() ?: return@mapNotNull null
                val last = fields[2].toIntOrNull() ?: return@mapNotNull null
                if (first !in 2018..2026 || last !in first..2026) return@mapNotNull null
                fields[0] to Entry(first, last, fields[3])
            }.toMap()
        )
    }
}
