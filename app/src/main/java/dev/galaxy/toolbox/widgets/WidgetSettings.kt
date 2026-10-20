package dev.galaxy.toolbox.widgets

enum class WidgetBackground(val title: String) { GLASS("液態玻璃風格（靜態）"), SOLID("純色背景"), TRANSPARENT("完全透明／無背景") }
enum class WidgetClick(val title: String) { APP("開啟 App"), SETTINGS("小工具設定"), NONE("無動作") }
enum class WidgetField(val title: String) {
    BATTERY("電量"), TEMPERATURE("電池溫度"), MEMORY("記憶體"), STORAGE("儲存空間"), CPU("CPU 最高頻率"), TRAFFIC("流量 · 自開機累計")
}

data class WidgetSettings(
    val background: WidgetBackground = WidgetBackground.GLASS,
    val opacity: Int = 80,
    val backgroundColor: Int = 0xFF162237.toInt(),
    val textColor: Int = 0xFFEDF3FF.toInt(),
    val accentColor: Int = 0xFF76E1C3.toInt(),
    val showClock: Boolean = true,
    val showModel: Boolean = true,
    val fields: Set<WidgetField> = linkedSetOf(WidgetField.BATTERY, WidgetField.TEMPERATURE, WidgetField.MEMORY, WidgetField.STORAGE),
    val click: WidgetClick = WidgetClick.APP
) {
    // Transparent is a separate rendering policy, never just an alpha applied to a decorated card.
    val drawsBackground: Boolean get() = background != WidgetBackground.TRANSPARENT && opacity.coerceIn(0, 100) > 0
    val drawsDecoration: Boolean get() = drawsBackground && background == WidgetBackground.GLASS
    fun encode(): Map<String, String> = mapOf(
        "background" to background.name, "opacity" to opacity.coerceIn(0, 100).toString(),
        "backgroundColor" to backgroundColor.toString(), "textColor" to textColor.toString(), "accentColor" to accentColor.toString(),
        "clock" to showClock.toString(), "model" to showModel.toString(),
        "fields" to fields.joinToString(",") { it.name }, "click" to click.name
    )
    companion object {
        fun defaults(kind: WidgetKind) = when (kind) {
            WidgetKind.BATTERY -> WidgetSettings(showClock = false, showModel = false, fields = linkedSetOf(WidgetField.BATTERY, WidgetField.TEMPERATURE))
            WidgetKind.RESOURCES -> WidgetSettings(showClock = false, fields = linkedSetOf(WidgetField.MEMORY, WidgetField.STORAGE, WidgetField.CPU))
            WidgetKind.OVERVIEW -> WidgetSettings()
        }
        fun decode(values: Map<String, String>, kind: WidgetKind): WidgetSettings {
            val d = defaults(kind)
            fun color(key: String, fallback: Int) = values[key]?.toIntOrNull()?.or(0xFF000000.toInt()) ?: fallback
            return d.copy(
                background = WidgetBackground.entries.firstOrNull { it.name == values["background"] } ?: d.background,
                opacity = values["opacity"]?.toIntOrNull()?.coerceIn(0, 100) ?: d.opacity,
                backgroundColor = color("backgroundColor", d.backgroundColor), textColor = color("textColor", d.textColor), accentColor = color("accentColor", d.accentColor),
                showClock = values["clock"]?.toBooleanStrictOrNull() ?: d.showClock,
                showModel = values["model"]?.toBooleanStrictOrNull() ?: d.showModel,
                fields = if ("fields" in values) values.getValue("fields").split(',').mapNotNull { name -> WidgetField.entries.firstOrNull { it.name == name } }.toSet() else d.fields,
                click = WidgetClick.entries.firstOrNull { it.name == values["click"] } ?: d.click
            )
        }
        fun parseColor(value: String): Int? = value.removePrefix("#").takeIf { Regex("[0-9a-fA-F]{6}").matches(it) }
            ?.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())
    }
}

/** Small sizes use a compact row; no selected field silently disappears during resizing. */
data class WidgetLayoutPlan(val columns: Int, val dense: Boolean, val details: Boolean) {
    companion object {
        fun forSize(width: Int, height: Int, fontScale: Float, settings: WidgetSettings): WidgetLayoutPlan {
            val columns = if (width >= 260 * fontScale.coerceAtLeast(1f)) 2 else 1
            val rows = (settings.fields.size + columns - 1) / columns
            val header = 48 + (if (settings.showClock) 38 else 0) + (if (settings.showModel) 32 else 0)
            val dense = height < header + rows * 66 * fontScale
            return WidgetLayoutPlan(columns, dense, !dense && fontScale <= 1.3f)
        }
    }
}

data class WidgetMetric(val label: String, val value: String, val detail: String = "", val progress: Int? = null)
