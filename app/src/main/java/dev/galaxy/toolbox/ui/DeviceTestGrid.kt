package dev.galaxy.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.diagnostics.DeviceTest

internal fun LazyListScope.deviceTestGrid(tests: List<DeviceTest>, results: Map<String, String>, onOpen: (List<DeviceTest>) -> Unit) {
    item {
        InfoCard("裝置檢查") {
            Text("只顯示裝置宣告或可辨識的功能。每項結果由你確認；不是三星官方認證。", color = Muted, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onOpen(tests) }, modifier = Modifier.fillMaxWidth()) { Text("逐項檢查全部 ${tests.size} 項") }
            if (results.isNotEmpty()) Text("已記錄 ${results.size} 項手動判讀結果", color = Mint, style = MaterialTheme.typography.bodySmall)
        }
    }
    // Rows adapt to available width/font scale; every tile remains at least 48 dp tall.
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 330.dp || LocalDensity.current.fontScale > 1.3f) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tests.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { test ->
                            GlassCard(onClick = { onOpen(listOf(test)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Text(test.mark, Modifier.background(Mint.copy(alpha = .1f), RoundedCornerShape(12.dp)).padding(10.dp), color = Mint,
                                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(test.title, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, minLines = 2)
                                    results[test.name]?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
