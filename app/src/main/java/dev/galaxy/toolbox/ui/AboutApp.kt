package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutApp(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }
    var license by remember { mutableStateOf<String?>(null) }
    var licenseText by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(license) {
        licenseText = license?.let { name ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
                }.getOrElse { "無法載入授權文字。" }
            }
        } ?: ""
    }

    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
            .onFailure { linkError = "無法開啟連結，請稍後再試。" }
    }

    fun toggleLicense(name: String) {
        license = if (license == name) null else name
    }

    AboutSheetShell(onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GalaxyText("Galaxy Box", style = GalaxyTypography.headlineMedium, fontWeight = FontWeight.Bold)
            GalaxyText("v$version · 技術預覽版", color = Mint)
            GalaxyText("主要為 Samsung Galaxy 製作，也歡迎其他 Android 裝置使用可支援的功能。由個人獨立開發；正式版本將從 1.0.0 開始。")

            AboutSection("可以做什麼") {
                GalaxyText("查看處理器、裝置、記憶體與感測器資訊，以及可取得的即時溫度與電池資料。")
                GalaxyText("提供充電檢測、無線 ADB 電池讀取、系統版本與公開韌體查詢、硬體檢測、貼膜輔助和桌面小工具。")
                GalaxyText("介面可選簡約或 Miuix 風格；液態玻璃可獨立開啟，預設關閉。")
            }

            AboutSection("作者與專案") {
                GalaxyTextButton(onClick = { open("https://github.com/shadow11190/Galaxy-box") }) {
                    GalaxyText("GitHub 專案與使用說明")
                }
                GalaxyTextButton(onClick = { open("https://github.com/shadow11190/Galaxy-box/releases") }) {
                    GalaxyText("公開版本與更新內容")
                }
                GalaxyTextButton(onClick = { open("https://www.coolapk.com/u/39601996") }) {
                    GalaxyText("作者的酷安頁面")
                }
            }

            AboutSection("資料與已知限制") {
                GalaxyText("即時數值以本機可讀來源為準；公開規格只用於辨識與參考。讀不到時顯示未知，不以推測值補上。")
                GalaxyText("溫度測點、電池循環及健康資料會受機型、韌體和權限限制。機型資料庫仍在補齊，離線資料搜尋入口暫未開放。")
                GalaxyText("電池健康資料取決於裝置與韌體支援。桌面小工具更新受系統背景限制。公開韌體目錄不等於本機 OTA 更新檢查。")
                GalaxyTextButton(onClick = { open("https://github.com/shadow11190/Galaxy-box/blob/main/HARDWARE-CATALOG.md") }) {
                    GalaxyText("查看硬體資料收錄與來源")
                }
            }

            AboutSection("隱私與免責") {
                GalaxyText("電池診斷在本機讀取與處理。開啟第三方韌體目錄時，裝置型號會傳送給該網站。")
                GalaxyText("本應用與 Samsung／三星官方無關，也沒有官方認證。感測器名稱不代表已確認實體位置；檢測結果與電池估算不能代替原廠檢測。商品名稱和商標屬各權利人。")
            }

            AboutSection("開源致謝與授權") {
                GalaxyText("感謝 compose-miuix-ui 社群的 Miuix、Kyant 的 AndroidLiquidGlass／Backdrop、RikkaApps 的 Shizuku API、AndroidSVG、OpenCC4J、Apache Commons、LineageOS Wiki，以及 Android 與 Kotlin 開源社群。")
                GalaxyText("Miuix Skill 提供早期設計參考；部分診斷功能參考 Sam Helper 的公開介紹，均由本應用獨立實作。桌面小工具使用自行繪製的靜態玻璃風格。")
                GalaxyTextButton(onClick = { open("https://github.com/compose-miuix-ui/miuix/tree/v0.9.4") }) {
                    GalaxyText("Miuix 元件原始專案")
                }
                GalaxyTextButton(onClick = { open("https://github.com/Kyant0/AndroidLiquidGlass/tree/2.0.1") }) {
                    GalaxyText("AndroidLiquidGlass 原始專案")
                }
                GalaxyTextButton(onClick = { open("https://github.com/limczhh/miuix-skill") }) {
                    GalaxyText("Miuix Skill 設計參考")
                }
                GalaxyTextButton(onClick = { open("https://github.com/shadow11190/Galaxy-box/blob/main/THIRD_PARTY_NOTICES.md") }) {
                    GalaxyText("第三方元件與品牌圖示來源")
                }
                GalaxyTextButton(onClick = { open("https://github.com/shadow11190/Galaxy-box/blob/main/LICENSE") }) {
                    GalaxyText("本專案授權")
                }
                GalaxyTextButton(onClick = { toggleLicense("Backdrop-Apache-2.0.txt") }) { GalaxyText("Backdrop 授權原文") }
                GalaxyTextButton(onClick = { toggleLicense("Miuix-Apache-2.0.txt") }) { GalaxyText("Miuix 授權原文") }
                GalaxyTextButton(onClick = { toggleLicense("AndroidSVG-Apache-2.0.txt") }) { GalaxyText("AndroidSVG 授權原文") }
                GalaxyTextButton(onClick = { toggleLicense("OpenCC4J-Apache-2.0.txt") }) { GalaxyText("OpenCC4J 授權原文") }
                GalaxyTextButton(onClick = { toggleLicense("Backdrop-Catalog-NOTICE.txt") }) { GalaxyText("液態導覽修改聲明") }
                GalaxyTextButton(onClick = { toggleLicense("Apache-Commons-LICENSE-NOTICE.txt") }) { GalaxyText("Apache Commons 授權") }
                GalaxyTextButton(onClick = { toggleLicense("Shizuku-API-MIT.txt") }) { GalaxyText("Shizuku API 授權") }
                GalaxyTextButton(onClick = { toggleLicense("LineageOS-wiki-MIT.txt") }) { GalaxyText("LineageOS Wiki 授權") }
            }

            license?.let {
                AboutSection("授權原文") {
                    SelectionContainer {
                        GalaxyText(
                            if (licenseText.isEmpty()) "載入中…" else licenseText,
                            style = GalaxyTypography.bodySmall,
                        )
                    }
                }
            }
            linkError?.let { GalaxyText(it, color = GalaxyPalette.error) }
            GalaxyTextButton(onClick = onDismiss) { GalaxyText("關閉") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheetShell(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    GalaxyBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        hostedByMiuixScaffold = LocalAppStyle.current == AppStyle.MIUIX,
    ) { content() }
}

@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixGroupedSection(title, content = content)
    } else {
        GalaxyDivider()
        GalaxyText(title, style = GalaxyTypography.titleLarge, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}
