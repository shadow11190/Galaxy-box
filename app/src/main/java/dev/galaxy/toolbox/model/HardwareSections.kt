package dev.galaxy.toolbox.model

/** Semantic sections, independent of list size. Explicit API categories override label heuristics. */
object HardwareSections {
    fun title(reading: Reading, category: HardwareCategory): String {
        reading.group?.let { return it }
        val label = reading.label
        return when (category) {
            HardwareCategory.CPU -> Regex("CPU (\\d+) ").find(label)?.let { "CPU ${it.groupValues[1]} · 調頻能力" }
                ?: if (label.contains("指令") || label.contains("執行位元")) "指令與執行環境" else "處理器概覽"
            HardwareCategory.GPU -> when {
                label.contains("系統宣告") -> "系統宣告能力"
                label.contains("頻率") || label.contains("負載") || label.contains("忙碌") -> "GPU 即時監控"
                else -> "圖形處理器與驅動"
            }
            HardwareCategory.MEMORY -> if (label.startsWith("RAM")) "RAM 即時用量" else "儲存空間"
            HardwareCategory.DEVICE -> if (label.contains("版本") || label.contains("Build") || label.contains("Android") || label.contains("更新")) "系統版本" else "裝置識別"
            else -> "${category.title}資料"
        }
    }
}
