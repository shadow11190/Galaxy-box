package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import java.io.File

class HwmonProbe(
    private val roots: List<File> = listOf(File("/sys/class/hwmon"), File("/sys/devices/virtual/hwmon")),
    private val gpuFiles: List<File>? = null,
    private val files: TemperatureFiles = NioTemperatureFiles(),
    private val classifier: TemperatureClassifier = TemperatureClassifier(), private val model: String = ""
) {
    private data class Node(val path: String, val name: String, val prefix: String?, val metadata: Map<String, String>)
    private var nodes: List<Node>? = null
    private var diagnostics: List<TemperatureReading> = emptyList()
    fun sample(atMs: Long = System.currentTimeMillis()): List<TemperatureReading> {
        if (nodes == null) discover(atMs)
        return nodes.orEmpty().map { node ->
            fun flag(suffix: String) = node.prefix?.let { files.read(File(File(node.path).parentFile, "${it}_$suffix").path) }
            val type = flag("type")
            val fault = flag("fault")
            val enable = flag("enable")
            val typeRaw = type?.raw
            val flagFailure = listOfNotNull(type, fault, enable).firstOrNull { it.status !in listOf(TemperatureReadStatus.OK, TemperatureReadStatus.NOT_FOUND) }
            val blocked = when {
                flagFailure != null -> flagFailure.status
                enable?.raw == "0" -> TemperatureReadStatus.DISABLED
                enable?.status == TemperatureReadStatus.OK && enable.raw != "1" -> TemperatureReadStatus.UNPARSABLE
                fault?.raw == "1" -> TemperatureReadStatus.FAULT
                fault?.status == TemperatureReadStatus.OK && fault.raw != "0" -> TemperatureReadStatus.UNPARSABLE
                type?.status == TemperatureReadStatus.OK && typeRaw?.toIntOrNull() !in setOf(1, 2, 3, 5, 6) -> TemperatureReadStatus.UNIT_UNCONFIRMED
                else -> null
            }
            val typeUnavailable = type != null && type.status !in listOf(TemperatureReadStatus.OK, TemperatureReadStatus.NOT_FOUND)
            val unit = if (blocked == TemperatureReadStatus.UNIT_UNCONFIRMED || typeUnavailable) TemperatureUnit.RAW else TemperatureUnit.MILLI_C
            val metadata = node.metadata + listOfNotNull(type?.let { "temp_type" to (it.raw ?: it.status.title) }, fault?.let { "temp_fault" to (it.raw ?: it.status.title) }, enable?.let { "temp_enable" to (it.raw ?: it.status.title) }).toMap()
            val descriptor = files.descriptor(node.name, node.path, if (node.prefix == null) TemperatureSource.KGSL else TemperatureSource.HWMON,
                unit, metadata, classifier, model, if (node.prefix == null) KGSL_REFERENCE else HWMON_REFERENCE)
            val rawInput = files.read(node.path)
            val input = if (node.prefix == null && rawInput.status == TemperatureReadStatus.OK && rawInput.raw == "-2147483648")
                rawInput.copy(status = TemperatureReadStatus.NO_DATA, detail = "KGSL 驅動未取得測點時回傳 INT_MIN") else rawInput
            TemperatureConversion.observation(descriptor, input, atMs, if (input.status == TemperatureReadStatus.OK) blocked else null,
                if (blocked != null) "旗標限制：${blocked.title}；熱敏電阻可能回報電壓，不猜換算。" else "")
        } + diagnostics
    }
    private fun discover(atMs: Long) {
        val result = mutableListOf<Node>()
        val seen = mutableSetOf<String>()
        val errors = mutableListOf<TemperatureReading>()
        fun add(node: Node) { if (result.size < 256 && seen.add(files.canonical(node.path))) result.add(node) }
        roots.forEach { root ->
            val listing = files.list(root.path)
            if (listing.status != TemperatureReadStatus.OK) errors.add(diagnostic(root.path, listing.status, atMs, listing.detail))
            val listed = listing.paths.filter { Regex("hwmon\\d+").matches(File(it).name) }
            val chips = if (listed.isEmpty()) (0 until 32).map { File(root, "hwmon$it").path } else listed.take(64)
            chips.forEach { chip ->
                listOf(chip, File(chip, "device").path).forEach { directory ->
                    val name = files.read(File(directory, "name").path).raw ?: files.read(File(chip, "name").path).raw
                    val inputsListed = files.list(directory).paths.filter { Regex("temp[1-9]\\d*_input").matches(File(it).name) }
                    val inputs = if (inputsListed.isEmpty() && name != null) (1..16).map { File(directory, "temp${it}_input").path } else inputsListed.take(64)
                    val metadata by lazy { files.metadata(directory) + ("探索時間 epoch ms" to atMs.toString()) }
                    inputs.sorted().forEach inputLoop@ { input ->
                        if (result.size >= 256) return@inputLoop
                        val value = files.read(input)
                        if (input !in inputsListed && value.status != TemperatureReadStatus.OK) return@inputLoop
                        val prefix = File(input).name.removeSuffix("_input")
                        val label = files.read(File(directory, "${prefix}_label").path).raw ?: name ?: "${File(chip).name} / $prefix"
                        add(Node(input, label.take(160), prefix, metadata + ("hwmon name" to (name ?: "未提供"))))
                    }
                }
            }
        }
        val gpuInputs = gpuFiles ?: buildList {
            GpuPaths.bases(files).forEach { add(File("$it/temp")) }
            val alias = "/sys/kernel/gpu/gpu_tmu"
            if (GpuPaths.isKgslTemp(GpuPaths.canonical(files, alias))) add(File(alias))
        }
        gpuInputs.forEach { input ->
            val value = files.read(input.path)
            if (value.status != TemperatureReadStatus.NOT_FOUND) add(Node(input.path, "KGSL GPU", null,
                files.metadata(File(GpuPaths.canonical(files, input.path)).parent.orEmpty()) + files.metadata(input.parent.orEmpty()) + ("KGSL contract path" to GpuPaths.canonical(files, input.path)) + ("介面依據" to "公開 KGSL temp 契約為毫攝氏度；仍需本機 compatible 或機型規則佐證，僅憑名稱不納摘要")))
        }
        nodes = result
        diagnostics = errors
    }
    companion object {
        const val HWMON_REFERENCE = "https://docs.kernel.org/hwmon/sysfs-interface.html"
        const val KGSL_REFERENCE = "https://android.googlesource.com/kernel/msm/+/730cf84987cc34f1f0e53cbdd4153a2227563dc7/drivers/gpu/msm/kgsl_pwrctrl.c"
    }
}
