package dev.galaxy.toolbox.ui

import android.Manifest
import android.annotation.SuppressLint
import android.media.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.galaxy.toolbox.data.ConnectionDetails
import dev.galaxy.toolbox.diagnostics.AudioSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable internal fun SpeakerTest() {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
    var track by remember { mutableStateOf<AudioTrack?>(null) }
    var message by remember { mutableStateOf("按下後播放短音，請自行確認聲音與聲道。") }
    fun stop() { track?.let { runCatching { it.stop() }; it.release() }; track = null }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
        owner.lifecycle.addObserver(observer); onDispose { stop(); owner.lifecycle.removeObserver(observer) }
    }
    Text(message)
    listOf("左聲道" to 0, "右聲道" to 1, "雙聲道" to 2).forEach { (label, channel) ->
        Button(onClick = {
            stop()
            runCatching {
                val pcm = AudioSignal.tone(channel)
                val next = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                track = next; check(next.state == AudioTrack.STATE_INITIALIZED)
                val speaker = context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) next.setPreferredDevice(speaker)
                check(next.write(pcm, 0, pcm.size) == pcm.size); next.play()
                message = "已送出 $label 音訊；請自行確認，不自動判定通過。"
                scope.launch {
                    delay(100)
                    if (track === next) next.routedDevice?.let { message = "播放路徑：${ConnectionDetails.audioType(it.type)} · $label；請自行確認。" }
                    delay(600); if (track === next) stop()
                }
            }.onFailure { stop(); message = "無法播放（${it.javaClass.simpleName}）" }
        }) { Text("播放 $label") }
    }
    TextButton(onClick = ::stop) { Text("停止播放") }
}

@SuppressLint("MissingPermission") // PermissionGate requires RECORD_AUDIO before creating AudioRecord.
@Composable internal fun MicrophoneTest() {
    PermissionGate(listOf(Manifest.permission.RECORD_AUDIO), "麥克風") {
        val owner = LocalLifecycleOwner.current
        var running by remember { mutableStateOf(false) }
        var record by remember { mutableStateOf<AudioRecord?>(null) }
        var level by remember { mutableStateOf<Double?>(null) }
        var message by remember { mutableStateOf("開始後對手機說話，觀察訊號幅度變化。音訊不存檔。") }
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { running = false; runCatching { record?.stop() } } }
            owner.lifecycle.addObserver(observer); onDispose { running = false; runCatching { record?.stop() }; owner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(running) {
            if (!running) return@LaunchedEffect
            try {
                withContext(Dispatchers.IO) {
                    val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(minimum > 0) { "沒有可用的錄音格式" }
                    val next = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(maxOf(minimum, 4096)).build()
                    try {
                        check(next.state == AudioRecord.STATE_INITIALIZED)
                        withContext(Dispatchers.Main) { record = next }
                        next.startRecording(); check(next.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                        val buffer = ShortArray(1600)
                        while (isActive) {
                            val read = next.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                            if (read < 0) error("錄音讀取失敗 $read")
                            if (read > 0) { val value = AudioSignal.dbfs(buffer, read); withContext(Dispatchers.Main) { level = value } }
                            delay(100)
                        }
                    } finally { runCatching { next.stop() }; next.release() }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = "無法取得麥克風訊號（${e.javaClass.simpleName}）"; running = false }
            finally { record = null }
        }
        Text(message)
        level?.let { Text(if (it == Double.NEGATIVE_INFINITY) "數位靜音（−∞ dBFS）" else String.format(Locale.ROOT, "訊號 %.1f dBFS", it), color = Mint) }
        Text("這是數位訊號幅度，並非經校正的環境分貝值，也不自動判定麥克風正常。", color = Muted, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { level = null; running = !running }) { Text(if (running) "停止麥克風檢查" else "開始麥克風檢查") }
    }
}
