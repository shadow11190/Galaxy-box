package dev.galaxy.toolbox.data

import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

/** Loaded once by the repository on Dispatchers.IO. No network or persistent identifiers. */
object HardwareCatalog {
    @Volatile private var names: DeviceNames? = null
    @Volatile private var variants: DeviceVariants? = null
    @Volatile private var classifications: DeviceClassifications? = null
    @Volatile private var processorPhoneUsage: ProcessorPhoneUsage? = null
    @Volatile private var processorDeviceUsage: ProcessorDeviceUsage? = null
    @Volatile private var gpuSpecs: GpuSpecs? = null
    @Synchronized fun names(context: Context): DeviceNames = names ?: context.assets.open("android-device-names.tsv")
        .bufferedReader().use { DeviceNames.parse(it.lineSequence()) }.also { names = it }
    @Synchronized fun variants(context: Context): DeviceVariants = variants ?: context.assets.open("device-variants.tsv")
        .bufferedReader().use { DeviceVariants.parse(it.lineSequence()) }.also { variants = it }
    @Synchronized fun classifications(context: Context): DeviceClassifications = classifications ?:
        context.assets.open("device-classifications.tsv").bufferedReader()
            .use { DeviceClassifications.parse(it.lineSequence()) }.also { classifications = it }
    @Synchronized fun processorPhoneUsage(context: Context): ProcessorPhoneUsage = processorPhoneUsage ?:
        context.assets.open("processor-phone-usage-2018plus.tsv").bufferedReader()
            .use { ProcessorPhoneUsage.parse(it.lineSequence()) }.also { processorPhoneUsage = it }
    @Synchronized fun processorDeviceUsage(context: Context): ProcessorDeviceUsage = processorDeviceUsage ?:
        context.assets.open("processor-device-usage-2018plus.tsv").bufferedReader()
            .use { ProcessorDeviceUsage.parse(it.lineSequence()) }.also { processorDeviceUsage = it }
    @Synchronized fun gpuSpecs(context: Context): GpuSpecs = gpuSpecs ?: context.assets.open("gpu-specs.tsv")
        .bufferedReader().use { GpuSpecs.parse(it.lineSequence()) }.also { gpuSpecs = it }
    private val firmwareMarketName by lazy { readProperty("ro.product.marketname") }

    fun identity(context: Context, reports: List<ChipReport> = chipReports()): HardwareIdentity {
        val identity = HardwareIdentityResolver.resolve(
            names(context), variants(context), Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.BRAND, reports)
        if (identity.name != null && !DeviceNames.looksLikeModelCode(identity.name, Build.MODEL)) return identity
        val marketName = firmwareMarketName?.takeUnless { DeviceNames.looksLikeModelCode(it, Build.MODEL) }
        return when {
            marketName != null -> identity.copy(name = marketName, nameSource = "ro.product.marketname")
            identity.name != null -> identity
            Build.MODEL.isNotBlank() && !Build.MODEL.equals("unknown", true) ->
                identity.copy(name = Build.MODEL, nameSource = "Build.MODEL")
            else -> identity
        }
    }

    private fun readProperty(key: String): String? = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(200, TimeUnit.MILLISECONDS) || process.exitValue() != 0) null
            else process.inputStream.bufferedReader().use { it.readLine()?.take(128)?.trim() }
                ?.takeUnless { it.isBlank() || it.equals("unknown", true) }
        } finally {
            process.destroy()
            process.inputStream.close()
        }
    }.getOrNull()
    fun chipReports(): List<ChipReport> = buildList {
        fun report(source: String, value: String?) {
            value?.trim()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }?.let { add(ChipReport(source, it)) }
        }
        if (Build.VERSION.SDK_INT >= 31) report("Build.SOC_MODEL", Build.SOC_MODEL)
        report("Build.HARDWARE", Build.HARDWARE)
        report("Build.BOARD", Build.BOARD)
        // Fixed read-only properties. No shell, root, serial, IMEI or location inference.
        for (key in listOf("ro.soc.model", "ro.board.platform", "ro.mediatek.platform", "ro.chipname", "ro.hardware.chipname")) {
            report("getprop $key", readProperty(key))
        }
        for (path in listOf("/sys/devices/soc0/machine", "/sys/devices/soc0/family", "/sys/devices/system/soc/soc0/machine")) {
            report(path, runCatching { File(path).bufferedReader().use { it.readLine()?.take(256) } }.getOrNull())
        }
        val cpuHardware = runCatching { File("/proc/cpuinfo").bufferedReader().use { reader ->
            val data = CharArray(16384)
            val count = reader.read(data)
            if (count <= 0) null else String(data, 0, count).lineSequence()
                .firstOrNull { it.substringBefore(':').trim().equals("Hardware", true) }?.substringAfter(':')?.trim()
        } }.getOrNull()
        report("/proc/cpuinfo · Hardware", cpuHardware)
    }
}
