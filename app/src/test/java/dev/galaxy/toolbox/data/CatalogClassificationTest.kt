package dev.galaxy.toolbox.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CatalogClassificationTest {
    @Test fun verifiedPhoneAndLegacyRecordsRemainSeparate() {
        val rows = sequenceOf(
            "Samsung\tSM-S9480\tm3q\tphone\t2026\thttps://example.test/new",
            "Samsung\tSM-G950F\tdreamlte\tphone\t2017\thttps://example.test/old",
            "Xiaomi\tPAD-1\tpad\ttablet\t2024\thttps://example.test/pad",
        )
        val catalog = DeviceClassifications.parse(rows)
        assertTrue(catalog.lookup("samsung", "sm-s9480", "M3Q")!!.isPhoneSince2018)
        assertFalse(catalog.lookup("Samsung", "SM-G950F", "dreamlte")!!.isPhoneSince2018)
        assertFalse(catalog.lookup("Xiaomi", "PAD-1", "pad")!!.isPhoneSince2018)
        assertTrue(catalog.lookup("Xiaomi", "PAD-1", "pad")!!.isTabletSince2018)
        assertEquals("2024 年 Android 平板（已核對）", catalog.lookup("Xiaomi", "PAD-1", "pad")!!.label)
        assertNull(catalog.lookup("Samsung", "UNKNOWN", "m3q"))
    }

    @Test fun generatedClassificationContainsOnlySourceBackedYears() {
        val names = File("src/main/assets/android-device-names.tsv").bufferedReader().use { DeviceNames.parse(it.lineSequence()) }
        val classes = File("src/main/assets/device-classifications.tsv").bufferedReader().use { DeviceClassifications.parse(it.lineSequence()) }
        val verified = names.entries.count { classes.lookup(it.brand, it.model, it.device)?.isPhoneSince2018 == true }
        assertTrue(verified >= 360)
        assertTrue(classes.lookup("Google", "Pixel 7a", "lynx")!!.isPhoneSince2018)
        assertTrue(classes.lookup("ZTE", "Z2465N", "Z7606O")!!.isPhoneSince2018)
        assertTrue(classes.lookup("Xiaomi", "2608BPX34C", "lhasa")!!.isPhoneSince2018)
        assertTrue(classes.lookup("Samsung", "SM-S9480", "m3q")!!.isPhoneSince2018)
        assertTrue(classes.lookup("Samsung", "SM-S942B", "m1s")!!.isPhoneSince2018)
        assertTrue(classes.lookup("Samsung", "SM-A5660", "a56x")!!.isPhoneSince2018)
        assertTrue(classes.lookup("Oppo", "CPH2873", "OP6275L1")!!.isPhoneSince2018)
        assertTrue(classes.lookup("Redmi", "25080RABDG", "lapis")!!.isPhoneSince2018)
        assertTrue(classes.lookup("Samsung", "SM-X920", "gts10uwifi")!!.isTabletSince2018)
        assertTrue(classes.lookup("Samsung", "SM-X730", "gts11wifi")!!.isTabletSince2018)
        assertTrue(classes.lookup("Samsung", "SM-X936B", "gts11u")!!.isTabletSince2018)
        assertTrue(classes.lookup("Xiaomi", "24091RPADG", "muyu")!!.isTabletSince2018)
        assertTrue(classes.lookup("Xiaomi", "25079RPDCG", "turner")!!.isTabletSince2018)
        assertTrue(classes.lookup("Redmi", "2509BRP2DG", "organ")!!.isTabletSince2018)
        assertNull(classes.lookup("Redmi", "2509BRP2DI", "organ"))
        assertFalse(classes.lookup("Samsung", "SM-T110", "goyawifi")?.isTabletSince2018 == true)
        assertNull(classes.lookup("Samsung", "NOT-A-MODEL", "m3q"))
    }

    @Test fun processorUsageDoesNotInventReleaseYear() {
        val usage = File("src/main/assets/processor-phone-usage-2018plus.tsv")
            .bufferedReader().use { ProcessorPhoneUsage.parse(it.lineSequence()) }
        assertNotNull(usage.lookup("Samsung Exynos 850"))
        assertNotNull(usage.lookup("Google Tensor G2"))
        assertNotNull(usage.lookup("UNISOC T8300"))
        assertNotNull(usage.lookup("Xiaomi XRING O1"))
        assertNotNull(usage.lookup("Xiaomi XRING O3"))
        assertNull(usage.lookup("nonexistent chipset"))
        val bad = ProcessorPhoneUsage.parse(sequenceOf("Chip\t2023\t2019\thttps://example.test"))
        assertNull(bad.lookup("Chip"))
    }

    @Test fun tabletAndPhoneProcessorUsageAreCombinedWithoutChangingLegacyPhoneList() {
        val all = File("src/main/assets/processor-device-usage-2018plus.tsv")
            .bufferedReader().use { ProcessorDeviceUsage.parse(it.lineSequence()) }
        val phones = File("src/main/assets/processor-phone-usage-2018plus.tsv")
            .bufferedReader().use { ProcessorPhoneUsage.parse(it.lineSequence()) }
        assertNotNull(all.lookup("Qualcomm Snapdragon 8s Gen 3"))
        assertNotNull(all.lookup("MediaTek Dimensity 9300+"))
        assertNotNull(phones.lookup("MediaTek Dimensity 9400"))
        assertNotNull(phones.lookup("MediaTek Dimensity 7400-Ultra"))
        assertNull(all.lookup("nonexistent chipset"))
        assertNull(ProcessorDeviceUsage.parse(sequenceOf("Chip\t2025\t2024\thttps://example.test"))
            .lookup("Chip"))
    }
}
