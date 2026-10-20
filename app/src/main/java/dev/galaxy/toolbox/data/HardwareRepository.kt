package dev.galaxy.toolbox.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import dev.galaxy.toolbox.model.Reading
import dev.galaxy.toolbox.model.Snapshot
import dev.galaxy.toolbox.model.ByteUsage
import dev.galaxy.toolbox.model.CpuCoreSample
import dev.galaxy.toolbox.model.*
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HardwareRepository(private val context: Context) {
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val temperatureFiles = AndroidTemperatureFiles()
    private val gpuMonitor = GpuMonitor(temperatureFiles)
    private val rescanGpu = java.util.concurrent.atomic.AtomicBoolean(false)
    fun rescanGpuSources() { rescanGpu.set(true) }
    private val thermalPlatform = buildMap {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null
        if (Build.VERSION.SDK_INT >= 31) put("SoC manufacturer", Build.SOC_MANUFACTURER)
        ChipNames.resolve(soc, Build.HARDWARE, null)?.let {
            put("晶片代碼對照廠商", it.vendor); put("晶片代碼依據", it.source)
        }
    }
    private var thermals = ThermalProbe(files = temperatureFiles, model = Build.MODEL, platformMetadata = thermalPlatform)
    private var hwmon = HwmonProbe(files = temperatureFiles, model = Build.MODEL)
    private val rescanTemperatures = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastHeadroomAttempt = -15_000L
    private var headroom = ThermalHeadroomReading(status = if (Build.VERSION.SDK_INT < 30) TemperatureReadStatus.UNSUPPORTED else TemperatureReadStatus.UNAVAILABLE)
    fun rescanTemperatureSources() { rescanTemperatures.set(true) }
    fun staticHardware(): List<Reading> = buildList {
        fun section(category: HardwareCategory, name: String, read: () -> List<Reading>) {
            try { addAll(read()) } catch (e: Exception) {
                add(Reading("讀取狀態", null, name, CameraDetails.failure(e), category, name))
            }
        }
        section(HardwareCategory.DEVICE, "裝置與晶片") { DeviceDetails.read(context) }
        add(Reading("硬體平台", Build.HARDWARE.takeUnless { it.isBlank() || it.equals(Build.UNKNOWN, true) }, "Build.HARDWARE"))
        add(Reading("指令集", Build.SUPPORTED_ABIS.joinToString(), "Build.SUPPORTED_ABIS"))
        add(Reading("CPU 邏輯核心", Runtime.getRuntime().availableProcessors().toString(), "Runtime.availableProcessors"))
        section(HardwareCategory.DISPLAY, "顯示資訊") { HardwareDetails.display(context) }
        section(HardwareCategory.MEMORY, "記憶體資訊") { HardwareDetails.memory(context) }
        section(HardwareCategory.DEVICE, "功能宣告") { HardwareDetails.features(context) }
        section(HardwareCategory.CAMERA, "相機資訊") { CameraDetails.read(context.getSystemService(CameraManager::class.java)) }
        section(HardwareCategory.SENSORS, "感測器資訊") { HardwareDetails.sensors(context) }
        section(HardwareCategory.CODECS, "編解碼資訊") { HardwareDetails.codecs() }
        section(HardwareCategory.NETWORK, "網路資訊") { ConnectionDetails.network(context) }
        section(HardwareCategory.AUDIO, "音訊資訊") { ConnectionDetails.audio(context) }
    }

    fun sample(includeTemperatures: Boolean = false): Snapshot {
        if (includeTemperatures && rescanTemperatures.compareAndSet(true, false)) {
            thermals = ThermalProbe(files = temperatureFiles, model = Build.MODEL, platformMetadata = thermalPlatform)
            hwmon = HwmonProbe(files = temperatureFiles, model = Build.MODEL)
        }
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        fun extra(key: String): Int? = intent?.takeIf { it.hasExtra(key) }?.getIntExtra(key, Int.MIN_VALUE)?.takeUnless { it == Int.MIN_VALUE }
        fun property(key: Int): Int? = runCatching { battery.getIntProperty(key) }.getOrNull()?.takeUnless { it == Int.MIN_VALUE }
        val mi = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val fs = StatFs(context.filesDir.absolutePath)
        val batteryTemperatureRaw = extra(BatteryManager.EXTRA_TEMPERATURE)?.toString()
        val temperatureAt = System.currentTimeMillis()
        val batteryDescriptor = temperatureFiles.descriptor("Android battery temperature", "ACTION_BATTERY_CHANGED / EXTRA_TEMPERATURE",
            TemperatureSource.BATTERY_API, TemperatureUnit.DECI_C, emptyMap(), TemperatureClassifier(), Build.MODEL,
            "https://developer.android.com/reference/android/os/BatteryManager#EXTRA_TEMPERATURE").copy(id = "android-battery-temperature")
        val batteryTemperatureReading = TemperatureConversion.observation(batteryDescriptor,
            TemperatureFileResult(batteryTemperatureRaw, if (batteryTemperatureRaw == null) TemperatureReadStatus.NO_DATA else TemperatureReadStatus.OK), temperatureAt)
        val temperature = batteryTemperatureReading.celsius?.toFloat()
        val level = extra(BatteryManager.EXTRA_LEVEL)
        val scale = extra(BatteryManager.EXTRA_SCALE)
        val memory = ByteUsage.of(mi.totalMem - mi.availMem, mi.totalMem)
        val storage = ByteUsage.of(fs.totalBytes - fs.availableBytes, fs.totalBytes)
        val percent = if (level != null && scale != null && scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else null
        fun readKHz(path: String): Long? = runCatching { File(path).readText().trim().toLong() }.getOrNull()?.takeIf { it > 0 }
        val cores = (0 until Runtime.getRuntime().availableProcessors().coerceAtMost(32)).map { cpu ->
            val base = "/sys/devices/system/cpu/cpu$cpu/cpufreq"
            CpuCoreSample(cpu, readKHz("$base/scaling_cur_freq"), readKHz("$base/cpuinfo_max_freq"))
        }
        val thermalAt = System.currentTimeMillis()
        val headroomElapsed = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= 30 && includeTemperatures && headroomElapsed - lastHeadroomAttempt >= 15_000L) {
            lastHeadroomAttempt = headroomElapsed
            headroom = try {
                val value = power.getThermalHeadroom(10)
                val thresholds = if (Build.VERSION.SDK_INT >= 35) runCatching { power.thermalHeadroomThresholds.filterValues { it.isFinite() && it >= 0f } }.getOrDefault(emptyMap()) else emptyMap()
                ThermalHeadroomReading(value.takeIf { it.isFinite() && it >= 0f }, if (value.isFinite() && value >= 0f) TemperatureReadStatus.OK else TemperatureReadStatus.NO_DATA, thermalAt, thresholds)
            } catch (_: UnsupportedOperationException) { ThermalHeadroomReading(status = TemperatureReadStatus.UNSUPPORTED, atMs = thermalAt) }
            catch (_: Exception) { ThermalHeadroomReading(status = TemperatureReadStatus.UNAVAILABLE, atMs = thermalAt) }
        }
        val thermal = if (Build.VERSION.SDK_INT >= 29) try {
            val level = power.currentThermalStatus
            SystemThermalReading(level, if (level in 0..6) TemperatureReadStatus.OK else TemperatureReadStatus.UNAVAILABLE, thermalAt)
        } catch (_: UnsupportedOperationException) { SystemThermalReading(status = TemperatureReadStatus.UNSUPPORTED, atMs = thermalAt) }
        catch (_: Exception) { SystemThermalReading(status = TemperatureReadStatus.UNAVAILABLE, atMs = thermalAt) }
        else SystemThermalReading(status = TemperatureReadStatus.UNSUPPORTED, atMs = thermalAt)
        val temperatureReadings = listOf(batteryTemperatureReading) + if (includeTemperatures) {
            val sampleAt = System.currentTimeMillis()
            thermals.sample(sampleAt) + hwmon.sample(sampleAt)
        } else emptyList()
        val health = when (extra(BatteryManager.EXTRA_HEALTH)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好（系統狀態）"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "過熱"
            BatteryManager.BATTERY_HEALTH_DEAD -> "失效"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "過電壓"
            BatteryManager.BATTERY_HEALTH_COLD -> "低溫"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "未指定故障"
            else -> null
        }
        val status = when (extra(BatteryManager.EXTRA_STATUS)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充電中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放電中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充滿"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充電"
            else -> null
        }
        if (rescanGpu.compareAndSet(true, false)) gpuMonitor.reset()
        val gpu = gpuMonitor.sample(System.currentTimeMillis())
        val live = buildList {
            addAll(gpu.readings(gpu.atMs!!))
            add(Reading("RAM 使用 / 總量", memory?.let { "${gib(it.used)} / ${gib(it.total)}" }, "ActivityManager.MemoryInfo", "系統使用量，含快取", HardwareCategory.MEMORY, "RAM 即時用量"))
            add(Reading("資料分割區可用 / 總量", storage?.let { "${gib(it.available)} / ${gib(it.total)}" }, "StatFs · App 所在分割區", "非整台手機的實體快閃容量", HardwareCategory.MEMORY, "儲存空間"))
            add(Reading("RAM 可用", gib(mi.availMem), "ActivityManager.MemoryInfo.availMem", "可用系統 RAM，包含可回收部分。", HardwareCategory.MEMORY, "RAM 即時用量"))
            add(Reading("RAM 記憶體壓力", if (mi.lowMemory) "系統回報偏低" else "未回報偏低", "ActivityManager.MemoryInfo.lowMemory", "系統記憶體壓力旗標。", HardwareCategory.MEMORY, "RAM 即時用量"))
            add(Reading("RAM 低記憶體門檻", gib(mi.threshold), "ActivityManager.MemoryInfo.threshold", "系統低記憶體判定門檻，不是剩餘量。", HardwareCategory.MEMORY, "RAM 即時用量"))
            add(Reading("開機經過時間", "${SystemClock.elapsedRealtime() / 1000 / 60} 分鐘", "SystemClock.elapsedRealtime · 含休眠"))
            cores.forEach { core ->
                val path = "/sys/devices/system/cpu/cpu${core.index}/cpufreq/scaling_cur_freq"
                add(Reading("CPU ${core.index} 當前頻率", core.currentKHz?.let { "${it / 1000} MHz" }, path, "可選唯讀來源；休眠核心或 SELinux 限制時未知"))
            }
        }
        val rawCurrent = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawCharge = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val energy = runCatching {
            battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        }.getOrNull()
        val usableCharge = BatteryVendorPolicy.remainingChargeMicroAh(rawCharge, percent,
            BatteryVendor.of(Build.MANUFACTURER, Build.BRAND))
        val reportedVoltage = extra(BatteryManager.EXTRA_VOLTAGE)
        val batteryOptions = BatteryReadOptions.readResolved(context, reportedVoltage, energy, usableCharge)
        val effectiveVoltage = BatteryElectricalInterpretation.usableMilliVolts(
            reportedVoltage, batteryOptions.estimateVoltage, energy, usableCharge)
        val adjustedCurrent = rawCurrent?.let { if (batteryOptions.invertCurrent) -it else it }
        return Snapshot(live, listOf(
            Reading("電量", percent?.let { "$it%" }, "ACTION_BATTERY_CHANGED · level / scale"),
            Reading("充放電狀態", status, "ACTION_BATTERY_CHANGED · status"),
            Reading("電源來源", when (extra(BatteryManager.EXTRA_PLUGGED)) { 0 -> "未接電源"; 1 -> "AC"; 2 -> "USB"; 4 -> "無線充電"; 8 -> "底座"; else -> null }, "ACTION_BATTERY_CHANGED · plugged"),
            Reading("電池溫度", temperature?.let { String.format(Locale.ROOT, "%.1f °C", it) }, "ACTION_BATTERY_CHANGED · temperature / 10"),
            Reading(if (effectiveVoltage.second) "電壓 · 估算" else "電壓", effectiveVoltage.first?.let { "$it mV" },
                if (effectiveVoltage.second) "BatteryManager.ENERGY_COUNTER / CHARGE_COUNTER" else "ACTION_BATTERY_CHANGED · voltage",
                if (effectiveVoltage.second) "剩餘能量與剩餘電荷比，非瞬時實測電壓；不依電量百分比猜測。" else "Android 電池廣播回報。"),
            Reading("瞬時電流", adjustedCurrent?.let { "${it / 1000.0} mA" }, "BatteryManager.CURRENT_NOW · µA → mA",
                if (batteryOptions.invertCurrent) "使用者已反轉正負；原始值 $rawCurrent µA，僅更改顯示與功率方向。" else "正值代表流入，負值代表流出；原始值 $rawCurrent µA。"),
            Reading("剩餘電荷", usableCharge?.let { "${it / 1000.0} mAh" },
                "BatteryManager.CHARGE_COUNTER", "目前剩餘電荷，不是滿充容量；數值疑似百分比或單位異常時隱藏。原始值 ${rawCharge ?: "未知"} µAh。"),
            Reading("雙電芯 · 每芯條件電壓", BatteryElectricalInterpretation.perCellMilliVolts(effectiveVoltage.first, batteryOptions.dualCell)?.let { "$it mV" },
                "電池電壓 ÷ 2", "僅在來源電壓 6–10 V 且手動開啟雙電芯模式時顯示；假設為兩芯串聯，不代表接線已確認。"),
            Reading("電池狀態", health, "ACTION_BATTERY_CHANGED · health", "良好不代表容量健康度為 100%"),
            BatteryPresentation.cycleCount(if (Build.VERSION.SDK_INT >= 34) extra(BatteryManager.EXTRA_CYCLE_COUNT) else null)
        ), memory?.fraction, temperature,
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            memory, storage, cores, percent, System.currentTimeMillis(), temperatureReadings, thermal.copy(headroom = headroom), includeTemperatures,
            ChargingPowerReading(rawCurrent, effectiveVoltage.first, percent,
                extra(BatteryManager.EXTRA_PLUGGED)?.let { it != 0 }, extra(BatteryManager.EXTRA_STATUS) == BatteryManager.BATTERY_STATUS_FULL,
                System.currentTimeMillis(), extra(BatteryManager.EXTRA_STATUS), batteryOptions.invertCurrent, effectiveVoltage.second,
                batteryOptions.dualCell, batteryOptions.estimateVoltage), gpu)
    }
    private fun gib(bytes: Long) = String.format(Locale.ROOT, "%.2f GiB", bytes / 1073741824.0)
}
