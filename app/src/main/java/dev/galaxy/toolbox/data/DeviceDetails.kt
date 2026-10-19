package dev.galaxy.toolbox.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.galaxy.toolbox.model.Reading
import java.io.File

object DeviceDetails {
    private fun text(value: String?) = value?.takeUnless { it.isBlank() || it.equals(Build.UNKNOWN, true) }
    private fun file(path: String) = runCatching { File(path).bufferedReader().use { val chars = CharArray(16384); val count = it.read(chars); if (count > 0) String(chars, 0, count).trim() else null } }.getOrNull()?.let(::text)

    fun read(context: Context): List<Reading> = buildList {
        val name = runCatching {
            context.assets.open("samsung-device-names.tsv").bufferedReader().use { reader ->
                DeviceNames.parse(reader.lineSequence()).resolve(Build.MANUFACTURER, Build.MODEL, Build.DEVICE)
            }
        }.getOrNull()
        val socModel = if (Build.VERSION.SDK_INT >= 31) text(Build.SOC_MODEL) else null
        val socVendor = if (Build.VERSION.SDK_INT >= 31) text(Build.SOC_MANUFACTURER) else null
        val chip = ChipNames.resolve(socModel, Build.HARDWARE, name)
        add(Reading("裝置名稱", name, "Google Play 公開裝置目錄 · ${DeviceNames.RETRIEVED}\n${DeviceNames.SOURCE}",
            "本機離線對照完整型號；有歧義時再核對裝置代號，無法唯一確認即未知。不是使用者自訂的藍牙名稱。"))
        add(Reading("裝置型號", text(Build.MODEL), "Build.MODEL"))
        add(Reading("品牌 / 製造商", "${Build.BRAND} / ${Build.MANUFACTURER}", "Build.BRAND / MANUFACTURER"))
        add(Reading("內部裝置代號", text(Build.DEVICE), "Build.DEVICE", "另列供查核，不當作市售名稱。"))
        add(Reading("產品代號", text(Build.PRODUCT), "Build.PRODUCT"))
        add(Reading("主機板代號", text(Build.BOARD), "Build.BOARD"))
        add(Reading("處理器名稱", chip?.name, chip?.let { "本機 SoC / 硬體代碼對照公開晶片規格\n${it.source}" } ?: "Build.SOC_MODEL / HARDWARE · 本機晶片名稱表",
            "依實際回報代碼辨識，不單靠手機名稱或地區猜晶片；名稱表未收錄時保留未知與下列原始代碼。"))
        add(Reading("處理器製造商", socVendor ?: chip?.vendor, if (socVendor != null) "Build.SOC_MANUFACTURER · Android 12+" else chip?.source ?: "Build.SOC_MANUFACTURER"))
        add(Reading("SoC 原始型號", socModel, "Build.SOC_MODEL · Android 12+"))
        add(Reading("CPU 品牌", chip?.cpu, chip?.source ?: "未取得可驗證的晶片規格", "晶片廠商的 CPU 品牌，非各核心架構實測。"))
        add(Reading("CPU 製程（規格）", chip?.process, chip?.source ?: "未取得可驗證的晶片規格", "公開晶片規格，非本機量測；不表示手機代工廠。"))
        add(Reading("CPU 64 位元指令集", Build.SUPPORTED_64_BIT_ABIS.joinToString().ifBlank { null }, "Build.SUPPORTED_64_BIT_ABIS"))
        add(Reading("CPU 32 位元指令集", Build.SUPPORTED_32_BIT_ABIS.joinToString().ifBlank { null }, "Build.SUPPORTED_32_BIT_ABIS", "未知或空白也可能代表系統沒有提供 32 位元 App 支援。"))
        add(Reading("CPU App 執行位元", if (android.os.Process.is64Bit()) "64 位元" else "32 位元", "Process.is64Bit", "目前 App 程序的執行模式。"))
        val gpu = GpuInfo.read()
        val glSource = "EGL14 離屏 Context · GLES20.glGetString"
        add(Reading("GPU 名稱", gpu?.renderer, "$glSource · GL_RENDERER", "驅動實際回報名稱；ANGLE 或軟體 renderer 會如實顯示，不以晶片規格覆蓋。"))
        add(Reading("GPU 廠商", gpu?.vendor, "$glSource · GL_VENDOR"))
        add(Reading("GPU OpenGL ES Context", gpu?.version, "$glSource · GL_VERSION", "離屏 GLES 2 Context 回報的驅動版本，不代表裝置最高支援版本。"))
        add(Reading("GPU GLSL", gpu?.shading, "$glSource · GL_SHADING_LANGUAGE_VERSION"))
        add(Reading("GPU 最大紋理尺寸", gpu?.maxTextureSize?.let { "$it × $it" }, "GLES20.glGetIntegerv · GL_MAX_TEXTURE_SIZE", "此 OpenGL Context 的能力。"))
        add(Reading("GPU Renderbuffer 上限", gpu?.maxRenderbufferSize?.let { "$it × $it" }, "GLES20 · GL_MAX_RENDERBUFFER_SIZE", "目前離屏 Context 的能力。", group = "OpenGL Context 能力"))
        add(Reading("GPU 頂點屬性上限", gpu?.maxVertexAttributes?.toString(), "GLES20 · GL_MAX_VERTEX_ATTRIBS", group = "OpenGL Context 能力"))
        add(Reading("GPU 紋理單元上限", gpu?.maxTextureUnits?.toString(), "GLES20 · GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS", group = "OpenGL Context 能力"))
        add(Reading("GPU OpenGL 擴充", gpu?.extensions?.split(' ')?.filter { it.isNotBlank() }?.sorted()?.joinToString("\n"), "GLES20 · GL_EXTENSIONS", "目前 Context 的驅動回報，非所有 GLES 版本的完整能力。", group = "OpenGL 擴充"))
        val config = context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo
        add(Reading("GPU OpenGL ES（系統宣告）", config.glEsVersion, "ActivityManager.deviceConfigurationInfo", "系統宣告的需求支援上限，與目前建立的 Context 分列。"))
        val vulkan = context.packageManager.systemAvailableFeatures.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }?.version
        add(Reading("GPU Vulkan（系統宣告）", vulkan?.let { "${it ushr 22}.${(it ushr 12) and 1023}.${it and 4095}" },
            "PackageManager.FEATURE_VULKAN_HARDWARE_VERSION", "系統功能宣告；未直接建立 Vulkan 裝置驗證。"))
        add(Reading("Android / API", "${Build.VERSION.RELEASE} / ${Build.VERSION.SDK_INT}", "Build.VERSION"))
        add(Reading("安全性更新日期", text(Build.VERSION.SECURITY_PATCH), "Build.VERSION.SECURITY_PATCH"))
        add(Reading("Bootloader 版本", text(Build.BOOTLOADER), "Build.BOOTLOADER"))
        add(Reading("核心版本", text(System.getProperty("os.version")), "System.getProperty · os.version"))
        add(Reading("系統 Build 指紋", text(Build.FINGERPRINT), "Build.FINGERPRINT", "韌體 Build 識別字串，不是生物辨識指紋或裝置唯一序號。"))
        add(Reading("資料分割區 / 快閃規格", null, "一般 Android App 無通用公開 API", "目前總量是可讀分割區；不由速度或容量猜 UFS 世代與廠商。"))
        val cpuInfo = file("/proc/cpuinfo")
        val features = cpuInfo?.lineSequence()?.filter { it.substringBefore(':').trim() in listOf("Features", "flags") }
            ?.map { it.substringAfter(':').trim() }?.distinct()?.joinToString("\n")?.ifBlank { null }
        add(Reading("CPU 指令功能", features, "/proc/cpuinfo · Features / flags", "核心公開旗標；未開放時未知，不由晶片名稱猜測。", group = "指令與執行環境"))
        add(Reading("CPU 核心識別原始資料", cpuInfo?.lineSequence()?.filter { it.substringBefore(':').trim() in listOf("processor", "CPU implementer", "CPU architecture", "CPU variant", "CPU part", "CPU revision", "model name") }?.joinToString("\n")?.ifBlank { null },
            "/proc/cpuinfo", "只顯示核心公開識別欄位；不含序號，不據此猜大小核心配置。", group = "指令與執行環境"))
        add(Reading("CPU online 核心", file("/sys/devices/system/cpu/online"), "/sys/devices/system/cpu/online", "啟動時快照，非持續的核心活動或使用率。", group = "處理器概覽"))
        add(Reading("CPU present 核心", file("/sys/devices/system/cpu/present"), "/sys/devices/system/cpu/present", group = "處理器概覽"))
        (0 until Runtime.getRuntime().availableProcessors().coerceAtMost(32)).forEach { cpu ->
            val base = "/sys/devices/system/cpu/cpu$cpu/cpufreq"
            fun mhz(leaf: String) = file("$base/$leaf")?.toLongOrNull()?.takeIf { it > 0 }?.let { "${it / 1000} MHz" }
            val min = mhz("cpuinfo_min_freq")
            val max = mhz("cpuinfo_max_freq")
            add(Reading("CPU $cpu 標示頻率範圍", if (min != null || max != null) "${min ?: "未知"} – ${max ?: "未知"}" else null,
                "$base/cpuinfo_min_freq\n$base/cpuinfo_max_freq", "可選唯讀節點；不是即時頻率或保證持續頻率。"))
            add(Reading("CPU $cpu 調頻策略", file("$base/scaling_governor"), "$base/scaling_governor", "僅讀取；不修改系統排程。"))
            add(Reading("CPU $cpu 共享調頻核心", file("$base/related_cpus"), "$base/related_cpus", "Linux cpufreq 政策的核心編號，不從頻率猜大小核心架構。"))
            add(Reading("CPU $cpu 調頻驅動", file("$base/scaling_driver"), "$base/scaling_driver", group = "CPU $cpu · 調頻能力"))
            add(Reading("CPU $cpu 可用頻率", file("$base/scaling_available_frequencies")?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull()?.takeIf { n -> n > 0 } }?.joinToString { "${it / 1000} MHz" }?.ifBlank { null },
                "$base/scaling_available_frequencies", "kHz → MHz；部分驅動不提供離散頻率表。", group = "CPU $cpu · 調頻能力"))

        }
    }
}
