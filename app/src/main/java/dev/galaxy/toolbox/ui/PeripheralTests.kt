package dev.galaxy.toolbox.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.galaxy.toolbox.data.ConnectionDetails
import kotlinx.coroutines.delay
import java.util.Locale

@SuppressLint("MissingPermission") // Bluetooth content is gated by the runtime permission below.
@Composable internal fun BluetoothTest() {
    val context = LocalContext.current
    @Composable fun read() {
        var refresh by remember { mutableIntStateOf(0) }
        val state = remember(refresh) { runCatching {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) "系統未提供藍牙介面" else "藍牙 ${if (adapter.isEnabled) "已開啟" else "已關閉"} · 已配對 ${adapter.bondedDevices.size} 個裝置"
        }.getOrElse { "藍牙狀態無法取得（${it.javaClass.simpleName}）" } }
        Text(state)
        Text("檢查狀態與系統配對流程；不會自動掃描或連接其他裝置。", color = Muted)
        TextButton(onClick = { refresh++ }) { Text("重新讀取藍牙狀態") }
        Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }) { Text("開啟藍牙設定") }
    }
    if (Build.VERSION.SDK_INT >= 31) PermissionGate(listOf(Manifest.permission.BLUETOOTH_CONNECT), "藍牙連線資訊") { read() }
    else read()
}

@Composable internal fun HeadphoneTest() {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    val am = remember { context.getSystemService(AudioManager::class.java) }
    var devices by remember { mutableStateOf(emptyList<String>()) }
    DisposableEffect(owner, am) {
        fun read() {
            devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26, 27)
            }.map { "${ConnectionDetails.audioType(it.type)} · ${it.productName}" }
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { read() }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { read() }
        }
        fun start() { am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper())); read() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) start()
            if (event == Lifecycle.Event.ON_STOP) am.unregisterAudioDeviceCallback(callback)
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { owner.lifecycle.removeObserver(observer); am.unregisterAudioDeviceCallback(callback) }
    }
    Text("插入或移除耳機，觀察公開音訊裝置是否改變。")
    if (devices.isEmpty()) Text("目前未偵測到外接耳機", color = Muted) else devices.forEach { Text(it, color = Mint) }
    Text("USB／有線／藍牙依系統回報列出；未偵測到不等同插孔故障。", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable internal fun NfcTest() {
    val activity = LocalActivity.current
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var active by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("按開始後，將一般 NFC 標籤靠近手機感應區。") }
    DisposableEffect(active, activity, owner) {
        fun stop() { if (activity != null) runCatching { adapter?.disableReaderMode(activity) } }
        fun start() {
            if (!active || adapter == null || activity == null) return
            runCatching {
                check(adapter.isEnabled) { "NFC 尚未開啟" }
                adapter.enableReaderMode(activity, { tag ->
                    activity.runOnUiThread { message = "已讀到 NFC 標籤 · ${tag.techList.joinToString { it.substringAfterLast('.') }}" }
                }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
                message = "等待 NFC 標籤…"
            }.onFailure { message = "無法開始：${it.message ?: it.javaClass.simpleName}"; active = false }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) start()
            if (event == Lifecycle.Event.ON_PAUSE) { stop(); active = false }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { stop(); owner.lifecycle.removeObserver(observer) }
    }
    Text(message)
    Button(onClick = { active = !active }) { Text(if (active) "停止 NFC 檢查" else "開始 NFC 檢查") }
    Text("只偵測標籤與技術類型，不讀取標籤內容、不寫入，也不視為付款功能認證。", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@SuppressLint("MissingPermission") // Start checks granted coarse/fine permissions; no background request.
@Composable internal fun LocationAccuracyTest() {
    PermissionGate(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), "前景定位", any = true) {
        val context = LocalContext.current; val owner = LocalLifecycleOwner.current
        val manager = remember { context.getSystemService(LocationManager::class.java) }
        var running by remember { mutableStateOf(false) }
        var sample by remember { mutableStateOf<Location?>(null) }
        var message by remember { mutableStateOf("按開始後取得定位回報；不顯示或保存座標。") }
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
        LaunchedEffect(running) { if (running) { delay(20_000); running = false; if (sample == null) message = "時段內未取得定位，請確認定位設定或移至訊號較佳處。" } }
        LaunchedEffect(sample, owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (sample != null) { now = SystemClock.elapsedRealtimeNanos(); delay(1000) } } }
        DisposableEffect(running, owner) {
            val listener = object : LocationListener {
                @Suppress("OVERRIDE_DEPRECATION") override fun onLocationChanged(location: Location) { sample = Location(location); now = SystemClock.elapsedRealtimeNanos(); message = "已取得系統定位回報" }
                @Deprecated("Platform compatibility") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            fun stop() { runCatching { manager.removeUpdates(listener) } }
            if (running && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList()).filter {
                    it == LocationManager.NETWORK_PROVIDER || fine && it == LocationManager.GPS_PROVIDER
                }
                if (providers.isEmpty()) { message = "沒有可用的定位來源；可檢查定位開關與精確定位授權。"; running = false }
                else providers.forEach { provider -> runCatching { manager.requestLocationUpdates(provider, 1000, 0f, listener, Looper.getMainLooper()) }.onFailure { message = "無法取得定位：${it.javaClass.simpleName}" } }
            }
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { stop(); running = false } }
            owner.lifecycle.addObserver(observer); onDispose { stop(); owner.lifecycle.removeObserver(observer) }
        }
        Text(message)
        sample?.let { location ->
            if (location.hasAccuracy() && location.accuracy.isFinite() && location.accuracy >= 0) Text(String.format(Locale.ROOT, "系統估計水平誤差 ± %.1f m", location.accuracy), color = Mint)
            Text("來源 ${location.provider} · ${((now - location.elapsedRealtimeNanos).coerceAtLeast(0) / 1_000_000_000L)} 秒前", color = Muted)
        }
        Button(onClick = { sample = null; running = !running }) { Text(if (running) "停止定位" else "開始定位（最多 20 秒）") }
        Text("誤差為系統估計，不是與已知座標比較的實測精度。", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
