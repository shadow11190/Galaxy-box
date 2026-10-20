package dev.galaxy.toolbox.data

import java.util.Locale

data class ChipDescription(val name: String, val vendor: String, val process: String?, val cpu: String?, val source: String,
    val candidates: List<String> = listOf(name), val evidence: String = "", val conflict: Boolean = false)
data class ChipReport(val source: String, val value: String)

/** Exact chip identifiers. Shared numbers retain candidates; model names never choose a chip. */
object ChipNames {
    private data class Entry(val name: String, val vendor: String, val process: String?, val cpu: String?,
        val aliases: List<String>, val sources: List<String>)
    private val entries = ProcessorData.rows.map { row ->
        val p = row.split('\t')
        Entry(p[0], p[1], p[2].ifBlank { null }, p[3].ifBlank { null }, p[4].split('|'),
            p[5].split('|').mapNotNull { ProcessorData.sources[it] })
    }
    private fun normalized(value: String) = value.trim().uppercase(Locale.ROOT).replace(Regex("[\\s_]"), "")
    private val index = entries.flatMap { entry -> entry.aliases.map { normalized(it) to entry } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.distinctBy { it.name } }
    private fun lookup(value: String): List<Entry> {
        val key = normalized(value)
        index[key]?.let { return it }
        // Only explicit vendor wrappers; arbitrary suffixes are never stripped.
        val unwrapped = Regex("(?:QTI|QUALCOMM(?: TECHNOLOGIES,? INC\\.?)?|MEDIATEK|SAMSUNG)[ ,:]+(.+)", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim())?.groupValues?.get(1) ?: return emptyList()
        return index[normalized(unwrapped)].orEmpty()
    }
    fun identify(reports: List<ChipReport>, marketingName: String? = null): ChipDescription? {
        val known = reports.map { it to lookup(it.value) }.filter { it.second.isNotEmpty() }
        if (known.isEmpty()) return null
        var candidates = known.first().second
        var conflict = false
        for ((_, other) in known.drop(1)) {
            val shared = candidates.filter { c -> other.any { it.name == c.name } }
            if (shared.isNotEmpty()) candidates = shared else conflict = true
        }
        val names = candidates.map { it.name }.sorted()
        var title = if (names.size == 1) names.single() else names.joinToString(" / ") + "（版本待確認）"
        // Commercial branding, not a measured clock/overclock claim.
        val galaxy = when (names.singleOrNull()) {
            "Qualcomm Snapdragon 8 Gen 2" -> setOf("Galaxy S23", "Galaxy S23+", "Galaxy S23 Ultra")
            "Qualcomm Snapdragon 8 Gen 3" -> setOf("Galaxy S24", "Galaxy S24+", "Galaxy S24 Ultra")
            "Qualcomm Snapdragon 8 Elite" -> setOf("Galaxy S25", "Galaxy S25+", "Galaxy S25 Ultra")
            "Qualcomm Snapdragon 8 Elite Gen 5" -> setOf("Galaxy S26", "Galaxy S26+", "Galaxy S26 Ultra")
            else -> emptySet()
        }
        if (marketingName in galaxy) title += " for Galaxy"
        val evidence = known.joinToString("\n") { (report, _) -> "${report.source} = ${report.value}" } +
            if (conflict) "\n來源代號有衝突；保留優先來源，未以裝置名稱覆蓋。" else if (names.size > 1) "\n共用代號，無法確認細分版本。" else ""
        return ChipDescription(title, candidates.map { it.vendor }.distinct().joinToString(" / "),
            candidates.map { it.process }.distinct().singleOrNull(), candidates.map { it.cpu }.distinct().singleOrNull(),
            candidates.flatMap { it.sources }.distinct().joinToString("\n"), names, evidence, conflict)
    }
    fun resolve(socModel: String?, hardware: String?, marketingName: String?): ChipDescription? = identify(
        listOfNotNull(socModel?.let { ChipReport("Build.SOC_MODEL", it) }, hardware?.let { ChipReport("Build.HARDWARE", it) }), marketingName)
    fun search(query: String): List<ChipDescription> {
        val q = normalized(query)
        if (q.length < 2) return emptyList()
        return entries.filter { normalized(it.name).contains(q) || it.aliases.any { a -> normalized(a).contains(q) } }
            .take(60).map { ChipDescription(it.name, it.vendor, it.process, it.cpu, it.sources.joinToString("\n"), evidence = it.aliases.joinToString(" · ")) }
    }
    val count: Int get() = entries.size
}
