package dev.galaxy.toolbox.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.galaxy.toolbox.data.AppUsageStats
import dev.galaxy.toolbox.data.UsageMetric
import dev.galaxy.toolbox.data.UsagePeriod
import dev.galaxy.toolbox.data.UsageRanking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AppUsageCard() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(AppUsageStats.allowed(context)) }
    var settingsFailed by remember { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(UsagePeriod.DAY) }
    var metric by rememberSaveable { mutableStateOf(UsageMetric.SCREEN) }
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = AppUsageStats.allowed(context)
                refresh++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    InfoCard("App 用量分析") {
        if (!expanded) {
            GalaxyPrimaryTextButton("查看 App 用量", { expanded = true })
        } else if (!allowed) {
            GalaxyText("允許使用情況存取後，可在本機查看 Android 留存的 App 用量。", color = Muted)
            GalaxyPrimaryTextButton("開啟使用情況存取設定", {
                settingsFailed = runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }.isFailure
            })
            if (settingsFailed) GalaxyText("無法開啟使用情況存取設定。", color = GalaxyPalette.error)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UsagePeriod.entries.forEach { option -> GalaxyChoiceChip(period == option, { period = option }, option.title) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UsageMetric.entries.forEach { option -> GalaxyChoiceChip(metric == option, { metric = option }, option.title) }
            }
            val result by produceState<UsageRanking?>(null, period, metric, refresh) {
                value = null
                value = withContext(Dispatchers.IO) { AppUsageStats.read(context.applicationContext, period, metric) }
            }
            val resultForSelection = result
            when {
                resultForSelection == null -> GalaxyText("載入中…", color = Muted)
                resultForSelection.rows == null -> GalaxyText("此項統計無法取得。", color = Muted)
                else -> {
                    val locale = Locale.forLanguageTag(LocalAppLanguage.current.key)
                    val format = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
                    GalaxyText("${format.format(Date(resultForSelection.from))} – ${format.format(Date(resultForSelection.to))}",
                        color = Muted, style = GalaxyTypography.labelSmall)
                    if (metric == UsageMetric.SCREEN) {
                        GalaxyText("使用 Android 的前景時間日統計，分割畫面中的多個 App 可能重疊。",
                            color = Muted, style = GalaxyTypography.bodySmall)
                        resultForSelection.firstRecorded?.takeIf { it > resultForSelection.from }?.let {
                            GalaxyText("最早可讀紀錄：${format.format(Date(it))}", color = Muted, style = GalaxyTypography.bodySmall)
                        }
                    } else {
                        GalaxyText("流量依系統記帳的 UID 統計，含上傳與下載，近期用量可能延後入帳。",
                            color = Muted, style = GalaxyTypography.bodySmall)
                    }
                    val rows = resultForSelection.rows
                    if (rows.isEmpty()) GalaxyText("此期間沒有可讀紀錄。", color = Muted)
                    rows.forEach { row ->
                        val value = if (metric == UsageMetric.SCREEN) {
                            val seconds = row.value / 1000
                            String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                        } else Formatter.formatShortFileSize(context, row.value)
                        GalaxyDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                GalaxyText(row.label)
                                if (row.source.isNotEmpty() && row.source != row.label)
                                    GalaxyText(row.source, color = Muted, style = GalaxyTypography.labelSmall)
                            }
                            GalaxyText(value, color = Mint)
                        }
                    }
                }
            }
            GalaxyTextButton("重新讀取", { refresh++ })
        }
        if (expanded) GalaxyTextButton("收合 App 用量", { expanded = false })
    }
}
