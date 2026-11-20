package dev.galaxy.toolbox.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.data.A2dpCodecAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CODEC_FRESH_MS = 12_000L
private data class CodecObservation(val status: BluetoothCodecStatus, val elapsedRealtime: Long)
private data class MediaVolume(val current: Int, val maximum: Int)

@RequiresApi(33)
@SuppressLint("MissingPermission") // BluetoothAudioCard gates BLUETOOTH_CONNECT.
@Composable
internal fun A2dpCodecPanel(
    profile: BluetoothA2dp,
    device: BluetoothDevice,
    deviceName: String,
    playing: Boolean,
    foreground: Boolean,
) {
    val context = LocalContext.current
    val address = remember(device) { device.address }
    val manager = remember(context) { context.getSystemService(CompanionDeviceManager::class.java) }
    val companionSupported = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
    }
    val scope = rememberCoroutineScope()
    var association by remember(address) { mutableStateOf<Boolean?>(null) }
    var observation by remember(address) { mutableStateOf<CodecObservation?>(null) }
    var readError by remember(address) { mutableStateOf<String?>(null) }
    var actionMessage by remember(address) { mutableStateOf<String?>(null) }
    var associationBusy by remember(address) { mutableStateOf(false) }
    var switchBusy by remember(address) { mutableStateOf(false) }
    var refresh by remember(address) { mutableIntStateOf(0) }
    var now by remember(address) { mutableStateOf(SystemClock.elapsedRealtime()) }
    var volume by remember(address) { mutableStateOf<MediaVolume?>(null) }
    var batteryLevel by remember(address) { mutableStateOf<Int?>(null) }
    var optionsExpanded by remember(address) { mutableStateOf(false) }

    val associationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        associationBusy = false
        actionMessage = if (result.resultCode == Activity.RESULT_OK)
            "已完成系統確認，正在核對耳機關聯" else "已取消耳機關聯"
        refresh++
    }

    LaunchedEffect(profile, address, foreground, refresh, companionSupported) {
        if (!foreground) return@LaunchedEffect
        while (isActive) {
            now = SystemClock.elapsedRealtime()
            volume = context.getSystemService(AudioManager::class.java)?.let { audio ->
                runCatching {
                    MediaVolume(audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                }.getOrNull()
            }?.takeIf { it.maximum > 0 }
            batteryLevel = withContext(Dispatchers.IO) {
                runCatching {
                    BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int
                }.getOrNull()?.takeIf { it in 0..100 }
            }
            if (companionSupported && manager != null) {
                val linked = withContext(Dispatchers.IO) {
                    runCatching { A2dpCodecAccess.hasAssociation(context, address) }
                }
                association = linked.getOrNull()
                if (linked.isFailure) readError = A2dpCodecAccess.failureMessage(linked.exceptionOrNull()!!)
                if (linked.getOrNull() == true) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { A2dpCodecAccess.read(profile, device) }
                    }
                    val status = result.getOrNull()
                    if (status != null) {
                        observation = CodecObservation(status, SystemClock.elapsedRealtime())
                        readError = null
                    } else {
                        readError = result.exceptionOrNull()?.let(A2dpCodecAccess::failureMessage)
                            ?: "已關聯，但系統沒有回報編碼狀態"
                    }
                }
            }
            delay(5_000)
        }
    }

    val observed = observation
    val fresh = association == true && readError == null && observed != null &&
        now - observed.elapsedRealtime <= CODEC_FRESH_MS
    val status = observed?.status?.takeIf { fresh }
    val current = status?.codecConfig
    val options = status?.codecsSelectableCapabilities.orEmpty()
        .distinctBy(A2dpCodecAccess::codecName)
    val local = status?.codecsLocalCapabilities.orEmpty()
        .distinctBy(A2dpCodecAccess::codecName)
    val currentName = current?.let(A2dpCodecAccess::codecName)
    val activeCapability = options.firstOrNull { A2dpCodecAccess.codecName(it) == currentName }
    var sampleRate by remember(address, currentName) {
        mutableIntStateOf(BluetoothCodecConfig.SAMPLE_RATE_NONE)
    }
    var bitDepth by remember(address, currentName) {
        mutableIntStateOf(BluetoothCodecConfig.BITS_PER_SAMPLE_NONE)
    }
    var channelMode by remember(address, currentName) {
        mutableIntStateOf(BluetoothCodecConfig.CHANNEL_MODE_NONE)
    }

    fun request(option: BluetoothCodecConfig, rate: Int = 0, depth: Int = 0, channel: Int = 0) {
        val snapshot = status ?: return
        if (switchBusy) return
        switchBusy = true
        actionMessage = "已提出偏好，正在讀回實際編碼"
        scope.launch {
            try {
                val requested = withContext(Dispatchers.IO) {
                    runCatching {
                        A2dpCodecAccess.request(context, profile, device, snapshot, option,
                            sampleRate = rate, bitsPerSample = depth, channelMode = channel)
                    }
                }
                if (requested.isFailure) {
                    actionMessage = A2dpCodecAccess.failureMessage(requested.exceptionOrNull()!!)
                    return@launch
                }
                delay(1_500)
                val reread = withContext(Dispatchers.IO) {
                    runCatching { A2dpCodecAccess.read(profile, device) }
                }
                val latest = reread.getOrNull()
                if (latest != null) {
                    observation = CodecObservation(latest, SystemClock.elapsedRealtime())
                    now = SystemClock.elapsedRealtime()
                    readError = null
                } else {
                    readError = reread.exceptionOrNull()?.let(A2dpCodecAccess::failureMessage)
                        ?: "偏好已送出，但系統未回報實際編碼"
                }
                val actual = latest?.codecConfig
                actionMessage = when {
                    actual == null -> "偏好已送出，無法確認是否套用"
                    A2dpCodecAccess.codecName(actual) != A2dpCodecAccess.codecName(option) ->
                        "系統目前仍使用 ${A2dpCodecAccess.describe(actual)}"
                    (rate != 0 && actual.sampleRate != rate) ||
                        (depth != 0 && actual.bitsPerSample != depth) ||
                        (channel != 0 && actual.channelMode != channel) ->
                        "編碼已選用；部分選項未套用。目前：${A2dpCodecAccess.describe(actual)}"
                    else -> "已讀回目前設定：${A2dpCodecAccess.describe(actual)}"
                }
            } finally {
                switchBusy = false
            }
        }
    }

    InfoCard("目前藍牙耳機") {
        GalaxyText(deviceName, style = GalaxyTypography.titleLarge, fontWeight = FontWeight.SemiBold)
        GalaxyText(if (playing) "A2DP 正在播放" else "已連線，未回報播放", color = Muted,
            style = GalaxyTypography.bodySmall)
        when {
            current != null -> {
                GalaxyText(A2dpCodecAccess.codecName(current), color = Mint,
                    style = GalaxyTypography.headlineMedium, fontWeight = FontWeight.SemiBold)
                A2dpCodecAccess.detail(current).takeIf { it.isNotBlank() }?.let {
                    GalaxyText(it, style = GalaxyTypography.bodyMedium)
                }
                GalaxyText("Android A2DP 回報 · ${((now - observed!!.elapsedRealtime) / 1_000).coerceAtLeast(0)} 秒前",
                    color = Muted, style = GalaxyTypography.bodySmall)
            }
            observed?.status?.codecConfig != null ->
                GalaxyText("目前編碼無法確認；上次讀值已過期", color = Muted)
            else -> GalaxyText("目前編碼尚未取得", color = Muted)
        }
        volume?.let { GalaxyText("系統媒體音量 ${it.current} / ${it.maximum}", color = Muted,
            style = GalaxyTypography.bodySmall) }
        batteryLevel?.let { GalaxyText("耳機電量（系統回報）$it%", color = Muted,
            style = GalaxyTypography.bodySmall) }
        readError?.let { GalaxyText(it, color = Muted, style = GalaxyTypography.bodySmall) }
    }

    if (!companionSupported || manager == null) {
        InfoCard("編碼控制") {
            GalaxyText("系統未提供伴侶裝置關聯，無法讀取或切換 A2DP 編碼。", color = Muted)
        }
    } else if (association != true) {
        InfoCard("編碼控制") {
            GalaxyText(if (association == null) "正在確認裝置關聯" else
                "先由系統關聯目前耳機，才能讀取編碼並提出切換偏好。",
                color = Muted, style = GalaxyTypography.bodySmall)
            if (association == false) {
                GalaxyTextButton(enabled = !associationBusy, onClick = {
                    associationBusy = true
                    actionMessage = null
                    val request = AssociationRequest.Builder()
                        .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
                        .setSingleDevice(true).build()
                    val callback = object : CompanionDeviceManager.Callback() {
                        private var launched = false
                        private fun launch(sender: IntentSender) {
                            if (launched) return
                            launched = true
                            runCatching {
                                associationLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }.onFailure {
                                associationBusy = false
                                actionMessage = A2dpCodecAccess.failureMessage(it)
                            }
                        }
                        override fun onAssociationPending(intentSender: IntentSender) = launch(intentSender)
                        override fun onDeviceFound(chooserLauncher: IntentSender) = launch(chooserLauncher)
                        override fun onAssociationCreated(associationInfo: android.companion.AssociationInfo) {
                            associationBusy = false
                            refresh++
                        }
                        override fun onFailure(error: CharSequence?) {
                            associationBusy = false
                            actionMessage = "系統未建立關聯：$error"
                        }
                    }
                    runCatching { manager.associate(request, context.mainExecutor, callback) }
                        .onFailure {
                            associationBusy = false
                            actionMessage = A2dpCodecAccess.failureMessage(it)
                        }
                }) { GalaxyText(if (associationBusy) "等待系統確認" else "關聯目前耳機") }
            }
            actionMessage?.let { GalaxyText(it, color = Muted, style = GalaxyTypography.bodySmall) }
        }
    } else {
        if (options.isNotEmpty()) {
            InfoCard("快速切換編碼") {
                GalaxyText("此連線可選編碼", color = Muted, style = GalaxyTypography.bodySmall)
                CodecChips(options.map { it to A2dpCodecAccess.codecName(it) }, currentName,
                    enabled = !switchBusy, onClick = {
                        if (A2dpCodecAccess.codecName(it) == currentName) optionsExpanded = true
                        else request(it)
                    })
                actionMessage?.let { GalaxyText(it, color = Muted, style = GalaxyTypography.bodySmall) }
            }
        } else if (actionMessage != null) {
            InfoCard("編碼狀態") {
                GalaxyText(actionMessage!!, color = Muted, style = GalaxyTypography.bodySmall)
            }
        }

        if (activeCapability != null) {
            InfoCard("編碼選項") {
                GalaxyTextButton(onClick = { optionsExpanded = !optionsExpanded }) {
                    GalaxyText(if (optionsExpanded) "收起取樣設定" else "調整取樣率、位元深度與聲道")
                }
                if (optionsExpanded) {
                    current?.let {
                        GalaxyText("目前實際設定：${A2dpCodecAccess.detail(it).ifBlank { "系統未列出" }}",
                            color = Muted, style = GalaxyTypography.bodySmall)
                    }
                    CodecValueChoices("取樣率", A2dpCodecAccess.sampleRateOptions(activeCapability),
                        sampleRate) { sampleRate = it }
                    CodecValueChoices("每個樣本的位元數", A2dpCodecAccess.bitDepthOptions(activeCapability),
                        bitDepth) { bitDepth = it }
                    CodecValueChoices("聲道模式", A2dpCodecAccess.channelModeOptions(activeCapability),
                        channelMode) { channelMode = it }
                    GalaxyTextButton(enabled = !switchBusy, onClick = {
                        request(activeCapability, sampleRate, bitDepth, channelMode)
                    }) { GalaxyText(if (switchBusy) "正在確認" else "套用選項") }
                    GalaxyText("系統可能忽略偏好；以套用後讀回的實際設定為準。", color = Muted,
                        style = GalaxyTypography.bodySmall)
                }
            }
        }

        if (options.isNotEmpty() || local.isNotEmpty()) {
            InfoCard("編碼支援") {
                if (options.isNotEmpty()) {
                    GalaxyText("此連線可選（手機與耳機共同支援）", color = Muted,
                        style = GalaxyTypography.bodySmall)
                    GalaxyText(options.joinToString(" · ", transform = A2dpCodecAccess::codecName))
                }
                if (local.isNotEmpty()) {
                    GalaxyText("本機支援", color = Muted, style = GalaxyTypography.bodySmall)
                    GalaxyText(local.joinToString(" · ", transform = A2dpCodecAccess::codecName))
                }
            }
        }
        InfoCard("連線設定") {
            GalaxyText("編碼切換僅提出偏好；部分系統可能限制存取或忽略請求。",
                color = Muted, style = GalaxyTypography.bodySmall)
            GalaxyTextButton(onClick = { refresh++ }) { GalaxyText("重新讀取") }
            GalaxyTextButton(onClick = {
                scope.launch {
                    val removed = withContext(Dispatchers.IO) {
                        runCatching { A2dpCodecAccess.removeAssociation(context, address) }
                    }
                    actionMessage = when {
                        removed.isFailure -> A2dpCodecAccess.failureMessage(removed.exceptionOrNull()!!)
                        removed.getOrNull() == true -> "已解除這副耳機的 App 關聯"
                        else -> "找不到這副耳機的 App 關聯"
                    }
                    refresh++
                }
            }) { GalaxyText("解除耳機關聯") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodecChips(
    options: List<Pair<BluetoothCodecConfig, String>>,
    currentName: String?,
    enabled: Boolean,
    onClick: (BluetoothCodecConfig) -> Unit,
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (config, label) ->
            GalaxyFilterChip(selected = label == currentName, enabled = enabled,
                onClick = { onClick(config) }, label = { GalaxyText(label) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodecValueChoices(
    title: String,
    values: List<Pair<Int, String>>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        GalaxyText(title, style = GalaxyTypography.titleSmall)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            (listOf(0 to "自動") + values).forEach { (value, label) ->
                GalaxyFilterChip(selected = value == selected, onClick = { onSelected(value) },
                    label = { GalaxyText(label) })
            }
        }
    }
}
