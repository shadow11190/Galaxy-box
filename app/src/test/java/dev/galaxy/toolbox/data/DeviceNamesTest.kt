package dev.galaxy.toolbox.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DeviceNamesTest {
    private fun catalog(text: String) = DeviceNames.parse(text.lineSequence())
    @Test fun usesFullModelAndSamsungManufacturer() {
        val names = catalog("SM-S9280\te3q\tGalaxy S24 Ultra")
        assertEquals("Galaxy S24 Ultra", names.resolve("Samsung", " sm-s9280 ", "e3q"))
        assertNull(names.resolve("Xiaomi", "SM-S9280", "e3q"))
        assertNull(names.resolve("Samsung", "SM-S928", "e3q"))
        assertNull(names.resolve("Samsung", "SM-S9280FAKE", "e3q"))
    }
    @Test fun dualSimSuffixIsExplicitlyNormalized() {
        val names = catalog("SM-S938B\tpa3q\tGalaxy S25 Ultra")
        assertEquals("Galaxy S25 Ultra", names.resolve("samsung", "SM-S938B/DS", "pa3q"))
        assertEquals("Galaxy S25 Ultra", names.resolve("samsung", "SM-S938B/DSN", "pa3q"))
        assertNull(names.resolve("samsung", "SM-S938B/OTHER", "pa3q"))
    }
    @Test fun ambiguousNamesNeedDeviceAndCannotGuess() {
        val names = catalog("MODEL\talpha\tPhone A\nMODEL\tbeta\tPhone B")
        assertEquals("Phone B", names.resolve("Samsung", "MODEL", "beta"))
        assertNull(names.resolve("Samsung", "MODEL", "gamma"))
        val duplicate = catalog("MODEL\talpha\tPhone A\nMODEL\talpha\tPhone B")
        assertNull(duplicate.resolve("Samsung", "MODEL", "alpha"))
    }
    @Test fun bundledOfficialCatalogHasKnownFullModels() {
        val file = File("src/main/assets/samsung-device-names.tsv")
        assertTrue(file.isFile)
        val names = file.bufferedReader().use { DeviceNames.parse(it.lineSequence()) }
        assertEquals("Galaxy S24 Ultra", names.resolve("Samsung", "SM-S9280", "e3q"))
        assertEquals("Galaxy S25 Ultra", names.resolve("Samsung", "SM-S938B", "pa3q"))
    }
    @Test fun chipLookupDoesNotInferFromPhoneNameOrSimilarCode() {
        assertNull(ChipNames.resolve(null, "qcom", "Galaxy S24 Ultra"))
        assertNull(ChipNames.resolve("SM8650Q", "qcom", "Galaxy S24 Ultra"))
        assertNull(ChipNames.resolve("SM86501", null, "Galaxy S24 Ultra"))
        assertEquals(setOf("Samsung Exynos 2400", "Samsung Exynos 2400e"), ChipNames.resolve("S5E9945", null, "Galaxy S24")?.candidates?.toSet())
        assertEquals("Qualcomm Snapdragon 8 Gen 3 for Galaxy", ChipNames.resolve("SM8650-AC", "qcom", "Galaxy S24 Ultra")?.name)
        assertEquals("Qualcomm Snapdragon 8 Gen 3", ChipNames.resolve("SM8650", "qcom", "Other phone")?.name)
    }
    @Test fun exynosAndSnapdragonVariantsFollowActualChipCode() {
        assertEquals("Samsung Exynos 2400", ChipNames.resolve("Exynos 2400", null, "Galaxy S24")?.name)
        assertEquals("Qualcomm Snapdragon 8 Gen 3 for Galaxy", ChipNames.resolve("SM8650", null, "Galaxy S24")?.name)
        assertEquals("Qualcomm Snapdragon 8 Elite for Galaxy", ChipNames.resolve(null, "SM8750", "Galaxy S25 Ultra")?.name)
        assertEquals("Samsung Exynos 2500", ChipNames.resolve("unknown", "exynos2500", null)?.name)
    }
    @Test fun s26UltraHasOfficialMarketingNameAndNeedsActualGen5Code() {
        val names = File("src/main/assets/samsung-device-names.tsv").bufferedReader().use { DeviceNames.parse(it.lineSequence()) }
        assertEquals("Galaxy S26 Ultra", names.resolve("Samsung", "SM-S9480", "m3q"))
        assertEquals("Galaxy S26 Ultra", names.resolve("Samsung", "SM-S948B/DS", "m3q"))
        assertEquals("Qualcomm Snapdragon 8 Elite Gen 5 for Galaxy", ChipNames.resolve("SM8850", null, "Galaxy S26 Ultra")?.name)
        assertEquals("Qualcomm Snapdragon 8 Elite Gen 5 for Galaxy", ChipNames.resolve("SM8850-1-AD", null, "Galaxy S26 Ultra")?.name)
        assertEquals("Qualcomm Snapdragon 8 Elite Gen 5", ChipNames.resolve("SM8850-AC", null, "Other phone")?.name)
        assertNull(ChipNames.resolve("SM88500", null, "Galaxy S26 Ultra"))
        assertNull(ChipNames.resolve(null, "qcom", "Galaxy S26 Ultra"))
    }
}
