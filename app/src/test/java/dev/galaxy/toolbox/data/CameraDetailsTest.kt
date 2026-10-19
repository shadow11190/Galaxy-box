package dev.galaxy.toolbox.data

import android.hardware.camera2.CameraCharacteristics as C
import android.util.Range
import android.util.Size
import dev.galaxy.toolbox.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowCameraCharacteristics

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraDetailsTest {
    private fun camera(): C = ShadowCameraCharacteristics.newCameraCharacteristics().also { c ->
        val s = Shadow.extract<ShadowCameraCharacteristics>(c)
        s.set(C.LENS_FACING, C.LENS_FACING_BACK)
        s.set(C.SENSOR_INFO_PIXEL_ARRAY_SIZE, Size(20000, 10000))
        s.set(C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS, floatArrayOf(6.3f))
        s.set(C.SENSOR_INFO_SENSITIVITY_RANGE, Range(50, 3200))
        s.set(C.SENSOR_INFO_EXPOSURE_TIME_RANGE, Range(100000L, 2000000000L))
        s.set(C.FLASH_INFO_AVAILABLE, false)
        s.set(C.INFO_SUPPORTED_HARDWARE_LEVEL, C.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
    }
    @Test fun exposesRealMetadataWithoutReplacingJpegWithPixelArray() {
        val rows = CameraDetails.describe("0", camera())
        assertEquals("200.0 MP · 20000 × 10000 px", rows.single { it.label == "像素陣列" }.value)
        assertNull(rows.single { it.label == "最大 JPEG（一般模式）" }.value)
        assertEquals("6.3 mm", rows.single { it.label == "焦距" }.value)
        assertEquals("50 – 3200", rows.single { it.label == "ISO 範圍" }.value)
        assertEquals("0.1 ms – 2 s", rows.single { it.label == "曝光時間範圍" }.value)
        assertEquals("無", rows.single { it.label == "閃光燈" }.value)
        assertEquals("FULL", rows.single { it.label == "Camera2 硬體等級" }.value)
        assertTrue(rows.all { it.category == HardwareCategory.CAMERA && it.group == "後置相機 · ID 0" })
        assertTrue(rows.single { it.label == "ISO 範圍" }.source.contains("android.sensor.info.sensitivityRange"))
    }
    @Test fun restrictedCameraDoesNotHideLaterIds() {
        val rows = CameraDetails.read({ listOf("blocked", "working") }) { if (it == "blocked") throw SecurityException() else camera() }
        assertTrue(rows.any { it.group == "相機 · ID blocked" && it.value == null && it.detail.contains("權限不足") })
        assertTrue(rows.any { it.group == "後置相機 · ID working" && it.value == "6.3 mm" })
    }
    @Test fun cannotEnumerateIsDistinctFromEmptyList() {
        val failed = CameraDetails.read({ throw IllegalStateException() }) { camera() }
        assertTrue(failed.single().detail.contains("IllegalStateException"))
        val empty = CameraDetails.read({ emptyList() }) { error("must not read") }
        assertNull(empty.single().value)
        assertFalse(empty.single().detail.contains("權限不足"))
    }
    @Test fun missingSensorModelStaysUnknownAndDoesNotAssumeMainLens() {
        val rows = CameraDetails.describe("9", camera())
        assertNull(rows.single { it.label == "感光元件型號／廠商" }.value)
        assertFalse(rows.any { it.value?.contains("主鏡") == true })
        assertFalse(rows.any { it.value?.contains("ISOCELL") == true })
    }
    @Test fun physicalMetadataPreservesParentsAndDoesNotClaimStandaloneCamera() {
        val rows = CameraDetails.describe("9", camera(), setOf("0", "2"), false)
        val relation = rows.single { it.label == "Camera ID 關係" }
        assertTrue(relation.value!!.contains("無法單獨開啟"))
        assertTrue(relation.value.contains("0, 2"))
        assertEquals("後置實體相機 · ID 9", relation.group)
    }
    @Test fun largestSizeUsesAreaWithLongArithmeticAndIgnoresInvalidSizes() {
        assertEquals(Size(9000, 7000), CameraDetails.largest(listOf(Size(10000, 1000), Size(9000, 7000), Size(0, 10))))
        assertEquals(2500000000L, CameraDetails.area(Size(50000, 50000)))
        assertNull(CameraDetails.largest(emptyList()))
    }
    @Test fun zeroExposureAndUnknownEnumsAreNotSilentlyReplaced() {
        assertEquals("0 ms", CameraDetails.exposure(0))
        assertEquals("代碼 99", CameraDetails.named(intArrayOf(99), emptyMap()))
        assertEquals("代碼 99", CameraDetails.hardwareLevel(99))
    }
    @Test @Config(sdk = [26]) fun oldAndroidDoesNotReadNewCameraKeys() {
        val rows = CameraDetails.describe("0", camera())
        assertFalse(rows.any { it.label.contains("最大解析度模式") || it.label.contains("實體 ID") || it.label == "變焦比例範圍" })
        assertTrue(rows.any { it.label == "焦距" })
    }
    @Test fun maximumResolutionAndNormalModesRemainSeparate() {
        val c = camera()
        Shadow.extract<ShadowCameraCharacteristics>(c).set(C.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION, Size(30000, 20000))
        val rows = CameraDetails.describe("0", c)
        assertTrue(rows.single { it.label == "像素陣列（最大解析度模式）" }.value!!.startsWith("600.0 MP"))
        assertTrue(rows.single { it.label == "像素陣列" }.value!!.startsWith("200.0 MP"))
        assertNull(rows.single { it.label == "最大 JPEG（一般模式）" }.value)
    }
    @Test fun linuxMemoryUnitAndOverflowAreHandled() {
        assertEquals(2048L, HardwareDetails.meminfoBytes("SwapFree: 2 kB"))
        assertEquals(0L, HardwareDetails.meminfoBytes("SwapFree: 0 kB"))
        assertNull(HardwareDetails.meminfoBytes("SwapFree: -1 kB"))
        assertNull(HardwareDetails.meminfoBytes("SwapFree: 1 MB"))
        assertNull(HardwareDetails.meminfoBytes("SwapFree: ${Long.MAX_VALUE} kB"))
    }
    @Test fun semanticGroupsDoNotDependOnReadingCountOrLabelPrefixes() {
        val reading = Reading("焦距", "6.3 mm", "Camera2", category = HardwareCategory.CAMERA, group = "後置相機 · ID 0")
        assertEquals(HardwareCategory.CAMERA, HardwareCategory.of(reading))
        assertEquals("後置相機 · ID 0", HardwareSections.title(reading, HardwareCategory.CAMERA))
        assertEquals("CPU 3 · 調頻能力", HardwareSections.title(Reading("CPU 3 標示頻率範圍", null, "sysfs"), HardwareCategory.CPU))
    }
}
