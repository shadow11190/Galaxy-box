package dev.galaxy.toolbox.data

/** Published chipset GPU specifications, kept separate from the device's live GL renderer. */
class GpuSpecs private constructor(private val byChip: Map<String, Entry>) {
    data class Entry(val chip: String, val gpu: String, val source: String)

    fun forChip(chip: ChipDescription?): Entry? = chip?.candidates?.singleOrNull()?.let(byChip::get)

    companion object {
        fun parse(lines: Sequence<String>): GpuSpecs {
            val rows = lines.drop(1).filter { it.isNotBlank() && !it.startsWith('#') }
                .mapNotNull { line ->
                    line.split('\t').takeIf { it.size == 3 && it.all(String::isNotBlank) }
                        ?.let { Entry(it[0], it[1], it[2]) }
                }.toList()
            require(rows.map { it.chip }.distinct().size == rows.size) { "Duplicate GPU chipset entry" }
            return GpuSpecs(rows.associateBy { it.chip })
        }
    }
}
