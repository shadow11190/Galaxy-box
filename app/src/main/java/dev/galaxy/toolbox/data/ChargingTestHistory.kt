package dev.galaxy.toolbox.data

import android.content.Context
import dev.galaxy.toolbox.model.ChargingTestResult
import org.json.JSONArray
import org.json.JSONObject

internal class ChargingTestHistory(context: Context) {
    private val preferences = context.getSharedPreferences("charging_tests", Context.MODE_PRIVATE)

    fun read(): List<ChargingTestResult> {
        val records = runCatching { JSONArray(preferences.getString("history", "[]")) }.getOrElse { JSONArray() }
        return (0 until records.length()).mapNotNull { index ->
            runCatching {
                val item = records.getJSONObject(index)
                ChargingTestResult(
                    startedAt = item.getLong("start"), durationMs = item.getLong("duration"),
                    samples = item.getInt("samples"), peak = item.optDouble("peak").takeIf { it.isFinite() },
                    integratedWattMs = item.getDouble("area"), measuredMs = item.getLong("measured"),
                    startPercent = item.optInt("from", -1).takeIf { it in 0..100 },
                    endPercent = item.optInt("to", -1).takeIf { it in 0..100 },
                    estimatedVoltage = item.optBoolean("estimated"),
                )
            }.getOrNull()
        }
    }

    fun save(history: List<ChargingTestResult>) {
        val records = JSONArray()
        history.forEach { result ->
            records.put(JSONObject().apply {
                put("start", result.startedAt)
                put("duration", result.durationMs)
                put("samples", result.samples)
                put("peak", result.peak)
                put("area", result.integratedWattMs)
                put("measured", result.measuredMs)
                put("from", result.startPercent)
                put("to", result.endPercent)
                put("estimated", result.estimatedVoltage)
            })
        }
        preferences.edit().putString("history", records.toString()).apply()
    }
}
