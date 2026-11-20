package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun RawMemoryCard() {
    var open by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var at by remember { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(open, seconds, refresh, owner) {
        if (open) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                val result = withContext(Dispatchers.IO) { runCatching { File("/proc/meminfo").bufferedReader().use { reader -> val buffer = CharArray(32768); var count = 0; while (count < buffer.size) { val n = reader.read(buffer, count, buffer.size - count); if (n < 0) break; count += n }; String(buffer, 0, count) } } }
                text = result.getOrNull(); error = result.exceptionOrNull()?.let { "無法取得（${it.javaClass.simpleName}）" }
                at = java.time.LocalTime.now().withNano(0).toString()
                if (seconds > 0) delay(seconds * 1000L)
            } while (seconds > 0)
        }
    }
    InfoCard("記憶體原始資料") {
        GalaxyTextButton(onClick = { open = !open }) { GalaxyText(if (open) "收合 /proc/meminfo" else "讀取 /proc/meminfo") }
        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0, 2, 5, 10).forEach { n -> GalaxyFilterChip(seconds == n, { seconds = n }, label = { GalaxyText(if (n == 0) "手動" else "${n}s") }) }
            }
            GalaxyTextButton(onClick = { refresh++ }) { GalaxyText("重新讀取") }
            at?.let { GalaxyText("讀取時間 $it · Linux kB 依 KiB 解讀", color = Muted, style = GalaxyTypography.bodySmall) }
            error?.let { GalaxyText(it, color = GalaxyPalette.error) }
            text?.let { SelectionContainer { GalaxyText(it, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()), style = GalaxyTypography.bodySmall) } }
        }
    }
}
