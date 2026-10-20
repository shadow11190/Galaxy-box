package dev.galaxy.toolbox.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HardwareCatalogTest {
    private fun names() = File("src/main/assets/android-device-names.tsv").bufferedReader().use { DeviceNames.parse(it.lineSequence()) }
    private fun variants() = File("src/main/assets/device-variants.tsv").bufferedReader().use { DeviceVariants.parse(it.lineSequence()) }

    @Test fun chipsetBaseNumbersRetainSharedCommercialVariants() {
        assertEquals(setOf("Qualcomm Snapdragon 855", "Qualcomm Snapdragon 855+", "Qualcomm Snapdragon 860"), ChipNames.resolve("SM8150", null, null)!!.candidates.toSet())
        assertEquals(setOf("Qualcomm Snapdragon 865", "Qualcomm Snapdragon 865+", "Qualcomm Snapdragon 870"), ChipNames.resolve("SM8250", null, null)!!.candidates.toSet())
        val mtk = ChipNames.resolve("MT6991", null, null)!!
        assertTrue(mtk.candidates.containsAll(listOf("MediaTek Dimensity 9400", "MediaTek Dimensity 9400+", "MediaTek Dimensity 9500s")))
        assertTrue(mtk.name.contains("版本待確認"))
    }
    @Test fun exactVariantReportsCanRefineSharedPartNumbers() {
        val c = ChipNames.identify(listOf(ChipReport("SOC", "MediaTek Dimensity 9400+"), ChipReport("board", "mt6991")))!!
        assertEquals("MediaTek Dimensity 9400+", c.name)
        assertFalse(c.conflict)
        assertEquals("Qualcomm Snapdragon 870", ChipNames.resolve("SM8250-AC", null, null)!!.name)
        assertEquals("Qualcomm Snapdragon 8s Gen 4", ChipNames.resolve("QTI SM8735", null, null)!!.name)
        assertEquals("Qualcomm Snapdragon 8 Gen 5", ChipNames.resolve("Qualcomm Technologies, Inc SM8845", null, null)!!.name)
        assertEquals("Qualcomm Snapdragon 8 Elite Gen 6", ChipNames.resolve("SM8950", null, null)!!.name)
        assertEquals("Qualcomm Snapdragon 8 Elite Extreme Gen 6", ChipNames.resolve("SM8975", null, null)!!.name)
        assertEquals("Samsung Exynos 2600", ChipNames.resolve("s5e9965", null, null)!!.name)
        assertEquals("Samsung Exynos 1680", ChipNames.resolve("S5E8865", null, null)!!.name)
        assertEquals("Samsung Exynos 1680", ChipNames.resolve("universal1680", null, null)!!.name)
        assertEquals("Samsung Exynos 880", ChipNames.resolve("Exynos880", null, null)!!.name)
        assertEquals("Xiaomi XRING O1", ChipNames.resolve("Xring O1", null, null)!!.name)
    }
    @Test fun contradictoryChipReportsRemainExplicitAndRespectPriority() {
        val c = ChipNames.identify(listOf(ChipReport("SOC_MODEL", "Exynos 2400"), ChipReport("HARDWARE", "SM8650")), "Galaxy S24")!!
        assertTrue(c.conflict)
        assertEquals("Samsung Exynos 2400", c.name)
        assertTrue(c.evidence.contains("衝突"))
    }
    @Test fun unknownCodesAndDeviceNamesCannotInventChipIdentity() {
        for (code in listOf("qcom", "SM88500", "SM8650Q", "MT69910", "S5E99650", "universal9999", "V10100B")) {
            assertNull(code, ChipNames.resolve(code, null, "Galaxy S26 Ultra"))
        }
    }
    @Test fun fullSamsungRegionalModelsHaveSeparateChipReferences() {
        val v = variants()
        assertEquals(setOf("Samsung Exynos 2400"), v.lookup("Samsung", "SM-S921B/DS", "e1s").map { it.soc }.toSet())
        assertEquals(setOf("Qualcomm Snapdragon 8 Gen 3"), v.lookup("Samsung", "SM-S921U1", "e1q").map { it.soc }.toSet())
        assertEquals(setOf("Qualcomm Snapdragon 8 Gen 3"), v.lookup("Samsung", "SM-S9210", "e1q").map { it.soc }.toSet())
        assertEquals(setOf("Samsung Exynos 2600"), v.lookup("Samsung", "SM-S942B", "m1s").map { it.soc }.toSet())
        assertEquals(setOf("Qualcomm Snapdragon 8 Elite Gen 5"), v.lookup("Samsung", "SM-S9480", "m3q").map { it.soc }.toSet())
        assertTrue(v.lookup("Samsung", "SM-S9480FAKE", "m3q").isEmpty())
        assertTrue(v.lookup("Xiaomi", "SM-S9480", "m3q").isEmpty())
    }
    @Test fun publicNameCatalogContainsSamsungAndOtherManufacturersWithoutCrossBrandGuessing() {
        val n = names()
        assertEquals("Galaxy S26 Ultra", n.resolve("Samsung", "SM-S9480", "m3q"))
        assertEquals("Galaxy S26", n.resolve("Samsung", "SM-S942B", "m1s"))
        assertTrue(n.entries.size > 50_000)
        for (brand in listOf("Samsung", "vivo", "OPPO", "Xiaomi", "POCO", "HONOR", "Meizu", "Google", "motorola", "OnePlus", "realme", "Sony")) {
            assertTrue(brand, n.entries.any { it.brand.equals(brand, true) })
        }
        assertNull(n.resolve("Samsung", "SM-S948", "m3q"))
        assertNull(n.resolve("Xiaomi", "SM-S9480", "m3q"))
    }
    @Test fun reusedCodenameDoesNotMergeModelsOrConflictingSpecs() {
        val n = DeviceNames.parse("Xiaomi\tMODEL-CN\tshared\tPhone\nPOCO\tMODEL-GL\tshared\tPhone Pro\nOther\tMODEL-CN\tshared\tOther Phone".lineSequence())
        assertEquals("Phone", n.resolve("Xiaomi", "MODEL-CN", "shared"))
        assertEquals("Phone Pro", n.resolve("Xiaomi", "MODEL-GL", "shared", "POCO"))
        assertNull(n.resolve("Xiaomi", "UNKNOWN", "shared"))
        val v = DeviceVariants.parse("Vendor\tSKU\tdev\tPhone\tChip A\tCN\tSource A\nVendor\tSKU\tdev\tPhone\tChip B\tGlobal\tSource B".lineSequence())
        assertEquals(2, v.lookup("Vendor", "SKU", "dev").size)
    }
    @Test fun xiaomiOfficialKernelIndexCrosswalkDoesNotSpreadToOtherSkus() {
        val v = variants()
        assertEquals(setOf("Xiaomi XRING O1"), v.lookup("Xiaomi", "25042PN24C", "dijun").map { it.soc }.toSet())
        assertEquals(setOf("Xiaomi XRING O3"), v.lookup("Xiaomi", "2608BPX34C", "lhasa").map { it.soc }.toSet())
        assertEquals(setOf("Qualcomm Snapdragon 8 Elite Gen 5"), v.lookup("Xiaomi", "25098PN5AC", "pandora").map { it.soc }.toSet())
        assertTrue(v.lookup("Xiaomi", "2608BPX34G", "lhasa").isEmpty())
        assertTrue(v.lookup("Xiaomi", "25042PN24G", "dijun").isEmpty())
    }
    @Test fun aliasesAlwaysHaveTraceableSourcesAndSafeUnknownProcess() {
        assertTrue(ChipNames.count >= 250)
        for (c in ChipNames.search("Snapdragon")) assertTrue(c.source.startsWith("https://"))
        assertNull(ChipNames.resolve("MT6991", null, null)!!.process)
    }
}
