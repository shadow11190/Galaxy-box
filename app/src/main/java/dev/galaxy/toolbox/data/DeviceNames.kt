package dev.galaxy.toolbox.data

import java.util.Locale

/** Public marketing names, never inferred from a model prefix or a device codename. */
class DeviceNames private constructor(private val entries: List<Entry>) {
    private data class Entry(val model: String, val device: String, val name: String)

    fun resolve(manufacturer: String, model: String, device: String): String? {
        if (!manufacturer.equals("samsung", ignoreCase = true)) return null
        val matching = entries.filter { it.model == normalizeModel(model) }
        val names = matching.map { it.name }.distinct()
        if (names.size == 1) return names.single()
        return matching.filter { it.device.equals(device.trim(), true) }.map { it.name }.distinct().singleOrNull()
    }

    companion object {
        const val SOURCE = "https://storage.googleapis.com/play_public/supported_devices.csv"
        const val RETRIEVED = "2026-10-19"
        fun parse(lines: Sequence<String>): DeviceNames = DeviceNames(lines.filterNot { it.startsWith('#') }
            .map { it.split('\t') }.filter { it.size == 3 && it.all(String::isNotBlank) }
            .map { Entry(normalizeModel(it[0]), it[1].trim(), it[2].trim()) }.toList())
        private fun normalizeModel(model: String) = model.trim().uppercase(Locale.ROOT).replace(Regex("/(DS|DSN)$"), "")
    }
}
