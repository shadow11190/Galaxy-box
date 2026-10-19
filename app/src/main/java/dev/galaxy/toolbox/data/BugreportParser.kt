package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.zip.ZipFile

/** Progress refers to the current phase, never to a fabricated overall percentage. */
data class ImportProgress(val phase: String, val bytes: Long = 0, val total: Long? = null, val entry: String? = null)

object BugreportParser {
    private const val MAX_TEXT = 16L * 1024 * 1024 * 1024
    private const val MAX_LINE = 16 * 1024
    private const val MAX_ENTRIES = 100_000
    private val reportName = Regex("(?:bugreport|dumpstate)[^/]*\\.(?:txt|log)", RegexOption.IGNORE_CASE)
    private fun isReport(name: String) = reportName.matches(name.replace('\\', '/').substringAfterLast('/'))

    /** Seekable documents (including ZIP64) are read in place; attachments are never inflated. */
    fun parseZip(channel: SeekableByteChannel, checkCancelled: () -> Unit = {}, onProgress: (ImportProgress) -> Unit = {}): List<Evidence> {
        return parseZipReport(channel, checkCancelled, onProgress).fields
    }
    fun parseZipReport(channel: SeekableByteChannel, checkCancelled: () -> Unit = {}, onProgress: (ImportProgress) -> Unit = {}): ParsedBatteryReport {
        onProgress(ImportProgress("分析 ZIP 目錄 · 直接讀取，不複製整包"))
        val guarded = ReadOnlyChannel(channel, checkCancelled)
        val scanner = Scanner(checkCancelled, onProgress)
        ZipFile.builder().setSeekableByteChannel(guarded).setIgnoreLocalFileHeader(true).get().use { zip ->
            val entries = zip.entries
            val reports = mutableListOf<org.apache.commons.compress.archivers.zip.ZipArchiveEntry>()
            var count = 0
            while (entries.hasMoreElements()) {
                checkCancelled()
                val entry = entries.nextElement()
                require(++count <= MAX_ENTRIES) { "ZIP 項目超過 100,000 筆，無法安全索引" }
                if (!entry.isDirectory && isReport(entry.name)) reports += entry
            }
            require(reports.isNotEmpty()) { "ZIP 內未找到 bugreport／dumpstate 的 TXT 或 LOG；請選取 Android 或 Samsung 的原始錯誤報告" }
            val total = reports.map { it.size }.takeIf { sizes -> sizes.all { it >= 0 } }?.fold(0L) { sum, n -> Math.addExact(sum, n) }
            require(total == null || total <= MAX_TEXT) { "報告文字超過 16 GiB，已停止；不需要手動解壓，可重新產生較短時段的錯誤報告" }
            reports.forEach { entry ->
                checkCancelled()
                require((entry.method == 0 || entry.method == 8) && zip.canReadEntryData(entry) && !entry.generalPurposeBit.usesEncryption()) {
                    "報告項目加密或使用不支援的壓縮格式；請選取 Android 原始 ZIP（STORED／DEFLATE）"
                }
                val crc = CRC32()
                CheckedInputStream(zip.getInputStream(entry), crc).use { scanner.scan(it, entry.name.take(240), total) }
                require(entry.crc < 0 || crc.value == entry.crc) { "報告 CRC 檢查失敗，ZIP 可能未完整下載或已損毀" }
            }
        }
        return scanner.result()
    }

    /** TXT and non-seekable document providers. Only a streaming provider needs a private ZIP copy. */
    fun parse(input: InputStream, cacheDirectory: File? = null, onProgress: (ImportProgress) -> Unit = {},
        inputSize: Long? = null, checkCancelled: () -> Unit = {}): List<Evidence> {
        return parseReport(input, cacheDirectory, onProgress, inputSize, checkCancelled).fields
    }
    fun parseReport(input: InputStream, cacheDirectory: File? = null, onProgress: (ImportProgress) -> Unit = {},
        inputSize: Long? = null, checkCancelled: () -> Unit = {}): ParsedBatteryReport {
        val scanner = Scanner(checkCancelled, onProgress)
        val peek = PushbackInputStream(input.buffered(64 * 1024), 4)
        val signature = ByteArray(4)
        var n = 0
        while (n < 4) { checkCancelled(); val byte = peek.read(); if (byte < 0) break; signature[n++] = byte.toByte() }
        if (n > 0) peek.unread(signature, 0, n)
        val zip = n >= 2 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()
        if (!zip) { scanner.scan(peek, "", inputSize); return scanner.result() }
        if (cacheDirectory != null) {
            require(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) { "無法建立私有暫存區" }
            val temporary = File.createTempFile("bugreport-", ".zip", cacheDirectory)
            try {
                copyDocument(peek, temporary, checkCancelled, onProgress, inputSize)
                return FileInputStream(temporary).use { parseZipReport(it.channel, checkCancelled, onProgress) }
            } finally { temporary.delete() }
        }
        // Compatibility path for stream-only callers; production always supplies a cache directory.
        ZipInputStream(peek).use { archive ->
            var count = 0
            var reports = 0
            var entry = archive.nextEntry
            val buffer = ByteArray(64 * 1024)
            var skipped = 0L
            while (entry != null) {
                checkCancelled()
                require(++count <= MAX_ENTRIES) { "ZIP 項目超過 100,000 筆" }
                if (!entry.isDirectory && isReport(entry.name)) { reports++; scanner.scan(archive, entry.name.take(240), null) }
                else while (true) {
                    checkCancelled()
                    val read = archive.read(buffer)
                    if (read < 0) break
                    skipped += read
                    require(skipped <= MAX_TEXT) { "串流附件資料超過 16 GiB，請從本機檔案來源選取報告" }
                }
                archive.closeEntry(); entry = archive.nextEntry
            }
            require(reports > 0) { "ZIP 內未找到 bugreport／dumpstate 的 TXT 或 LOG" }
        }
        return scanner.result()
    }

    private fun copyDocument(input: InputStream, target: File, check: () -> Unit, progress: (ImportProgress) -> Unit, total: Long?) {
        val bytes = ByteArray(256 * 1024)
        var copied = 0L
        var last = 0L
        progress(ImportProgress("檔案來源僅支援串流 · 正在建立私有暫存", 0, total))
        target.outputStream().buffered().use { output ->
            while (true) {
                check()
                val read = input.read(bytes)
                if (read < 0) break
                if (copied % (16 * 1024 * 1024) < bytes.size) {
                    require(target.parentFile!!.usableSpace > read + 16L * 1024 * 1024) { "私有暫存空間不足；請先把 ZIP 下載到手機本機儲存，再從系統檔案選取" }
                }
                output.write(bytes, 0, read); copied += read
                val now = System.nanoTime()
                if (now - last >= 250_000_000) { progress(ImportProgress("檔案來源僅支援串流 · 正在建立私有暫存", copied, total)); last = now }
            }
        }
        progress(ImportProgress("私有暫存完成", copied, total))
    }

    private class Scanner(val check: () -> Unit, val progress: (ImportProgress) -> Unit) {
        private class Field {
            val values = linkedSetOf<Int>()
            val statuses = mutableSetOf<ImportedFieldStatus>()
            val lines = linkedSetOf<String>()
        }
        private val candidates = linkedMapOf<String, Field>()
        private val sources = linkedSetOf<String>()
        private var decoded = 0L
        private var skippedLines = 0
        private var batterySections = 0
        private var outsideFields = 0
        private var omittedLines = 0
        private var lastProgress = 0L
        private val started = System.nanoTime()
        private val patterns = listOf(
            "循環次數" to Regex("^\\s*(?:cycle\\s+count|cycle_count|mCycleCount|batteryCycleCount)\\s*[:=]\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE),
            "Samsung ASOC" to Regex("^\\s*mSavedBatteryAsoc\\s*[:=]\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE),
            "Samsung 使用量原始值" to Regex("^\\s*mSavedBatteryUsage\\s*[:=]\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE)
        )
        private val serviceHeader = Regex("^(?:-+\\s*)?DUMP\\s+OF\\s+SERVICE\\s+(?:(?:CRITICAL|HIGH|NORMAL|DEFAULT)\\s+)?([^:\\s]+)\\s*:", RegexOption.IGNORE_CASE)
        private val currentBatteryHeader = Regex("^Current\\s+Battery\\s+Service\\s+state\\s*:\\s*$", RegexOption.IGNORE_CASE)
        private val backupHeader = Regex("^(?:\\[[^\\]\\r\\n]{1,80}])?\\s*BatteryInfoBackUp\\s*:?\\s*$", RegexOption.IGNORE_CASE)
        private val otherHeader = Regex("^[-=]{3,}\\s*[A-Za-z\\[].*[-=]{3,}$")
        private val integer = Regex("[+-]?\\d+")
        private fun record(key: String, raw: String, line: String) {
            val field = candidates.getOrPut(key) { Field() }
            if (field.lines.size < 16) field.lines += line else omittedLines++
            fun keepImportantLine() {
                if (line !in field.lines) {
                    field.lines.lastOrNull()?.let(field.lines::remove)
                    field.lines += line
                }
            }
            fun mark(status: ImportedFieldStatus) {
                if (field.statuses.add(status)) keepImportantLine()
            }
            if (raw.lowercase(java.util.Locale.ROOT) in setOf("null", "unsupported", "not supported", "n/a")) {
                mark(ImportedFieldStatus.UNSUPPORTED); return
            }
            val tokens = if (raw.startsWith('[') && raw.endsWith(']')) raw.substring(1, raw.length - 1).split(',').map(String::trim)
                else listOf(raw.trim())
            if (tokens.isEmpty() || tokens.size > 16 || tokens.any { !integer.matches(it) }) {
                mark(ImportedFieldStatus.INVALID); return
            }
            tokens.forEach { token ->
                val value = token.toIntOrNull()
                if (value == null || value < 0 || key == "Samsung ASOC" && value !in 0..100) {
                    mark(ImportedFieldStatus.INVALID)
                } else {
                    mark(ImportedFieldStatus.FOUND)
                    // Two different values establish a conflict; retaining more is unnecessary.
                    if (field.values.size < 2 && field.values.add(value)) keepImportantLine()
                }
            }
        }
        fun scan(stream: InputStream, source: String, total: Long?) {
            val sourceName = source.ifEmpty { "選取的文字報告" }
            sources += sourceName
            var section: String? = null
            var otherService = false
            var lineNumber = 0L
            val bounded = object : FilterInputStream(stream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also {
                    check()
                    if (it > 0) {
                        decoded += it
                        require(decoded <= MAX_TEXT) { "報告文字超過 16 GiB，已停止" }
                        require(System.nanoTime() - started < 600_000_000_000L) { "解析已超過 10 分鐘，已停止；可取消後重新產生較短時段的錯誤報告" }
                        if (System.nanoTime() - lastProgress > 250_000_000L) { progress(ImportProgress("讀取報告文字", decoded, total, source)); lastProgress = System.nanoTime() }
                    }
                }
            }
            val reader = InputStreamReader(bounded, Charsets.UTF_8)
            val buffer = CharArray(32 * 1024)
            val line = StringBuilder()
            var skipping = false
            fun process() {
                val text = line.toString().trim().trimStart('\uFEFF').trim()
                val service = serviceHeader.find(text)?.groupValues?.get(1)
                if (service != null) {
                    otherService = !service.equals("battery", true)
                    section = if (otherService) null else "battery 服務"
                    if (section != null) batterySections++
                } else if (otherHeader.matches(text)) {
                    section = null; otherService = false
                } else if (!otherService && (currentBatteryHeader.matches(text) || backupHeader.matches(text))) {
                    if (section == null) batterySections++
                    section = if (currentBatteryHeader.matches(text)) "Current Battery Service state" else "BatteryInfoBackUp"
                }
                patterns.forEach { (key, regex) ->
                    val match = regex.matchEntire(text)
                    if (match != null) {
                        if (section == null) outsideFields++
                        else record(key, match.groupValues[1], "[$sourceName:$lineNumber · $section] $text")
                    }
                }
                line.setLength(0)
            }
            progress(ImportProgress("讀取報告文字", decoded, total, source))
            while (true) {
                check()
                val count = reader.read(buffer)
                if (count < 0) break
                for (i in 0 until count) {
                    val c = buffer[i]
                    if (c == '\n') {
                        lineNumber++
                        if (!skipping) process()
                        line.setLength(0); skipping = false
                    } else if (c != '\r' && !skipping) {
                        if (line.length >= MAX_LINE) {
                            require(section == null) { "電池區段含超長行，已停止；未輸出不完整數據" }
                            skipping = true; skippedLines++; line.setLength(0)
                        } else line.append(c)
                    }
                }
            }
            if (!skipping && line.isNotEmpty()) { lineNumber++; process() }
            progress(ImportProgress("已讀取報告文字", decoded, total, source))
        }
        fun result(): ParsedBatteryReport {
            val fields = listOf("循環次數", "Samsung ASOC", "Samsung 使用量原始值").map { key ->
                val field = candidates[key]
                val invalid = field?.statuses?.any { it != ImportedFieldStatus.FOUND } == true
                val status = when {
                    field == null -> ImportedFieldStatus.MISSING
                    field.values.size > 1 || field.values.isNotEmpty() && invalid -> ImportedFieldStatus.CONFLICT
                    ImportedFieldStatus.INVALID in field.statuses -> ImportedFieldStatus.INVALID
                    ImportedFieldStatus.UNSUPPORTED in field.statuses -> ImportedFieldStatus.UNSUPPORTED
                    else -> ImportedFieldStatus.FOUND
                }
                val value = if (status == ImportedFieldStatus.FOUND) field?.values?.singleOrNull() else null
                val note = when (status) {
                    ImportedFieldStatus.MISSING -> "未在可辨識的電池區段找到此欄位。"
                    ImportedFieldStatus.UNSUPPORTED -> "來源明確回報未提供此欄位；保留原始依據。"
                    ImportedFieldStatus.INVALID -> "來源回報的格式、範圍或數值無效；保留原始依據。"
                    ImportedFieldStatus.CONFLICT -> "同欄位包含不同電池／報告值，或有效與無效回報；不選第一筆、不平均。"
                    ImportedFieldStatus.FOUND -> when (key) {
                        "Samsung ASOC" -> "Samsung 回報的 ASOC 原始值（0–100），不是本 App 實測容量健康度。"
                        "Samsung 使用量原始值" -> "廠商原始欄位；不除以 100 換算成循環次數。"
                        else -> "來源明確列出的循環次數，為報告當時的快照。"
                    }
                }
                Evidence(key, value, field?.lines?.toList().orEmpty(), note, status)
            }
            return ParsedBatteryReport(fields, ReportDiagnostics(sources.toList(), batterySections, skippedLines, outsideFields, omittedLines))
        }
    }
    private class ReadOnlyChannel(val channel: SeekableByteChannel, val check: () -> Unit) : SeekableByteChannel {
        override fun read(dst: ByteBuffer): Int { check(); return channel.read(dst) }
        override fun position(): Long = channel.position()
        override fun position(newPosition: Long): SeekableByteChannel { check(); channel.position(newPosition); return this }
        override fun size(): Long = channel.size()
        override fun isOpen(): Boolean = channel.isOpen
        override fun close() = channel.close()
        override fun write(src: ByteBuffer): Int = throw java.nio.channels.NonWritableChannelException()
        override fun truncate(size: Long): SeekableByteChannel = throw java.nio.channels.NonWritableChannelException()
    }
}
