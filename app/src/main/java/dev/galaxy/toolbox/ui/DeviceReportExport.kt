package dev.galaxy.toolbox.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.galaxy.toolbox.ToolboxState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DeviceReportExport(state: ToolboxState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(state)
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val selected = latest
            scope.launch {
                busy = true
                result = null
                try {
                    withContext(Dispatchers.IO) {
                        val build = ReportBuildFields(
                            Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE,
                            Build.PRODUCT, Build.BOARD,
                            if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else null,
                            if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null,
                            Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
                        )
                        @Suppress("DEPRECATION")
                        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
                        val body = DeviceDiagnosticReport.render(selected, build, version, System.currentTimeMillis())
                        checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                            .bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                    }
                    result = "已儲存至你選取的位置。"
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    result = "匯出失敗；請選擇另一個儲存位置後重試。"
                } finally {
                    busy = false
                }
            }
        }
    }
    InfoCard("裝置診斷報告") {
        GalaxyText("匯出目前的裝置辨識、處理器、電池與監控快照，包含資料來源和讀取時間。")
        GalaxyPrimaryButton(
            onClick = {
                val suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))
                launcher.launch("Galaxy-box-device-$suffix.json")
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            GalaxyText(if (busy) "匯出中…" else "匯出裝置報告")
        }
        GalaxyText(
            "只儲存到你選的位置；不包含序號、網路識別或匯入的 Bugreport。讀不到的欄位保留未知，舊快照會標示過期。",
            color = Muted,
            style = GalaxyTypography.bodySmall,
        )
        result?.let { GalaxyText(it, color = Mint, style = GalaxyTypography.bodySmall) }
    }
}
