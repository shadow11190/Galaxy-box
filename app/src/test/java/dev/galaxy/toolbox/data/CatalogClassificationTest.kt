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
}
