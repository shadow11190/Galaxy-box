package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.Reading

/** Shared catalog for old/new chips from every vendor; never invent runtime capabilities. */
object CpuSpecs {
    private val specs = CpuSpecData.rows.associate { row -> row.split('\t').let { it.first() to it.drop(1) } }
    fun readings(chip: ChipDescription?): List<Reading> {
        if (chip == null) return emptyList()
        val candidates = chip.candidates.map { specs[it] ?: return emptyList() }
        if (candidates.isEmpty()) return emptyList()
        val labels = listOf("CPU 微架構世代（規格）", "CPU 核心組成（規格）", "CPU 核心數（規格）", "CPU 架構（規格）", "CPU 公版頻率（規格）", "CPU 快取（規格）")
        return labels.mapIndexedNotNull { i, label ->
            val value = candidates.map { it[i] }.distinct().singleOrNull()?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            Reading(label, value.replace('|', '\n'), candidates.map { it[6] }.distinct().joinToString("\n").replace('|', '\n'),
                "公開規格，非本機逐核心量測。公版時脈／快取可能因細分版本不同；本機頻率、ABI 與指令旗標另列，不由規格推算。",
                group = "CPU 公開規格")
        }
    }
}
