package dev.galaxy.toolbox.model

enum class HardwareCategory(val title: String) {
    CPU("處理器"), GPU("圖形"), THERMAL("溫度"), MEMORY("記憶體"), DISPLAY("螢幕"),
    CAMERA("相機"), NETWORK("網路"), AUDIO("音訊"), SENSORS("感測器"), CODECS("編解碼"), DEVICE("裝置");

    companion object {
        fun of(reading: Reading): HardwareCategory = reading.category ?: when {
            reading.label.startsWith("熱區") || reading.label.startsWith("溫度 ·") || reading.label == "熱節流狀態" || reading.label == "電池溫度" -> THERMAL
            reading.label.startsWith("CPU") || reading.label.startsWith("SoC") || reading.label.startsWith("處理器") || reading.label in listOf("指令集", "硬體平台", "熱節流狀態") -> CPU
            reading.label.startsWith("GPU") -> GPU
            reading.label.startsWith("RAM") || reading.label.startsWith("資料分割區") -> MEMORY
            reading.label.contains("顯示") -> DISPLAY
            reading.label.startsWith("相機") -> CAMERA
            reading.label.startsWith("感測器") -> SENSORS
            reading.label.startsWith("編碼") || reading.label.startsWith("解碼") -> CODECS
            else -> DEVICE
        }
    }
}
