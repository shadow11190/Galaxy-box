package dev.galaxy.toolbox.data

import android.content.Context

/** User corrections are local display choices, never driver calibration. All default off. */
data class BatteryReadOptions(
    val invertCurrent: Boolean = false,
    val estimateVoltage: Boolean = false,
    val dualCell: Boolean = false
) {
    companion object {
        const val STORE = "battery_reading"
        const val INVERT = "invert_current"
        const val ESTIMATE = "estimate_voltage"
        const val DUAL_CELL = "dual_cell"
        fun read(context: Context): BatteryReadOptions = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).let {
            BatteryReadOptions(it.getBoolean(INVERT, false), it.getBoolean(ESTIMATE, false), it.getBoolean(DUAL_CELL, false))
        }
        /** A missing preference means automatic; an explicit off always wins. */
        fun resolve(invertOverride: Boolean?, estimateOverride: Boolean?, dualOverride: Boolean?,
                    reportedMilliVolts: Int?, energyNanoWh: Long?, chargeMicroAh: Int?): BatteryReadOptions {
            val estimate = estimateOverride ?: ((reportedMilliVolts == null || reportedMilliVolts !in 2500..10000) &&
                BatteryElectricalInterpretation.estimatedMilliVolts(energyNanoWh, chargeMicroAh) != null)
            val voltage = BatteryElectricalInterpretation.usableMilliVolts(reportedMilliVolts, estimate, energyNanoWh, chargeMicroAh).first
            return BatteryReadOptions(invertOverride ?: false, estimate,
                dualOverride ?: (voltage != null && voltage in 6000..10000))
        }
        fun readResolved(context: Context, reportedMilliVolts: Int?, energyNanoWh: Long?, chargeMicroAh: Int?): BatteryReadOptions =
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).let { prefs ->
                resolve(if (prefs.contains(INVERT)) prefs.getBoolean(INVERT, false) else null,
                    if (prefs.contains(ESTIMATE)) prefs.getBoolean(ESTIMATE, false) else null,
                    if (prefs.contains(DUAL_CELL)) prefs.getBoolean(DUAL_CELL, false) else null,
                    reportedMilliVolts, energyNanoWh, chargeMicroAh)
            }
    }
}

object BatteryElectricalInterpretation {
    /** nWh / µAh has the numeric unit mV; this is an average energy/charge ratio, not a voltmeter. */
    fun estimatedMilliVolts(energyNanoWh: Long?, chargeMicroAh: Int?): Int? {
        if (energyNanoWh == null || energyNanoWh <= 0 || energyNanoWh == Long.MIN_VALUE ||
            chargeMicroAh == null || chargeMicroAh <= 0 || chargeMicroAh == Int.MIN_VALUE) return null
        val milliVolts = energyNanoWh.toDouble() / chargeMicroAh
        return milliVolts.takeIf { it.isFinite() && it in 2500.0..10000.0 }?.toInt()
    }

    fun usableMilliVolts(reported: Int?, estimateEnabled: Boolean, energyNanoWh: Long?, chargeMicroAh: Int?): Pair<Int?, Boolean> {
        if (reported != null && reported in 2500..10000) return reported to false
        val estimated = if (estimateEnabled) estimatedMilliVolts(energyNanoWh, chargeMicroAh) else null
        return estimated to (estimated != null)
    }

    fun perCellMilliVolts(packMilliVolts: Int?, dualCellEnabled: Boolean): Int? =
        packMilliVolts?.takeIf { dualCellEnabled && it in 6000..10000 }?.div(2)
}
