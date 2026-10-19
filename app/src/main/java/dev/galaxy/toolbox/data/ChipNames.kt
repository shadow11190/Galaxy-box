package dev.galaxy.toolbox.data

import java.util.Locale

data class ChipDescription(val name: String, val vendor: String, val process: String, val cpu: String?, val source: String)

/** Small, cited chip-code table. Unknown codes remain unknown; phone names alone never select a chip. */
object ChipNames {
    private val chips = mapOf(
        "SM8450" to ChipDescription("Qualcomm Snapdragon 8 Gen 1", "Qualcomm", "4 nm", "Qualcomm Kryo",
            "https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/snapdragon-8-gen-1-mobile-platform-product-brief.pdf"),
        "SM8550" to ChipDescription("Qualcomm Snapdragon 8 Gen 2", "Qualcomm", "4 nm", "Qualcomm Kryo",
            "https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-gen-2-mobile-platform"),
        "SM8650" to ChipDescription("Qualcomm Snapdragon 8 Gen 3", "Qualcomm", "4 nm", "Qualcomm Kryo",
            "https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-gen-3-mobile-platform"),
        "SM8750" to ChipDescription("Qualcomm Snapdragon 8 Elite", "Qualcomm", "3 nm", "Qualcomm Oryon",
            "https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-elite-mobile-platform"),
        "SM8850" to ChipDescription("Qualcomm Snapdragon 8 Elite Gen 5", "Qualcomm", "3 nm", "Qualcomm Oryon（第 3 代）",
            "https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Snapdragon-8-Elite-Gen-5-product-brief.pdf"),
        "EXYNOS2200" to ChipDescription("Samsung Exynos 2200", "Samsung", "4 nm", null,
            "https://semiconductor.samsung.com/processor/mobile-processor/exynos-2200/"),
        "EXYNOS2400" to ChipDescription("Samsung Exynos 2400", "Samsung", "4 nm", null,
            "https://semiconductor.samsung.com/processor/mobile-processor/exynos-2400/"),
        "EXYNOS2400E" to ChipDescription("Samsung Exynos 2400e", "Samsung", "4 nm", null,
            "https://news.samsung.com/global/galaxy-s24-series-expands-with-s24-fe-a-premium-experience-that-makes-full-galaxy-ai-capabilities-attainable-for-more-users"),
        "EXYNOS2500" to ChipDescription("Samsung Exynos 2500", "Samsung", "3 nm", null,
            "https://semiconductor.samsung.com/processor/mobile-processor/exynos-2500/")
    )

    fun resolve(socModel: String?, hardware: String?, marketingName: String?): ChipDescription? {
        fun lookup(raw: String?): ChipDescription? {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT)?.replace(Regex("[ _]"), "") ?: return null
            // Part suffixes are explicit; SM8650Q and future chip numbers must not match SM8650.
            val code = Regex("(SM(?:8450|8550|8650|8750))(?:-(?:AB|AC))?").matchEntire(normalized)?.groupValues?.get(1)
                ?: Regex("SM8850(?:-AC|-1-AD|-5-AC|-1-AB)?").matchEntire(normalized)?.let { "SM8850" }
                ?: normalized.replace("EXYNOS-", "EXYNOS")
            return chips[code]
        }
        val chip = lookup(socModel) ?: lookup(hardware) ?: return null
        val galaxyModels = when (chip.name) {
            "Qualcomm Snapdragon 8 Gen 2" -> setOf("Galaxy S23", "Galaxy S23+", "Galaxy S23 Ultra")
            "Qualcomm Snapdragon 8 Gen 3" -> setOf("Galaxy S24", "Galaxy S24+", "Galaxy S24 Ultra")
            "Qualcomm Snapdragon 8 Elite" -> setOf("Galaxy S25", "Galaxy S25+", "Galaxy S25 Ultra")
            "Qualcomm Snapdragon 8 Elite Gen 5" -> setOf("Galaxy S26 Ultra")
            else -> emptySet()
        }
        return if (marketingName in galaxyModels) chip.copy(name = "${chip.name} for Galaxy", source = chip.source +
            if (marketingName == "Galaxy S26 Ultra") "\nhttps://www.samsung.com/us/smartphones/galaxy-s26-ultra/" else "") else chip
    }
}
