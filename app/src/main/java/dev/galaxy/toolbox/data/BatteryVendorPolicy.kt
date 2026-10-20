package dev.galaxy.toolbox.data

import java.util.Locale

enum class BatteryVendor(val title: String) {
    SAMSUNG("Samsung"), XIAOMI("Xiaomi／Redmi／POCO"), OPPO("OPPO"), OTHER("Android");

    companion object {
        fun of(manufacturer: String?, brand: String? = null): BatteryVendor {
            val names = listOfNotNull(manufacturer, brand).map { it.trim().lowercase(Locale.ROOT) }
            return when {
                names.any { it == "samsung" } -> SAMSUNG
                names.any { it in setOf("xiaomi", "redmi", "poco") } -> XIAOMI
                names.any { it in setOf("oppo", "oplus") } -> OPPO
                else -> OTHER
            }
        }
    }
}

object BatteryVendorPolicy {
    /** Some OPPO MediaTek drivers have exposed SOC percent as CHARGE_COUNTER. */
    fun remainingChargeMicroAh(raw: Int?, percent: Int?, vendor: BatteryVendor = BatteryVendor.OTHER): Int? {
        if (raw == null || raw <= 0 || raw == Int.MIN_VALUE) return null
        // An OPLUS driver has published SOC percent through CHARGE_COUNTER. A 1..100
        // reading is not useful as a remaining-charge measurement even without level.
        if (vendor == BatteryVendor.OPPO && raw in 1..100) return null
        // At >=10% battery, <100 mAh of *remaining* charge is inconsistent with a
        // handset-sized pack. This catches mAh/percent masquerading as µAh without
        // suppressing a near-empty battery solely because its raw number is small.
        if (percent != null && percent >= 10 && raw < 100_000) return null
        return raw
    }
}
