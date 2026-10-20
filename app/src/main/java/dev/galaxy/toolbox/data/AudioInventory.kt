package dev.galaxy.toolbox.data

import android.media.AudioDeviceInfo
import dev.galaxy.toolbox.model.HardwareCategory
import dev.galaxy.toolbox.model.Reading

internal data class AudioPort(val id: Int, val type: Int, val input: Boolean, val output: Boolean)
internal data class AudioMic(val description: String, val type: Int, val group: Int, val index: Int)

/** Counts the API's objects, not loudspeaker drivers, channel counts or microphone holes. */
internal object AudioInventory {
    fun summary(ports: List<AudioPort>?, microphones: List<AudioMic>?, micStatus: String): List<Reading> = buildList {
        fun row(label: String, count: Int?, source: String, detail: String) = add(Reading(label, count?.toString(), source, detail, HardwareCategory.AUDIO, "音訊數量摘要"))
        val p = ports?.distinctBy { Triple(it.id, it.type, it.input to it.output) }
        val api = "AudioManager.getDevices · 依 ID／類型去重"
        val note = "目前系統列出的音訊端點，不是實體喇叭數；聲道數也不代表實體單體數。聽筒與安全音量路徑分列，避免加總成喇叭顆數。"
        row("內建喇叭端點", p?.count { it.output && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }, api, note)
        row("聽筒端點", p?.count { it.output && it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }, api, note)
        if (p?.any { it.output && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE } == true)
            row("安全音量喇叭端點", p.count { it.output && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE }, api, note)
        val m = microphones?.filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val identities = m?.map { mic -> mic.description.takeIf { it.isNotBlank() } ?: if (mic.group >= 0 && mic.index >= 0) "group:${mic.group}/index:${mic.index}" else null }
        val count = identities?.takeIf { it.isNotEmpty() && it.all { id -> id != null } }?.distinct()?.size
        row("內建麥克風（系統列出）", count, "AudioManager.getMicrophones · Android 9+ · MicrophoneInfo.description / type", micStatus + "；以系統列出的唯一麥克風特徵計數，韌體可能未公開全部硬體，不代表拆機清點。")
        row("內建麥克風輸入端點", p?.count { it.input && it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }, api, "音訊輸入端點可連到麥克風陣列；與 MicrophoneInfo 特徵清單分開，不以聲道數推算麥克風顆數。")
        row("全部輸入端點", p?.count { it.input }, api, "包含系統、內建與目前外接路徑，非實體裝置總數。")
        row("全部輸出端點", p?.count { it.output }, api, "包含系統、內建與目前外接路徑，非實體裝置總數。")
    }
}
