package dev.galaxy.toolbox.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject
import java.util.Locale

internal enum class AppLanguage(val key: String, val title: String) {
    ZH_TW("zh-TW", "繁體中文"), ZH_CN("zh-CN", "简体中文"), EN("en", "English");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: when {
            Locale.getDefault().language == "zh" && (Locale.getDefault().script.equals("Hans", true) ||
                Locale.getDefault().country.uppercase(Locale.ROOT) in setOf("CN", "SG", "MY")) -> ZH_CN
            Locale.getDefault().language == "zh" -> ZH_TW
            else -> EN
        }
    }
}

internal enum class TemperatureDisplayUnit(val key: String, val title: String) {
    CELSIUS("c", "攝氏 °C"), FAHRENHEIT("f", "華氏 °F");
    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } ?: CELSIUS }
}

internal val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ZH_TW }
internal val LocalTemperatureDisplayUnit = staticCompositionLocalOf { TemperatureDisplayUnit.CELSIUS }

internal object AppDisplayPreferences {
    private const val STORE = "display"
    fun language(context: Context) = AppLanguage.from(context.getSharedPreferences(STORE, 0).getString("language", null))
    fun temperatureUnit(context: Context) = TemperatureDisplayUnit.from(context.getSharedPreferences(STORE, 0).getString("temperature_unit", null))
}

/** Translation is applied at the shared text boundary; diagnostic raw values stay untouched. */
internal object AppText {
    @Volatile private var installedEnglish: Map<String, String> = emptyMap()
    @Volatile private var installedSimplified: Map<String, String> = emptyMap()
    @Volatile private var simplifiedCharacters: Map<String, String> = emptyMap()

    /** Call once before the first screen is composed; files contain only presentation text. */
    fun install(context: Context) {
        fun read(name: String): Map<String, String> = runCatching {
            val json = context.assets.open("i18n/$name.json").bufferedReader().use { JSONObject(it.readText()) }
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
        installedEnglish = read("en-generated") + read("en")
        installedSimplified = read("zh-CN-generated") + read("zh-CN")
        simplifiedCharacters = read("zh-CN-chars")
        synchronized(simplifiedCache) { simplifiedCache.clear() }
    }

    private val simplifiedCache = object : LinkedHashMap<String, String>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 512
    }
    private val simplifiedPhrases = mapOf(
        "設定" to "设置", "自訂背景" to "自定义背景", "背景組合" to "背景预设",
        "簡約風格" to "简约风格",
        "儲存目前組合" to "保存当前预设", "套用" to "应用", "匯入" to "导入",
        "選擇圖片" to "选择图片", "跳過裁切" to "使用原图", "電池溫度" to "电池温度",
    )
    private fun mainlandTerms(text: String): String = text
        .replace("记忆体", "内存")
        .replace("储存", "存储")
        .replace("资料", "数据")
        .replace("韧体", "固件")
        .replace("萤幕", "屏幕")
        .replace("小工具", "小组件")
    private val english = mapOf(
        "總覽" to "Overview", "硬體" to "Hardware", "電池" to "Battery", "更新" to "Updates", "檢測" to "Tests",
        "設定" to "Settings", "關於" to "About", "關於本應用" to "About this app", "關閉" to "Close", "取消" to "Cancel", "確認" to "Confirm",
        "外觀" to "Appearance", "背景" to "Background", "語言" to "Language", "單位" to "Units",
        "介面風格" to "Interface style", "原有風格" to "Original style", "簡約風格" to "Clean style", "Miuix 風格" to "Miuix style",
        "液態玻璃" to "Liquid glass", "導覽列背景" to "Navigation background",
        "主題" to "Theme", "選擇或拖曳圖片" to "Choose or drop an image",
        "可選 JPG、PNG、WebP、AVIF、BMP、SVG、HEIC、GIF；檔案上限 20 MB。" to "JPG, PNG, WebP, AVIF, BMP, SVG, HEIC and GIF. Maximum file size: 20 MB.",
        "跟隨系統" to "Follow system", "淺色" to "Light", "深色" to "Dark", "動態取色" to "Dynamic color",
        "繁體中文" to "Traditional Chinese", "简体中文" to "Simplified Chinese", "攝氏 °C" to "Celsius °C", "華氏 °F" to "Fahrenheit °F",
        "自訂背景" to "Custom background", "選擇圖片" to "Choose image", "裁切圖片" to "Crop image",
        "跳過裁切" to "Use original", "填滿方式" to "Fill mode", "覆蓋" to "Cover", "適應" to "Contain",
        "拉伸" to "Stretch", "置中" to "Center", "平鋪" to "Tile", "模糊度" to "Blur",
        "亮度" to "Brightness", "遮罩不透明度" to "Overlay opacity", "背景組合" to "Background presets",
        "儲存目前組合" to "Save current preset", "重新命名" to "Rename", "刪除" to "Delete", "套用" to "Apply",
        "恢復預設背景" to "Restore default background", "無自訂背景" to "No custom background",
        "圖片太大，請選擇較小的檔案。" to "Image is too large. Choose a smaller file.",
        "不支援此圖片格式或無法解碼。" to "This image format is unsupported or could not be decoded.",
        "無法讀取圖片。" to "Could not read image.", "尚未儲存背景組合" to "No saved background presets",
        "GIF 尺寸過大，請縮小後再匯入。" to "GIF dimensions are too large. Resize it before importing.",
        "無法儲存背景組合。" to "Could not save background preset.",
        "背景名稱" to "Background name", "使用原圖" to "Use original image",
        "調整裁切" to "Adjust crop",
        "圖片載入中…" to "Loading image…",
        "Galaxy Box · 即時預覽" to "Galaxy Box · Live preview",
        "恢復預設背景？" to "Restore the default background?", "已儲存的背景組合會保留。" to "Saved background presets will be kept.",
        "刪除背景組合？" to "Delete this background preset?", "此操作會移除圖片檔案。" to "This will delete the image file.",
        "已啟用 · 透底模糊、折射與柔和亮邊。" to "On · background blur, refraction and soft highlights.",
        "玻璃已啟用；部分元件使用簡化效果。" to "Glass is on; some elements use a simpler effect.",
        "此裝置目前無法啟用，使用一般卡片。" to "Unavailable on this device; standard cards are shown.",
        "預設關閉，開啟後套用至資訊卡片與導覽列。" to "Off by default; applies to cards and navigation when enabled.",
        "自由比例" to "Free", "16:9" to "16:9", "16:10" to "16:10", "4:3" to "4:3", "1:1" to "1:1",
        "符合螢幕" to "Match screen", "縮放" to "Zoom", "旋轉" to "Rotate",
    )
    private val dynamicEnglish: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("^最早可讀紀錄：(.+)$") to { "Earliest available record: ${it.groupValues[1]}" },
        Regex("^(\\d+) 次（來源回報）$") to { "Reported cycles: ${it.groupValues[1]}" },
        Regex("^(\\d+)%（驅動回報）$") to { "${it.groupValues[1]}% (driver reported)" },
        Regex("^取樣失敗：(.+)$") to { "Sampling failed: ${it.groupValues[1]}" },
        Regex("^原始值 (.+)$") to { "Raw value ${it.groupValues[1]}" },
        Regex("^(.+) · 系統熱壓力等級（不是 °C）$") to { "${translate(it.groupValues[1], AppLanguage.EN)} · System thermal status" },
        Regex("^(.+)，來源 cpuinfo_max_freq。頻率比例不是 CPU 使用率；休眠核心或系統限制時未知。$") to {
            "${it.groupValues[1]}, from cpuinfo_max_freq. The frequency ratio is unavailable if the core sleeps or Android restricts access."
        },
        Regex("^若為兩芯串聯，每芯約 (.+)；接線未確認。$") to { m: MatchResult ->
            "Approx. ${m.groupValues[1]} per cell if wired in series. Wiring unverified."
        },
        Regex("^(.+)：電流方向與系統充放電狀態不一致，可嘗試開啟反轉並比對原始值。不同機型／韌體不一定相同。$") to { m: MatchResult ->
            "${m.groupValues[1]}: Current polarity disagrees with the system charging status. Try reversing polarity and compare readings. This varies by model and firmware."
        },
        Regex("^(v[^\\s]+) · 技術預覽版$") to { m: MatchResult -> "${m.groupValues[1]} · Technical preview" },
        Regex("^電池溫度 (.+)$") to { m: MatchResult -> "Battery temperature ${m.groupValues[1]}" },
        Regex("^(.+) / (.+) GiB 已用$") to { m: MatchResult -> "${m.groupValues[1]} / ${m.groupValues[2]} GiB used" },
        Regex("^可用 (.+) GiB$") to { m: MatchResult -> "${m.groupValues[1]} GiB available" },
        Regex("^(\\d+) / (\\d+) 個核心可讀$") to { m: MatchResult -> "${m.groupValues[1]} / ${m.groupValues[2]} cores readable" },
        Regex("^(.+) · 前景每 2 秒取樣$") to { m: MatchResult -> "${m.groupValues[1]} · Samples every 2 s in foreground" },
        Regex("^最小 (.+) · 最大 (.+)$") to { m: MatchResult -> "Min ${m.groupValues[1]} · Max ${m.groupValues[2]}" },
        Regex("^(.+) 副本$") to { m: MatchResult -> "${m.groupValues[1]} copy" },
        Regex("^縱軸 0–100% · (\\d+) 筆$") to { m: MatchResult -> "Y-axis 0–100% · ${m.groupValues[1]} samples" },
        Regex("^已讀 (\\d+) MiB(.*)$") to { m: MatchResult -> "Read ${m.groupValues[1]} MiB${m.groupValues[2]}" },
        Regex("^已取得 (\\d+) 項電池資料$") to { m: MatchResult -> "Found ${m.groupValues[1]} battery readings" },
        Regex("^匯入時間 (.+)$") to { m: MatchResult -> "Imported ${m.groupValues[1]}" },
        Regex("^已辨識 SoC 來源 · (\\d+)$") to { m: MatchResult -> "Identified SoC sources · ${m.groupValues[1]}" },
        Regex("^其他來源 · 未確認為 SoC · (\\d+)$") to { m: MatchResult -> "Other sources · Not confirmed as SoC · ${m.groupValues[1]}" },
        Regex("^Android 熱狀態 API · 更新 (.+)$") to { m: MatchResult -> "Android thermal status API · Updated ${m.groupValues[1]}" },
        Regex("^更新 (.+) · 每 15 秒讀取$") to { m: MatchResult -> "Updated ${m.groupValues[1]} · Reads every 15 s" },
        Regex("^更新 (.+)$") to { m: MatchResult -> "Updated ${m.groupValues[1]}" },
        Regex("^收合 (.+) 原始依據$") to { m: MatchResult -> "Hide raw evidence for ${m.groupValues[1]}" },
        Regex("^查看 (.+) 原始依據$") to { m: MatchResult -> "View raw evidence for ${m.groupValues[1]}" },
        Regex("^切換(.+) 趨勢$") to { m: MatchResult -> "Show trend for ${translate(m.groupValues[1], AppLanguage.EN)}" },
        Regex("^查看 CPU (\\d+) 資料$") to { m: MatchResult -> "View CPU ${m.groupValues[1]} details" },
        Regex("^查看(.+)詳細資訊$") to { m: MatchResult -> "View details for ${translate(m.groupValues[1], AppLanguage.EN)}" },
        Regex("^背景不透明度 (\\d+)%$") to { m: MatchResult -> "Background opacity ${m.groupValues[1]}%" },
        Regex("^(充電中|已充滿|暫停充電|放電中|未接電源|已接電源|充電狀態) · (.+)$") to { m: MatchResult ->
            val status = mapOf("充電中" to "Charging", "已充滿" to "Full", "暫停充電" to "Charging paused",
                "放電中" to "Discharging", "未接電源" to "Unplugged", "已接電源" to "Plugged in", "充電狀態" to "Charging status")
            "${status[m.groupValues[1]]} · ${m.groupValues[2]}"
        },
        Regex("^電池淨功率 (.+)$") to { m: MatchResult -> "Battery net power ${m.groupValues[1]}" },
        Regex("^快照 (.+)$") to { m: MatchResult -> "Snapshot ${m.groupValues[1]}" },
        Regex("^系統目前仍使用 (.+)$") to { m: MatchResult -> "System is still using ${m.groupValues[1]}" },
        Regex("^編碼已選用；部分選項未套用。目前：(.+)$") to { m: MatchResult -> "Codec selected; some options were not applied. Current: ${m.groupValues[1]}" },
        Regex("^已讀回目前設定：(.+)$") to { m: MatchResult -> "Current settings read back: ${m.groupValues[1]}" },
        Regex("^Android A2DP 回報 · (\\d+) 秒前$") to { m: MatchResult -> "Android A2DP report · ${m.groupValues[1]} s ago" },
        Regex("^系統媒體音量 (.+)$") to { m: MatchResult -> "System media volume ${m.groupValues[1]}" },
        Regex("^耳機電量（系統回報）(.+)$") to { m: MatchResult -> "Headset battery (system reported) ${m.groupValues[1]}" },
        Regex("^系統未建立關聯：(.+)$") to { m: MatchResult -> "System did not establish a connection: ${m.groupValues[1]}" },
        Regex("^目前實際設定：(.+)$") to { m: MatchResult -> "Current effective settings: ${m.groupValues[1]}" },
        Regex("^已送出 (.+) 音訊；請自行確認，不自動判定通過。$") to { m: MatchResult -> "Played ${m.groupValues[1]} audio. Check it yourself; the result is not judged automatically." },
        Regex("^播放路徑：(.+) · (.+)；請自行確認。$") to { m: MatchResult -> "Playback route: ${m.groupValues[1]} · ${m.groupValues[2]}. Check it yourself." },
        Regex("^無法播放（(.+)）$") to { m: MatchResult -> "Could not play (${m.groupValues[1]})" },
        Regex("^播放 (.+)$") to { m: MatchResult -> "Play ${m.groupValues[1]}" },
        Regex("^錄音讀取失敗 (.+)$") to { m: MatchResult -> "Recording read failed: ${m.groupValues[1]}" },
        Regex("^無法取得麥克風訊號（(.+)）$") to { m: MatchResult -> "Could not capture microphone input (${m.groupValues[1]})" },
        Regex("^取樣率：(.+) · 聲道：(.+)$") to { m: MatchResult -> "Sample rates: ${m.groupValues[1]} · Channels: ${m.groupValues[2]}" },
        Regex("^端點接受格式：(.+)$") to { m: MatchResult -> "Endpoint formats: ${m.groupValues[1]}" },
        Regex("^(.+) 品牌標誌$") to { m: MatchResult -> "${m.groupValues[1]} brand logo" },
        Regex("^已讀取 (\\d+) 份文字報告 · 辨識 (\\d+) 個電池區段$") to { m: MatchResult -> "Read ${m.groupValues[1]} text reports · Identified ${m.groupValues[2]} battery sections" },
        Regex("^已略過 (\\d+) 條電池區段以外的超長行；這不等於電池欄位讀取失敗。$") to { m: MatchResult -> "Skipped ${m.groupValues[1]} long lines outside battery sections; this does not mean battery fields failed to load." },
        Regex("^另有 (\\d+) 行電池欄位位於未辨識或其他服務區段，未採用其值。$") to { m: MatchResult -> "${m.groupValues[1]} battery field lines were outside recognized sections; their values were not used." },
        Regex("^原始依據僅保留每欄前 16 行；另有 (\\d+) 行未列出，衝突判定仍涵蓋完整掃描。$") to { m: MatchResult -> "Showing only the first 16 evidence lines per field; ${m.groupValues[1]} more lines are omitted. Conflict checks use the full scan." },
        Regex("^匯入時間 (.+)；來源裝置未驗證$") to { m: MatchResult -> "Imported ${m.groupValues[1]}; source device not verified" },
        Regex("^逐項檢查全部 (\\d+) 項$") to { m: MatchResult -> "Check all ${m.groupValues[1]} items" },
        Regex("^已記錄 (\\d+) 項手動判讀結果$") to { m: MatchResult -> "Recorded ${m.groupValues[1]} manual results" },
        Regex("^結果由你手動判讀 · 剩餘 (\\d+) 項$") to { m: MatchResult -> "Judge results yourself · ${m.groupValues[1]} items left" },
        Regex("^安全性更新 (.+)$") to { m: MatchResult -> "Security patch ${m.groupValues[1]}" },
        Regex("^(.+) 需要你允許相關權限，拒絕可略過此項。$") to { m: MatchResult -> "${m.groupValues[1]} needs permission. You can skip this item if you decline." },
        Regex("^允許 (.+) 權限$") to { m: MatchResult -> "Allow ${m.groupValues[1]} permission" },
        Regex("^狀態碼 (.+)$") to { m: MatchResult -> "Status code ${m.groupValues[1]}" },
        Regex("^觸控 (\\d+) 指 · 本次最多 (\\d+) 指$") to { m: MatchResult -> "${m.groupValues[1]} touches · Session maximum ${m.groupValues[2]}" },
        Regex("^已儲存 (.+) PNG$") to { m: MatchResult -> "Saved ${m.groupValues[1]} PNG" },
        Regex("^無法儲存圖片：(.+)$") to { m: MatchResult -> "Could not save image: ${m.groupValues[1]}" },
        Regex("^邊框寬度 (.+)$") to { m: MatchResult -> "Border width ${m.groupValues[1]}" },
        Regex("^系統 R 角：上 (.+)／下 (.+)$") to { m: MatchResult -> "System corner radius: top ${m.groupValues[1]} / bottom ${m.groupValues[2]}" },
        Regex("^此畫面亮度 (.+)$") to { m: MatchResult -> "Screen brightness ${m.groupValues[1]}" },
        Regex("^本機型號 · (.+)$") to { m: MatchResult -> "Device model · ${m.groupValues[1]}" },
        Regex("^已取得 (\\d+) 類即時數據$") to { m: MatchResult -> "Found ${m.groupValues[1]} live data types" },
        Regex("^取樣 (.+)$") to { m: MatchResult -> "Sampled ${m.groupValues[1]}" },
        Regex("^(.+) · 前景每 2 秒取樣$") to { m: MatchResult -> "${m.groupValues[1]} · Samples every 2 s in foreground" },
        Regex("^CPU (\\d+) 核心頻率$") to { m: MatchResult -> "CPU ${m.groupValues[1]} core frequency" },
        Regex("^收合 (.+)$") to { m: MatchResult -> "Collapse ${translate(m.groupValues[1], AppLanguage.EN)}" },
        Regex("^展開 (.+)（共 (\\d+) 項）$") to { m: MatchResult -> "Expand ${translate(m.groupValues[1], AppLanguage.EN)} (${m.groupValues[2]} items)" },
        Regex("^(.+) 筆結果（最多顯示 (\\d+) 筆，請縮小搜尋範圍）$") to { m: MatchResult -> "${m.groupValues[1]} results (showing up to ${m.groupValues[2]}; narrow your search)" },
        Regex("^讀取(.+)原始資料$") to { m: MatchResult -> "Read raw ${translate(m.groupValues[1], AppLanguage.EN)} data" },
        Regex("^上次手動讀取 · (.+)$") to { m: MatchResult -> "Last manual read · ${m.groupValues[1]}" },
        Regex("^已取得 (\\d+) 項；各來源分列。$") to { m: MatchResult -> "Found ${m.groupValues[1]} items; sources listed separately." },
        Regex("^已匯出 (\\d+) 個來源；僅儲存至你選取的位置。$") to { m: MatchResult -> "Exported ${m.groupValues[1]} sources to your chosen location." },
        Regex("^匯出失敗：(.+)$") to { m: MatchResult -> "Export failed: ${m.groupValues[1]}" },
        Regex("^(.+) · 溫度趨勢$") to { m: MatchResult -> "${translate(m.groupValues[1], AppLanguage.EN)} · Temperature trend" },
        Regex("^縱軸：(°C|°F) · 橫軸：距今時間。失敗或停止輪詢的時段留空，不補 0。$") to { m: MatchResult -> "Y-axis: ${m.groupValues[1]} · X-axis: time ago. Failed or paused sampling stays blank, not zero." },
        Regex("^(\\d+)dp 高$") to { m: MatchResult -> "${m.groupValues[1]} dp high" },
    )
    private fun englishDynamic(text: String): String? = dynamicEnglish.firstNotNullOfOrNull { (pattern, render) ->
        pattern.matchEntire(text)?.let(render)
    }

    private val reviewedTemplates = listOf(
        """Android (.+?)\s+·\s+(\d+) 個邏輯核心""" to "Android {1} · {2} logical cores",
        """CPU (\d+) 當前頻率""" to "CPU {1} current frequency",
        """CPU (\d+) 標示頻率範圍""" to "CPU {1} reported frequency range",
        """CPU (\d+) 調頻策略""" to "CPU {1} scaling governor",
        """CPU (\d+) 共享調頻核心""" to "CPU {1} related cores",
        """CPU (\d+) 調頻驅動""" to "CPU {1} frequency scaling driver",
        """CPU (\d+) 可用頻率""" to "CPU {1} available frequencies",
        """CPU (\d+) · 調頻能力""" to "CPU {1} · Frequency scaling",
        """(處理器|圖形|溫度|記憶體|螢幕|相機|網路|音訊|感測器|編解碼|裝置)資料""" to "{1} information",
        """(\d+) 分鐘""" to "{1} min",
        """(\d+) 次""" to "{1}",
        """上層邏輯 ID：(.+)""" to "Parent logical IDs: {1}",
        """相機 · ID (.+)""" to "Camera · ID {1}",
        """實體相機 · ID (.+)""" to "Physical camera · ID {1}",
        """代碼 (.+)""" to "Code {1}",
        """原始代碼 (.+)""" to "Raw code {1}",
        """格式代碼 (.+)""" to "Format code {1}",
        """(.+) 步""" to "{1} steps",
        """(.+) EV / 步""" to "{1} EV per step",
        """([\d, ]+) 聲道""" to "{1} channels",
        """無法取得（(.+)）""" to "Unavailable ({1})",
        """權限不足（(.+)）""" to "Permission denied ({1})",
        """讀取失敗：(.+)；保留上次快照。""" to "Read failed: {1}. The previous snapshot is retained.",
        """讀取失敗：(.+)""" to "Read failed: {1}",
        """無法取得麥克風清單（(.+)）""" to "Microphone list unavailable ({1})",
        """無法啟動系統辨識：(.+)""" to "Could not start Android authentication: {1}",
        """系統回覆 (.+?)：(.*)""" to "Android response {1}: {2}",
        """音量＋：(\d+) 次　音量－：(\d+) 次""" to "Volume up: {1} · Volume down: {2}",
        """(.+) · (紅|綠|藍|白|黑)色""" to "{1} · {2}",
        """藍牙 (.+?) · 已配對 (\d+) 個裝置""" to "Bluetooth {1} · {2} paired devices",
        """藍牙狀態無法取得（(.+)）""" to "Bluetooth status unavailable ({1})",
        """已讀到 NFC 標籤 · (.+)""" to "NFC tag detected · {1}",
        """無法開始：(.+)""" to "Could not start: {1}",
        """無法取得定位：(.+)""" to "Location unavailable: {1}",
        """來源 (.+?) · (\d+) 秒前""" to "Source: {1} · {2} s ago",
        """螢幕更新率 · (.+)""" to "Display refresh rate · {1}",
        """前景請求 · (.+)""" to "Foreground request · {1}",
        """讀取時間 (.+?) · Linux kB 依 KiB 解讀""" to "Read at {1} · Linux kB interpreted as KiB",
        """加入(.+)""" to "Add {1}",
        """(收合|展開)([^ ].*)""" to "{1} {2}",
        """規則：(.+)""" to "Rule: {1}",
        """排除；(.+)""" to "Excluded. {1}",
        """本次允許；(.+) 依摘要優先規則選取""" to "Included. {1} is selected by the summary priority rules.",
        """已辨識 (\d+) 個 SoC 來源，目前無有效摘要。""" to "Identified {1} SoC sources. A valid summary is unavailable.",
        """讀取 epoch ms (.+)""" to "Read at (epoch ms): {1}",
        """最後嘗試 (.+)""" to "Last attempt: {1}",
        """單位 (.+)""" to "Unit: {1}",
        """原始單位 (.+)""" to "Raw unit: {1}",
        """GPU 溫度 (.+)""" to "GPU temperature {1}",
        """編碼代號 (.+)""" to "Codec ID {1}",
        """所選(.+)不在耳機可選清單中""" to "The headset does not offer the selected {1}",
        """無法讀取或切換藍牙編碼（(.+)）""" to "Could not read or switch Bluetooth codecs ({1})",
        """(.+) 模式""" to "{1} mode",
        """完整型號 (.+)""" to "Exact model {1}",
        """(.+)（共用型號）""" to "{1} (shared model number)",
        """(.+)（版本待確認）""" to "{1} (variant unverified)",
        """([0-9]+) 年 Android 手機（已核對）""" to "{1} Android phone (verified)",
        """([0-9]+) 年 Android 平板（已核對）""" to "{1} Android tablet (verified)",
        """([0-9]+) 年手機（2018 年以前）""" to "{1} phone (before 2018)",
        """([0-9]+) 年平板（2018 年以前）""" to "{1} tablet (before 2018)",
        """([0-9]+) 年(.+)（非手機）""" to "{1} {2} (non-phone)",
        """模式 (\d+)""" to "Mode {1}",
        """寬 (.+?) · 高 (.+)""" to "Width {1} · Height {2}",
        """幀率 (.+?) · 位元率 (.+)""" to "Frame rate {1} · Bit rate {2}",
        """聲道上限 (.+?) · 取樣率 (.+?) · (.+)""" to "Maximum channels {1} · Sample rates {2} · {3}",
        """旗標限制：(.+)；熱敏電阻可能回報電壓，不猜換算。""" to "Flag restriction: {1}. The thermistor may report a voltage.",
        """使用者已反轉正負；原始值 (.+) µA，僅更改顯示與功率方向。""" to "Displayed current and power polarity is reversed. Raw value: {1} µA.",
        """正值代表流入，負值代表流出；原始值 (.+) µA。""" to "Positive indicates charging and negative indicates discharging. Raw value: {1} µA.",
        """目前剩餘電荷，不是滿充容量；數值疑似百分比或單位異常時隱藏。原始值 (.+)""" to "Remaining charge. Suspect units or percentage readings are hidden. Raw value: {1}",
        """Samsung 來源回覆 HTTP (\d+)。可能未公開、限制存取或型號／CSC 不符；無法判定更新。""" to "Samsung returned HTTP {1}. The source may be restricted or the model/CSC may not match. Update status is unavailable.",
        """XML：(.+)""" to "XML: {1}",
        """官方紀錄：(.+)""" to "Official history: {1}",
        """XML 來源未取得：(.+)。以下為更新紀錄，不能等同 OTA 推送狀態。""" to "XML unavailable: {1}. The entries below are release history. OTA eligibility is determined by Android.",
        """目錄探索結果，不能代替個別檔案的讀取狀態。(.+)""" to "Directory discovery result. Individual read status is shown separately. {1}",
        """已核對用於 (\d+) 年起的 Android 手機或平板""" to "Verified on an Android phone or tablet from {1} onward",
        """(.+?)；以系統列出的唯一麥克風特徵計數，韌體可能未公開全部硬體，不代表拆機清點。""" to "{1}. Counts distinct microphones listed by Android. Firmware may omit some hardware.",
        """(.+?) 此欄位列於需相機權限清單；允許後仍可能受韌體限制。""" to "{1} This field requires camera permission and may still be restricted by firmware.",
    ).map { (pattern, template) -> Regex(pattern) to template }
    // Android's ICU regex engine requires both literal braces to be escaped.
    private val templateSlot = Regex("""\{(\d+)\}""")

    private fun reviewedTemplate(text: String): String? = reviewedTemplates.firstNotNullOfOrNull { (pattern, template) ->
        pattern.matchEntire(text)?.let { match ->
            templateSlot.replace(template) { token -> translate(match.groupValues[token.groupValues[1].toInt()], AppLanguage.EN) }
        }
    }
    private val han = Regex("[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]")

    fun translate(text: String, language: AppLanguage): String = when (language) {
        AppLanguage.ZH_TW -> text
        AppLanguage.ZH_CN -> mainlandTerms(simplifiedPhrases[text] ?: installedSimplified[text] ?: synchronized(simplifiedCache) {
            simplifiedCache[text] ?: buildString(text.length) {
                text.forEach { append(simplifiedCharacters[it.toString()] ?: it.toString()) }
            }.also { simplifiedCache[text] = it }
        })
        AppLanguage.EN -> english[text] ?: installedEnglish[text] ?: if (!han.containsMatchIn(text)) text
            else englishDynamic(text) ?: reviewedTemplate(text) ?: when {
                '\n' in text -> text.split('\n').joinToString("\n") { translate(it, language) }
                " · " in text -> text.split(" · ").joinToString(" · ") { translate(it, language) }
                '、' in text -> {
                    val parts = text.split('、')
                    val translated = parts.map { translate(it, language) }
                    if (parts == translated) text else translated.joinToString(", ")
                }
                else -> text.trim().takeIf { it != text }?.let { translate(it, language) } ?: text
            }
    }
    fun forContext(context: Context, text: String): String = translate(text, AppDisplayPreferences.language(context))
}

/** Catalog prose is app-authored. Keep device names, paths, and vendor readings outside this conversion. */
internal fun catalogReadingValue(reading: dev.galaxy.toolbox.model.Reading, language: AppLanguage): String {
    val value = reading.value ?: return "未知"
    if (language != AppLanguage.EN || (reading.group != "CPU 公開規格" &&
        reading.label !in setOf("CPU 品牌／架構（規格）", "CPU 製程（規格）"))) return value
    return cpuSpecEnglish.fold(value) { text, (source, translated) -> text.replace(source, translated) }
}

private val cpuSpecEnglish = listOf(
    "（依效能／效率分組）" to " (performance and efficiency groups)",
    "（依平台版本）" to " (varies by platform)",
    "64 位元規格" to "64-bit specification", "32 位元規格" to "32-bit specification",
    "64 位元" to "64-bit", "32 位元" to "32-bit",
    "官方規格" to "official specification",
    "第二代" to "2nd generation ", "第三代" to "3rd generation ",
    "第四代" to "4th generation ", "第五代" to "5th generation ",
    "效能／效率核心" to "performance and efficiency cores",
    "效率核心" to "efficiency cores", "效能核心" to "performance cores",
    "大核心" to "prime cores", "中核心" to "mid-tier cores", "小核心" to "efficiency cores",
    "核心群組" to "core groups", "自訂" to "custom", "最高 " to "up to ",
    " 群組 " to " cluster ", " 版本" to " variant",
    "系統級" to "system-level", "另有 " to "also available at ", "含 " to "including ",
    "：" to ": ", "；" to "; ",
    "（" to " (", "）" to ")", "｜" to " · ", "／" to " / ", "＋" to " + ",
)

internal object TemperatureText {
    private val reading = Regex("(-?\\d+(?:\\.\\d+)?)\\s*°C")
    fun display(text: String, unit: TemperatureDisplayUnit): String {
        if (unit == TemperatureDisplayUnit.CELSIUS) return text
        if (text.trim() == "0.1 °C" || text.trim() == "0.001 °C") return text // Raw sensor unit scales, not readings.
        return reading.replace(text) { match ->
            val c = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            String.format(Locale.ROOT, "%.1f °F", c * 9.0 / 5.0 + 32.0)
        }
    }
    fun displayNumber(celsius: Double, unit: TemperatureDisplayUnit): Double =
        if (unit == TemperatureDisplayUnit.CELSIUS) celsius else celsius * 9.0 / 5.0 + 32.0
}
