package dev.galaxy.toolbox.ui

import android.os.Build
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
import dev.galaxy.toolbox.data.AudioCapabilitySnapshot
import dev.galaxy.toolbox.data.AudioLiveSnapshot
import dev.galaxy.toolbox.data.LiveAudioInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AudioMonitoringCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var live by remember { mutableStateOf<AudioLiveSnapshot?>(null) }
    var capabilities by remember { mutableStateOf<AudioCapabilitySnapshot?>(null) }
    var expanded by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) {
        while (foreground && isActive) {
            live = withContext(Dispatchers.IO) { LiveAudioInspector.read(context) }
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        capabilities = withContext(Dispatchers.IO) { LiveAudioInspector.capabilities(context) }
    }

    InfoCard("即時音訊活動") {
        GalaxyText(if (foreground) "前景每秒更新 · " +
            (live?.readAtMillis?.let { DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "等待讀取")
            else "頁面已停止更新", color = Muted, style = GalaxyTypography.labelSmall)
        GalaxyText("系統播放工作階段 · " + (live?.playbackCount?.toString() ?: "未知"))
        GalaxyText("媒體輸出路由候選 · " + when {
            Build.VERSION.SDK_INT < 33 -> "此 Android 版本不支援查詢"
            live?.mediaRouteCandidates == null -> "無法取得"
            live?.mediaRouteCandidates?.isEmpty() == true -> "系統未回報"
            else -> live!!.mediaRouteCandidates!!.joinToString("、")
        })
        GalaxyText("此路由是系統對新建媒體音軌的預測，不能確認 Spotify 等其他 App 的實際輸出端點，也不能辨識哪一顆實體喇叭正在發聲。",
            color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyText("進行中的錄音 · " + (live?.recordings?.size?.toString() ?: "未知"))
        live?.recordings?.forEachIndexed { index, session ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GalaxyText("錄音 " + (index + 1) + " · " + session.inputPort,
                    style = GalaxyTypography.titleSmall)
                GalaxyText("App 請求：" + session.requestedFormat, color = Muted,
                    style = GalaxyTypography.bodySmall)
                GalaxyText("裝置格式：" + session.deviceFormat, color = Muted,
                    style = GalaxyTypography.bodySmall)
            }
        }
        GalaxyText("輸入端點不等於實體麥克風顆數；其他 App 的錄音細節可能被系統隱藏。本 App 的麥克風測試會另外列出可辨識的作用中麥克風。",
            color = Muted, style = GalaxyTypography.bodySmall)
        live?.error?.let { GalaxyText("部分讀取失敗：" + it, color = Muted, style = GalaxyTypography.labelSmall) }
    }

    InfoCard("解碼與輸出能力") {
        GalaxyText("系統宣告硬體加速的音訊解碼器 · " +
            (capabilities?.hardwareDecoders?.size?.toString() ?: "讀取中"))
        GalaxyTextButton(onClick = { expanded = !expanded }) { GalaxyText(if (expanded) "收起規格" else "查看規格") }
        if (expanded) {
            val decoders = capabilities?.hardwareDecoders.orEmpty()
            if (decoders.isEmpty()) GalaxyText("未取得系統宣告的硬體音訊解碼器。", color = Muted)
            decoders.forEach { codec ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    GalaxyText(codec.mime + " · " + codec.name, style = GalaxyTypography.titleSmall)
                    GalaxyText("取樣率 " + codec.sampleRates + " · 位元率範圍 " + codec.bitrate +
                        " · 輸入聲道上限 " + codec.channels, color = Muted,
                        style = GalaxyTypography.bodySmall)
                }
            }
        }
        GalaxyText("DSD 輸出宣告 · " + when {
            Build.VERSION.SDK_INT < 34 -> "此 Android 版本無公開 DSD 格式常數"
            capabilities == null -> "讀取中"
            capabilities!!.dsdOutputProfiles.isEmpty() -> "未列出"
            else -> capabilities!!.dsdOutputProfiles.joinToString("；")
        })
        GalaxyText("DSD 輸出格式不等於已確認的硬體 DSD 解碼；未列出也不等於絕對不支援。解碼器能力與實際播放鏈不同。",
            color = Muted, style = GalaxyTypography.bodySmall)
        capabilities?.error?.let { GalaxyText("部分能力讀取失敗：" + it, color = Muted,
            style = GalaxyTypography.labelSmall) }
    }

}
