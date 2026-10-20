package dev.galaxy.toolbox.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class HardwareIdentityTest {
    private val names = File("src/main/assets/android-device-names.tsv").bufferedReader().use { DeviceNames.parse(it.lineSequence()) }
    private val variants = File("src/main/assets/device-variants.tsv").bufferedReader().use { DeviceVariants.parse(it.lineSequence()) }
    private fun xiaomi(model: String, reports: List<ChipReport> = emptyList(), device: String = "rothko") =
        HardwareIdentityResolver.resolve(names, variants, "Xiaomi", model, device, reports = reports)

    @Test fun japanFullModelRefinesSharedMediaTekNumberAndShowsProcess() {
        val identity = xiaomi("2407FPN8ER", listOf(ChipReport("Build.SOC_MODEL", "MT6989")))
        assertEquals("Xiaomi 14T Pro", identity.name)
        assertEquals("MediaTek Dimensity 9300+", identity.chip?.name)
        assertEquals("TSMC 4 nm（第三代）", identity.chip?.process)
        assertTrue(identity.chip!!.cpu!!.contains("Cortex-X4"))
        assertEquals(ChipBasis.HARDWARE_WITH_SKU, identity.basis)
        assertEquals("日本版（SIM-free）", identity.market)
        assertFalse(identity.chip.conflict)
    }
    @Test fun unreadableOrUnrecognizedHardwareFallsBackToFullSku() {
        for (reports in listOf(emptyList(), listOf(ChipReport("SOC_MODEL", "unlisted-vendor-code")))) {
            val identity = xiaomi("2407FPN8ER", reports)
            assertEquals("MediaTek Dimensity 9300+", identity.chip?.name)
            assertEquals(ChipBasis.MODEL_REFERENCE, identity.basis)
            assertTrue(identity.chipSource.contains("https://www.mi.com/jp/"))
        }
    }
    @Test fun regionalModelsRemainSeparateAndDoNotUseSuffixRules() {
        assertEquals("國際版", xiaomi("2407FPN8EG").market)
        assertEquals("日本 SoftBank 版", xiaomi("A402XM").market)
        assertEquals("日本版（SIM-free）", xiaomi("2407FPN8ER").market)
        // Unknown SKU can use a unique codename's chip reference, but cannot invent a region.
        val unknown = xiaomi("2407FPN8E-FAKE")
        assertEquals(ChipBasis.DEVICE_REFERENCE, unknown.basis)
        assertTrue(unknown.exactVariants.isEmpty()); assertNull(unknown.market)
    }
    @Test fun actualUniqueChipIsNeverOverwrittenByModelSpecification() {
        val identity = xiaomi("2407FPN8ER", listOf(ChipReport("SOC_MODEL", "SM8650")))
        assertEquals("Qualcomm Snapdragon 8 Gen 3", identity.chip?.name)
        assertEquals(ChipBasis.HARDWARE, identity.basis)
        assertTrue(identity.chip!!.conflict)
    }
    @Test fun codenameFallbackConnectsMarketingNameAndChipButNotRegion() {
        val identity = xiaomi("unknown")
        assertEquals("Xiaomi 14T Pro", identity.name)
        assertEquals("MediaTek Dimensity 9300+", identity.chip?.name)
        assertEquals(ChipBasis.DEVICE_REFERENCE, identity.basis)
        assertNull(identity.market)
        assertNull(xiaomi("unknown", device = "unknown").chip)
    }
    @Test fun reusedCodeWithDifferentChipsOrNamesStaysUnresolved() {
        val n = DeviceNames.parse("Vendor\tSKU-A\tshared\tPhone\nVendor\tSKU-B\tshared\tPhone".lineSequence())
        val v = DeviceVariants.parse("Vendor\tSKU-A\tshared\tPhone\tMediaTek Dimensity 9300+\tJP\tSource A\nVendor\tSKU-B\tshared\tPhone\tMediaTek Dimensity 9400\tGlobal\tSource B".lineSequence())
        val result = HardwareIdentityResolver.resolve(n, v, "Vendor", "unknown", "shared", reports = emptyList())
        assertNull(result.chip); assertNull(result.market)
        val distinctNames = DeviceNames.parse("Vendor\tSKU-A\tshared\tPhone\nVendor\tSKU-B\tshared\tPhone Pro".lineSequence())
        val ambiguous = HardwareIdentityResolver.resolve(distinctNames, v, "Vendor", "unknown", "shared", reports = emptyList())
        assertNull(ambiguous.name); assertNull(ambiguous.chip)
    }
    @Test fun sourcesWithSameFullSkuAndChipDoNotCrashRefinement() {
        val n = DeviceNames.parse("Xiaomi\tSKU\tdev\tXiaomi 14T Pro".lineSequence())
        val v = DeviceVariants.parse("Xiaomi\tSKU\tdev\tXiaomi 14T Pro\tMediaTek Dimensity 9300+\tJP\tSource A\nXiaomi\tSKU\tdev\tXiaomi 14T Pro\tMediaTek Dimensity 9300+\tJP\tSource B".lineSequence())
        val result = HardwareIdentityResolver.resolve(n, v, "Xiaomi", "SKU", "dev", reports = listOf(ChipReport("SOC", "MT6989")))
        assertEquals("MediaTek Dimensity 9300+", result.chip?.name)
        assertEquals("JP", result.market)
    }
    @Test fun crossBrandCodesDoNotSelectXiaomiSpecifications() {
        val result = HardwareIdentityResolver.resolve(names, variants, "Samsung", "2407FPN8ER", "rothko", reports = emptyList())
        assertNull(result.name); assertNull(result.chip); assertNull(result.market)
    }
    @Test fun samsungKeepsHardwarePriorityAndCanUseExactSkuWhenMissing() {
        val reference = HardwareIdentityResolver.resolve(names, variants, "Samsung", "SM-S9480", "m3q", reports = emptyList())
        assertEquals("Galaxy S26 Ultra", reference.name)
        assertEquals("Qualcomm Snapdragon 8 Elite Gen 5 for Galaxy", reference.chip?.name)
        assertEquals(ChipBasis.MODEL_REFERENCE, reference.basis)
        val actual = HardwareIdentityResolver.resolve(names, variants, "Samsung", "SM-S9480", "m3q", reports = listOf(ChipReport("SOC", "Exynos 2400")))
        assertEquals("Samsung Exynos 2400", actual.chip?.name)
        assertTrue(actual.chip!!.conflict)
    }
    @Test fun everyUnambiguousCatalogSkuUsesTheSharedFallbackAcrossBrands() {
        var checked = 0; val brands = mutableSetOf<String>()
        for (entry in variants.entries.distinctBy { Triple(it.brand, it.model, it.device) }) {
            val matching = variants.lookup(entry.brand, entry.model, entry.device)
            val expected = matching.map { it.soc }.distinct().singleOrNull()?.let {
                ChipNames.identify(listOf(ChipReport("reference", it)))
            }?.takeIf { it.candidates.size == 1 } ?: continue
            val actual = HardwareIdentityResolver.resolve(names, variants, entry.brand, entry.model, entry.device, reports = emptyList())
            assertEquals("${entry.brand}/${entry.model}", expected.candidates, actual.chip?.candidates)
            assertEquals(expected.process, actual.chip?.process)
            assertEquals(ChipBasis.MODEL_REFERENCE, actual.basis)
            checked++; brands += entry.brand.lowercase()
        }
        assertTrue("Only $checked SKUs verified", checked > 1000)
        assertTrue(brands.containsAll(listOf("samsung", "xiaomi", "oneplus", "sony", "motorola")))
    }
    @Test fun officialCpuAndProcessFactsAreNotLimitedToOnePhone() {
        assertTrue(ChipNames.identify(listOf(ChipReport("SOC", "Dimensity 8300")))!!.process!!.contains("4 nm"))
        assertTrue(ChipNames.identify(listOf(ChipReport("SOC", "Helio G99")))!!.process!!.contains("6 nm"))
        assertTrue(ChipNames.identify(listOf(ChipReport("SOC", "Exynos 2200")))!!.process!!.contains("4 nm"))
        // On this product's page, 4 nm describes the companion connectivity chip, not the SoC.
        assertEquals("3 nm", ChipNames.identify(listOf(ChipReport("SOC", "Dimensity 9400+")))!!.process)
    }
    @Test fun fullSkuCanSupplyNameEvenWhenAbsentFromPlayNameList() {
        val n = DeviceNames.parse(emptySequence())
        val v = DeviceVariants.parse("Vendor\tSKU\tdev\tMarket Phone\tMediaTek Dimensity 9300+\tJP\thttps://example.com/spec".lineSequence())
        val actual = HardwareIdentityResolver.resolve(n, v, "Vendor", "SKU", "dev", reports = emptyList())
        assertEquals("Market Phone", actual.name)
        assertEquals("https://example.com/spec", actual.nameSource)
    }
}
