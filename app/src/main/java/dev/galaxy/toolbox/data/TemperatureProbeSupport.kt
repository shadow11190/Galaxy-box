package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import java.io.File

internal fun TemperatureFiles.metadata(directory: String): Map<String, String> = buildMap {
    listOf("type", "name", "device/uevent", "device/of_node/name", "of_node/name", "device/of_node/compatible", "of_node/compatible").forEach { leaf ->
        read(File(directory, leaf).path).raw?.takeIf { it.isNotBlank() }?.let { value ->
            put(if (leaf.endsWith("compatible")) "compatible" else leaf, value.replace('\u0000', ' ').take(2048))
        }
    }
    val driverPath = File(directory, "device/driver").path
    val driver = canonical(driverPath)
    if (driver != File(driverPath).absolutePath) put("driver", driver)
}
internal fun TemperatureFiles.gpuThermalMetadata(): Map<String, String> {
    // A readable local device-tree binding is stronger than a thermal label alone.
    for (base in listOf("/sys/class/kgsl/kgsl-3d0/device/of_node", "/sys/class/kgsl/kgsl-3d0/of_node")) {
        val compatible = read("$base/compatible").raw?.replace('\u0000', ' ') ?: continue
        if (!compatible.contains("qcom,kgsl-3d")) continue
        val names = read("$base/qcom,tzone-names").raw?.replace('\u0000', ' ') ?: continue
        return mapOf("gpu compatible" to compatible, "gpu thermal-zone names" to names, "gpu mapping source" to "$base/qcom,tzone-names")
    }
    return emptyMap()
}
internal fun TemperatureFiles.descriptor(name: String, path: String, source: TemperatureSource, unit: TemperatureUnit,
    metadata: Map<String, String>, classifier: TemperatureClassifier, model: String, reference: String): TemperatureDescriptor {
    val result = classifier.classify(name, path, source, unit, metadata, model)
    val id = listOf(source.name, canonical(path), name, result.unit.name, result.classification.ruleId).joinToString("|")
    return TemperatureDescriptor(id, name, path, source, result.unit, result.classification, metadata, reference)
}
internal fun diagnostic(path: String, status: TemperatureReadStatus, atMs: Long, detail: String): TemperatureReading {
    val classification = TemperatureClassification(TemperatureKind.UNKNOWN, TemperatureGroup.UNKNOWN, "來源探索 · $path",
        EvidenceConfidence.UNKNOWN, "檔案系統明確回報的探索狀態；不推測感測器物理位置。", "source-diagnostic")
    return TemperatureReading(TemperatureDescriptor("diagnostic|$path", "來源探索", path, TemperatureSource.DIAGNOSTIC, TemperatureUnit.RAW, classification),
        null, null, status, atMs, statusDetail = detail)
}
