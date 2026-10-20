package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.FirmwareQuery
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.File
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BugreportParserTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun parse(text: String) = BugreportParser.parse(text.byteInputStream())
    @Test fun onlyBatteryServiceIsTrusted() {
        val fields = parse("""
            DUMP OF SERVICE unrelated:
            cycle count: 999
            DUMP OF SERVICE battery:
              cycle count: 0
              mSavedBatteryAsoc: 96
              mSavedBatteryUsage: 12345
            DUMP OF SERVICE batterystats:
              cycle count: 999
        """.trimIndent())
        assertEquals(0, fields[0].value)
        assertEquals(96, fields[1].value)
        assertEquals(12345, fields[2].value)
    }
    @Test fun missingUnsupportedAndOutOfRangeStayUnknown() {
        assertTrue(parse("cycle count: 72").all { it.value == null })
        assertTrue(parse("DUMP OF SERVICE battery:\ncycle count: -1\nmSavedBatteryAsoc: 101\nmSavedBatteryUsage: 999999999999999999").all { it.value == null })
    }
    @Test fun conflictingEvidenceDoesNotBecomeLatestValue() {
        val field = parse("DUMP OF SERVICE battery:\ncycle count: 12\ncycle count: 13")[0]
        assertNull(field.value)
        assertEquals(2, field.lines.size)
    }
    @Test fun zipIgnoresUnrelatedFilesAndParsesReportWithoutExtraction() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("other.txt")); zip.write("DUMP OF SERVICE battery:\ncycle count: 99".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("bugreport-device.txt")); zip.write("DUMP OF SERVICE battery:\ncycle count: 24".toByteArray()); zip.closeEntry()
        }
        assertEquals(24, BugreportParser.parse(bytes.toByteArray().inputStream())[0].value)
    }
    @Test fun seekableStoredReportUsesNoOptionalCodec() {
        val file = File(temporary.root, "stored.zip")
        val text = "DUMP OF SERVICE battery:\ncycle count: 31\nmSavedBatteryAsoc: 98".toByteArray()
        ZipOutputStream(file.outputStream()).use { zip ->
            val entry = ZipEntry("bugreport.txt").apply {
                method = ZipEntry.STORED; size = text.size.toLong(); compressedSize = size
                crc = java.util.zip.CRC32().apply { update(text) }.value
            }
            zip.putNextEntry(entry); zip.write(text); zip.closeEntry()
        }
        java.io.FileInputStream(file).use { input ->
            val fields = BugreportParser.parseZip(input.channel)
            assertEquals(31, fields[0].value); assertEquals(98, fields[1].value)
        }
    }
    @Test fun seekableOptionalCodecEntriesAreRejectedBeforeDecoderConstruction() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("bugreport.txt"))
                zip.write("DUMP OF SERVICE battery:\ncycle count: 31".toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        val central = (0..bytes.size-4).first { offset ->
            bytes[offset] == 0x50.toByte() && bytes[offset+1] == 0x4b.toByte() &&
                bytes[offset+2] == 1.toByte() && bytes[offset+3] == 2.toByte()
        }
        // Zstandard and XZ entries must fail at our format guard, even without their jars.
        for (method in listOf(93, 95)) {
            val altered = bytes.copyOf().apply {
                this[8] = method.toByte(); this[9] = 0
                this[central+10] = method.toByte(); this[central+11] = 0
            }
            val file = File(temporary.root, "unsupported-$method.zip").apply { writeBytes(altered) }
            java.io.FileInputStream(file).use { input ->
                val error = assertThrows(IllegalArgumentException::class.java) { BugreportParser.parseZip(input.channel) }
                assertTrue(error.message.orEmpty().contains("STORED／DEFLATE"))
            }
        }
    }
    @Test fun skipsUnrelatedLongLinesWithoutLosingTheBatteryReport() {
        val report = BugreportParser.parseReport(("a".repeat(17000) + "\nDUMP OF SERVICE battery:\nmSavedBatteryAsoc: 96\n").byteInputStream())
        assertEquals(96, report.fields[1].value)
        assertEquals(1, report.diagnostics.skippedLongLines)
        assertTrue(report.fields.none { it.note.contains("超長行") })
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnboundedBatteryLine() {
        parse("DUMP OF SERVICE battery:\n" + "a".repeat(17000))
    }
    @Test fun zipDetectionHandlesShortInputReads() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("bugreport.txt"))
            zip.write("DUMP OF SERVICE battery:\ncycle count: 15".toByteArray())
            zip.closeEntry()
        }
        val shortReads = object : java.io.FilterInputStream(bytes.toByteArray().inputStream()) {
            override fun read(b: ByteArray, off: Int, len: Int) = `in`.read(b, off, minOf(len, 1))
        }
        assertEquals(15, BugreportParser.parse(shortReads)[0].value)
    }
    @Test fun fullZipFindsNestedReportAmongHundredsOfAttachmentsAndDeletesCache() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            repeat(200) { i ->
                zip.putNextEntry(ZipEntry("FS/logs/$i.txt")); zip.write("unrelated log".toByteArray()); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("reports/bugreport-samsung.txt"))
            zip.write("DUMP OF SERVICE battery:\ncycle count: 82\nmSavedBatteryAsoc: 97\nmSavedBatteryUsage: 12345".toByteArray())
            zip.closeEntry()
        }
        val cache = File(temporary.root, "cache")
        val fields = BugreportParser.parse(bytes.toByteArray().inputStream(), cache)
        assertEquals(82, fields[0].value); assertEquals(97, fields[1].value)
        assertTrue(fields[1].lines.single().contains("reports/bugreport-samsung.txt"))
        assertTrue(cache.listFiles()!!.isEmpty())
    }
    @Test fun dumpstateNameWorksAndAZipWithoutAReportGivesAnActionableError() {
        fun archive(name: String): ByteArray = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry(name)); zip.write("DUMP OF SERVICE battery:\nmSavedBatteryAsoc: 99".toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        val cache = File(temporary.root, "cache")
        assertEquals(99, BugreportParser.parse(archive("nested/dumpstate.txt").inputStream(), cache)[1].value)
        assertThrows(IllegalArgumentException::class.java) { BugreportParser.parse(archive("other.txt").inputStream(), cache) }
        assertTrue(cache.listFiles()!!.isEmpty())
    }
    @Test fun cancelledFullZipImportRemovesItsTemporaryCopy() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("bugreport.txt")); zip.write("DUMP OF SERVICE battery:\ncycle count: 5".toByteArray()); zip.closeEntry()
        }
        val cache = File(temporary.root, "cache")
        var checks = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            BugreportParser.parse(bytes.toByteArray().inputStream(), cache) {
                if (++checks > 5) throw java.util.concurrent.CancellationException()
            }
        }
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }
    @Test fun firmwareRequiresExplicitPairAndEncodesNoPaths() {
        val query = FirmwareQuery.create(" sm-s9280 ", "tgy")
        assertEquals("https://fota-cloud-dn.ospserver.net/firmware/TGY/SM-S9280/version.xml", query.xmlUrl)
        listOf("", "../TGY", "TW", "TGY?x").forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { FirmwareQuery.create("SM-S9280", bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { FirmwareQuery.create("SM-S9280/DS", "TGY") }
    }
}
