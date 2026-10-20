package dev.galaxy.toolbox.data

import java.util.Locale

/** Only source-backed release years and device types. Missing means unverified. */
class DeviceClassifications private constructor(private val entries: Map<Key, Entry>) {
    data class Entry(val type: String, val year: Int, val source: String) {
        val isPhoneSince2018: Boolean get() = type in PHONE_TYPES && year >= 2018
        val label: String get() = when {
            isPhoneSince2018 -> "${year} 年 Android 手機（已核對）"
            type in PHONE_TYPES -> "${year} 年手機（2018 年以前）"
            else -> "${year} 年${type}（非手機）"
        }
    }
    private data class Key(val brand: String, val model: String, val device: String)

    fun lookup(brand: String, model: String, device: String): Entry? =
        entries[Key(DeviceNames.normalizeBrand(brand), model.trim().uppercase(Locale.ROOT), device.trim().lowercase(Locale.ROOT))]

    companion object {
        private val PHONE_TYPES = setOf("phone", "foldable", "phone (slider)")

        fun parse(lines: Sequence<String>): DeviceClassifications {
            val facts = lines.filterNot { it.startsWith('#') }.map { it.split('\t') }.mapNotNull { fields ->
                if (fields.size != 6 || fields.any(String::isBlank)) return@mapNotNull null
                val year = fields[4].toIntOrNull() ?: return@mapNotNull null
                if (year !in 2010..2026) return@mapNotNull null
                Key(DeviceNames.normalizeBrand(fields[0]), fields[1].trim().uppercase(Locale.ROOT), fields[2].trim().lowercase(Locale.ROOT)) to
                    Entry(fields[3], year, fields[5])
            }.toMap()
            return DeviceClassifications(facts)
        }
    }
}
