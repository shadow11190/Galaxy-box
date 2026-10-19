package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*

/** Bounded discovery of the documented Qualcomm KGSL interface, not a vendor-agnostic unit guess. */
internal object GpuPaths {
    const val CLASS = "/sys/class/kgsl/kgsl-3d0"
    const val LEGACY = "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0"
    const val REFERENCE = "https://android.googlesource.com/kernel/msm/+/bb9406fd87124400118f8034c745d8523267f1a5/drivers/gpu/msm/kgsl_pwrctrl.c"
    fun read(files: TemperatureFiles, path: String): TemperatureFileResult = try { files.read(path) }
        catch (e: Exception) { TemperatureFileResult(null, NioTemperatureFiles.fileFailure(e), e.javaClass.simpleName) }
    fun list(files: TemperatureFiles, path: String): TemperatureDirectoryResult = try { files.list(path) }
        catch (e: Exception) { TemperatureDirectoryResult(emptyList(), NioTemperatureFiles.fileFailure(e), e.javaClass.simpleName) }
    fun canonical(files: TemperatureFiles, path: String): String = runCatching { files.canonical(path) }.getOrDefault(path)
    fun isKgslTemp(path: String) = path == "$CLASS/temp" || path.startsWith("/sys/devices/") && path.endsWith("/kgsl/kgsl-3d0/temp")
    fun bases(files: TemperatureFiles, onDirectory: (String, TemperatureDirectoryResult) -> Unit = { _, _ -> }): List<String> = buildList {
        add(CLASS); add(LEGACY)
        for (root in listOf("/sys/devices/platform", "/sys/devices/platform/soc", "/sys/devices", "/sys/devices/soc.0")) {
            list(files, root).also { onDirectory(root, it) }.paths.asSequence().filter { it.substringBeforeLast('/', "") == root }
                .filter { Regex("(?:[0-9a-fA-F]+[.])?qcom,kgsl-3d0").matches(it.substringAfterLast('/')) }
                .sorted().take(2).forEach { add("$it/kgsl/kgsl-3d0") }
        }
    }.distinct().take(8)
    fun devfreq(files: TemperatureFiles, onDirectory: (String, TemperatureDirectoryResult) -> Unit = { _, _ -> }): List<String> = buildList {
        for (root in listOf("/sys/class/devfreq", "/sys/devices/virtual/devfreq")) {
            for (path in list(files, root).also { onDirectory(root, it) }.paths.filter { it.substringBeforeLast('/', "") == root }.sorted().take(64)) {
                val leaf = path.substringAfterLast('/')
                val canonical = canonical(files, path)
                // In particular, never assign Hz to gpufreq/Mali/DDR merely from a broad GPU token.
                if (leaf == "kgsl-3d0" || Regex("(?:[0-9a-fA-F]+[.])?qcom,kgsl-3d0").matches(leaf) ||
                    canonical.split('/').any { it == "kgsl-3d0" }) add(path)
            }
        }
    }.distinct().take(8)
}
