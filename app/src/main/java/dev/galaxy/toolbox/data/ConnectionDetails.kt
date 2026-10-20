package dev.galaxy.toolbox.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities as N
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import dev.galaxy.toolbox.model.HardwareCategory as H
import dev.galaxy.toolbox.model.Reading
import java.util.Locale

object ConnectionDetails {
    fun network(context: Context): List<Reading> = buildList {
        fun row(label: String, value: String?, source: String, group: String = "目前連線", detail: String = "手動讀取的本機快照。") {
            if (value != null) add(Reading(label, value, source, detail, H.NETWORK, group))
        }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val net = cm?.activeNetwork
        val cap = net?.let { cm.getNetworkCapabilities(it) }
        row("目前網路", cap?.let { c -> buildList {
            if (c.hasTransport(N.TRANSPORT_WIFI)) add("Wi-Fi"); if (c.hasTransport(N.TRANSPORT_CELLULAR)) add("行動網路")
            if (c.hasTransport(N.TRANSPORT_ETHERNET)) add("乙太網路"); if (c.hasTransport(N.TRANSPORT_VPN)) add("VPN")
            if (c.hasTransport(N.TRANSPORT_BLUETOOTH)) add("藍牙網路")
        }.joinToString(" / ").ifBlank { "其他傳輸" } } ?: "尚無預設網路", "ConnectivityManager.activeNetwork / NetworkCapabilities")
        cap?.let {
            row("網際網路狀態", if (it.hasCapability(N.NET_CAPABILITY_VALIDATED)) "系統已驗證連線" else "尚未驗證網際網路", "NetworkCapabilities.NET_CAPABILITY_VALIDATED")
            row("計量網路", if (cm.isActiveNetworkMetered) "是" else "否", "ConnectivityManager.isActiveNetworkMetered")
            row("回報下行頻寬", it.linkDownstreamBandwidthKbps.takeIf { n -> n > 0 }?.let { n -> "$n kbit/s" }, "NetworkCapabilities.linkDownstreamBandwidthKbps", detail = "系統回報的估計能力，不是下載速度實測。")
            row("回報上行頻寬", it.linkUpstreamBandwidthKbps.takeIf { n -> n > 0 }?.let { n -> "$n kbit/s" }, "NetworkCapabilities.linkUpstreamBandwidthKbps", detail = "系統回報的估計能力，不是上傳速度實測。")
        }
        net?.let { cm.getLinkProperties(it) }?.let { link ->
            row("網路介面", link.interfaceName, "LinkProperties.interfaceName", "本機介面")
            row("IP 位址", link.linkAddresses.joinToString("\n") { "${it.address.hostAddress}/${it.prefixLength}" }.ifBlank { null }, "LinkProperties.linkAddresses", "本機介面")
            row("DNS", link.dnsServers.mapNotNull { it.hostAddress }.joinToString("\n").ifBlank { null }, "LinkProperties.dnsServers", "本機介面")
            if (Build.VERSION.SDK_INT >= 29) row("MTU", link.mtu.takeIf { it > 0 }?.let { "$it B" }, "LinkProperties.mtu", "本機介面")
        }
        if (cap?.hasTransport(N.TRANSPORT_WIFI) == true) runCatching {
            @Suppress("DEPRECATION") val wifi = context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
            row("Wi-Fi 連線速率", wifi?.linkSpeed?.takeIf { it > 0 }?.let { "$it Mbit/s" }, "WifiInfo.linkSpeed", "Wi-Fi 連線", "無線鏈路回報值，不是網際網路速度測試。")
            row("Wi-Fi 頻率", wifi?.frequency?.takeIf { it > 0 }?.let { "$it MHz" }, "WifiInfo.frequency", "Wi-Fi 連線")
            if (Build.VERSION.SDK_INT >= 29) {
                row("Wi-Fi 接收鏈路", wifi?.rxLinkSpeedMbps?.takeIf { it > 0 }?.let { "$it Mbit/s" }, "WifiInfo.rxLinkSpeedMbps", "Wi-Fi 連線")
                row("Wi-Fi 傳送鏈路", wifi?.txLinkSpeedMbps?.takeIf { it > 0 }?.let { "$it Mbit/s" }, "WifiInfo.txLinkSpeedMbps", "Wi-Fi 連線")
            }
        }
        row("累計接收", traffic(TrafficStats.getTotalRxBytes()), "TrafficStats.getTotalRxBytes", "自本次開機累計", "所有網路介面的系統可讀累計；非本日、帳單或電信商統計。")
        row("累計傳送", traffic(TrafficStats.getTotalTxBytes()), "TrafficStats.getTotalTxBytes", "自本次開機累計", "所有網路介面的系統可讀累計；非本日、帳單或電信商統計。")
    }
    internal fun traffic(bytes: Long): String? = bytes.takeIf { it >= 0 }?.let { String.format(Locale.ROOT, "%.2f MiB · %,d B", it / 1048576.0, it) }
    fun audio(context: Context): List<Reading> = buildList {
        val am = context.getSystemService(AudioManager::class.java) ?: return@buildList
        val devices = runCatching { am.getDevices(AudioManager.GET_DEVICES_INPUTS or AudioManager.GET_DEVICES_OUTPUTS).toList() }
        val microphones = if (Build.VERSION.SDK_INT >= 28) runCatching { am.microphones } else null
        val micStatus = when (val error = microphones?.exceptionOrNull()) {
            is SecurityException -> "權限不足"
            null -> if (Build.VERSION.SDK_INT < 28) "此 Android 版本不支援" else if (microphones?.getOrNull().isNullOrEmpty()) "系統未提供麥克風清單" else "系統公開特徵清單"
            else -> "無法取得麥克風清單（${error.javaClass.simpleName}）"
        }
        addAll(AudioInventory.summary(devices.getOrNull()?.map { AudioPort(it.id, it.type, it.isSource, it.isSink) },
            if (Build.VERSION.SDK_INT >= 28) microphones?.getOrNull()?.map { AudioMic(it.description.orEmpty(), it.type, it.group, it.indexInTheGroup) } else null, micStatus))
        for (d in devices.getOrDefault(emptyList())) {
            val group = "${if (d.isSource) "輸入" else "輸出"} · ${audioType(d.type)} · ID ${d.id}"
            fun row(label: String, value: String?, source: String) {
                if (value != null) add(Reading(label, value, source, "公開音訊裝置能力快照；非實際錄音或播放測試。", H.AUDIO, group))
            }
            row("裝置名稱", d.productName?.toString()?.ifBlank { null }, "AudioDeviceInfo.productName")
            row("類型", "${audioType(d.type)} · ${d.type}", "AudioDeviceInfo.type")
            row("取樣率", d.sampleRates.takeIf { it.isNotEmpty() }?.joinToString { if (it == 0) "可變" else "$it Hz" }, "AudioDeviceInfo.sampleRates")
            row("聲道數", d.channelCounts.takeIf { it.isNotEmpty() }?.joinToString(), "AudioDeviceInfo.channelCounts")
            row("編碼格式原始碼", d.encodings.takeIf { it.isNotEmpty() }?.joinToString(), "AudioDeviceInfo.encodings")
        }
    }
    fun audioType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "內建揚聲器"; AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "聽筒"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "內建揚聲器（安全音量路徑）"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "內建麥克風"; AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有線耳機"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有線耳麥"; AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 耳麥"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 音訊"; AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "藍牙 A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "藍牙 SCO"; AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        26 -> "藍牙 LE 耳麥"; 27 -> "藍牙 LE 揚聲器"; else -> "其他音訊裝置"
    }
}
