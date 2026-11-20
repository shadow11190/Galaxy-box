package dev.galaxy.toolbox.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GpuSpecsTest {
    private val specs = File("src/main/assets/gpu-specs.tsv").bufferedReader()
        .use { GpuSpecs.parse(it.lineSequence()) }

    @Test fun chipsetGpuSpecificationIsSeparateFromLiveRenderer() {
        val snapdragon = ChipNames.identify(listOf(ChipReport("SOC", "Snapdragon 870")))
        assertEquals("Adreno 650", specs.forChip(snapdragon)?.gpu)
        val mediatek = ChipNames.identify(listOf(ChipReport("SOC", "Helio G200")))
        assertEquals("Mali-G57 MC2", specs.forChip(mediatek)?.gpu)
        assertTrue(specs.forChip(mediatek)!!.source.startsWith("https://"))
    }

    @Test fun ambiguousOrUnknownChipDoesNotGuessGpu() {
        val ambiguous = ChipDescription("候選", "Qualcomm", null, null, "", listOf(
            "Qualcomm Snapdragon 888", "Qualcomm Snapdragon 888+"))
        assertNull(specs.forChip(ambiguous))
        assertNull(specs.forChip(null))
    }
}
