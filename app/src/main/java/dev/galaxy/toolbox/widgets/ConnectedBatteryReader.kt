package dev.galaxy.toolbox.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ConnectedBattery(val name: String, val percent: Int?, val kind: Kind) {
    enum class Kind { PHONE, HEADPHONES, WATCH, ACCESSORY }
}

/** A short, permission-gated snapshot. Unknown remote battery levels stay unknown. */
internal object ConnectedBatteryReader {
    data class Result(val devices: List<ConnectedBattery>, val status: String?)
    private data class Cache(val sampledAt: Long, val result: Result)
    @Volatile private var cache: Cache? = null

    fun read(context: Context, force: Boolean = false): Result {
        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return Result(emptyList(), "需允許藍牙裝置權限")
        val now = SystemClock.elapsedRealtime()
        cache?.takeIf { !force && now - it.sampledAt in 0..120_000 }?.let { return it.result }
        val result = runCatching { readGranted(context) }.getOrElse { Result(emptyList(), "藍牙資料無法讀取") }
        cache = Cache(now, result)
        return result
    }

    @SuppressLint("MissingPermission") // read() checks BLUETOOTH_CONNECT on Android 12+.
    private fun readGranted(context: Context): Result {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return Result(emptyList(), "不支援藍牙")
        val adapter = manager.adapter ?: return Result(emptyList(), "不支援藍牙")
        if (!adapter.isEnabled) return Result(emptyList(), "藍牙已關閉")
        val connected = linkedMapOf<String, BluetoothDevice>()
        fun add(device: BluetoothDevice) { connected[device.address] = device }

        // GATT covers many wearables. A2DP/HEADSET cover active audio accessories.
        runCatching { manager.getConnectedDevices(BluetoothProfile.GATT).forEach(::add) }
        val routed = context.getSystemService(AudioManager::class.java)
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            .filter { it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26, 27) }
        val bonded = runCatching { adapter.bondedDevices }.getOrDefault(emptySet())
        val routedAddresses = if (Build.VERSION.SDK_INT >= 28)
            routed.mapNotNull { runCatching { it.address }.getOrNull()?.takeIf(String::isNotBlank) }.toSet()
        else emptySet()
        bonded.filter { it.address in routedAddresses }.forEach(::add)

        val lock = Any()
        val remaining = CountDownLatch(2)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                try { synchronized(lock) { proxy.connectedDevices.forEach(::add) } }
                finally { adapter.closeProfileProxy(profile, proxy); remaining.countDown() }
            }
            override fun onServiceDisconnected(profile: Int) { remaining.countDown() }
        }
        for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            if (!runCatching { adapter.getProfileProxy(context, listener, profile) }.getOrDefault(false)) remaining.countDown()
        }
        remaining.await(700, TimeUnit.MILLISECONDS)
        val devices = synchronized(lock) { connected.values.toList() }
        val sorted = devices.map { device ->
            val name = device.name?.trim()?.takeIf(String::isNotBlank) ?: "藍牙裝置"
            val lower = name.lowercase()
            val kind = when {
                listOf("watch", "band", "手錶", "手环", "手環").any(lower::contains) -> ConnectedBattery.Kind.WATCH
                listOf("buds", "ear", "head", "耳機", "耳麦", "耳麥").any(lower::contains) -> ConnectedBattery.Kind.HEADPHONES
                else -> ConnectedBattery.Kind.ACCESSORY
            }
            val percent = runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int }
                .getOrNull()?.takeIf { it in 0..100 }
            ConnectedBattery(name, percent, kind)
        }.sortedWith(compareBy({ it.kind != ConnectedBattery.Kind.HEADPHONES }, { it.kind != ConnectedBattery.Kind.WATCH }, { it.name }))
        return Result(sorted, if (sorted.isEmpty()) "沒有已連線裝置" else null)
    }
}
