package dev.galaxy.toolbox.model

object BatteryPresentation {
    fun visible(readings: List<Reading>): List<Reading> = readings.filter { !it.value.isNullOrBlank() }

    fun cycleCount(raw: Int?): Reading = Reading("循環次數", raw?.takeIf { it >= 0 }?.let {
        if (it == 0) "系統回報 0" else "$it 次"
    }, "ACTION_BATTERY_CHANGED · CYCLE_COUNT · Android 14+",
        "保留系統原始回報；0 未經其他來源交叉驗證，不代表已確認電池從未循環。")
}
