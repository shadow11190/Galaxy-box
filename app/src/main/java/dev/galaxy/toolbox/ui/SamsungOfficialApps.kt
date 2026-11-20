package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

private const val GOOD_LOCK_STORE = "https://galaxystore.samsung.com/detail/com.samsung.android.goodlock"
private const val GOOD_GUARDIANS_STORE = "https://galaxystore.samsung.com/detail/com.android.samsung.utilityapp"
private const val GOOD_LOCK_MIRROR = "https://www.apkmirror.com/apk/samsung-electronics-co-ltd/good-lock-2018/"
private const val GOOD_GUARDIANS_MIRROR = "https://www.apkmirror.com/apk/samsung-electronics-co-ltd/galaxy-labs/"

@Composable
internal fun SamsungOfficialApps() {
    val uriHandler = LocalUriHandler.current
    var error by rememberSaveable { mutableStateOf(false) }
    var useApkMirror by rememberSaveable { mutableStateOf(false) }
    InfoCard("三星官方擴充工具") {
        GalaxyText("Good Lock 提供外觀與操作模組；Good Guardians（原 Galaxy Labs）提供電池、溫控與記憶體工具。",
            color = Muted, style = GalaxyTypography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalaxyFilterChip(selected = !useApkMirror, onClick = { useApkMirror = false }, label = { GalaxyText("Galaxy Store") })
            GalaxyFilterChip(selected = useApkMirror, onClick = { useApkMirror = true }, label = { GalaxyText("APKMirror") })
        }
        GalaxyText(if (useApkMirror) "第三方下載頁面；請核對發行者、套件名稱與簽章，安裝後模組仍可能受地區或系統限制。" else "三星官方商店頁面。",
            color = Muted, style = GalaxyTypography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalaxyTextButton(onClick = {
                error = runCatching { uriHandler.openUri(if (useApkMirror) GOOD_LOCK_MIRROR else GOOD_LOCK_STORE) }.isFailure
            }) { GalaxyText("Good Lock") }
            GalaxyTextButton(onClick = {
                error = runCatching { uriHandler.openUri(if (useApkMirror) GOOD_GUARDIANS_MIRROR else GOOD_GUARDIANS_STORE) }.isFailure
            }) { GalaxyText("Good Guardians") }
        }
        if (error) GalaxyText("無法開啟下載頁面。", color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyText("安裝與可用模組由裝置、系統版本及地區決定；工具箱只開啟頁面，不代為下載或安裝。",
            color = Muted, style = GalaxyTypography.bodySmall)
    }
}
