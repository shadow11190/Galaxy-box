package dev.galaxy.toolbox.data

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BugreportDocumentTest {
    @Test fun seekableAndroidDocumentReadsInPlaceWithoutZipCopy() {
        val app = RuntimeEnvironment.getApplication()
        val file = File(app.filesDir, "document-test.zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("report/bugreport-test.txt"))
                zip.write("DUMP OF SERVICE battery:\nmSavedBatteryAsoc: [97]\ncycle count: [28]\n".toByteArray()); zip.closeEntry()
            }
            val phases = mutableListOf<String>()
            val report = BugreportDocument(app).readReport(Uri.fromFile(file)) { phases += it.phase }
            val fields = report.fields
            assertEquals(28, fields[0].value); assertEquals(97, fields[1].value)
            assertEquals(1, report.diagnostics.batterySections)
            assertTrue(phases.any { it.contains("直接讀取") })
            assertFalse(phases.any { it.contains("建立私有暫存") })
            assertTrue(File(app.cacheDir, "report-import").listFiles().orEmpty().isEmpty())
        } finally { file.delete() }
    }
    @Test fun cancelBeforeOpeningDoesNotReadPrivateReport() {
        val app = RuntimeEnvironment.getApplication()
        val document = BugreportDocument(app)
        document.cancel()
        assertThrows(CancellationException::class.java) { document.read(Uri.parse("content://test/missing")) {} }
    }
}
