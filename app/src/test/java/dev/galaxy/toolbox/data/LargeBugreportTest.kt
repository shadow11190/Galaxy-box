package dev.galaxy.toolbox.data

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import org.apache.commons.compress.archivers.zip.*

class LargeBugreportTest {
    @Test fun archiveLargerThanOneGiBReadsOnlyDirectoryAndReport() {
        val content = "DUMP OF SERVICE battery:\ncycle count: 24\nmSavedBatteryAsoc: 96\n".toByteArray()
        val name = "reports/bugreport-samsung.txt".toByteArray()
        val offset = 1200L * 1024 * 1024
        val crc = CRC32().apply { update(content) }.value
        fun header(size: Int, block: ByteBuffer.() -> Unit) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(block).array()
        val local = header(30 + name.size + content.size) {
            putInt(0x04034b50); putShort(20); putShort(0); putShort(0); putShort(0); putShort(0)
            putInt(crc.toInt()); putInt(content.size); putInt(content.size); putShort(name.size.toShort()); putShort(0); put(name); put(content)
        }
        val central = header(46 + name.size) {
            putInt(0x02014b50); putShort(20); putShort(20); putShort(0); putShort(0); putShort(0); putShort(0)
            putInt(crc.toInt()); putInt(content.size); putInt(content.size); putShort(name.size.toShort()); putShort(0); putShort(0)
            putShort(0); putShort(0); putInt(0); putInt(offset.toInt()); put(name)
        }
        val directoryAt = offset + local.size
        val end = header(22) { putInt(0x06054b50); putShort(0); putShort(0); putShort(1); putShort(1); putInt(central.size); putInt(directoryAt.toInt()); putShort(0) }
        val channel = SparseChannel(directoryAt + central.size + end.size,
            listOf(offset to local, directoryAt to central, (directoryAt + central.size) to end))
        val fields = BugreportParser.parseZip(channel)
        assertEquals(24, fields[0].value); assertEquals(96, fields[1].value)
        assertTrue("Must not copy or read 1.2 GiB of unrelated bytes", channel.bytesRead < 1024 * 1024)
    }
    @Test fun zip64ReportIsReadWithoutExtraction() {
        val bytes = ByteArrayOutputStream()
        ZipArchiveOutputStream(bytes).use { zip ->
            zip.setUseZip64(Zip64Mode.Always)
            zip.putArchiveEntry(ZipArchiveEntry("nested/dumpstate.txt"))
            zip.write("DUMP OF SERVICE battery:\ncycle count: 0\nmSavedBatteryAsoc: 98".toByteArray())
            zip.closeArchiveEntry()
        }
        val data = bytes.toByteArray()
        val fields = BugreportParser.parseZip(SparseChannel(data.size.toLong(), listOf(0L to data)))
        assertEquals(0, fields[0].value); assertEquals(98, fields[1].value)
    }
    @Test fun textBeyondOld512MiBLimitIsScannedWithBoundedMemory() {
        val filler = 513L * 1024 * 1024
        val end = "\nDUMP OF SERVICE battery:\nmSavedBatteryAsoc: 95\n".toByteArray()
        val input = object : InputStream() {
            var at = 0L
            override fun read(): Int = throw AssertionError("Expected bulk reads")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (at >= filler + end.size) return -1
                val count = minOf(len.toLong(), filler + end.size - at).toInt()
                repeat(count) { i -> b[off + i] = if (at + i < filler) 'a'.code.toByte() else end[(at + i - filler).toInt()] }
                at += count; return count
            }
        }
        var progress = 0L
        val fields = BugreportParser.parse(input, onProgress = { progress = it.bytes })
        assertEquals(95, fields[1].value)
        assertTrue(progress > 512L * 1024 * 1024)
    }
    @Test fun cancelCanInterruptIndexingAndMalformedZipDoesNotYieldEvidence() {
        val broken = ByteArray(64) { 0 }
        assertThrows(IOException::class.java) { BugreportParser.parseZip(SparseChannel(64, listOf(0L to broken))) }
        assertThrows(CancellationException::class.java) { BugreportParser.parseZip(SparseChannel(64, listOf(0L to broken)), { throw CancellationException() }) }
    }
    private class SparseChannel(private val length: Long, private val segments: List<Pair<Long, ByteArray>>) : SeekableByteChannel {
        private var at = 0L
        private var opened = true
        var bytesRead = 0L
        override fun read(dst: ByteBuffer): Int {
            if (at >= length) return -1
            val n = minOf(dst.remaining().toLong(), length - at).toInt()
            repeat(n) { i ->
                val position = at + i
                val segment = segments.firstOrNull { (start, data) -> position in start until start + data.size }
                dst.put(segment?.let { (start, data) -> data[(position - start).toInt()] } ?: 0)
            }
            at += n; bytesRead += n; return n
        }
        override fun position(): Long = at
        override fun position(newPosition: Long): SeekableByteChannel { require(newPosition >= 0); at = newPosition; return this }
        override fun size(): Long = length
        override fun isOpen() = opened
        override fun close() { opened = false }
        override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException()
        override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException()
    }
}
