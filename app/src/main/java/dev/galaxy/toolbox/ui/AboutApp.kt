package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AboutApp(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—" }
    var license by remember { mutableStateOf<String?>(null) }
    var licenseText by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(license) {
        licenseText = license?.let { name -> withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrElse { "無法載入授權文字。" }
        } } ?: ""
    }
    fun open(url: String) { runCatching { uriHandler.openUri(url) }.onFailure { linkError = "無法開啟連結，請稍後再試。" } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("關於 Galaxy box", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("版本 $version · 個人獨立製作", color = Mint)
            Text("免 Root 的 Galaxy 硬體資訊與檢測工具；Shizuku 可選用 ADB 或 Root 模式進階讀取。資料依手機實際開放的 API 與來源讀取；未知數據不以示範數字補上。")
            HorizontalDivider()
            Text("開源致謝", style = MaterialTheme.typography.titleLarge)
            Text("AndroidLiquidGlass／Backdrop · Kyant", color = Mint, fontWeight = FontWeight.SemiBold)
            Text("Backdrop 1.0.0 提供 App 內的背景捕捉、模糊、折射與高光。底部液態導覽改編自 Kyant 的 LiquidBottomTabs 範例，保留圖示／文字的折射放大、色散與彈性形變，選取泡泡停留時仍保持凸起。桌面小工具使用本應用自行繪製的靜態玻璃風格。Apache License 2.0。")
            TextButton(onClick = { open("https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.0") }) { Text("開啟 AndroidLiquidGlass 專案") }
            TextButton(onClick = { license = if (license == "Backdrop-Apache-2.0.txt") null else "Backdrop-Apache-2.0.txt" }) { Text("查看 Backdrop 授權") }
            TextButton(onClick = { license = if (license == "Backdrop-Catalog-NOTICE.txt") null else "Backdrop-Catalog-NOTICE.txt" }) { Text("查看液態導覽來源與修改聲明") }
            Text("Apache Commons Compress · Apache Software Foundation", color = Mint, fontWeight = FontWeight.SemiBold)
            Text("Commons Compress 1.28.0 與 Commons IO／Lang 協助以可定位的檔案來源讀取 ZIP／ZIP64，不需將整包報告載入記憶體。Apache License 2.0。")
            TextButton(onClick = { open("https://commons.apache.org/proper/commons-compress/") }) { Text("開啟 Apache Commons Compress") }
            TextButton(onClick = { license = if (license == "Apache-Commons-LICENSE-NOTICE.txt") null else "Apache-Commons-LICENSE-NOTICE.txt" }) { Text("查看 Commons 授權與聲明") }
            Text("Shizuku API · RikkaApps", color = Mint, fontWeight = FontWeight.SemiBold)
            Text("API／Provider 13.1.5 提供使用者授權後的 ADB 讀取。僅讀取固定電池／GPU 來源，不執行任意指令或修改系統。MIT License。")
            TextButton(onClick = { open("https://github.com/RikkaApps/Shizuku-API") }) { Text("開啟 Shizuku API 專案") }
            TextButton(onClick = { license = if (license == "Shizuku-API-MIT.txt") null else "Shizuku-API-MIT.txt" }) { Text("查看 Shizuku API 授權") }
            Text("Kotlin、kotlinx.coroutines 與 Android Jetpack／Compose", color = Mint, fontWeight = FontWeight.SemiBold)
            Text("感謝 JetBrains、Google、Android 開源社群及相關貢獻者提供語言、介面與生命週期工具。相關元件採 Apache License 2.0。")
            Text("公開資料與文件", color = Mint, fontWeight = FontWeight.SemiBold)
            Text("市售機型名稱參考 Google Play 公開裝置目錄。離線晶片與型號對照另參考晶片廠商規格、Samsung 安全認證文件及 LineageOS 裝置資料；各地區版本分列，資料涵蓋範圍可在硬體頁的離線目錄查看，不能視為所有已上市手機均已完整收錄。LineageOS Wiki 資料依 MIT 授權，感謝原作者與所有貢獻者。更新頁連至 SamFW 第三方機型目錄，是否可更新以手機系統為準。商品名稱與商標仍屬各權利人所有。")
            TextButton(onClick = { open("https://github.com/LineageOS/lineage_wiki") }) { Text("開啟 LineageOS Wiki") }
            TextButton(onClick = { license = if (license == "LineageOS-wiki-MIT.txt") null else "LineageOS-wiki-MIT.txt" }) { Text("查看 LineageOS Wiki 授權") }
            TextButton(onClick = { open("https://storage.googleapis.com/play_public/supported_devices.html") }) { Text("查看公開裝置目錄") }
            Text("品牌標誌 · Simple Icons／Wikimedia Commons", color = Mint, fontWeight = FontWeight.SemiBold)
            Text("首頁裝置卡片的透明標誌來自 Simple Icons 及個別 Wikimedia Commons 檔案。只有辨識到裝置與品牌時才顯示；品牌標誌屬各權利人，並不表示官方合作。完整逐項來源見專案 BRAND-ASSETS-0.7.0.tsv。")
            TextButton(onClick = { open("https://github.com/simple-icons/simple-icons") }) { Text("查看 Simple Icons") }
            TextButton(onClick = { open("https://commons.wikimedia.org/") }) { Text("查看 Wikimedia Commons") }
            license?.let {
                HorizontalDivider()
                Text("授權原文", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(if (licenseText.isEmpty()) "載入中…" else licenseText, style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider()
            Text("免責聲明", style = MaterialTheme.typography.titleLarge)
            Text("本應用為個人獨立製作，並非 Samsung／三星官方應用，與 Samsung 無隸屬、授權合作或官方認證關係。Samsung、Galaxy 及其他產品名稱或商標屬各自權利人所有。")
            Text("數據可能受機型、韌體、權限、驅動及更新時間影響。感測器名稱不等於已確認的實體位置；硬體檢測結果需使用者自行判讀，不能作為官方檢測、維修保固或安全認證。")
            Text("電池健康、循環與 Bugreport 數據僅反映來源當時的回報，不等於本應用實測容量。匯入報告的來源裝置與時間未經驗證，不能視為本機目前狀況。充電功率為電池端淨估算，不是充電器輸入或額定瓦數。")
            Text("本應用按現況提供，不保證所有機型均能讀取全部項目或數據完全正確。使用者應依實際情況使用；重要異常請以原廠檢測與說明為準。")
            Text("資料與隱私", style = MaterialTheme.typography.titleLarge)
            Text("Bugreport 可能含個人與裝置資訊，本應用在手機本地解析，不上傳報告；必要時建立的私有暫存檔會於完成、失敗或取消後刪除。開啟韌體目錄會將本機型號傳給第三方 SamFW 網站。麥克風檢測只處理記憶體訊號、不存檔；定位只顯示系統誤差估計，不顯示或保存座標。Shizuku 原始資料只在手機本地處理。一般充電通知由你啟動，背景只讀電池，可隨時停止；不讀 GPU 或錄音／定位。")
            linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss) { Text("關閉") }
            Spacer(Modifier.height(20.dp))
        }
    }
}
