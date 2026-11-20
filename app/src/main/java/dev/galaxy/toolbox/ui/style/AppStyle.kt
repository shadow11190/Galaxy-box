package dev.galaxy.toolbox.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** The page model is shared; only its visual language changes. */
internal enum class AppStyle(val preferenceValue: String, val label: String) {
    LIQUID_GLASS("liquid_glass", "Liquid Glass"),
    MIUIX("miuix", "Miuix 風格");

    companion object {
        fun fromPreference(value: String?): AppStyle = when (value) {
            "miuix" -> MIUIX
            // The old `original` value is migrated without changing the user's page or data.
            else -> LIQUID_GLASS
        }
    }
}

internal val LocalAppStyle = staticCompositionLocalOf { AppStyle.LIQUID_GLASS }
