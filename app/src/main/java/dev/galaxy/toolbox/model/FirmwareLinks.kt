package dev.galaxy.toolbox.model

import java.util.Locale

object FirmwareLinks {
    fun deviceModel(manufacturer: String, raw: String): String? {
        if (!manufacturer.equals("samsung", true)) return null
        val normalized = raw.trim().uppercase(Locale.ROOT)
        if (!Regex("SM-[A-Z0-9]{3,12}(?:/DS)?").matches(normalized)) return null
        return normalized.substringBefore('/')
    }
    fun catalogue(model: String): String {
        require(Regex("SM-[A-Z0-9]{3,12}").matches(model))
        return "https://samfw.com/firmware/$model"
    }
}
