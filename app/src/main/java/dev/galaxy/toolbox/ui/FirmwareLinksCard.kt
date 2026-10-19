package dev.galaxy.toolbox.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import dev.galaxy.toolbox.model.FirmwareLinks

@Composable internal fun FirmwareLinksCard(device: String? = FirmwareLinks.deviceModel(Build.MANUFACTURER, Build.MODEL)) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    InfoCard("公開韌體與系統更新") {
        device?.let { model ->
            Text("本機型號 · $model", color = Mint, style = MaterialTheme.typography.titleMedium)
            Button(onClick = { runCatching { uri.openUri(FirmwareLinks.catalogue(model)) }.onFailure { error = "無法開啟韌體網站" } }) {
                Text("查看此型號公開韌體")
            }
            Text("SamFW · 第三方韌體目錄，已帶入本機型號。", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("目錄包含不同地區版本；本機可用更新以系統檢查為準。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent("android.settings.SYSTEM_UPDATE_SETTINGS")) }
                .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .onFailure { error = "無法開啟系統設定" }
        }) { Text("開啟系統更新／設定") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
