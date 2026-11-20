package dev.galaxy.toolbox.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

internal data class BluetoothAudioPort(
    val name: String,
    val type: String,
    val sampleRates: String,
    val channelCounts: String,
    val acceptedFormats: String,
)

internal data class A2dpDeviceState(
    val device: BluetoothDevice,
    val name: String,
    val playing: Boolean,
)

internal object BluetoothAudioInspector {
    private val bluetoothTypes = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        26, // TYPE_BLE_HEADSET
        27, // TYPE_BLE_SPEAKER
    )

    fun ports(context: Context): List<BluetoothAudioPort> {
        val manager = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in bluetoothTypes }
            .map { device ->
                BluetoothAudioPort(
                    device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "名稱未提供",
                    ConnectionDetails.audioType(device.type),
                    device.sampleRates.filter { it > 0 }.distinct().sorted()
                        .joinToString { "$it Hz" }.ifBlank { "未列出" },
                    device.channelCounts.filter { it > 0 }.distinct().sorted()
                        .joinToString().ifBlank { "未列出" },
                    device.encodings.distinct().joinToString { LiveAudioInspector.encoding(it) }
                        .ifBlank { "未列出" },
                )
            }
    }

    @SuppressLint("MissingPermission") // Called only after BLUETOOTH_CONNECT is granted on Android 12+.
    fun a2dpDevices(profile: BluetoothA2dp): List<A2dpDeviceState> =
        profile.connectedDevices.map { device ->
            A2dpDeviceState(device, device.name?.takeIf { it.isNotBlank() } ?: "名稱未提供",
                profile.isA2dpPlaying(device))
        }
}
