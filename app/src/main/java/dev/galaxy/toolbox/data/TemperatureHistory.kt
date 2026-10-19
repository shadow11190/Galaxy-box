package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*

/** All state is kept on the ViewModel's main dispatcher. Series keys include source path, name and unit. */
class TemperatureHistory {
    private val latest = linkedMapOf<String, TemperatureReading>()
    private val samples = linkedMapOf<String, List<TemperaturePoint>>()
    fun update(observations: List<TemperatureReading>, nowMs: Long, replaceFileSources: Boolean = false) {
        if (replaceFileSources) {
            val present = observations.map { it.sensor.id }.toSet()
            latest.keys.filter { it !in present && latest[it]?.sensor?.source != TemperatureSource.BATTERY_API }.forEach { latest.remove(it); samples.remove(it) }
        }
        // Preserve live sources before diagnostic trip points when a device exceeds the budget.
        observations.sortedBy { if (it.sensor.source == TemperatureSource.TRIP_POINT) 1 else 0 }.take(768).forEach { current ->
            val id = current.sensor.id
            val previous = latest[id]
            val valid = current.status == TemperatureReadStatus.OK
            val updated = current.copy(lastSuccessAtMs = if (valid) current.attemptedAtMs else previous?.lastSuccessAtMs,
                lastGoodCelsius = if (valid) current.celsius else previous?.lastGoodCelsius)
            latest[id] = updated
            if (current.sensor.classification.kind == TemperatureKind.MEASUREMENT) {
                val point = TemperaturePoint(current.attemptedAtMs, if (updated.validMeasurement(nowMs)) updated.celsius else null)
                samples[id] = (samples[id].orEmpty() + point).filter { it.atMs >= nowMs - WINDOW_MS && it.atMs <= nowMs }.takeLast(160)
            }
        }
        while (latest.size > 768) { val first = latest.keys.first(); latest.remove(first); samples.remove(first) }
        samples.keys.toList().forEach { id -> samples[id] = samples[id].orEmpty().filter { it.atMs >= nowMs - WINDOW_MS && it.atMs <= nowMs } }
    }
    fun readings(): List<TemperatureReading> = latest.values.toList()
    fun series(): Map<String, List<TemperaturePoint>> = samples.toMap()
    companion object { const val WINDOW_MS = 300_000L; const val MAX_CONNECTED_GAP_MS = 6_000L }
}
