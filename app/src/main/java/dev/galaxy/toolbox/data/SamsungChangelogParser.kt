package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.FirmwareQuery

data class ChangelogEntry(val build: String, val releaseDate: String?, val securityPatch: String?)

/** Narrow, fail-closed parser for Samsung's public English changelog layout. No script execution. */
object SamsungChangelogParser {
    fun englishPage(query: FirmwareQuery, html: String): String {
        val model = Regex.escape(query.model)
        val id = Regex("[\"']\\.\\./\\.\\./$model/(\\d{6,20})/eng\\.html[\"']")
            .find(html)?.groupValues?.get(1) ?: error("官方紀錄入口未提供可辨識的英文頁面")
        return "https://doc.samsungmobile.com/${query.model}/$id/eng.html"
    }
    fun firstEntry(html: String): ChangelogEntry {
        val buildPattern = Regex("<strong>\\s*Build Number\\s*:\\s*</strong>\\s*([A-Z0-9]+)\\s*</div>", RegexOption.IGNORE_CASE)
        val first = buildPattern.find(html) ?: error("官方更新紀錄格式未辨識；無法判定版本")
        val next = buildPattern.find(html, first.range.last + 1)
        val block = html.substring(first.range.first, next?.range?.first ?: html.length)
        fun date(label: String): String? {
            val raw = Regex("<strong>\\s*$label\\s*:\\s*</strong>\\s*(\\d{4}-\\d{2}-\\d{2})\\s*</div>", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1) ?: return null
            return runCatching { java.time.LocalDate.parse(raw).toString() }.getOrNull()
        }
        return ChangelogEntry(first.groupValues[1], date("Release Date"), date("Security patch level"))
    }
}
