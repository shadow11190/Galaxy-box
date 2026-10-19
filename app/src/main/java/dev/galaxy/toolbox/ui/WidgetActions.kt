package dev.galaxy.toolbox.ui

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import dev.galaxy.toolbox.widgets.WidgetConfigurationActivity
import dev.galaxy.toolbox.widgets.WidgetKind
import dev.galaxy.toolbox.widgets.WidgetUpdates

@Composable internal fun WidgetActionsCard() {
    val context = LocalContext.current
    var result by remember { mutableStateOf<String?>(null) }
    InfoCard("桌面小工具") {
        Text("獨立背景、顏色與欄位設定；靜態玻璃／純色／完全透明。點 ↻ 重新取樣。", color = Muted)
        listOf(WidgetKind.OVERVIEW to "完整版 · 4 × 3", WidgetKind.BATTERY to "精簡版 · 2 × 2", WidgetKind.RESOURCES to "記憶體與儲存 · 4 × 2").forEach { (kind, label) ->
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                val manager = AppWidgetManager.getInstance(context)
                result = runCatching {
                    if (manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(WidgetUpdates.component(context, kind), null, null))
                        "請在桌面啟動器的視窗中確認新增。"
                    else "請長按桌面空白處 → 小工具 → Galaxy box，再選擇樣式。"
                }.getOrElse { "請長按桌面空白處 → 小工具 → Galaxy box。" }
            }) { Text("加入$label") }
        }
        Button(onClick = { context.startActivity(Intent(context, WidgetConfigurationActivity::class.java)) }) { Text("設定已加入的小工具") }
        result?.let { Text(it, color = Mint) }
        Text("小工具顯示快照，背景約每 30 分鐘由系統排程更新，可能延後。App 前景約每 30 秒同步；不常駐秒級監控。", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("可拉寬或拉高；窄版改為單欄，較矮時採緊湊資料列。若文字放大或選擇較多欄位，請加大小工具。", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
