package dev.galaxy.toolbox.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.galaxy.toolbox.data.A2dpDeviceState
import dev.galaxy.toolbox.data.BluetoothAudioInspector
import dev.galaxy.toolbox.data.BluetoothAudioPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
internal fun BluetoothAudioCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var ports by remember { mutableStateOf<List<BluetoothAudioPort>?>(null) }
    var portError by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) {
        while (foreground && isActive) {
            val result = withContext(Dispatchers.IO) { runCatching { BluetoothAudioInspector.ports(context) } }
            ports = result.getOrNull()
            portError = result.isFailure
            delay(2000)
        }
    }
    if (Build.VERSION.SDK_INT >= 31) {
        PermissionGate(listOf(Manifest.permission.BLUETOOTH_CONNECT), "藍牙播放狀態") {
            A2dpPlaybackStatus(foreground)
        }
    } else A2dpPlaybackStatus(foreground)

    InfoCard("藍牙端點詳細資料") {
        GalaxyText("目前系統音訊端點", style = GalaxyTypography.titleSmall)
        when {
            portError -> GalaxyText("無法取得端點資訊", color = Muted)
            ports == null -> GalaxyText("等待讀取", color = Muted)
            ports!!.isEmpty() -> GalaxyText("未偵測到藍牙輸出端點", color = Muted)
            else -> ports!!.forEach { port ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GalaxyText("${port.type} · ${port.name}")
                    GalaxyText("取樣率：${port.sampleRates} · 聲道：${port.channelCounts}", color = Muted,
                        style = GalaxyTypography.bodySmall)
                    GalaxyText("端點接受格式：${port.acceptedFormats}", color = Muted,
                        style = GalaxyTypography.bodySmall)
                }
            }
        }
        GalaxyText("端點格式是 Android 公布的輸出能力，不是藍牙連線正在使用的 SBC、AAC、LDAC 等傳輸編碼器，也不是即時位元率。",
            color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyTextButton(onClick = {
            settingsError = runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }.isFailure
        }) { GalaxyText("開啟開發人員選項查看藍牙編碼設定") }
        if (settingsError) GalaxyText("這台裝置未提供可開啟的開發人員選項頁面。", color = Muted)
    }
}

@SuppressLint("MissingPermission") // PermissionGate owns Android 12+ runtime permission.
@Composable
private fun A2dpPlaybackStatus(foreground: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adapter = remember(context) { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var profile by remember { mutableStateOf<BluetoothA2dp?>(null) }
    var devices by remember { mutableStateOf<List<A2dpDeviceState>?>(null) }
    var readError by remember { mutableStateOf(false) }
    DisposableEffect(adapter, lifecycle) {
        var active = false
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                if (profileId != BluetoothProfile.A2DP) return
                if (active) profile = proxy as? BluetoothA2dp
                else runCatching { adapter?.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
            }
            override fun onServiceDisconnected(profileId: Int) {
                if (profileId == BluetoothProfile.A2DP) profile = null
            }
        }
        fun start() {
            if (active) return
            active = true
            if (adapter != null) {
                readError = runCatching {
                    if (!adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)) {
                        error("A2DP 服務無法連線")
                    }
                }.isFailure
            }
        }
        fun stop() {
            if (!active) return
            active = false
            profile?.let { runCatching { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) } }
            profile = null
            devices = null
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) start()
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    LaunchedEffect(profile, foreground) {
        val current = profile
        while (foreground && current != null && isActive) {
            val result = withContext(Dispatchers.IO) {
                runCatching { BluetoothAudioInspector.a2dpDevices(current) }
            }
            devices = result.getOrNull()
            readError = result.isFailure
            delay(2000)
        }
    }
    when {
        adapter == null -> InfoCard("藍牙耳機") {
            GalaxyText("此裝置沒有藍牙介面", color = Muted)
        }
        readError -> InfoCard("藍牙耳機") {
            GalaxyText("A2DP 狀態無法取得", color = Muted)
        }
        devices == null -> InfoCard("藍牙耳機") {
            GalaxyText("等待藍牙服務回報", color = Muted)
        }
        devices!!.isEmpty() -> InfoCard("藍牙耳機") {
            GalaxyText("沒有 A2DP 已連線裝置", color = Muted)
        }
        Build.VERSION.SDK_INT >= 33 && profile != null -> devices!!.forEach { device ->
            key(device.device.address) {
                A2dpCodecPanel(profile!!, device.device, device.name, device.playing, foreground)
            }
        }
        else -> InfoCard("藍牙耳機") {
            devices!!.forEach { device ->
                GalaxyText("${device.name} · ${if (device.playing) "A2DP 正在播放" else "已連線，未回報播放"}")
            }
            if (Build.VERSION.SDK_INT < 33) {
                GalaxyText("目前編碼診斷需 Android 13 以上。", color = Muted,
                    style = GalaxyTypography.bodySmall)
            } else {
                GalaxyText("等待 A2DP 服務回報編碼資訊", color = Muted,
                    style = GalaxyTypography.bodySmall)
            }
            GalaxyText("播放旗標由 Android 回報，無法據此判定實際編碼、位元率或其他 App 的音訊內容。",
                color = Muted, style = GalaxyTypography.bodySmall)
        }
    }
}
