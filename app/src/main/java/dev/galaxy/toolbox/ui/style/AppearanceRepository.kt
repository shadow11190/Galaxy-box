package dev.galaxy.toolbox.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_v1")

internal data class AppAppearance(
    val style: AppStyle = AppStyle.LIQUID_GLASS,
    val glassEnabled: Boolean = true,
    val reduceTransparency: Boolean = false,
)

/** Only appearance settings live here; existing hardware and widget stores are untouched. */
internal class AppearanceRepository(context: Context) {
    private val application = context.applicationContext
    private val legacy = application.getSharedPreferences("display", Context.MODE_PRIVATE)

    private companion object {
        val STYLE = stringPreferencesKey("app_style")
        val GLASS = booleanPreferencesKey("glass_enabled")
        val REDUCED = booleanPreferencesKey("reduce_transparency")
    }

    fun legacySettings() = AppAppearance(
        style = AppStyle.fromPreference(legacy.getString("visual_style", null)),
        glassEnabled = if (legacy.contains("liquid_glass")) legacy.getBoolean("liquid_glass", true) else true,
    )

    val settings: Flow<AppAppearance> = application.appearanceDataStore.data.map { stored ->
        val fallback = legacySettings()
        AppAppearance(
            style = AppStyle.fromPreference(stored[STYLE] ?: fallback.style.preferenceValue),
            glassEnabled = stored[GLASS] ?: fallback.glassEnabled,
            reduceTransparency = stored[REDUCED] ?: false,
        )
    }

    /** Fill absent keys once; preserving the old SharedPreferences keeps older installs safe. */
    suspend fun migrateLegacy() {
        application.appearanceDataStore.edit { current ->
            val fallback = legacySettings()
            if (current[STYLE] == null) current[STYLE] = fallback.style.preferenceValue
            if (current[GLASS] == null) current[GLASS] = fallback.glassEnabled
            if (current[REDUCED] == null) current[REDUCED] = false
        }
    }

    suspend fun setStyle(style: AppStyle) {
        application.appearanceDataStore.edit { it[STYLE] = style.preferenceValue }
    }

    suspend fun setGlassEnabled(enabled: Boolean) {
        application.appearanceDataStore.edit { it[GLASS] = enabled }
    }

    suspend fun setReduceTransparency(enabled: Boolean) {
        application.appearanceDataStore.edit { it[REDUCED] = enabled }
    }
}
