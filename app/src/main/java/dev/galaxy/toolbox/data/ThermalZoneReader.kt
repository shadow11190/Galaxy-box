package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import java.io.File

class ThermalProbe(
    private val roots: List<File> = listOf(File("/sys/class/thermal"), File("/sys/devices/virtual/thermal")),
    private val files: TemperatureFiles = NioTemperatureFiles(),
    private val classifier: TemperatureClassifier = TemperatureClassifier(), private val model: String = "",
    private val platformMetadata: Map<String, String> = emptyMap()
) {
    private var descriptors: List<TemperatureDescriptor>? = null
    private var discoveryDiagnostics: List<TemperatureReading> = emptyList()
    fun sample(atMs: Long = System.currentTimeMillis()): List<TemperatureReading> {
        if (descriptors == null) discover(atMs)
        return descriptors.orEmpty().map { descriptor -> TemperatureConversion.observation(descriptor, files.read(descriptor.path), atMs) } + discoveryDiagnostics
    }
    private fun discover(atMs: Long) {
        val seen = mutableSetOf<String>()
        val sensors = mutableListOf<TemperatureDescriptor>()
        val diagnostics = mutableListOf<TemperatureReading>()
        val gpuMetadata by lazy { files.gpuThermalMetadata() }
        roots.forEach { root ->
            val listing = files.list(root.path)
            if (listing.status != TemperatureReadStatus.OK) diagnostics.add(diagnostic(root.path, listing.status, atMs, listing.detail))
            val listed = listing.paths.filter { Regex("thermal_zone\\d+").matches(File(it).name) }
            val candidates = if (listed.isEmpty()) (0 until 256).map { File(root, "thermal_zone$it").path } else listed.take(256)
            candidates.forEach zone@ { directory ->
                val type = files.read(File(directory, "type").path)
                val tempPath = File(directory, "temp").path
                val initial = files.read(tempPath)
                if (directory !in listed && type.status != TemperatureReadStatus.OK && initial.status != TemperatureReadStatus.OK) return@zone
                if (!seen.add(files.canonical(directory))) return@zone
                val children = files.list(directory).paths
                val trips = children.filter { Regex("trip_point_\\d+_temp").matches(File(it).name) }.sorted().take(16)
                val metadata = (platformMetadata + gpuMetadata + files.metadata(directory)).toMutableMap().apply {
                    put("探索時間 epoch ms", atMs.toString())
                    put("實際量測候選路徑", tempPath)
                    put("type 讀取狀態", type.status.title)
                    put("介面定義單位", "thermal temp / trip_point_*_temp 依 ABI 為毫攝氏度；狀態或未辨識節點不直接套用")
                    trips.forEach { trip ->
                        val setting = files.read(trip)
                        put(trip, setting.raw ?: setting.status.title)
                        val tripType = trip.removeSuffix("temp") + "type"
                        files.read(tripType).raw?.let { put(tripType, it) }
                    }
                }
                val name = type.raw?.substringBefore('\n')?.trim()?.takeIf { it.isNotBlank() }?.take(160) ?: "${File(directory).name}（驅動名稱未取得）"
                sensors.add(files.descriptor(name, tempPath, TemperatureSource.THERMAL, TemperatureUnit.MILLI_C, metadata, classifier, model, REFERENCE))
                trips.forEach { trip ->
                    // Reserve the diagnostic budget separately: early trip settings must not
                    // prevent later CPU/GPU measurement zones from being discovered.
                    if (sensors.count { it.source == TemperatureSource.TRIP_POINT } < 256)
                        sensors.add(files.descriptor(name, trip, TemperatureSource.TRIP_POINT, TemperatureUnit.MILLI_C, metadata, classifier, model, REFERENCE))
                }
            }
        }
        descriptors = sensors
        discoveryDiagnostics = diagnostics
    }
    companion object { const val REFERENCE = "https://docs.kernel.org/driver-api/thermal/sysfs-api.html" }
}
