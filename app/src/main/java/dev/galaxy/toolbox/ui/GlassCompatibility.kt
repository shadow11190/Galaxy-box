package dev.galaxy.toolbox.ui

import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

internal enum class GlassTier { REFRACTIVE, BLUR, STATIC, REDUCED, OFF }

/** Blur works on Android 12; refraction is an optional Android 13+ enhancement. */
internal object GlassCompatibility {
    val refractionSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= 33 && runCatching {
            RuntimeShader("uniform shader source; half4 main(float2 p) { return source.eval(p); }")
        }.isSuccess
    }
    fun canUse(api: Int, accelerated: Boolean, lowRam: Boolean) =
        api >= 31 && accelerated && !lowRam

    fun tier(style: AppStyle, enabled: Boolean, reduceTransparency: Boolean,
        powerSave: Boolean, api: Int, accelerated: Boolean, lowRam: Boolean,
        refraction: Boolean): GlassTier = when {
        !enabled -> GlassTier.OFF
        reduceTransparency -> GlassTier.REDUCED
        powerSave || !canUse(api, accelerated, lowRam) -> GlassTier.STATIC
        refraction -> GlassTier.REFRACTIVE
        else -> GlassTier.BLUR
    }
}

/** Observe the system setting only while this composition is alive. */
@Composable internal fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isPowerSaveMode == true) }
    DisposableEffect(context, manager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                enabled = manager?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(context, receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}
