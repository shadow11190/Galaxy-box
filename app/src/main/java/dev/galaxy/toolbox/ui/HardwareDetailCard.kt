package dev.galaxy.toolbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.model.*

@Composable internal fun HardwareDetailCard(title: String, readings: List<Reading>, category: HardwareCategory, onReading: (Reading) -> Unit) {
    var expanded by rememberSaveable(title, category) { mutableStateOf(false) }
    val initialCount = when (category) { HardwareCategory.CAMERA -> 8; HardwareCategory.SENSORS -> 2; else -> 7 }
    val visible = if (expanded) readings else readings.take(initialCount)
    InfoCard(title) {
        if (category == HardwareCategory.CAMERA && title.contains("ID")) {
            GalaxyText("Camera2 公開中繼資料", color = Mint, style = GalaxyTypography.labelMedium)
        }
        visible.forEachIndexed { index, reading ->
            if (index > 0) GalaxyDivider(color = Muted.copy(alpha = .12f))
            HardwareDetailRow(reading, onReading)
        }
        if (readings.size > initialCount) GalaxyTextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            GalaxyText(if (expanded) "收合 $title" else "展開 $title（共 ${readings.size} 項）")
        }
    }
}

@Composable private fun HardwareDetailRow(reading: Reading, onReading: (Reading) -> Unit) {
    val language = LocalAppLanguage.current
    val large = LocalDensity.current.fontScale > 1.3f
    val value = catalogReadingValue(reading, language)
    val prominent = reading.label in listOf("最大 JPEG（一般模式）", "處理器名稱", "GPU 名稱", "啟動時顯示模式")
    val color = if (prominent && reading.value != null) Mint else if (reading.value == null) Muted else GalaxyPalette.onSurface
    val style = if (prominent) GalaxyTypography.titleMedium else GalaxyTypography.bodyMedium
    BoxWithConstraints(Modifier.fillMaxWidth().clickable(role = Role.Button,
        onClickLabel = AppText.translate("查看${reading.label}詳細資訊", language)) { onReading(reading) }.padding(vertical = 9.dp)) {
        if (large || maxWidth < 290.dp || prominent) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                GalaxyText(reading.label, color = Muted, style = GalaxyTypography.labelLarge)
                GalaxyText(value, color = color, style = style, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GalaxyText(reading.label, Modifier.weight(.42f), color = Muted, style = GalaxyTypography.bodyMedium)
            GalaxyText(value, Modifier.weight(.58f), color = color, style = style, textAlign = TextAlign.End, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
