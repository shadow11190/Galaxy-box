package dev.galaxy.toolbox.data

import android.util.Xml
import dev.galaxy.toolbox.model.FirmwareQuery
import dev.galaxy.toolbox.model.FirmwareResult
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class FirmwareRepository {
    fun fetch(query: FirmwareQuery): FirmwareResult {
        return try { fetchXml(query) }
        catch (xmlError: Exception) {
            try {
                val page = SamsungChangelogParser.englishPage(query, getText(query.changelogUrl, 512 * 1024))
                val entry = SamsungChangelogParser.firstEntry(getText(page, 1024 * 1024))
                FirmwareResult(query, entry.build, Instant.now().toString(), page,
                    "Samsung 官方更新紀錄頁首筆", entry.releaseDate, entry.securityPatch,
                    "XML 來源未取得：${xmlError.message}。以下為更新紀錄，不能等同 OTA 推送狀態。")
            } catch (docError: Exception) {
                error("XML：${xmlError.message}\n官方紀錄：${docError.message}")
            }
        }
    }

    private fun fetchXml(query: FirmwareQuery): FirmwareResult {
            val text = getText(query.xmlUrl, 256 * 1024)
            require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "不支援此 XML 格式" }
            val parser = Xml.newPullParser()
            parser.setInput(text.reader())
            var latest: String? = null
            var inFirmware = false
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "firmware") inFirmware = true
                if (inFirmware && parser.eventType == XmlPullParser.START_TAG && parser.name == "latest") latest = parser.nextText().trim().takeIf { it.isNotEmpty() }
                if (parser.eventType == XmlPullParser.END_TAG && parser.name == "firmware") inFirmware = false
                parser.next()
            }
            return FirmwareResult(query, requireNotNull(latest) { "來源未提供 latest；無法判定更新" }, Instant.now().toString())
    }

    private fun getText(url: String, limit: Int): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/xml,text/xml,text/html")
            val code = connection.responseCode
            check(code == 200) { "Samsung 來源回覆 HTTP $code。可能未公開、限制存取或型號／CSC 不符；無法判定更新。" }
            val bytes = connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= limit) { "來源回應過大" }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
}
