package dev.galaxy.toolbox.data

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dev.galaxy.toolbox.model.Evidence
import dev.galaxy.toolbox.model.ParsedBatteryReport
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Owns the selected descriptor, so Cancel can interrupt blocking provider reads as well as scanning. */
class BugreportDocument(private val context: Context) {
    private val signal = CancellationSignal()
    private val cancelled = AtomicBoolean(false)
    private val active = AtomicReference<Closeable?>()
    fun cancel() { cancelled.set(true); signal.cancel(); runCatching { active.getAndSet(null)?.close() } }
    private fun check() { if (cancelled.get()) throw CancellationException("已取消匯入") }
    fun read(uri: Uri, onProgress: (ImportProgress) -> Unit): List<Evidence> {
        return readReport(uri, onProgress).fields
    }
    fun readReport(uri: Uri, onProgress: (ImportProgress) -> Unit): ParsedBatteryReport {
        check()
        val resolver = context.contentResolver
        val size = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null, signal)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0 } else null
            }
        }.getOrNull()
        onProgress(ImportProgress("開啟所選檔案", total = size))
        val descriptor = resolver.openFileDescriptor(uri, "r", signal)
        if (descriptor != null) ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            active.set(input); check()
            val channel = input.channel
            val seekable = runCatching { val at = channel.position(); channel.position(at); channel.size(); true }.getOrDefault(false)
            if (seekable) {
                val signature = ByteBuffer.allocate(4)
                while (signature.hasRemaining()) { check(); if (channel.read(signature) < 0) break }
                channel.position(0)
                val zip = signature.position() >= 2 && signature.array()[0] == 0x50.toByte() && signature.array()[1] == 0x4b.toByte()
                return try {
                    if (zip) BugreportParser.parseZipReport(channel, ::check, onProgress)
                    else BugreportParser.parseReport(input, checkCancelled = ::check, onProgress = onProgress, inputSize = size)
                } finally { active.set(null) }
            }
            return try { BugreportParser.parseReport(input, File(context.cacheDir, "report-import"), onProgress = onProgress, inputSize = size, checkCancelled = ::check) }
            finally { active.set(null) }
        }
        val input = resolver.openInputStream(uri) ?: error("無法開啟檔案，請從系統檔案選取本機 ZIP／TXT")
        return input.use {
            active.set(it); check()
            try { BugreportParser.parseReport(it, File(context.cacheDir, "report-import"), onProgress = onProgress, inputSize = size, checkCancelled = ::check) }
            finally { active.set(null) }
        }
    }
}
