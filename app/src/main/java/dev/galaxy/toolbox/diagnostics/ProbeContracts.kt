package dev.galaxy.toolbox.diagnostics

enum class ProbeKind { BATTERY, GPU }
enum class ProbeValue { TYPE, CHARGE_UAH, ENERGY_UWH, CYCLES, SAMSUNG_ASOC, SAMSUNG_RAW, GPU_HZ, GPU_BUSY }
data class ProbeFile(val path: String, val label: String, val value: ProbeValue)

/** No path or command is supplied by the caller. These are the complete read-only contracts. */
object ProbeContracts {
    val batteryCommand = listOf("/system/bin/dumpsys", "-t", "5", "battery")
    val batteryFiles = listOf("battery", "bms").flatMap { supply ->
        val base = "/sys/class/power_supply/$supply"
        listOf(ProbeFile("$base/type", "電源類型", ProbeValue.TYPE),
            ProbeFile("$base/charge_full_design", "設計電荷容量", ProbeValue.CHARGE_UAH),
            ProbeFile("$base/charge_full", "驅動回報滿充容量", ProbeValue.CHARGE_UAH),
            ProbeFile("$base/energy_full_design", "設計能量", ProbeValue.ENERGY_UWH),
            ProbeFile("$base/energy_full", "驅動回報滿充能量", ProbeValue.ENERGY_UWH),
            ProbeFile("$base/cycle_count", "循環次數", ProbeValue.CYCLES))
    } + listOf(ProbeFile("/sys/class/power_supply/battery/batt_asoc", "Samsung ASOC 原始值", ProbeValue.SAMSUNG_ASOC),
        ProbeFile("/sys/class/power_supply/battery/battery_cycle", "Samsung 循環欄位原始值", ProbeValue.SAMSUNG_RAW))
    val gpuFiles = listOf(
        ProbeFile("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq", "GPU 當前頻率", ProbeValue.GPU_HZ),
        ProbeFile("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", "GPU 頻率下限", ProbeValue.GPU_HZ),
        ProbeFile("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", "GPU 頻率上限", ProbeValue.GPU_HZ),
        ProbeFile("/sys/class/kgsl/kgsl-3d0/gpubusy", "GPU 忙碌比例", ProbeValue.GPU_BUSY))
    fun files(kind: ProbeKind) = if (kind == ProbeKind.BATTERY) batteryFiles else gpuFiles
}
