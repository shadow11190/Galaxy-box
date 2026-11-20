package dev.galaxy.toolbox.data

enum class ChipBasis(val sourceLabel: String) {
    HARDWARE("本機硬體回報"), HARDWARE_WITH_SKU("本機硬體回報＋完整型號規格"),
    MODEL_REFERENCE("完整型號規格對照"), DEVICE_REFERENCE("內部代號規格對照")
}

data class HardwareIdentity(val name: String?, val chip: ChipDescription?, val basis: ChipBasis,
    val exactVariants: List<DeviceVariants.Entry>, val market: String?, val nameSource: String) {
    val chipSource: String get() = basis.sourceLabel + "\n" + chip?.source.orEmpty()
}

/** Actual reports first; exact SKU next; only unambiguous, same-brand code references last. */
object HardwareIdentityResolver {
    fun resolve(names: DeviceNames, variants: DeviceVariants, manufacturer: String, model: String,
        device: String, brand: String = manufacturer, reports: List<ChipReport>): HardwareIdentity {
        val exact = variants.lookup(manufacturer, model, device, brand)
        val nameFromModel = names.resolve(manufacturer, model, device, brand)
        val isBlackShark = DeviceNames.normalizeBrand(brand) == "blackshark" ||
            DeviceNames.normalizeBrand(manufacturer) == "blackshark"
        val isHonor = DeviceNames.normalizeBrand(brand) == "honor" ||
            DeviceNames.normalizeBrand(manufacturer) == "honor"
        val specNames = exact.map {
            if (isBlackShark) it.name.replace('_', ' ')
                .replace(Regex("^Black\\s+Shark", RegexOption.IGNORE_CASE), "Black Shark") else it.name
        }.distinct()
        val nameFromSpec = specNames.singleOrNull()
        val preferSpecName = nameFromSpec != null && (nameFromModel == null ||
            DeviceNames.looksLikeModelCode(nameFromModel, model) || nameFromModel.equals(nameFromSpec, ignoreCase = true) || isBlackShark)
        val sharedName = if (isBlackShark && specNames.size > 1)
            specNames.joinToString(" / ") { it.removePrefix("Black Shark ") }
                .let { "Black Shark $it（共用型號）" }
            else if (isHonor && specNames.size > 1)
                specNames.joinToString(" / ") + "（共用型號）"
            else null
        val name = sharedName ?: if (preferSpecName) nameFromSpec else nameFromModel ?: names.resolveWithDeviceFallback(manufacturer, model, device, brand)
        val nameSource = if (preferSpecName || sharedName != null) exact.map { it.source }.distinct().joinToString("\n")
            else "Google Play 公開裝置目錄 · ${DeviceNames.RETRIEVED}\n${DeviceNames.SOURCE}"
        val codeNames = names.matchesByDevice(manufacturer, device, brand).map { DeviceNames.canonicalName(it) }.distinct()
        val codeReferences = if (codeNames.size == 1 && (name == null || codeNames.single() == name))
            variants.matchesByDevice(manufacturer, device, brand).filter { it.name == codeNames.single() } else emptyList()
        val reference = exact.ifEmpty { codeReferences }
        // A reused code or conflicting per-SKU specification never picks a chip arbitrarily.
        val referenceChip = reference.map { it.soc }.distinct().singleOrNull()
            ?.let { ChipNames.identify(listOf(ChipReport("公開規格", it)), name) }
            ?.takeIf { it.candidates.size == 1 && !it.conflict }
        val actual = ChipNames.identify(reports, name)
        var basis = ChipBasis.HARDWARE
        val chip = when {
            actual == null && referenceChip != null -> {
                basis = if (exact.isNotEmpty()) ChipBasis.MODEL_REFERENCE else ChipBasis.DEVICE_REFERENCE
                referenceChip.copy(source = (reference.map { it.source } + referenceChip.source).distinct().joinToString("\n"))
            }
            actual != null && referenceChip != null -> {
                val agrees = referenceChip.candidates.single() in actual.candidates
                if (agrees && actual.candidates.size > 1 && !actual.conflict && exact.isNotEmpty()) {
                    basis = ChipBasis.HARDWARE_WITH_SKU
                    ChipNames.identify(reports + ChipReport("完整型號 $model", reference.first().soc), name)!!
                        .let { it.copy(source = (reference.map { r -> r.source } + it.source).distinct().joinToString("\n")) }
                } else if (!agrees) actual.copy(conflict = true, evidence = actual.evidence + "\n本機回報與規格參考不同；保留本機回報。")
                else actual
            }
            else -> actual
        }
        // Codename lookup cannot identify a region, even if all known SKUs share one chip.
        val market = exact.map { it.market.substringBefore(" / ") }.filterNot { it.startsWith("完整型號") }.distinct().singleOrNull()
        return HardwareIdentity(name, chip, basis, exact, market, nameSource)
    }
}
