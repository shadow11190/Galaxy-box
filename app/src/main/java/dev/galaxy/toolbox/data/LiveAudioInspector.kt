package dev.galaxy.toolbox.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build

internal data class ActiveRecording(
    val inputPort: String,
    val requestedFormat: String,
    val deviceFormat: String
)

internal data class AudioLiveSnapshot(
    val playbackCount: Int?,
    val mediaRouteCandidates: List<String>?,
    val recordings: List<ActiveRecording>?,
    val readAtMillis: Long,
    val error: String?
)

internal data class AudioDecoderCapability(
    val name: String,
    val mime: String,
    val sampleRates: String,
    val bitrate: String,
    val channels: Int
)

internal data class AudioCapabilitySnapshot(
    val hardwareDecoders: List<AudioDecoderCapability>,
    val dsdOutputProfiles: List<String>,
    val error: String?
)

internal object LiveAudioInspector {
    fun read(context: Context): AudioLiveSnapshot {
        val now = System.currentTimeMillis()
        val manager = context.getSystemService(AudioManager::class.java)
            ?: return AudioLiveSnapshot(null, null, null, now, "AudioManager 無法取得")
        val playback = runCatching { manager.activePlaybackConfigurations.size }
        val routes = if (Build.VERSION.SDK_INT >= 33) runCatching {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            manager.getAudioDevicesForAttributes(attributes).map(::deviceLabel).distinct()
        } else null
        val recording = runCatching {
            manager.activeRecordingConfigurations.map { session ->
                ActiveRecording(
                    session.audioDevice?.let(::deviceLabel) ?: "輸入端點未公開",
                    format(session.clientFormat),
                    format(session.format) + (pcmDataRate(session.format)?.let { " · PCM 原始資料率 " + it } ?: "")
                )
            }
        }
        return AudioLiveSnapshot(
            playback.getOrNull(),
            routes?.getOrNull(),
            recording.getOrNull(),
            now,
            listOfNotNull(playback.exceptionOrNull(), routes?.exceptionOrNull(), recording.exceptionOrNull())
                .takeIf { it.isNotEmpty() }?.joinToString { it.javaClass.simpleName }
        )
    }

    fun capabilities(context: Context): AudioCapabilitySnapshot {
        val manager = context.getSystemService(AudioManager::class.java)
            ?: return AudioCapabilitySnapshot(emptyList(), emptyList(), "AudioManager 無法取得")
        val dsd = if (Build.VERSION.SDK_INT >= 34) runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).flatMap { device ->
                device.audioProfiles.filter { it.format == AudioFormat.ENCODING_DSD }.map { profile ->
                    val rates = profile.sampleRates.takeIf { it.isNotEmpty() }
                        ?.joinToString { dsdRateLabel(it) } ?: "取樣率未列出"
                    deviceLabel(device) + " · " + rates
                }
            }.distinct()
        } else null
        val codecs = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && Build.VERSION.SDK_INT >= 29 && it.isHardwareAccelerated }
                .flatMap { codec ->
                    codec.supportedTypes.filter { it.startsWith("audio/") }.mapNotNull { mime ->
                        runCatching {
                            val cap = codec.getCapabilitiesForType(mime).audioCapabilities
                                ?: return@runCatching null
                            AudioDecoderCapability(
                                codec.name, mime,
                                cap.supportedSampleRateRanges.joinToString { it.toString() } + " Hz",
                                cap.bitrateRange.toString() + " bit/s",
                                cap.maxInputChannelCount
                            )
                        }.getOrNull()
                    }
                }
        }
        return AudioCapabilitySnapshot(
            codecs.getOrDefault(emptyList()),
            dsd?.getOrDefault(emptyList()) ?: emptyList(),
            listOfNotNull(codecs.exceptionOrNull(), dsd?.exceptionOrNull())
                .takeIf { it.isNotEmpty() }?.joinToString { it.javaClass.simpleName }
        )
    }

    internal fun format(value: AudioFormat?): String {
        if (value == null) return "未公開"
        val rate = value.sampleRate.takeIf { it > 0 }?.toString()?.plus(" Hz") ?: "取樣率未知"
        val channels = value.channelCount.takeIf { it > 0 }?.toString()?.plus(" 聲道") ?: "聲道未知"
        return rate + " · " + channels + " · " + encoding(value.encoding)
    }

    internal fun encoding(value: Int): String = when (value) {
        AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
        AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"
        else -> "格式代碼 " + value
    }

    internal fun pcmDataRate(value: AudioFormat?): String? {
        if (value == null || value.sampleRate <= 0 || value.channelCount <= 0) return null
        val bits = when (value.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 8
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> return null
        }
        return (value.sampleRate.toLong() * value.channelCount * bits / 1000).toString() + " kbit/s"
    }

    internal fun dsdRateLabel(rate: Int): String {
        val ratio = rate / 44100
        return if (rate >= 2_822_400 && rate % 44100 == 0 && ratio > 0 && ratio and (ratio - 1) == 0)
            "DSD" + ratio + " (" + rate + " Hz)"
        else rate.toString() + " Hz"
    }

    private fun deviceLabel(device: AudioDeviceInfo): String =
        ConnectionDetails.audioType(device.type) + " · " +
            (device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "ID " + device.id)
}
