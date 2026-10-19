package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import java.util.Locale

/** Rules match driver names and source evidence, never thermal_zone numbers. Overrides require a documented exact model/source match. */
data class VerifiedTemperatureRule(val model: String, val rawName: String, val path: String, val source: TemperatureSource,
    val group: TemperatureGroup, val unit: TemperatureUnit, val evidence: String, val priority: Int = 0)
class TemperatureClassifier(private val verified: List<VerifiedTemperatureRule> = emptyList()) {
    data class Result(val classification: TemperatureClassification, val unit: TemperatureUnit)
    fun classify(name: String, path: String, source: TemperatureSource, unit: TemperatureUnit,
        metadata: Map<String, String> = emptyMap(), model: String = ""): Result {
        fun result(kind: TemperatureKind, group: TemperatureGroup, title: String, confidence: EvidenceConfidence,
            basis: String, rule: String, eligible: Boolean = false, priority: Int = 100, convertedUnit: TemperatureUnit = unit) =
            Result(TemperatureClassification(kind, group, title, confidence, basis, rule, eligible, priority), convertedUnit)
        val key = name.lowercase(Locale.ROOT).trim()
        if (source == TemperatureSource.DIAGNOSTIC) return result(TemperatureKind.UNKNOWN, TemperatureGroup.UNKNOWN, name,
            EvidenceConfidence.UNKNOWN, "來源探索／讀取診斷，沒有量測語意。", "diagnostic", convertedUnit = TemperatureUnit.RAW)
        if (Regex(".*bcl[-_]lvl[0-2](?:$|[-_].*)").matches(key)) return result(
            if (source == TemperatureSource.TRIP_POINT) TemperatureKind.THRESHOLD else TemperatureKind.PROTECTION_STATE,
            TemperatureGroup.PROTECTION, name, EvidenceConfidence.LOW,
            "bcl-lvl 名稱是保護狀態類線索，尚未驗證狀態語意及物理單位；只顯示原始值。", "bcl-candidate", convertedUnit = TemperatureUnit.RAW)
        if (source == TemperatureSource.TRIP_POINT || Regex("trip_point_\\d+_temp").matches(path.substringAfterLast('/'))) return result(
            TemperatureKind.THRESHOLD, TemperatureGroup.PROTECTION, "$name · 門檻", EvidenceConfidence.HIGH,
            "實際來源是 trip_point_*_temp，屬核心介面門檻欄位，不是當前溫度。", "trip-point-path")
        if (Regex(".*cpu[-_]hw[-_]trip[-_][01].*").matches(key) || key.contains("hw-trip") || key.contains("hw_trip")) return result(
            TemperatureKind.UNKNOWN, TemperatureGroup.UNKNOWN, "$name · 用途待確認", EvidenceConfidence.LOW,
            "目前讀的是 ${path.substringAfterLast('/')}；hw-trip 名稱不足以判定量測或門檻。核對同區 trip_point 與驅動設定前不轉作即時溫度。", "ambiguous-hw-trip", convertedUnit = TemperatureUnit.RAW)
        val match = verified.firstOrNull { it.model == model && it.rawName == name && it.path == path && it.source == source && it.evidence.isNotBlank() }
        if (match != null) return result(TemperatureKind.MEASUREMENT, match.group, name, EvidenceConfidence.HIGH,
            match.evidence, "verified-model-source", true, match.priority, match.unit)
        if (source == TemperatureSource.BATTERY_API) return result(TemperatureKind.MEASUREMENT, TemperatureGroup.BATTERY, "電池",
            EvidenceConfidence.HIGH, "Android ACTION_BATTERY_CHANGED 的 EXTRA_TEMPERATURE，來源定義為十分之一攝氏度。", "android-battery", true, 0, TemperatureUnit.DECI_C)
        val cpu = Regex("(?:cpu(?:[-_]?(?:therm|thermal|temperature|temp|\\d+))*|cpuss(?:[-_]?\\d+)?|cpu[-_]\\d+[-_]\\d+(?:[-_]\\d+)?|cpullc[-_]\\d+[-_]\\d+|cpu\\d+[-_](?:silver|gold|gold-plus|prime))(?:[-_]usr|[-_]thermal)?").matches(key)
        if (cpu && source in listOf(TemperatureSource.THERMAL, TemperatureSource.HWMON) && unit != TemperatureUnit.RAW) return result(
            TemperatureKind.MEASUREMENT, TemperatureGroup.CPU, "$name · CPU 相關測點", EvidenceConfidence.MEDIUM,
            "驅動標籤符合 CPU 測點規則且來源有溫度單位定義；不確認實體位置，也不代表每核心都有感測器。", "cpu-driver-label", true, 20)
        if (Regex("soc(?:[-_]?(?:therm|thermal|temp|temperature|sensor|die|\\d+))*(?:[-_]usr)?").matches(key) &&
            source in listOf(TemperatureSource.THERMAL, TemperatureSource.HWMON) && unit != TemperatureUnit.RAW) return result(
            TemperatureKind.MEASUREMENT, TemperatureGroup.SOC, "$name · SoC 相關測點", EvidenceConfidence.MEDIUM,
            "來源有溫度單位定義，驅動明確標示 SoC；不以 CPU、GPU、電池或其他測點替代，也不表示整顆晶片的唯一溫度。", "soc-driver-label", true, 20)
        val tsensLabel = Regex("tsens(?:[-_](?:tz|sensor|\\d+)|\\d+)*").matches(key)
        val compatible = metadata["compatible"].orEmpty().lowercase(Locale.ROOT).replace('\u0000', ' ').split(Regex("\\s+"))
        val tsensBinding = compatible.any { it in setOf("qcom,tsens-v0_1", "qcom,tsens-v1", "qcom,tsens-v2") } ||
            metadata["driver"].orEmpty().substringAfterLast('/').lowercase(Locale.ROOT) in setOf("qcom-tsens", "qcom_tsens")
        if (tsensLabel && tsensBinding && source in listOf(TemperatureSource.THERMAL, TemperatureSource.HWMON) && unit == TemperatureUnit.MILLI_C)
            return result(TemperatureKind.MEASUREMENT, TemperatureGroup.SOC, "$name · SoC 相關測點", EvidenceConfidence.MEDIUM,
                "此來源的本機 compatible／driver 與 TSENS 標籤共同符合 Qualcomm SoC 感測器定義；thermal／hwmon 介面輸出毫攝氏度。不由編號推測核心或實體位置。依據：$QCOM_TSENS_REFERENCE",
                "soc-local-tsens-binding", true, 30)
        val qcomVendor = metadata["SoC manufacturer"].orEmpty().trim().uppercase(Locale.ROOT) in setOf("QUALCOMM", "QTI") ||
            metadata["晶片代碼對照廠商"].orEmpty().equals("Qualcomm", true)
        if (model.uppercase(Locale.ROOT) in S26_ULTRA_MODELS && qcomVendor && key == "video" &&
            source == TemperatureSource.THERMAL && unit == TemperatureUnit.MILLI_C) return result(
            TemperatureKind.MEASUREMENT, TemperatureGroup.SOC, "video · SoC 影音相關測點", EvidenceConfidence.LOW,
            "使用者 S26 Ultra 的 DevCheck 6.60 畫面（2026-10-19）明確顯示 SoC (video)，APK 分類流程亦將 video 歸入 SoC。此為該機型系列的相容分類，來源仍是 thermal 的 video 測點，依 ABI 換算；未驗證所有區域型號／韌體，不代表整顆晶片或晶片中心的物理溫度。",
            "soc-s26-video-category", true, 40)
        if (key.contains("gpu") || key.contains("g3d") || key.contains("kgsl") || source == TemperatureSource.KGSL) {
            val binding = listOfNotNull(metadata["driver"], metadata["compatible"]).joinToString(" ").lowercase(Locale.ROOT)
            val mappedNames = metadata["gpu thermal-zone names"].orEmpty().split(' ').filter { it.isNotBlank() }
            val localMapped = name in mappedNames && metadata["gpu compatible"].orEmpty().contains("qcom,kgsl-3d")
            val documentedQcom = qcomVendor && Regex("gpuss-\\d{1,2}(?:-usr|-thermal)?").matches(key)
            val kgslContract = source == TemperatureSource.KGSL && GpuPaths.isKgslTemp(metadata["KGSL contract path"] ?: path) &&
                metadata["compatible"].orEmpty().split(' ').any { it in setOf("qcom,kgsl-3d0", "qcom,kgsl-3d") }
            if (kgslContract) return result(TemperatureKind.MEASUREMENT, TemperatureGroup.GPU, "$name · GPU 相關測點", EvidenceConfidence.MEDIUM,
                "精確 KGSL temp 路徑與本機 qcom,kgsl-3d compatible 共同符合公開驅動契約（毫攝氏度）；不是只憑 KGSL 名稱。尚未實測本機韌體或物理位置。依據：${HwmonProbe.KGSL_REFERENCE}",
                "gpu-kgsl-contract", true, 35, TemperatureUnit.MILLI_C)
            val corroborated = source in listOf(TemperatureSource.THERMAL, TemperatureSource.HWMON) && unit != TemperatureUnit.RAW &&
                (listOf("kgsl", "mali", "adreno", "gpu", "g3d").any { binding.contains(it) } || localMapped || documentedQcom)
            return result(if (corroborated) TemperatureKind.MEASUREMENT else TemperatureKind.UNKNOWN, TemperatureGroup.GPU,
                if (corroborated) "$name · GPU 相關測點" else "$name · GPU 來源待確認",
                if (corroborated) EvidenceConfidence.MEDIUM else EvidenceConfidence.LOW,
                if (localMapped) "本機 GPU compatible 與 qcom,tzone-names 明確連結此 thermal 測點；依 thermal ABI 換算，未確認物理位置。"
                else if (documentedQcom) "本機回報 Qualcomm SoC，GPUSS 數字測點符合公開 GPU 熱區命名及來源單位；使用者的 S26 Ultra 畫面亦顯示 gpuss-0…10。中可信度的類型辨識，不是本機韌體或物理位置的實測確認。依據：$QCOM_GPU_REFERENCE"
                else if (corroborated) "驅動標籤及可讀 driver / compatible 綁定共同指向 GPU；依來源定義換算，仍未確認物理位置。"
                else "GPU／KGSL 名稱及參考介面僅為線索；缺少本機驅動綁定或型號來源驗證，排除摘要。",
                if (localMapped) "gpu-local-dt" else if (documentedQcom) "gpu-qcom-documented-label" else if (corroborated) "gpu-binding" else "gpu-unconfirmed",
                corroborated, if (localMapped) 10 else if (documentedQcom) 25 else if (source == TemperatureSource.THERMAL) 20 else 30,
                if (corroborated) unit else TemperatureUnit.RAW)
        }
        if (Regex("(?:batt(?:ery)?|bat)(?:[-_].*)?").matches(key)) return result(TemperatureKind.MEASUREMENT, TemperatureGroup.BATTERY,
            "$name · 電池相關測點", EvidenceConfidence.MEDIUM, "僅由驅動標籤辨識；詳細頁保留，摘要不替代或平均 Android 電池來源。", "battery-secondary")
        val related = listOf("camera" to "相機相關測點", "video" to "影音相關測點", "ddr" to "DDR 相關測點", "skin" to "機身相關測點", "usb" to "USB 相關測點")
            .firstOrNull { key.contains(it.first) }
        if (related != null) return result(TemperatureKind.MEASUREMENT, TemperatureGroup.OTHER, "${related.second} · $name", EvidenceConfidence.MEDIUM,
            "驅動名稱含 ${related.first}，僅表示相關測點；未確認相機表面、記憶體顆粒或其他實體位置。", "related-driver-label")
        return result(TemperatureKind.UNKNOWN, TemperatureGroup.UNKNOWN, name, EvidenceConfidence.UNKNOWN,
            "名稱及可讀設定不足以辨識用途；保留原文與原始值，不納入溫度摘要。", "unknown", convertedUnit = TemperatureUnit.RAW)
    }
    companion object {
        // Exact catalog models, not a thermal_zone-number mapping. Only this series has user source evidence.
        private val S26_ULTRA_MODELS = setOf("SM-S9480", "SM-S948B", "SM-S948C", "SM-S948N", "SM-S948Q", "SM-S948U", "SM-S948U1", "SM-S948W", "SM-S948Z")
        const val QCOM_TSENS_REFERENCE = "https://github.com/torvalds/linux/blob/v6.12/Documentation/devicetree/bindings/thermal/qcom-tsens.yaml"
        const val QCOM_GPU_REFERENCE = "https://android.googlesource.com/kernel/common/+/refs/tags/android16-6.12-2025-09_r1/arch/arm64/boot/dts/qcom/sm8550.dtsi"
    }
}
