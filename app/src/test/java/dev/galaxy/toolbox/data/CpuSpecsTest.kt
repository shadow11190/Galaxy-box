package dev.galaxy.toolbox.data

import org.junit.Assert.*
import org.junit.Test

class CpuSpecsTest {
    private fun specs(model: String) = CpuSpecs.readings(ChipNames.resolve(model, null, null))
    @Test fun eliteGenerationDoesNotComeFromMarketingGenerationNumber() {
        assertTrue(specs("SM8850").first { it.label.contains("世代") }.value!!.contains("第三代"))
        assertTrue(specs("SM8750").first { it.label.contains("世代") }.value!!.contains("第二代"))
        val layout = specs("SM8850").first { it.label.contains("核心組成") }.value!!
        assertTrue(layout.contains("2 × Oryon Prime")); assertTrue(layout.contains("6 × Oryon Performance"))
    }
    @Test fun forGalaxyUsesSameCpuFactsWithoutClaimingRuntimeClock() {
        val chip = ChipNames.resolve("SM8850", null, "Galaxy S26 Ultra")!!
        assertTrue(chip.name.endsWith("for Galaxy"))
        val rows = CpuSpecs.readings(chip)
        assertTrue(rows.first { it.label.contains("公版頻率") }.value!!.contains("4.74"))
        assertTrue(rows.all { it.group == "CPU 公開規格" && it.source.contains("https://") })
        assertFalse(rows.any { it.label.contains("當前") || it.label.contains("辨識依據") || it.label.contains("指令功能") })
    }
    @Test fun oldChipsRetainSpecificKryoNamesAndCoreCounts() {
        assertTrue(ChipNames.resolve("Snapdragon 865", null, null)!!.cpu!!.contains("585"))
        assertEquals("8", specs("Snapdragon 865").first { it.label.contains("核心數") }.value)
        assertTrue(specs("SM8750").first { it.label.contains("核心數") }.value!!.contains("7／8"))
    }
    @Test fun ambiguousOrMissingChipsNeverBorrowAnAdjacentChipsGeneration() {
        assertTrue(CpuSpecs.readings(null).isEmpty())
        assertFalse(specs("SM8550").any { it.value?.contains("Oryon") == true })
        assertTrue(specs("not-a-chip").isEmpty())
        val conflicting = ChipDescription("shared", "", null, null, "", listOf("Qualcomm Snapdragon 8 Elite", "Qualcomm Snapdragon 8 Elite Gen 5"))
        assertFalse(CpuSpecs.readings(conflicting).any { it.label.contains("世代") || it.label.contains("頻率") })
    }
    @Test fun legacyAndOtherVendorsUseTheSameDetailedCatalog() {
        assertTrue(specs("Snapdragon 845").first { it.label.contains("核心組成") }.value!!.contains("Kryo 385"))
        assertTrue(specs("Exynos 9825").first { it.label.contains("世代") }.value!!.contains("第四代"))
        assertTrue(specs("Exynos 2200").first { it.label.contains("核心組成") }.value!!.contains("Cortex-X2"))
        assertTrue(specs("Dimensity 9300+").first { it.label.contains("核心組成") }.value!!.contains("Cortex"))
        assertFalse(specs("SM8950").any { it.label.contains("世代") && it.value?.contains("第四代") == true })
    }
    @Test fun everyGeneratedCpuRecordHasTheSameSchemaAndNoOrphanNames() {
        assertEquals(ChipNames.count, CpuSpecData.rows.size)
        for (row in CpuSpecData.rows) {
            val p = row.split('\t'); assertEquals(p.first(), 8, p.size)
            assertNotNull(p.first(), ChipNames.identify(listOf(ChipReport("test", p.first()))))
        }
    }
}
