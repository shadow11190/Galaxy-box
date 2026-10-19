package dev.galaxy.toolbox.data

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Size
import dev.galaxy.toolbox.model.HardwareCategory
import dev.galaxy.toolbox.model.Reading
import java.util.Locale

/** Metadata only. Does not open cameras, request CAMERA permission, or probe private IDs. */
object CameraDetails {
    fun read(manager: CameraManager?): List<Reading> = read(
        { requireNotNull(manager).cameraIdList.toList() }, { requireNotNull(manager).getCameraCharacteristics(it) })

    internal fun read(ids: () -> List<String>, characteristics: (String) -> C): List<Reading> = buildList {
        val listed = try { ids() } catch (e: Exception) {
            add(row("相機清單", null, "CameraManager.cameraIdList", failure(e), "公開相機")); return@buildList
        }
        add(row("可開啟的公開 Camera ID", listed.joinToString().ifBlank { null }, "CameraManager.cameraIdList",
            "Camera2 公開清單；邏輯相機與實體相機可能重疊，數量不代表鏡頭總數。", "公開相機"))
        val known = linkedMapOf<String, C>()
        val parents = linkedMapOf<String, MutableSet<String>>()
        // A restricted/failing ID must not suppress the rest of the camera list.
        listed.forEach { id ->
            try {
                val c = characteristics(id); known[id] = c
                if (Build.VERSION.SDK_INT >= 28) runCatching { c.physicalCameraIds }.getOrNull()?.sorted()?.forEach {
                    parents.getOrPut(it) { linkedSetOf() }.add(id)
                }
            } catch (e: Exception) { add(row("讀取狀態", null, "CameraManager.getCameraCharacteristics($id)", failure(e), "相機 · ID $id")) }
        }
        // Only IDs explicitly exposed by a public logical camera are inspected; never brute force IDs.
        parents.keys.filterNot { it in listed }.forEach { id ->
            try { known[id] = characteristics(id) }
            catch (e: Exception) { add(row("讀取狀態", null, "CameraManager.getCameraCharacteristics($id)", failure(e), "實體相機 · ID $id")) }
        }
        known.forEach { (id, c) -> addAll(describe(id, c, parents[id].orEmpty(), id in listed)) }
    }

    internal fun describe(id: String, c: C, parents: Set<String> = emptySet(), openable: Boolean = true): List<Reading> = buildList {
        fun <T> get(key: C.Key<T>): T? = runCatching { c[key] }.getOrNull()
        val permissionKeys = if (Build.VERSION.SDK_INT >= 29) runCatching { c.keysNeedingPermission.toSet() }.getOrDefault(emptySet()) else emptySet()
        val facing = when (get(C.LENS_FACING)) { C.LENS_FACING_BACK -> "後置"; C.LENS_FACING_FRONT -> "前置"; C.LENS_FACING_EXTERNAL -> "外接"; else -> "方向未知" }
        val group = "$facing${if (openable) "相機" else "實體相機"} · ID $id"
        fun <T> field(label: String, key: C.Key<T>, note: String = "", format: (T) -> String?) {
            val result = runCatching { c[key] }
            add(row(label, result.getOrNull()?.let(format), "Camera2[$id] · ${key.name}",
                result.exceptionOrNull()?.let(::failure) ?: (note.ifBlank { "系統未提供時顯示未知；相機中繼資料，不是即時拍攝結果。" } +
                    if (result.getOrNull() == null && key in permissionKeys) " 此欄位列於需相機權限清單；允許後仍可能受韌體限制。" else ""), group))
        }
        fun extra(label: String, source: String, note: String = "", value: () -> String?) {
            val result = runCatching(value)
            add(row(label, result.getOrNull(), "Camera2[$id] · $source", result.exceptionOrNull()?.let(::failure) ?: note, group))
        }
        val map = get(C.SCALER_STREAM_CONFIGURATION_MAP)
        extra("最大 JPEG（一般模式）", C.SCALER_STREAM_CONFIGURATION_MAP.name,
            "公開一般模式 JPEG 輸出上限，非宣傳像素；不代表所有模式可同時使用。") { largest(map?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty())?.let(::sizeWithMp) }
        field("焦距", C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS, "鏡頭實際焦距，並非 35 mm 等效焦距。") { values(it.toList(), "mm") }
        field("光圈", C.LENS_INFO_AVAILABLE_APERTURES) { it.joinToString(" / ") { n -> "ƒ/${decimal(n.toDouble())}" }.ifBlank { null } }
        field("感光元件尺寸", C.SENSOR_INFO_PHYSICAL_SIZE) { "${decimal(it.width.toDouble())} × ${decimal(it.height.toDouble())} mm" }
        field("像素陣列", C.SENSOR_INFO_PIXEL_ARRAY_SIZE, "驅動公開的像素陣列，可能包含非成像邊緣；不拿它取代 JPEG 輸出或廠商宣傳值。", ::sizeWithMp)
        field("ISO 範圍", C.SENSOR_INFO_SENSITIVITY_RANGE) { "${it.lower} – ${it.upper}" }
        field("曝光時間範圍", C.SENSOR_INFO_EXPOSURE_TIME_RANGE) { "${exposure(it.lower)} – ${exposure(it.upper)}" }
        field("Camera2 硬體等級", C.INFO_SUPPORTED_HARDWARE_LEVEL) { hardwareLevel(it) }
        field("自動對焦模式", C.CONTROL_AF_AVAILABLE_MODES) { named(it, mapOf(0 to "關閉", 1 to "自動", 2 to "近拍", 3 to "連續錄影", 4 to "連續拍照", 5 to "EDOF")) }
        field("光學防手震 OIS", C.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) { named(it, mapOf(0 to "關閉", 1 to "開啟")) }
        field("電子防手震 EIS", C.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) { named(it, mapOf(0 to "關閉", 1 to "開啟", 2 to "預覽防手震")) }
        field("閃光燈", C.FLASH_INFO_AVAILABLE) { if (it) "有" else "無" }
        field("有效成像區域", C.SENSOR_INFO_ACTIVE_ARRAY_SIZE) { "${it.width()} × ${it.height()} px · (${it.left}, ${it.top})–(${it.right}, ${it.bottom})" }
        field("最短對焦距離", C.LENS_INFO_MINIMUM_FOCUS_DISTANCE,
            "單位為屈光度；0 表示固定焦距鏡頭。非最近拍攝距離實測，校正程度另列。") { "${decimal(it.toDouble())} diopter" }
        field("對焦距離校正", C.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) { when (it) { 0 -> "未校正"; 1 -> "近似"; 2 -> "已校正"; else -> "代碼 $it" } }
        field("數位變焦上限", C.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM, "裁切數位變焦能力，不是光學倍率。") { "${decimal(it.toDouble())}×" }
        if (Build.VERSION.SDK_INT >= 30) field("變焦比例範圍", C.CONTROL_ZOOM_RATIO_RANGE) { "${decimal(it.lower.toDouble())}× – ${decimal(it.upper.toDouble())}×" }
        field("感測器方向", C.SENSOR_ORIENTATION) { "$it°" }
        field("色彩濾光陣列", C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) { when (it) { 0 -> "RGGB"; 1 -> "GRBG"; 2 -> "GBRG"; 3 -> "BGGR"; 4 -> "RGB"; 5 -> "單色"; 6 -> "NIR"; else -> "代碼 $it" } }
        field("曝光補償範圍", C.CONTROL_AE_COMPENSATION_RANGE, "此列為步數；每步 EV 另列。") { "${it.lower} – ${it.upper} 步" }
        field("曝光補償步長", C.CONTROL_AE_COMPENSATION_STEP) { "${decimal(it.toDouble())} EV / 步" }
        field("自動曝光 FPS 範圍", C.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES, "相機自動曝光請求能力；非所有錄影解析度的保證幀率。") { it.joinToString("、") { r -> "${r.lower}–${r.upper}" }.ifBlank { null } }
        field("自動曝光模式", C.CONTROL_AE_AVAILABLE_MODES) { named(it, mapOf(0 to "關閉", 1 to "開啟", 2 to "自動閃光", 3 to "強制閃光", 4 to "防紅眼", 5 to "外部閃光")) }
        field("白平衡模式", C.CONTROL_AWB_AVAILABLE_MODES) { named(it, mapOf(0 to "關閉", 1 to "自動", 2 to "白熾燈", 3 to "日光燈", 4 to "暖日光燈", 5 to "日光", 6 to "陰天", 7 to "黃昏", 8 to "陰影")) }
        field("鎖定自動曝光", C.CONTROL_AE_LOCK_AVAILABLE) { if (it) "支援" else "不支援" }
        field("鎖定白平衡", C.CONTROL_AWB_LOCK_AVAILABLE) { if (it) "支援" else "不支援" }
        field("臉部偵測模式", C.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) { named(it, mapOf(0 to "關閉", 1 to "簡易", 2 to "完整")) }
        field("最大偵測人臉數", C.STATISTICS_INFO_MAX_FACE_COUNT) { it.toString() }
        field("公開能力", C.REQUEST_AVAILABLE_CAPABILITIES) { named(it, capabilities) }
        extra("Camera ID 關係", "cameraIdList / physicalCameraIds",
            "邏輯與實體 ID 可能對應同一鏡頭，不重算為額外鏡頭；不按 ID 猜主鏡、長焦或鏡頭型號。") {
            buildList { add(if (openable) "在公開可開啟清單" else "由邏輯相機公開，無法單獨開啟"); if (parents.isNotEmpty()) add("上層邏輯 ID：${parents.joinToString()}") }.joinToString("\n")
        }
        if (Build.VERSION.SDK_INT >= 28) extra("此邏輯相機的實體 ID", "CameraCharacteristics.physicalCameraIds") {
            c.physicalCameraIds.sorted().joinToString().ifBlank { "未列出" }
        }
        extra("JPEG 輸出尺寸", "StreamConfigurationMap.getOutputSizes(JPEG)") { sizes(map?.getOutputSizes(ImageFormat.JPEG)?.toList()) }
        extra("YUV 輸出尺寸", "StreamConfigurationMap.getOutputSizes(YUV_420_888)") { sizes(map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList()) }
        extra("RAW 輸出尺寸", "StreamConfigurationMap.getOutputSizes(RAW_SENSOR)") { sizes(map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList()) }
        extra("錄影輸出尺寸／理論上限", "StreamConfigurationMap · MediaRecorder / getOutputMinFrameDuration",
            "依每個尺寸的最小幀間隔換算理論上限，非實測 FPS、編碼器或三星相機 App 的錄影保證。") {
            map?.getOutputSizes(MediaRecorder::class.java)?.sortedByDescending { area(it) }?.joinToString("\n") { size ->
                val duration = runCatching { map.getOutputMinFrameDuration(MediaRecorder::class.java, size) }.getOrNull()
                "${size.width} × ${size.height}" + (duration?.takeIf { it > 0 }?.let { " · ≤ ${decimal(1e9 / it)} fps" } ?: " · 幀率未知")
            }?.ifBlank { null }
        }
        val caps = get(C.REQUEST_AVAILABLE_CAPABILITIES)
        extra("受限高速錄影", "StreamConfigurationMap.highSpeedVideoSizes / getHighSpeedVideoFpsRangesFor") {
            if (caps == null) null else if (C.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO !in caps) "未宣告此能力"
            else map?.highSpeedVideoSizes?.joinToString("\n") { s -> "${s.width} × ${s.height} · " + map.getHighSpeedVideoFpsRangesFor(s).joinToString { "${it.lower}–${it.upper} fps" } }?.ifBlank { null }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            field("像素陣列（最大解析度模式）", C.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION,
                "僅部分裝置公開；與一般像素模式分列，不用機型宣傳規格補值。", ::sizeWithMp)
            extra("最大 JPEG（最大解析度模式）", C.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION.name) {
                largest(c[C.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION]?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty())?.let(::sizeWithMp)
            }
        }
        add(row("感光元件型號／廠商", null, "Camera2 無通用公開型號欄位", "不以 Camera ID、手機型號或像素猜 IMX／ISOCELL 型號。", group))
    }

    private fun row(label: String, value: String?, source: String, detail: String, group: String) =
        Reading(label, value, source, detail, HardwareCategory.CAMERA, group)
    internal fun failure(e: Throwable): String = when (e) {
        is SecurityException -> "權限不足（${e.javaClass.simpleName}）"
        else -> "無法取得（${e.javaClass.simpleName}）"
    }
    internal fun area(size: Size): Long = size.width.toLong() * size.height
    internal fun largest(sizes: List<Size>): Size? = sizes.filter { it.width > 0 && it.height > 0 }.maxByOrNull(::area)
    internal fun sizeWithMp(s: Size) = "${String.format(Locale.ROOT, "%.1f", area(s) / 1_000_000.0)} MP · ${s.width} × ${s.height} px"
    private fun sizes(s: List<Size>?): String? = s?.distinct()?.sortedByDescending(::area)?.joinToString("\n") { "${it.width} × ${it.height} px" }?.ifBlank { null }
    internal fun exposure(ns: Long): String = if (ns < 1_000_000_000L) "${decimal(ns / 1_000_000.0)} ms" else "${decimal(ns / 1_000_000_000.0)} s"
    private fun decimal(n: Double) = String.format(Locale.ROOT, "%.3f", n).trimEnd('0').trimEnd('.')
    private fun values(n: List<Float>, unit: String) = n.joinToString(" / ") { "${decimal(it.toDouble())} $unit" }.ifBlank { null }
    internal fun named(values: IntArray, names: Map<Int, String>) = values.joinToString("、") { names[it] ?: "代碼 $it" }.ifBlank { null }
    internal fun hardwareLevel(level: Int) = mapOf(0 to "LIMITED", 1 to "FULL", 2 to "LEGACY", 3 to "LEVEL_3", 4 to "EXTERNAL")[level] ?: "代碼 $level"
    private val capabilities = mapOf(0 to "向後相容", 1 to "手動感測器", 2 to "手動後處理", 3 to "RAW", 4 to "私有重處理", 5 to "讀取感測器設定", 6 to "突發拍攝", 7 to "YUV 重處理", 8 to "深度輸出", 9 to "受限高速錄影", 10 to "Motion tracking", 11 to "邏輯多鏡頭", 12 to "單色", 13 to "安全影像", 14 to "系統專用", 15 to "離線處理", 16 to "超高解析度感測器", 17 to "重混相", 18 to "10-bit 動態範圍", 19 to "串流使用情境", 20 to "色彩空間設定")
}
