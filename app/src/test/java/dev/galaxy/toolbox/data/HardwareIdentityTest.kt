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
    @Test fun exactRedmiAndPocoSkusWorkWhenAndroidReportsXiaomiBrand() {
        val redmi = HardwareIdentityResolver.resolve(names, variants, "Xiaomi", "24117RK2CC", "zorn", "Xiaomi", emptyList())
        assertEquals("REDMI K80", redmi.name)
        assertEquals("Qualcomm Snapdragon 8 Gen 3", redmi.chip?.name)
        assertEquals(ChipBasis.MODEL_REFERENCE, redmi.basis)
        val poco = HardwareIdentityResolver.resolve(names, variants, "Xiaomi", "24117RK2CG", "zorn", "Xiaomi", emptyList())
        assertEquals("Poco F7 Pro", poco.name)
        assertEquals("Qualcomm Snapdragon 8 Gen 3", poco.chip?.name)
        assertEquals(ChipBasis.MODEL_REFERENCE, poco.basis)
        val codenameOnly = HardwareIdentityResolver.resolve(names, variants, "Xiaomi", "unknown", "zorn", "Xiaomi", emptyList())
        assertNull(codenameOnly.chip)
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
    @Test fun honorMainlandPhoneAndTabletSkusUseTheirOwnDeviceCodes() {
        val x60 = HardwareIdentityResolver.resolve(names, variants, "HONOR", "BRC-AN00", "HNBRC-M", reports = emptyList())
        assertEquals("荣耀X60", x60.name)
        assertEquals("MediaTek Dimensity 7025-Ultra", x60.chip?.name)
        val x70 = HardwareIdentityResolver.resolve(names, variants, "HONOR", "MTN-AN80", "HNMTN-Q", reports = emptyList())
        assertEquals("荣耀X70", x70.name)
        assertEquals("Qualcomm Snapdragon 6 Gen 4", x70.chip?.name)
        val v9 = HardwareIdentityResolver.resolve(names, variants, "HONOR", "ROL-W00", "HNROL-M", reports = emptyList())
        assertEquals("荣耀平板V9", v9.name)
        assertEquals("MediaTek Dimensity 8350 Ultimate", v9.chip?.name)
        val internationalCode = HardwareIdentityResolver.resolve(names, variants, "HONOR", "ROL-W00", "HNROL-M1", reports = emptyList())
        assertEquals("HONOR Pad V9", internationalCode.name)
    }
    @Test fun reusedHonorMainlandCodesNeverSelectOneProductArbitrarily() {
        val ser = HardwareIdentityResolver.resolve(names, variants, "HONOR", "SER-AN00", "HNSER-M", reports = emptyList())
        assertTrue(ser.name!!.contains("Power2"))
        assertTrue(ser.name!!.contains("WIN Turbo"))
        assertNull(ser.chip)
        val lna = HardwareIdentityResolver.resolve(names, variants, "HONOR", "LNA-AN00", "HNLNA-M", reports = emptyList())
        assertTrue(lna.name!!.contains("X80i"))
        assertTrue(lna.name!!.contains("Play11 Pro"))
        assertEquals("MediaTek Dimensity 6500 Elite", lna.chip?.name)
    }
    @Test fun honorManufacturerModelCanResolveWithoutInventingAndroidDeviceCode() {
        val magic3Pro = HardwareIdentityResolver.resolve(names, variants, "HONOR", "ELZ-AN10", "unverified-code", reports = emptyList())
        assertEquals("荣耀Magic3 Pro", magic3Pro.name)
        assertEquals("Qualcomm Snapdragon 888+", magic3Pro.chip?.name)
        assertEquals("", magic3Pro.exactVariants.single().device)
        val magic8Pro = HardwareIdentityResolver.resolve(names, variants, "HONOR", "BKQ-AN10", "unverified-code", reports = emptyList())
        assertEquals("荣耀Magic8 Pro", magic8Pro.name)
        assertEquals("Qualcomm Snapdragon 8 Elite Gen 5", magic8Pro.chip?.name)
    }
    @Test fun honorMainlandDigitalSeriesKeepsExactModelAndChipPairing() {
        fun honor(model: String) = HardwareIdentityResolver.resolve(
            names, variants, "HONOR", model, "unverified-code", reports = emptyList())
        assertEquals("MediaTek Dimensity 9000", honor("HPB-AN00").chip?.name)
        assertEquals("Qualcomm Snapdragon 7 Gen 3", honor("AMM-AN00").chip?.name)
        assertEquals("Qualcomm Snapdragon 8 Gen 3", honor("AMP-AN00").chip?.name)
        assertEquals("Qualcomm Snapdragon 7 Gen 4", honor("DNN-AN00").chip?.name)
        assertEquals("Qualcomm Snapdragon 8 Gen 3", honor("DNP-AN00").chip?.name)
        assertEquals("Qualcomm Snapdragon 8 Elite", honor("MEP-AN00").chip?.name)
    }
    @Test fun reusedHonorPlayModelWithDifferentChipDoesNotGuess() {
        val shared = HardwareIdentityResolver.resolve(
            names, variants, "HONOR", "CLK-AN00", "unverified-code", reports = emptyList())
        assertTrue(shared.name!!.contains("Play8T"))
        assertTrue(shared.name!!.contains("畅玩50 Plus"))
        assertNull(shared.chip)
    }
    @Test fun honorRegionalSkusAndOctober2026Magic9AreResolvedSeparately() {
        fun honor(model: String, device: String) = HardwareIdentityResolver.resolve(
            names, variants, "HONOR", model, device, reports = emptyList())
        assertEquals("Qualcomm Snapdragon 7 Gen 4", honor("VKJ-NX9", "HNVKJX").chip?.name)
        assertEquals("Qualcomm Snapdragon 8 Elite", honor("VKP-NX9", "HNVKPX").chip?.name)
        val magic9 = honor("MLY-AN00", "hnmly")
        assertEquals("HONOR Magic9", magic9.name)
        assertEquals("Qualcomm Snapdragon 8 Elite Gen 5", magic9.chip?.name)
        assertTrue(magic9.nameSource.contains("honor.com"))
        val proMax = honor("WKL-AN20", "hnwkl")
        assertEquals("HONOR Magic9 Pro Max", proMax.name)
        assertEquals("Qualcomm Snapdragon 8 Elite Extreme Gen 6", proMax.chip?.name)
        val gpuSpecs = File("src/main/assets/gpu-specs.tsv").bufferedReader().use { GpuSpecs.parse(it.lineSequence()) }
        assertEquals("Adreno 850", gpuSpecs.forChip(proMax.chip)?.gpu)
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
    @Test fun blackSharkGlobalModelUsesVerifiedNameAndChip() {
        val pro = HardwareIdentityResolver.resolve(names, variants, "SHARK", "SHARK KTUS-H0", "katyusha",
            brand = "Blackshark", reports = listOf(ChipReport("Build.SOC_MODEL", "SM8450")))
        assertEquals("Black Shark 5 Pro", pro.name)
        assertEquals("Qualcomm Snapdragon 8 Gen 1", pro.chip?.name)
        assertTrue(pro.nameSource.contains("ntc.gov.ph"))
        val third = HardwareIdentityResolver.resolve(names, variants, "blackshark", "SHARK KLE-H0", "klein",
            reports = emptyList())
        assertEquals("Black Shark 3", third.name)
        assertEquals("Qualcomm Snapdragon 865", third.chip?.name)
    }
    @Test fun blackSharkTabletIdentityUsesExactModelAndDoesNotGuessSharedModels() {
        val gaming = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "BSG1",
            "Black_Shark_Gaming_Tablet", reports = emptyList())
        assertEquals("Black Shark Gaming Tablet", gaming.name)
        assertEquals("Qualcomm Snapdragon 8s Gen 3", gaming.chip?.name)
        val padSe = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "BSM2",
            "PadSE", reports = emptyList())
        assertEquals("Black Shark Pad SE", padSe.name)
        assertEquals("MediaTek Helio G85", padSe.chip?.name)
        val shared = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "SHARK KSR-A0",
            "kaiser", reports = emptyList())
        assertEquals(null, shared.chip)
    }
    @Test fun blackSharkSharedModelUsesDeviceIdentifierBeforeSelectingTabletChip() {
        val wifi = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "BSM4", "Pad7_WiFi", reports = emptyList())
        assertEquals("Black Shark Pad 7 Wi-Fi", wifi.name)
        assertEquals("MediaTek Helio G88", wifi.chip?.name)
        val air = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "BSM4", "Pad_Air7", reports = emptyList())
        assertEquals("Black Shark Pad Air 7", air.name)
        assertEquals("MediaTek Helio G200", air.chip?.name)
        val unknown = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "BSM4", "unknown", reports = emptyList())
        assertTrue(unknown.name!!.contains("共用型號"))
        assertNull(unknown.chip)
    }
    @Test fun blackSharkSharedPhoneCodesKeepCandidateNamesButDoNotInventExactModel() {
        val ksr = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "SHARK KSR-A0", "kaiser", reports = emptyList())
        assertTrue(ksr.name!!.contains("4 Pro / 4S Pro / 5 RS"))
        assertNull(ksr.chip)
        val kle = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "SHARK KLE-A0", "klein", reports = emptyList())
        assertTrue(kle.name!!.contains("3 / 3S"))
        assertEquals("Qualcomm Snapdragon 865", kle.chip?.name)
    }
    @Test fun blackSharkOlderPhoneAndTabletNamesUseReviewedReferences() {
        val helo = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "AWM-A0", "bullhead", reports = emptyList())
        assertEquals("Black Shark Helo", helo.name)
        assertEquals("Qualcomm Snapdragon 845", helo.chip?.name)
        val original = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "SKR-H0", "shark", reports = emptyList())
        assertEquals("Black Shark", original.name)
        assertEquals("Qualcomm Snapdragon 845", original.chip?.name)
        val pad6 = HardwareIdentityResolver.resolve(names, variants, "Blackshark", "ML1107H", "Pad6", reports = emptyList())
        assertEquals("Black Shark Pad 6", pad6.name)
        assertNull(pad6.chip)
    }
    @Test fun mate20ProGoogleNameAliasesCollapseWithoutChangingRsEdition() {
        val pro = HardwareIdentityResolver.resolve(names, variants, "HUAWEI", "LYA-L29", "HWLYA",
            reports = emptyList())
        assertEquals("Mate 20 Pro", pro.name)
        assertEquals("HiSilicon Kirin 980", pro.chip?.name)
        assertEquals("多地區", pro.market)
        val rs = HardwareIdentityResolver.resolve(names, variants, "Huawei", "LYA-AL00P", "HWLYA",
            reports = emptyList())
        assertEquals("Mate 20 RS", rs.name)
    }
}
