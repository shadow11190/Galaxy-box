package dev.galaxy.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.diagnostics.SamsungServiceCode
import dev.galaxy.toolbox.diagnostics.SamsungServiceCodes
import dev.galaxy.toolbox.diagnostics.ServiceCodeAccess
import dev.galaxy.toolbox.diagnostics.ServiceCodeCategory

@Composable
internal fun SamsungServiceShortcuts() {
    val context = LocalContext.current
    var expandedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var resetToReveal by remember { mutableStateOf<SamsungServiceCode?>(null) }
    var revealedReset by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun copy(entry: SamsungServiceCode) {
        runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
                ?: error("Clipboard unavailable")
            clipboard.setPrimaryClip(ClipData.newPlainText(entry.title, entry.code))
        }.onSuccess { message = "已複製 " + entry.title + " 代碼" }
            .onFailure { message = "無法複製代碼" }
    }

    InfoCard("三星診斷與工程代碼") {
        GalaxyText("收錄 " + SamsungServiceCodes.entries.size + " 個代碼。功能依型號、地區與韌體而異；部分代碼可能在三星撥號器輸入後立即執行。",
            color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyText("可檢測的代碼能帶入撥號器；可能變更設定的代碼僅供複製。重置代碼需先確認才顯示，App 不會直接執行。",
            color = Muted, style = GalaxyTypography.bodySmall)

        ServiceCodeCategory.entries.forEach { category ->
            val codes = SamsungServiceCodes.entries.filter { it.category == category }
            GalaxyDivider()
            GalaxyTextButton(onClick = {
                expandedCategory = if (expandedCategory == category.name) null else category.name
            }) {
                GalaxyText(category.label + " · " + codes.size + "  " +
                    if (expandedCategory == category.name) "收起" else "展開")
            }
            if (expandedCategory == category.name) {
                codes.forEach { entry ->
                    GalaxyDivider()
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        GalaxyText(entry.title, style = GalaxyTypography.titleSmall)
                        if (entry.access == ServiceCodeAccess.RESET && revealedReset != entry.code) {
                            GalaxyText(entry.purpose, color = Muted, style = GalaxyTypography.bodySmall)
                            GalaxyTextButton(onClick = { resetToReveal = entry }) { GalaxyText("確認後顯示代碼") }
                        } else {
                            GalaxyText(entry.code + " · " + entry.purpose, color = Muted,
                                style = GalaxyTypography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (entry.access == ServiceCodeAccess.DIAL) {
                                    GalaxyTextButton(onClick = {
                                        runCatching { context.startActivity(SamsungServiceCodes.dialIntent(entry)) }
                                            .onFailure { message = "無法開啟撥號器；可複製後手動輸入。" }
                                    }) { GalaxyText("開啟撥號器") }
                                }
                                GalaxyTextButton(onClick = { copy(entry) }) { GalaxyText("複製代碼") }
                            }
                        }
                    }
                }
            }
        }
        message?.let { GalaxyText(it, color = Muted, style = GalaxyTypography.bodySmall) }
    }

    resetToReveal?.let { entry ->
        if (LocalAppStyle.current == AppStyle.MIUIX) {
            key(entry.code) {
                var dialogShown by remember { mutableStateOf(true) }
                MiuixConfirmationDialog(
                    show = dialogShown,
                    title = "確認顯示重置代碼",
                    message = entry.title + "：" + entry.purpose +
                        "。在撥號器輸入後可能立即執行並造成資料遺失。請先備份；此處只顯示代碼，不會開啟撥號器。",
                    confirmLabel = "顯示代碼",
                    onConfirm = { revealedReset = entry.code; dialogShown = false },
                    onDismissRequest = { dialogShown = false },
                    onDismissFinished = { resetToReveal = null },
                    hostedByMiuixScaffold = true,
                )
            }
        } else AlertDialog(
            onDismissRequest = { resetToReveal = null },
            title = { GalaxyText("確認顯示重置代碼") },
            text = { GalaxyText(entry.title + "：" + entry.purpose +
                "。在撥號器輸入後可能立即執行並造成資料遺失。請先備份；此處只顯示代碼，不會開啟撥號器。") },
            confirmButton = {
                GalaxyTextButton(onClick = {
                    revealedReset = entry.code
                    resetToReveal = null
                }) { GalaxyText("顯示代碼") }
            },
            dismissButton = { GalaxyTextButton(onClick = { resetToReveal = null }) { GalaxyText("取消") } }
        )
    }
}
