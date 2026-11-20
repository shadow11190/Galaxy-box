package dev.galaxy.toolbox.data

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.reflect.InvocationTargetException

/**
 * Best-effort access to the A2DP codec APIs that Android does not expose to ordinary apps.
 *
 * AOSP permits BLUETOOTH_CONNECT plus a user-approved CompanionDeviceManager association
 * with the exact Bluetooth device as an alternative to BLUETOOTH_PRIVILEGED. The entry points
 * remain hidden @SystemApi methods, however, so non-SDK restrictions or OEM Bluetooth stacks
 * can still reject them. A successful setter invocation only means the request was sent; the
 * caller must read the codec status again before reporting that a codec actually changed.
 */
@RequiresApi(33)
internal object A2dpCodecAccess {
    private val macPattern = Regex("(?i)^[0-9a-f]{2}(?::[0-9a-f]{2}){5}$")

    fun hasAssociation(context: Context, address: String): Boolean {
        if (!macPattern.matches(address)) return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.myAssociations.any {
                    it.deviceMacAddress?.toString()?.equals(address, ignoreCase = true) == true
                }
            } else {
                @Suppress("DEPRECATION")
                manager.associations.any { it.equals(address, ignoreCase = true) }
            }
        } catch (_: SecurityException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    /** Removes only this app's association for the exact Bluetooth MAC address. */
    fun removeAssociation(context: Context, address: String): Boolean {
        if (!macPattern.matches(address)) return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val association = manager.myAssociations.firstOrNull {
                    it.deviceMacAddress?.toString()?.equals(address, ignoreCase = true) == true
                } ?: return false
                manager.disassociate(association.id)
            } else {
                @Suppress("DEPRECATION")
                val associatedAddress = manager.associations.firstOrNull {
                    it.equals(address, ignoreCase = true)
                } ?: return false
                @Suppress("DEPRECATION")
                manager.disassociate(associatedAddress)
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    /** Returns null when the connected device has not yet reported a codec configuration. */
    fun read(profile: BluetoothA2dp, device: BluetoothDevice): BluetoothCodecStatus? {
        requireCodecApi()
        val method = BluetoothA2dp::class.java.getMethod(
            "getCodecStatus", BluetoothDevice::class.java,
        )
        return method.invoke(profile, device) as? BluetoothCodecStatus
    }

    /**
     * Sends a codec preference. A NONE parameter leaves that choice to Android; an explicit
     * parameter must be one flag reported by the selected capability. This never asserts that
     * the remote device accepted or began using the requested configuration.
     */
    fun request(
        context: Context,
        profile: BluetoothA2dp,
        device: BluetoothDevice,
        status: BluetoothCodecStatus,
        selected: BluetoothCodecConfig,
        sampleRate: Int = BluetoothCodecConfig.SAMPLE_RATE_NONE,
        bitsPerSample: Int = BluetoothCodecConfig.BITS_PER_SAMPLE_NONE,
        channelMode: Int = BluetoothCodecConfig.CHANNEL_MODE_NONE,
    ) {
        requireCodecApi()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("BLUETOOTH_CONNECT permission is required")
        }
        if (!hasAssociation(context, device.address)) {
            throw SecurityException("A companion-device association with this Bluetooth device is required")
        }
        // Selectable capabilities are bit masks, not single configurations. Android's
        // isCodecConfigSelectable() intentionally rejects a multi-bit capability itself.
        if (status.codecsSelectableCapabilities.none { it == selected }) {
            throw IllegalArgumentException("選擇的編碼不在裝置可選清單中")
        }
        validateExactChoice(sampleRate, selected.sampleRate, sampleRates, "取樣率")
        validateExactChoice(bitsPerSample, selected.bitsPerSample, sampleDepths, "位元深度")
        validateExactChoice(channelMode, selected.channelMode, channelModes, "聲道模式")

        val preference = BluetoothCodecConfig.Builder().apply {
            if (Build.VERSION.SDK_INT >= 35 && selected.extendedCodecType != null) {
                setExtendedCodecType(selected.extendedCodecType)
            } else {
                @Suppress("DEPRECATION")
                setCodecType(selected.codecType)
            }
            setCodecPriority(BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)
            setSampleRate(sampleRate)
            setBitsPerSample(bitsPerSample)
            setChannelMode(channelMode)
        }.build()
        if (!status.isCodecConfigSelectable(preference)) {
            throw IllegalArgumentException("所選音訊規格組合不在耳機可選清單中")
        }
        val latest = read(profile, device)
            ?: throw IllegalStateException("目前沒有可讀取的 A2DP 編碼狀態")
        if (!latest.isCodecConfigSelectable(preference)) {
            throw IllegalStateException("耳機的可選編碼已變更，請重新整理後再選")
        }
        BluetoothA2dp::class.java.getMethod(
            "setCodecConfigPreference", BluetoothDevice::class.java, BluetoothCodecConfig::class.java,
        ).invoke(profile, device, preference)
    }

    /** The selectable capability contains bit masks; each UI option is one exact flag. */
    fun sampleRateOptions(capability: BluetoothCodecConfig): List<Pair<Int, String>> =
        optionsIn(capability.sampleRate, sampleRates).map { (flag, label) -> flag to "$label kHz" }

    fun bitDepthOptions(capability: BluetoothCodecConfig): List<Pair<Int, String>> =
        optionsIn(capability.bitsPerSample, sampleDepths).map { (flag, label) -> flag to "$label bit" }

    fun channelModeOptions(capability: BluetoothCodecConfig): List<Pair<Int, String>> =
        optionsIn(capability.channelMode, channelModes)

    fun describe(config: BluetoothCodecConfig): String =
        listOf(codecName(config), detail(config)).filter { it.isNotBlank() }.joinToString(" · ")

    fun codecName(config: BluetoothCodecConfig): String {
        if (Build.VERSION.SDK_INT >= 35) {
            config.extendedCodecType?.codecName?.takeIf { it.isNotBlank() }?.let { return it }
        }
        @Suppress("DEPRECATION")
        return when (config.codecType) {
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC -> "SBC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "AAC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "aptX"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LC3 -> "LC3"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_OPUS -> "Opus"
            else -> "編碼代號 ${config.codecType}"
        }
    }

    /** Audio parameters only; A2DP codec status does not expose the music app's bitrate. */
    fun detail(config: BluetoothCodecConfig): String = listOf(
        flagNames(config.sampleRate, sampleRates, "kHz"),
        flagNames(config.bitsPerSample, sampleDepths, "bit"),
        flagNames(config.channelMode, channelModes, ""),
    ).filter { it.isNotBlank() }.joinToString(" · ")

    fun failureMessage(error: Throwable): String {
        val cause = if (error is InvocationTargetException) error.targetException ?: error else error
        return when (cause) {
            is NoSuchMethodException, is IllegalAccessException, is NoSuchMethodError ->
                "此系統不允許應用存取 A2DP 編碼狀態或切換介面"
            is SecurityException -> when {
                cause.message?.contains("BLUETOOTH_CONNECT", ignoreCase = true) == true ->
                    "請授予附近裝置權限"
                cause.message?.contains("association", ignoreCase = true) == true ->
                    "請先與目前連線的藍牙裝置建立伴侶裝置關聯"
                else -> "系統藍牙服務拒絕存取；已關聯仍發生時代表此裝置可能不支援"
            }
            is UnsupportedOperationException -> "此功能需要 Android 13 或以上版本"
            is IllegalArgumentException -> cause.message ?: "所選編碼不受此裝置支援"
            is IllegalStateException -> cause.message ?: "藍牙編碼狀態已變更，請重新整理"
            else -> "無法讀取或切換藍牙編碼（${cause.javaClass.simpleName}）"
        }
    }

    private fun requireCodecApi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw UnsupportedOperationException("A2DP codec status requires Android 13+")
        }
    }

    private fun flagNames(value: Int, flags: List<Pair<Int, String>>, suffix: String): String {
        if (value == 0) return ""
        val names = flags.filter { (bit, _) -> value and bit != 0 }.map { it.second }
        return if (names.isEmpty()) "" else names.joinToString("/") + suffix
    }

    internal fun optionsIn(mask: Int, flags: List<Pair<Int, String>>): List<Pair<Int, String>> =
        flags.filter { (bit, _) -> mask and bit == bit }

    internal fun validateExactChoice(
        requested: Int,
        capabilityMask: Int,
        flags: List<Pair<Int, String>>,
        fieldName: String,
    ) {
        if (requested == 0) return // The Android codec API defines NONE as a wildcard.
        if (flags.none { (flag, _) -> requested == flag } || capabilityMask and requested != requested) {
            throw IllegalArgumentException("所選${fieldName}不在耳機可選清單中")
        }
    }

    private val sampleRates = listOf(
        BluetoothCodecConfig.SAMPLE_RATE_44100 to "44.1",
        BluetoothCodecConfig.SAMPLE_RATE_48000 to "48",
        BluetoothCodecConfig.SAMPLE_RATE_88200 to "88.2",
        BluetoothCodecConfig.SAMPLE_RATE_96000 to "96",
        BluetoothCodecConfig.SAMPLE_RATE_176400 to "176.4",
        BluetoothCodecConfig.SAMPLE_RATE_192000 to "192",
    )
    private val sampleDepths = listOf(
        BluetoothCodecConfig.BITS_PER_SAMPLE_16 to "16",
        BluetoothCodecConfig.BITS_PER_SAMPLE_24 to "24",
        BluetoothCodecConfig.BITS_PER_SAMPLE_32 to "32",
    )
    private val channelModes = listOf(
        BluetoothCodecConfig.CHANNEL_MODE_MONO to "單聲道",
        BluetoothCodecConfig.CHANNEL_MODE_STEREO to "立體聲",
    )
}
