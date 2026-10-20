package dev.galaxy.toolbox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import dev.galaxy.toolbox.performance.RefreshSnapshot
import java.util.Locale

internal val LocalRefreshSnapshot = staticCompositionLocalOf { RefreshSnapshot() }

@Composable internal fun RefreshStatus() {
    val info = LocalRefreshSnapshot.current
    fun hz(value: Float?) = value?.let { String.format(Locale.ROOT, "%.2f Hz", it) } ?: "—"
    Text("螢幕更新率 · ${hz(info.reportedHz)}", style = MaterialTheme.typography.bodyMedium)
    Text("前景請求 · ${hz(info.requestedHz)}\n${info.reason}", color = Muted, style = MaterialTheme.typography.bodySmall)
    Text("這是系統回報的螢幕更新率，不是 App 實際繪製 fps。靜止時不持續重繪。", color = Muted, style = MaterialTheme.typography.bodySmall)
}
