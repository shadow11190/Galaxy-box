package dev.galaxy.toolbox.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HardwareCatalogSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(Build.MODEL) }
    var chips by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var summary by remember { mutableStateOf("載入離線目錄…") }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        summary = withContext(Dispatchers.IO) { runCatching {
            val json = context.assets.open("hardware-catalog-coverage.json").bufferedReader().use { JSONObject(it.readText()) }
            "${json.getInt("processorNames")} 個處理器名稱 · ${json.getInt("deviceRows")} 筆原始裝置紀錄\n" +
                "已核對 2018 年起手機 ${json.getInt("verified2018PhoneModels")} 款型號、${json.getInt("verified2018PhoneProcessors")} 款處理器\n" +
                "三星 ${json.getInt("samsungRows")} 筆原始紀錄 · ${json.getInt("deviceSpecRows")} 筆完整型號與處理器對照\n資料日期 ${json.getString("retrieved")}"
        }.getOrElse { "離線目錄資訊無法取得" } }
    }
    LaunchedEffect(query, chips) {
        loading = true
        delay(180)
        results = withContext(Dispatchers.IO) { runCatching {
            if (chips) ChipNames.search(query).map { chip ->
                val details = CpuSpecs.readings(chip).joinToString("\n") { "${it.label}：${it.value}" }
                val usage = HardwareCatalog.processorPhoneUsage(context).lookup(chip.name)
                val scope = usage?.let { "已核對用於 ${it.firstPhoneYear} 年起的 Android 手機\n手機資料來源：${it.source}" }
                    ?: "2018 年起手機對照尚未核對"
                chip.name to "$scope\n${chip.process.orEmpty()}\n${chip.cpu.orEmpty()}\n$details\n處理器資料來源\n${chip.source}"
            } else {
                val names = HardwareCatalog.names(context)
                val variants = HardwareCatalog.variants(context)
                val classifications = HardwareCatalog.classifications(context)
                names.search(query).map { d ->
                    val specs = variants.lookup(d.brand, d.model, d.device)
                    val classification = classifications.lookup(d.brand, d.model, d.device)
                    "${d.name} · ${d.model}" to "${d.brand} · ${d.device}\n${classification?.label ?: "上市年／裝置類型待核對"}\n" +
                        (if (specs.isEmpty()) "尚未收錄此完整型號的處理器規格" else specs.map { "${it.soc}\n${it.market}\n${it.source}" }.distinct().joinToString("\n\n")) +
                        "\n分類來源：${classification?.source ?: "無可核對資料"}\n名稱來源：Google Play 公開裝置目錄\n${DeviceNames.SOURCE}"
                }
            }
        }.getOrElse { listOf("無法讀取目錄" to "請稍後重試") } }
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("裝置與處理器目錄", style = MaterialTheme.typography.titleLarge)
            Text(summary, color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("2018 年起的手機逐筆核對；原有裝置名稱均保留，未核對者標示未知。不同地區完整型號分列，公開規格與本機讀值分開。", color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !chips, onClick = { chips = false }, label = { Text("裝置型號") })
                FilterChip(selected = chips, onClick = {
                    chips = true
                    query = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeUnless { it.isBlank() || it.equals("unknown", true) }.orEmpty() else ""
                }, label = { Text("處理器") })
            }
            OutlinedTextField(value = query, onValueChange = { query = it.take(100) }, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (chips) "晶片名稱或代號" else "市售名稱、完整型號或內部代號") }, singleLine = true)
            Text(if (loading) "查詢中…" else if (query.trim().length < 2) "輸入至少兩個字元" else "${results.size} 筆結果（最多顯示 ${if (chips) 60 else 100} 筆，請縮小搜尋範圍）", color = Muted, style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(results) { (title, detail) ->
                    var expanded by remember(title, detail) { mutableStateOf(false) }
                    InfoCard(title) {
                        SelectionContainer { Text(if (expanded) detail else detail.lineSequence().take(2).joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收合" else "查看對照與來源") }
                    }
                }
            }
        }
    }
}
