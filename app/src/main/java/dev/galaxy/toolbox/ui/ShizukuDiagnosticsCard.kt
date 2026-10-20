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
    InfoCard(if (kind == ProbeKind.BATTERY) "進階電池讀取 · Shizuku" else "可選備援 · Shizuku GPU 讀取") {
        Text(availability.title, color = Mint)
        Text("可透過無線偵錯或 Root 啟動 Shizuku，再授權本應用。只讀取資料，不修改系統。", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel) { Text("取消讀取") }
        } else when (availability.connection) {
            ShizukuConnection.READY -> Button(onClick = onRead) { Text("讀取${if (kind == ProbeKind.BATTERY) "電池" else "GPU"}原始資料") }
            ShizukuConnection.NEED_PERMISSION -> if (availability.permissionBlocked) {
                Button(onClick = ::openManager) { Text("前往 Shizuku 管理授權") }
            } else Button(onClick = onRead) { Text("授權並讀取") }
            else -> OutlinedButton(onClick = ::openManager) { Text("開啟 Shizuku") }
        }
        TextButton(onClick = { runCatching { uri.openUri("https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging") }.onFailure { linkError = "無法開啟啟動說明" } }) { Text("無線偵錯啟動說明") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        snapshot?.let { result ->
            HorizontalDivider()
            Text("上次手動讀取 · ${formatImportTime(java.time.Instant.ofEpochMilli(result.atMs).toString())}", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (result.fields.isEmpty()) Text("未取得可用資料", color = Muted)
            else {
                Text("已取得 ${result.fields.size} 項；各來源分列。", color = Muted, style = MaterialTheme.typography.bodySmall)
                result.fields.forEach { field ->
                    TextButton(onClick = { onReading(field) }, contentPadding = PaddingValues(vertical = 6.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(field.label, color = Muted, style = MaterialTheme.typography.labelLarge)
                            Text(field.value.orEmpty(), color = Mint, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (result.diagnostics.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收合來源診斷" else "查看來源診斷") }
                if (expanded) result.diagnostics.forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
