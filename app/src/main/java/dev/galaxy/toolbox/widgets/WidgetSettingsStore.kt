package dev.galaxy.toolbox.widgets

import android.content.Context
import androidx.core.content.edit

class WidgetSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("widget_settings_v1", Context.MODE_PRIVATE)
    fun load(id: Int, kind: WidgetKind) = WidgetSettings.decode(preferences.all.filterKeys { it.startsWith("$id/") }
        .mapKeys { it.key.substringAfter('/') }.mapValues { it.value.toString() }, kind)
    fun save(id: Int, settings: WidgetSettings) {
        require(id > 0)
        preferences.edit { settings.encode().forEach { (key, value) -> putString("$id/$key", value) } }
    }
    fun delete(id: Int) { preferences.edit { preferences.all.keys.filter { it.startsWith("$id/") }.forEach(::remove) } }
}
