package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SamsungBatteryReportTest {
    @get:Rule val temp = TemporaryFolder()
    private fun parse(text: String) = BugreportParser.parseReport(text.byteInputStream())
    private fun archive(vararg entries: Pair<String, String>) = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
        } }
    }.toByteArray()

    @Test fun samsungSingleElementArraysAreReadWithExactEvidence() {
        val report = parse("DUMP OF SERVICE battery:\ncycle count: [0]\nmSavedBatteryAsoc: [96]\nmSavedBatteryUsage: [12345]\n")
        assertEquals(listOf(0, 96, 12345), report.fields.map { it.value })
        assertTrue(report.fields.all { it.status == ImportedFieldStatus.FOUND })
        assertTrue(report.fields[1].lines.single().contains(":3 · battery 服務] mSavedBatteryAsoc: [96]"))
        assertEquals(1, report.diagnostics.batterySections)
    }
    @Test fun differentPackValuesAreNotAveragedOrTruncatedToTheFirstPack() {
        val report = parse("DUMP OF SERVICE battery:\ncycle_count: [42, 43]\nmSavedBatteryAsoc: [96, 94]\n")
        assertTrue(report.fields.take(2).all { it.value == null && it.status == ImportedFieldStatus.CONFLICT })
        assertTrue(report.fields[1].lines.single().contains("[96, 94]"))
    }
    @Test fun matchingPackValuesAndRealZeroRemainValid() {
        val report = parse("DUMP OF SERVICE battery:\nmCycleCount=[0, 0]\nmSavedBatteryAsoc=[0]\nmSavedBatteryUsage=[100, 100]")
        assertEquals(listOf(0, 0, 100), report.fields.map { it.value })
    }
    @Test fun standaloneDumpsysHeaderHandlesBomCrLfAndCaseWithoutRequiringDumpstateWrapper() {
        val report = parse("\uFEFFCurrent Battery Service state:\r\n batteryCycleCount = 27\r\n MSAVEDBATTERYASOC : [ 98 ]\r\n")
        assertEquals(27, report.fields[0].value)
        assertEquals(98, report.fields[1].value)
        assertEquals(1, report.diagnostics.batterySections)
    }
    @Test fun explicitBatteryBackupFragmentIsSupported() {
        val report = parse("[Not Battery Adaptive Protect Mode]BatteryInfoBackUp\nmSavedBatteryAsoc: [95]\nmSavedBatteryUsage: [15400]")
        assertEquals(95, report.fields[1].value)
        assertEquals(15400, report.fields[2].value)
        assertNull(report.fields[0].value)
    }
    @Test fun priorityServiceHeadersWorkAndBatterystatsNeverSubstitutesBattery() {
        val report = parse("------ DUMP OF SERVICE HIGH BATTERY: ------\nCurrent Battery Service state:\ncycle count: 8\nDUMP OF SERVICE batterystats:\ncycle count: 999")
        assertEquals(8, report.fields[0].value)
        assertEquals(1, report.diagnostics.batterySections)
        assertEquals(1, report.diagnostics.outsideSectionFields)
    }
    @Test fun headersQuotedInsideAnotherServiceCannotPromoteItsFields() {
        val report = parse("DUMP OF SERVICE unrelated:\nCurrent Battery Service state:\nmSavedBatteryAsoc: [97]\nBatteryInfoBackUp\ncycle count: 99")
        assertTrue(report.fields.all { it.value == null })
        assertEquals(0, report.diagnostics.batterySections)
        assertEquals(2, report.diagnostics.outsideSectionFields)
    }
    @Test fun absentUnsupportedAndMalformedAreDifferentStates() {
        val report = parse("DUMP OF SERVICE battery:\nmSavedBatteryAsoc: unsupported\nmSavedBatteryUsage: [123,]")
        assertEquals(ImportedFieldStatus.MISSING, report.fields[0].status)
        assertEquals(ImportedFieldStatus.UNSUPPORTED, report.fields[1].status)
        assertEquals(ImportedFieldStatus.INVALID, report.fields[2].status)
        assertTrue(report.fields[2].lines.single().contains("[123,]"))
    }
    @Test fun negativeOutOfRangeOverflowAndPartialArraysDoNotYieldNumbers() {
        for (raw in listOf("[-1]", "[101]", "[96", "96]", "96 percent", "[999999999999999999]", "[]")) {
            val asoc = parse("DUMP OF SERVICE battery:\nmSavedBatteryAsoc: $raw").fields[1]
            assertNull(raw, asoc.value)
            assertEquals(raw, ImportedFieldStatus.INVALID, asoc.status)
        }
        assertEquals(ImportedFieldStatus.CONFLICT,
            parse("DUMP OF SERVICE battery:\nmSavedBatteryAsoc: [96, -1]").fields[1].status)
    }
    @Test fun repeatedValuesHaveBoundedEvidenceButLateConflictStillCounts() {
        val text = "DUMP OF SERVICE battery:\n" + "mSavedBatteryAsoc: [96]\n".repeat(100)
        val same = parse(text)
        assertEquals(96, same.fields[1].value)
        assertEquals(16, same.fields[1].lines.size)
        assertEquals(84, same.diagnostics.omittedEvidenceLines)
        val conflict = parse(text + "mSavedBatteryAsoc: [95]")
        assertEquals(ImportedFieldStatus.CONFLICT, conflict.fields[1].status)
        assertNull(conflict.fields[1].value)
        assertTrue(conflict.fields[1].lines.any { it.contains("[95]") })
    }
    @Test fun nestedSamsungLogAndTxtConflictsArePreservedAcrossSeekableZipEntries() {
        val data = archive("logs/dumpState_S9480.log" to "BatteryInfoBackUp\nmSavedBatteryAsoc: [97]",
            "reports/bugreport-device.txt" to "DUMP OF SERVICE battery:\nmSavedBatteryAsoc: [98]",
            "other.txt" to "DUMP OF SERVICE battery:\ncycle count: 999")
        val file = temp.newFile("report.zip").apply { writeBytes(data) }
        val report = FileInputStream(file).use { BugreportParser.parseZipReport(it.channel) }
        assertEquals(2, report.diagnostics.sources.size)
        assertNull(report.fields[0].value)
        assertEquals(ImportedFieldStatus.CONFLICT, report.fields[1].status)
        assertTrue(report.fields[1].lines.any { it.contains("dumpState_S9480.log") })
    }
    @Test fun sectionContextDoesNotLeakAcrossFilesOrIntoFollowingLogcat() {
        val data = archive("bugreport-a.txt" to "DUMP OF SERVICE battery:\nmSavedBatteryAsoc: [97]",
            "dumpstate-b.log" to "mSavedBatteryAsoc: [80]")
        val report = BugreportParser.parseReport(data.inputStream())
        assertEquals(97, report.fields[1].value)
        assertEquals(1, report.diagnostics.outsideSectionFields)
        val logs = parse("DUMP OF SERVICE battery:\ncycle count: 5\n------ SYSTEM LOG ------\ncycle count: 999")
        assertEquals(5, logs.fields[0].value)
        assertEquals(1, logs.diagnostics.outsideSectionFields)
    }
}
