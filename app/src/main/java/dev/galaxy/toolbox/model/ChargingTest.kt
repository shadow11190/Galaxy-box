package dev.galaxy.toolbox.model

data class ChargingPoint(val elapsedMs: Long, val watts: Double?)

data class ChargingTestResult(
    val startedAt: Long,
    val durationMs: Long = 0,
    val samples: Int = 0,
    val peak: Double? = null,
    val integratedWattMs: Double = 0.0,
    val measuredMs: Long = 0,
    val startPercent: Int? = null,
    val endPercent: Int? = null,
    val estimatedVoltage: Boolean = false,
) {
    val average: Double? get() = if (measuredMs > 0) integratedWattMs / measuredMs else null
    val energyWh: Double? get() = if (measuredMs > 0) integratedWattMs / 3_600_000 else null
}

data class ChargingTestState(
    val active: Boolean = false,
    val result: ChargingTestResult? = null,
    val points: List<ChargingPoint> = emptyList(),
    val message: String? = null,
    val history: List<ChargingTestResult> = emptyList(),
)

/** Accumulates net battery power only across consecutive, timestamped readings. */
internal fun ChargingTestState.addReading(reading: ChargingPowerReading?, elapsedMs: Long): ChargingTestState {
    val result = result ?: return this
    val last = points.lastOrNull()
    if (last != null && elapsedMs <= last.elapsedMs) return this
    val watts = reading?.watts?.takeIf { it.isFinite() }
    val interval = last?.let { elapsedMs - it.elapsedMs } ?: 0
    val covered = if (last?.watts != null && watts != null && interval in 1..6_000) interval else 0
    val area = if (covered > 0 && last?.watts != null && watts != null) (last.watts + watts) * .5 * covered else 0.0
    return copy(
        points = (points + ChargingPoint(elapsedMs, watts)).takeLast(180),
        result = result.copy(
            durationMs = elapsedMs,
            samples = result.samples + if (watts == null) 0 else 1,
            peak = listOfNotNull(result.peak, watts).maxOrNull(),
            integratedWattMs = result.integratedWattMs + area,
            measuredMs = result.measuredMs + covered,
            endPercent = reading?.batteryPercent ?: result.endPercent,
            estimatedVoltage = result.estimatedVoltage || (watts != null && reading?.voltageEstimated == true),
        ),
    )
}
