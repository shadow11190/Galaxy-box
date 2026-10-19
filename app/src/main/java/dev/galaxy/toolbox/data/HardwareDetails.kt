package dev.galaxy.toolbox.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager as PM
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import android.os.StatFs
import android.view.Display
import dev.galaxy.toolbox.model.HardwareCategory as H
import dev.galaxy.toolbox.model.Reading
import java.io.File
import java.util.Locale

/** Optional public capabilities. Kept off the main thread with staticHardware(). */
object HardwareDetails {
    private fun n(value: Number) = String.format(Locale.ROOT, "%.2f", value.toDouble()).trimEnd('0').trimEnd('.')
    private fun bytes(value: Long) = String.format(Locale.ROOT, "%.2f GiB · %,d B", value / 1073741824.0, value)
    fun display(context: Context): List<Reading> = buildList {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val dm = context.resources.displayMetrics
        fun row(label: String, source: String, group: String = "顯示與密度", detail: String = "啟動時讀取的公開顯示資訊；系統顯示設定或多視窗可能影響結果。", read: () -> String?) {
            val result = runCatching(read)
            add(Reading(label, result.getOrNull(), source, result.exceptionOrNull()?.let(CameraDetails::failure) ?: detail, H.DISPLAY, group))
        }
        row("啟動時顯示模式", "Display.mode", detail = "系統當前顯示模式，非面板原生解析度保證、非即時遊戲 FPS。") {
            display?.mode?.let { "${it.physicalWidth} × ${it.physicalHeight} · ${n(it.refreshRate)} Hz" }
        }
        row("App 可用顯示區", "Resources.displayMetrics") { "${dm.widthPixels} × ${dm.heightPixels} px" }
        row("邏輯密度", "DisplayMetrics.densityDpi / density") { "${dm.densityDpi} dpi · ${n(dm.density)}×" }
        row("App 可用尺寸", "DisplayMetrics.widthPixels / density") { "${n(dm.widthPixels / dm.density)} × ${n(dm.heightPixels / dm.density)} dp" }
        row("X / Y 回報密度", "DisplayMetrics.xdpi / ydpi", detail = "驅動回報值，可能近似；不據此宣稱已量測螢幕尺寸或真實 PPI。") { "${n(dm.xdpi)} / ${n(dm.ydpi)} dpi" }
        row("字體縮放", "Configuration.fontScale") { "${n(context.resources.configuration.fontScale)}×" }
        row("顯示旋轉", "Display.rotation") { display?.rotation?.let { "${it * 90}°" } }
        row("寬色域", "Display.isWideColorGamut", "色彩與 HDR") { display?.let { if (it.isWideColorGamut) "系統宣告支援" else "系統未宣告" } }
        row("HDR 格式", if (Build.VERSION.SDK_INT >= 34) "Display.Mode.supportedHdrTypes" else "Display.HdrCapabilities.supportedHdrTypes", "色彩與 HDR",
            "API 34+ 列出目前模式支援的 HDR；舊版使用顯示能力。非亮度、播放或相機錄影實測。") {
            val types = if (Build.VERSION.SDK_INT >= 34) display?.mode?.supportedHdrTypes else display?.hdrCapabilities?.supportedHdrTypes
            types?.let { if (it.isEmpty()) "未列出 HDR 格式" else CameraDetails.named(it, mapOf(1 to "Dolby Vision", 2 to "HDR10", 3 to "HLG", 4 to "HDR10+")) }
        }
        // Android 34 deprecates desired luminance estimates; do not present them as peak-nit measurements.
        display?.supportedModes?.forEach { mode ->
            row("模式 ${mode.modeId}", "Display.supportedModes · modeId ${mode.modeId}", "系統公開顯示模式",
                "系統公開模式，不保證 App 可切換所有模式或持續達到此刷新率。") {
                "${mode.physicalWidth} × ${mode.physicalHeight} · ${n(mode.refreshRate)} Hz"
            }
        }
    }

    fun memory(context: Context): List<Reading> = buildList {
        fun row(label: String, source: String, group: String, detail: String = "", value: String?) =
            add(Reading(label, value, source, detail, H.MEMORY, group))
        val am = context.getSystemService(ActivityManager::class.java)
        row("App 一般記憶體額度", "ActivityManager.memoryClass", "App 記憶體額度", "App VM 的近似額度，不是手機 RAM 總容量。", am?.memoryClass?.let { "$it MiB" })
        row("App 大型記憶體額度", "ActivityManager.largeMemoryClass", "App 記憶體額度", "系統提供的 largeHeap 類別，不表示本 App 已啟用或使用此額度。", am?.largeMemoryClass?.let { "$it MiB" })
        row("低 RAM 裝置旗標", "ActivityManager.isLowRamDevice", "App 記憶體額度", "系統配置旗標，非目前 RAM 是否不足。", am?.isLowRamDevice?.let { if (it) "是" else "否" })
        val mem = runCatching { File("/proc/meminfo").useLines { it.take(128).toList() } }.getOrNull()
        for ((key, label) in listOf("SwapTotal" to "Swap 總量", "SwapFree" to "Swap 可用", "Zram" to "ZRAM（若有回報）")) {
            val value = mem?.firstOrNull { it.startsWith("$key:") }?.let(::meminfoBytes)
            row(label, "/proc/meminfo · $key（kB = KiB）", "交換空間快照", "啟動時快照；Swap 可包含壓縮 RAM，不代表實體儲存擴充，與 RAM 總量分列。", value?.let(::bytes))
        }
        runCatching {
            val fs = StatFs(context.filesDir.absolutePath)
            row("分割區區塊大小", "StatFs.blockSizeLong", "儲存空間", "App 所在資料分割區。", "${fs.blockSizeLong} B")
        }
    }
    internal fun meminfoBytes(line: String): Long? {
        val match = Regex("^[A-Za-z_]+:\\s*(\\d+)\\s+kB\\s*$").matchEntire(line) ?: return null
        val kib = match.groupValues[1].toLongOrNull() ?: return null
        return if (kib > Long.MAX_VALUE / 1024) null else kib * 1024
    }

    fun features(context: Context): List<Reading> = buildList {
        val pm = context.packageManager
        listOf(PM.FEATURE_NFC to "NFC", PM.FEATURE_BLUETOOTH to "藍牙", PM.FEATURE_BLUETOOTH_LE to "藍牙 LE",
            PM.FEATURE_WIFI to "Wi-Fi", PM.FEATURE_WIFI_DIRECT to "Wi-Fi Direct", PM.FEATURE_TELEPHONY to "行動通訊",
            PM.FEATURE_LOCATION_GPS to "GNSS / GPS", PM.FEATURE_FINGERPRINT to "指紋辨識", PM.FEATURE_USB_HOST to "USB Host",
            PM.FEATURE_USB_ACCESSORY to "USB 配件", PM.FEATURE_AUDIO_LOW_LATENCY to "低延遲音訊",
            PM.FEATURE_AUDIO_PRO to "專業音訊", PM.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND to "多指觸控（5 點以上）",
            PM.FEATURE_SENSOR_STEP_COUNTER to "計步器", PM.FEATURE_SENSOR_BAROMETER to "氣壓計").forEach { (key, name) ->
            val result = runCatching { pm.hasSystemFeature(key) }
            add(Reading(name, result.getOrNull()?.let { if (it) "已宣告" else "未宣告" }, "PackageManager.hasSystemFeature · $key",
                result.exceptionOrNull()?.let(CameraDetails::failure) ?: "系統功能宣告，不是硬體實測，未宣告不等於已確認無此硬體。", H.DEVICE, "硬體功能宣告"))
        }
    }

    fun sensors(context: Context): List<Reading> = buildList {
        val sensors = runCatching { context.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL) }
        if (sensors.isFailure) { add(Reading("感測器清單", null, "SensorManager.TYPE_ALL", CameraDetails.failure(sensors.exceptionOrNull()!!), H.SENSORS, "感測器")); return@buildList }
        sensors.getOrThrow().forEachIndexed { index, s ->
            val group = "${s.name} · #${index + 1}"
            fun row(label: String, value: String?, source: String) { add(Reading(label, value, "Sensor · $source", "系統／廠商標示的感測器資訊，不代表已通過硬體檢測；數值單位依感測器類型。", H.SENSORS, group)) }
            row("廠商", s.vendor, "vendor"); row("類型", "${s.stringType} · ${s.type}", "stringType / type")
            row("版本", s.version.toString(), "version"); row("最大量程", s.maximumRange.toString(), "maximumRange")
            row("解析度", s.resolution.toString(), "resolution"); row("標示功耗", "${s.power.toString()} mA", "power")
            row("最短／最長取樣間隔", "${s.minDelay} / ${s.maxDelay} µs", "minDelay / maxDelay")
            row("喚醒感測器", if (s.isWakeUpSensor) "是" else "否", "isWakeUpSensor")
            row("回報模式", mapOf(0 to "連續", 1 to "變更時", 2 to "單次", 3 to "特殊觸發")[s.reportingMode] ?: "代碼 ${s.reportingMode}", "reportingMode")
            row("FIFO 保留／最大事件", "${s.fifoReservedEventCount} / ${s.fifoMaxEventCount}", "fifoReservedEventCount / fifoMaxEventCount")
        }
    }

    fun codecs(): List<Reading> = buildList {
        val infos = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }
        if (infos.isFailure) { add(Reading("編解碼器清單", null, "MediaCodecList.REGULAR_CODECS", CameraDetails.failure(infos.exceptionOrNull()!!), H.CODECS, "編解碼器")); return@buildList }
        infos.getOrThrow().forEach { codec -> codec.supportedTypes.forEach { mime ->
            val title = "${if (codec.isEncoder) "編碼" else "解碼"} · $mime"
            val detail = runCatching {
                val caps = codec.getCapabilitiesForType(mime)
                buildList {
                    if (Build.VERSION.SDK_INT >= 29) add("${if (codec.isHardwareAccelerated) "硬體加速" else if (codec.isSoftwareOnly) "軟體" else "實作未分類"} · ${if (codec.isVendor) "廠商" else "平台"}")
                    caps.videoCapabilities?.let { v -> add("寬 ${v.supportedWidths} · 高 ${v.supportedHeights}\n幀率 ${v.supportedFrameRates} fps · 位元率 ${v.bitrateRange} bit/s\n寬高與幀率範圍分列，不代表所有組合可同時使用。") }
                    caps.audioCapabilities?.let { a -> add("聲道上限 ${a.maxInputChannelCount} · 取樣率 ${a.supportedSampleRateRanges.joinToString()} Hz · ${a.bitrateRange} bit/s") }
                    add("Profile / Level（原始碼）：" + caps.profileLevels.joinToString { "${it.profile}/${it.level}" })
                    add("色彩格式（原始碼）：" + caps.colorFormats.joinToString())
                }.joinToString("\n")
            }.getOrElse { CameraDetails.failure(it) }
            add(Reading(codec.name, mime, "MediaCodecList.REGULAR_CODECS / getCapabilitiesForType($mime)", detail, H.CODECS, title))
        } }
    }
}
