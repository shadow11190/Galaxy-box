package dev.galaxy.toolbox.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.galaxy.toolbox.diagnostics.*

@Composable internal fun ShizukuDiagnosticsCard(kind: ProbeKind, availability: ShizukuAvailability, busy: Boolean,
    snapshot: PrivilegedSnapshot?, error: String?, onRead: () -> Unit, onCancel: () -> Unit, onRefresh: () -> Unit,
    onReading: (dev.galaxy.toolbox.model.Reading) -> Unit) {
    val context = LocalContext.current; val uri = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable(kind, snapshot?.atMs) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onRefresh() }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun openManager() {
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) context.startActivity(intent) else uri.openUri("https://shizuku.rikka.app/download/")
        }.onFailure { linkError = "無法開啟 Shizuku" }
    }
    InfoCard(if (kind == ProbeKind.BATTERY) "電池健康度 · 無線 ADB" else "可選備援 · Shizuku GPU 讀取") {
        GalaxyText(availability.title, color = Mint)
        GalaxyText(if (kind == ProbeKind.BATTERY) "透過 Shizuku 即時讀取系統回報的循環次數與容量。可用欄位因裝置而異。"
            else "可透過無線偵錯或 Root 啟動 Shizuku，再授權本應用。只讀取資料，不修改系統。",
            color = Muted, style = GalaxyTypography.bodySmall)
        if (busy) {
            GalaxyLinearProgressIndicator(Modifier.fillMaxWidth())
            GalaxyOutlinedButton(onClick = onCancel) { GalaxyText("取消讀取") }
        } else when (availability.connection) {
            ShizukuConnection.READY -> GalaxyPrimaryTextButton(
                if (kind == ProbeKind.BATTERY) "讀取電池健康資料" else "讀取 GPU 原始資料", onRead)
            ShizukuConnection.NEED_PERMISSION -> if (availability.permissionBlocked) {
                GalaxyPrimaryButton(onClick = ::openManager) { GalaxyText("前往 Shizuku 管理授權") }
            } else GalaxyPrimaryButton(onClick = onRead) { GalaxyText("授權並讀取") }
            else -> {
                GalaxyPrimaryTextButton("開啟 Shizuku", ::openManager)
                if (kind == ProbeKind.BATTERY && android.os.Build.VERSION.SDK_INT >= 30) {
                    GalaxyText("在開發人員選項開啟無線偵錯，用 Shizuku 配對並啟動，再回到這裡授權讀取。",
                        color = Muted, style = GalaxyTypography.bodySmall)
                    GalaxyTextButton("開啟開發人員選項", {
                        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                            .onFailure { linkError = "請從系統設定開啟開發人員選項。" }
                    })
                }
            }
        }
        GalaxyTextButton(onClick = { runCatching { uri.openUri("https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging") }.onFailure { linkError = "無法開啟啟動說明" } }) { GalaxyText("無線偵錯啟動說明") }
        error?.let { GalaxyText(it, color = GalaxyPalette.error) }
        linkError?.let { GalaxyText(it, color = GalaxyPalette.error) }
        snapshot?.let { result ->
            GalaxyDivider()
            GalaxyText("上次手動讀取 · ${formatImportTime(java.time.Instant.ofEpochMilli(result.atMs).toString())}", color = Muted, style = GalaxyTypography.bodySmall)
            if (result.fields.isEmpty()) GalaxyText("未取得可用資料", color = Muted)
            else {
                GalaxyText("已取得 ${result.fields.size} 項；各來源分列。", color = Muted, style = GalaxyTypography.bodySmall)
                result.fields.forEach { field ->
                    GalaxyTextButton(onClick = { onReading(field) }, contentPadding = PaddingValues(vertical = 6.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            GalaxyText(field.label, color = Muted, style = GalaxyTypography.labelLarge)
                            GalaxyText(field.value.orEmpty(), color = Mint, style = GalaxyTypography.titleMedium)
                        }
                    }
                }
            }
            if (result.diagnostics.isNotEmpty()) {
                GalaxyTextButton(onClick = { expanded = !expanded }) { GalaxyText(if (expanded) "收合來源診斷" else "查看來源診斷") }
                if (expanded) result.diagnostics.forEach { GalaxyText(it, color = Muted, style = GalaxyTypography.bodySmall) }
            }
        }
    }
}
