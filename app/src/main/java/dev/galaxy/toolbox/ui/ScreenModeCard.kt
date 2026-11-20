package dev.galaxy.toolbox.ui

import android.content.Intent
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun ScreenModeCard() {
    val context = LocalContext.current
    val snapshot = LocalRefreshSnapshot.current
    val setMode = LocalDisplayModeSetter.current
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    val modes = display?.supportedModes.orEmpty().sortedWith(compareByDescending<Display.Mode> {
        it.physicalWidth.toLong() * it.physicalHeight
    }.thenByDescending { it.refreshRate })
    var error by remember { mutableStateOf(false) }
    InfoCard("App 螢幕模式") {
        GalaxyText("從系統公開模式中選擇 Galaxy Box 前景視窗的解析度與更新率。這是請求，系統可能忽略；離開 App 後會還原視窗設定。",
            color = Muted, style = GalaxyTypography.bodySmall)
        GalaxyFilterChip(selected = snapshot.preferredModeId == null, onClick = { setMode(null) }, label = { GalaxyText("自動 · 同解析度最高更新率") })
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            modes.forEach { mode ->
                Row(Modifier.fillMaxWidth()) {
                    GalaxyFilterChip(selected = snapshot.preferredModeId == mode.modeId,
                        onClick = { setMode(mode.modeId) },
                        label = { GalaxyText("${mode.physicalWidth} × ${mode.physicalHeight} · ${String.format(Locale.ROOT, "%.2f", mode.refreshRate)} Hz") })
                }
            }
        }
        GalaxyTextButton(onClick = {
            error = runCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }.isFailure
        }) { GalaxyText("開啟系統螢幕設定") }
        if (error) GalaxyText("無法開啟系統螢幕設定。", color = GalaxyPalette.error)
        GalaxyText("全手機的解析度、色彩模式與更新率仍由系統設定管理；此處不修改其他 App，也不繞過省電或溫控限制。",
            color = Muted, style = GalaxyTypography.bodySmall)
    }
}
