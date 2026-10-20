package dev.galaxy.toolbox.data

import org.junit.Assert.*
import org.junit.Test

class BrandMarksTest {
    @Test fun selectedBrandsHaveATransparentMarkEntry() {
        assertEquals(32, BrandMarks.supported.size)
        for (mark in listOf("Xiaomi", "Redmi", "POCO", "HUAWEI", "HONOR", "OPPO", "vivo",
                "SAMSUNG", "ASUS", "ROG Phone", "Sony", "Google Pixel", "Nothing", "CMF by Nothing",
                "Black Shark", "CMF by Nothing", "Rakuten Mobile")) assertTrue(mark, mark in BrandMarks.supported)
        assertFalse("8848" in BrandMarks.supported)
    }
    @Test fun unavailableDeviceOrBrandLeavesNoMark() {
        assertNull(BrandMarks.resolve("Samsung", "samsung", null))
        assertNull(BrandMarks.resolve("unknown", "unknown", "Galaxy S26 Ultra"))
        assertNull(BrandMarks.resolve("", "", "Xiaomi 14T Pro"))
    }
    @Test fun exactBrandAndKnownSubbrandSelectCorrectMark() {
        assertEquals("SAMSUNG", BrandMarks.resolve("samsung", "samsung", "Galaxy S26 Ultra"))
        assertEquals("Redmi", BrandMarks.resolve("Xiaomi", "Xiaomi", "Redmi Note 14"))
        assertEquals("POCO", BrandMarks.resolve("Xiaomi", "Xiaomi", "POCO F7"))
        assertEquals("ROG Phone", BrandMarks.resolve("ASUSTeK", "ASUS", "ROG Phone 9"))
        assertEquals("Nokia", BrandMarks.resolve("HMD Global", "HMD", "Nokia XR21"))
        assertEquals("HUAWEI", BrandMarks.resolve("Huawei", "Huawei", "Pura 70"))
        assertEquals("Google Pixel", BrandMarks.resolve("Google", "google", "Pixel 9"))
    }
}
