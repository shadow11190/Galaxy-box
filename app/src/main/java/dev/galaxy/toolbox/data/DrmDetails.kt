package dev.galaxy.toolbox.data

import android.media.MediaDrm
import android.os.Build
import dev.galaxy.toolbox.model.HardwareCategory
import dev.galaxy.toolbox.model.Reading
import java.util.UUID

/** Public DRM capabilities only. Never opens a playback session or reads device identifiers. */
object DrmDetails {
    private val widevine = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    private const val group = "受保護影片播放 · Widevine"

    fun read(): List<Reading> {
        val supported = runCatching { MediaDrm.isCryptoSchemeSupported(widevine) }.getOrDefault(false)
        if (!supported) return listOf(row("Widevine", "系統未宣告支援", "MediaDrm.isCryptoSchemeSupported", "僅代表這個 DRM 方案的公開支援查詢結果。"))
        val drm = runCatching { MediaDrm(widevine) }.getOrElse {
            return listOf(row("Widevine", null, "MediaDrm(Widevine)", CameraDetails.failure(it)))
        }
        return try {
            buildList {
                add(row("Widevine", "系統宣告支援", "MediaDrm.isCryptoSchemeSupported", "不代表任何串流服務保證提供特定畫質。"))
                fun property(label: String, key: String, detail: String) {
                    val value = runCatching { drm.getPropertyString(key) }.getOrNull()?.let(::cleanText)
                    if (value != null) add(row(label, value, "MediaDrm.getPropertyString($key)", detail))
                }
                property("DRM 廠商", MediaDrm.PROPERTY_VENDOR, "DRM 插件回報，非手機或面板製造商。")
                property("DRM 版本", MediaDrm.PROPERTY_VERSION, "DRM 插件版本。")
                property("Widevine 安全等級", "securityLevel", "Widevine 插件自訂欄位原文；不是 Android 標準安全等級 API，也不保證串流平台畫質。")
                if (Build.VERSION.SDK_INT >= 28) {
                    runCatching { drm.maxHdcpLevel }.getOrNull()?.let { level ->
                        add(row("HDCP 最高宣告等級", hdcpLabel(level), "MediaDrm.getMaxHdcpLevel", "裝置可用輸出介面的最高宣告能力，非目前連線或播放時的等級。"))
                    }
                    runCatching { drm.connectedHdcpLevel }.getOrNull()?.let { level ->
                        add(row("HDCP 目前連線等級", hdcpLabel(level), "MediaDrm.getConnectedHdcpLevel", "僅反映目前連線；沒有外接接收端時可能為未知或無。"))
                    }
                }
            }
        } finally {
            runCatching { drm.release() }
        }
    }

    internal fun cleanText(value: String): String? = value.trim().takeIf {
        it.length in 1..80 && it.all { ch -> !Character.isISOControl(ch) }
    }

    internal fun hdcpLabel(level: Int): String = when (level) {
        MediaDrm.HDCP_LEVEL_UNKNOWN -> "未知"
        MediaDrm.HDCP_NONE -> "無"
        MediaDrm.HDCP_V1 -> "1.0"
        MediaDrm.HDCP_V2 -> "2.0"
        MediaDrm.HDCP_V2_1 -> "2.1"
        MediaDrm.HDCP_V2_2 -> "2.2"
        MediaDrm.HDCP_V2_3 -> "2.3"
        MediaDrm.HDCP_NO_DIGITAL_OUTPUT -> "無數位輸出"
        else -> "原始代碼 $level"
    }

    private fun row(label: String, value: String?, source: String, detail: String) =
        Reading(label, value, source, detail, HardwareCategory.CODECS, group)
}
