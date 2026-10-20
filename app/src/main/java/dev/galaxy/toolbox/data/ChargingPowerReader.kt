package dev.galaxy.toolbox.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import dev.galaxy.toolbox.model.ChargingPowerReading

object ChargingPowerReader {
    fun read(context: Context): ChargingPowerReading {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        fun extra(key: String) = intent?.takeIf { it.hasExtra(key) }?.getIntExtra(key, Int.MIN_VALUE)?.takeUnless { it == Int.MIN_VALUE }
        val current = runCatching { context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
            .getOrNull()?.takeUnless { it == Int.MIN_VALUE }
        val level = extra(BatteryManager.EXTRA_LEVEL)
        val scale = extra(BatteryManager.EXTRA_SCALE)
        val battery = context.getSystemService(BatteryManager::class.java)
        val energy = runCatching { battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) }.getOrNull()
        val charge = runCatching { battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) }.getOrNull()
        val usableCharge = BatteryVendorPolicy.remainingChargeMicroAh(charge,
            if (level != null && scale != null && scale > 0) (level.toLong() * 100 / scale).toInt() else null,
            BatteryVendor.of(Build.MANUFACTURER, Build.BRAND))
        val reportedVoltage = extra(BatteryManager.EXTRA_VOLTAGE)
        val options = BatteryReadOptions.readResolved(context, reportedVoltage, energy, usableCharge)
        val voltage = BatteryElectricalInterpretation.usableMilliVolts(reportedVoltage, options.estimateVoltage, energy, usableCharge)
        return ChargingPowerReading(current, voltage.first,
            if (level != null && scale != null && scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else null,
            extra(BatteryManager.EXTRA_PLUGGED)?.let { it != 0 }, extra(BatteryManager.EXTRA_STATUS) == BatteryManager.BATTERY_STATUS_FULL,
            System.currentTimeMillis(), extra(BatteryManager.EXTRA_STATUS), options.invertCurrent, voltage.second, options.dualCell,
            options.estimateVoltage)
    }
}
