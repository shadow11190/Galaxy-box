package dev.galaxy.toolbox.data

import java.util.Locale

/** Full SKU lookup. Same marketing names in different markets remain separate records. */
class DeviceNames private constructor(val entries: List<Entry>) {
    data class Entry(val brand: String, val model: String, val device: String, val name: String)
    private val byModel = entries.groupBy { normalizeModel(it.model) }
    private val byDevice = entries.groupBy { it.device.trim().lowercase(Locale.ROOT) }
    fun matchesByDevice(manufacturer: String, device: String, brand: String = manufacturer): List<Entry> {
        val brands = setOf(normalizeBrand(manufacturer), normalizeBrand(brand))
        if (device.isBlank() || device.equals("unknown", true)) return emptyList()
        return byDevice[device.trim().lowercase(Locale.ROOT)].orEmpty().filter { normalizeBrand(it.brand) in brands }
    }
    fun matches(manufacturer: String, model: String, device: String, brand: String = manufacturer): List<Entry> {
        val brands = setOf(normalizeBrand(manufacturer), normalizeBrand(brand))
        val matched = byModel[normalizeModel(model)].orEmpty().filter { normalizeBrand(it.brand) in brands }
        val exactDevice = matched.filter { it.device.equals(device.trim(), true) }
        return exactDevice.ifEmpty { matched }
    }
    fun resolve(manufacturer: String, model: String, device: String, brand: String = manufacturer): String? =
        matches(manufacturer, model, device, brand).map { it.name }.distinct().singleOrNull()
    fun resolveWithDeviceFallback(manufacturer: String, model: String, device: String, brand: String = manufacturer): String? {
        val exact = matches(manufacturer, model, device, brand)
        return exact.ifEmpty { matchesByDevice(manufacturer, device, brand) }.map { it.name }.distinct().singleOrNull()
    }
    fun search(query: String): List<Entry> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return entries.asSequence().filter { it.model.contains(q, true) || it.device.contains(q, true) || it.name.contains(q, true) }
            .distinct().take(100).toList()
    }
    companion object {
        const val SOURCE = "https://storage.googleapis.com/play_public/supported_devices.csv"
        const val RETRIEVED = "2026-10-20"
        fun parse(lines: Sequence<String>): DeviceNames = DeviceNames(lines.filterNot { it.startsWith('#') }
            .map { it.split('\t') }.mapNotNull {
                when {
                    it.size == 3 && it.all(String::isNotBlank) -> Entry("Samsung", it[0], it[1], it[2])
                    it.size == 4 && it.all(String::isNotBlank) -> Entry(it[0], it[1], it[2], it[3])
                    else -> null
                }
            }.toList())
        internal fun normalizeModel(model: String) = model.trim().uppercase(Locale.ROOT).replace(Regex("/(DS|DSN)$"), "")
        internal fun normalizeBrand(brand: String): String = when (val b = brand.trim().lowercase(Locale.ROOT)) {
            "asustek", "asustek computer inc.", "asus" -> "asus"
            "lge", "lg electronics", "lg" -> "lg"
            "hmd global", "hmd global oy", "nokia" -> "nokia"
            "motorola mobility", "motorola" -> "motorola"
            else -> b
        }
    }
}

class DeviceVariants private constructor(val entries: List<Entry>) {
    data class Entry(val brand: String, val model: String, val device: String, val name: String,
        val soc: String, val market: String, val source: String)
    private val byModel = entries.groupBy { DeviceNames.normalizeModel(it.model) }
    private val byDevice = entries.groupBy { it.device.trim().lowercase(Locale.ROOT) }
    fun matchesByDevice(manufacturer: String, device: String, brand: String = manufacturer): List<Entry> {
        val brands = setOf(DeviceNames.normalizeBrand(manufacturer), DeviceNames.normalizeBrand(brand))
        if (device.isBlank() || device.equals("unknown", true)) return emptyList()
        return byDevice[device.trim().lowercase(Locale.ROOT)].orEmpty().filter { DeviceNames.normalizeBrand(it.brand) in brands }
    }
    fun lookup(manufacturer: String, model: String, device: String, brand: String = manufacturer): List<Entry> {
        val brands = setOf(DeviceNames.normalizeBrand(manufacturer), DeviceNames.normalizeBrand(brand))
        val matching = byModel[DeviceNames.normalizeModel(model)].orEmpty().filter { DeviceNames.normalizeBrand(it.brand) in brands }
        return matching.filter { it.device.equals(device.trim(), true) }.ifEmpty { matching }
    }
    companion object {
        fun parse(lines: Sequence<String>) = DeviceVariants(lines.filterNot { it.startsWith('#') }
            .map { it.split('\t') }.filter { it.size == 7 && it.all(String::isNotBlank) }
            .map { Entry(it[0], it[1], it[2], it[3], it[4], it[5], it[6]) }.toList())
    }
}
