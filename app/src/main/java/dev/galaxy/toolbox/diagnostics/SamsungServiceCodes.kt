package dev.galaxy.toolbox.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

internal enum class ServiceCodeCategory(val label: String) {
    HARDWARE("硬體檢測"),
    DEVICE("裝置資訊"),
    NETWORK("網路與連線"),
    BATTERY("電池"),
    ENGINEERING("工程與偵錯"),
    SYSTEM("系統操作"),
    RESET("資料重置"),
    SECURITY("安全狀態")
}

internal enum class ServiceCodeAccess { DIAL, COPY_ONLY, RESET }

internal data class SamsungServiceCode(
    val title: String,
    val code: String,
    val purpose: String,
    val category: ServiceCodeCategory,
    val access: ServiceCodeAccess = ServiceCodeAccess.DIAL
)

/** The third-party source is a candidate catalogue, not a compatibility guarantee. */
internal object SamsungServiceCodes {
    val entries = listOf(
        SamsungServiceCode("硬體檢測選單", "*#0*#", "螢幕、觸控與感測器檢查", ServiceCodeCategory.HARDWARE),
        SamsungServiceCode("觸控檢測", "*#2664#", "觸控輸入檢查", ServiceCodeCategory.HARDWARE),
        SamsungServiceCode("觸控韌體版本", "*#2663#", "選單可能包含韌體操作", ServiceCodeCategory.HARDWARE, ServiceCodeAccess.COPY_ONLY),
        SamsungServiceCode("震動與背光", "*#0842#", "震動與背光測試", ServiceCodeCategory.HARDWARE),
        SamsungServiceCode("光線感測器", "*#0589#", "光線讀值檢查", ServiceCodeCategory.HARDWARE),
        SamsungServiceCode("音訊測試 A", "*#0289#", "揚聲器／音訊檢查", ServiceCodeCategory.HARDWARE),
        SamsungServiceCode("音訊測試 B", "*#0673#", "揚聲器／音訊檢查", ServiceCodeCategory.HARDWARE),

        SamsungServiceCode("IMEI", "*#06#", "顯示裝置識別碼，請勿公開分享", ServiceCodeCategory.DEVICE),
        SamsungServiceCode("韌體版本", "*#1234#", "PDA、CSC 與數據機版本", ServiceCodeCategory.DEVICE),
        SamsungServiceCode("裝置版本資訊", "*#12580*369#", "軟體與硬體版本", ServiceCodeCategory.DEVICE),
        SamsungServiceCode("硬體版本", "*#2222#", "硬體版本資訊", ServiceCodeCategory.DEVICE),
        SamsungServiceCode("軟體版本", "*#1111#", "軟體版本資訊", ServiceCodeCategory.DEVICE),

        SamsungServiceCode("網路服務狀態", "*#0011#", "行動網路服務資訊", ServiceCodeCategory.NETWORK),
        SamsungServiceCode("射頻頻段選擇", "*#2263#", "可能變更行動網路頻段", ServiceCodeCategory.NETWORK, ServiceCodeAccess.COPY_ONLY),
        SamsungServiceCode("網路鎖定狀態", "*#7465625#", "SIM／網路鎖定資訊", ServiceCodeCategory.NETWORK),

        SamsungServiceCode("電池狀態", "*#0228#", "電壓、電量與溫度候選資訊", ServiceCodeCategory.BATTERY),
        SamsungServiceCode("舊版電池資訊", "*#9998*228#", "可用欄位依裝置而異", ServiceCodeCategory.BATTERY),

        SamsungServiceCode("系統轉儲模式", "*#9900#", "偵錯與日誌選項可能改動設定", ServiceCodeCategory.ENGINEERING, ServiceCodeAccess.COPY_ONLY),
        SamsungServiceCode("USB 設定", "*#0808#", "可能變更 USB／偵錯介面", ServiceCodeCategory.ENGINEERING, ServiceCodeAccess.COPY_ONLY),
        SamsungServiceCode("診斷配置", "*#9090#", "可能變更診斷設定", ServiceCodeCategory.ENGINEERING, ServiceCodeAccess.COPY_ONLY),
        SamsungServiceCode("USB／I2C 診斷", "*#7284#", "工程診斷選單", ServiceCodeCategory.ENGINEERING, ServiceCodeAccess.COPY_ONLY),

        SamsungServiceCode("軟重置", "*#7780#", "可能重置系統設定", ServiceCodeCategory.SYSTEM, ServiceCodeAccess.COPY_ONLY),
        SamsungServiceCode("電源鍵行為", "*#7594#", "可能改變電源鍵功能", ServiceCodeCategory.SYSTEM, ServiceCodeAccess.COPY_ONLY),

        SamsungServiceCode("完整恢復原廠設定", "*2767*3855#", "可能永久清除使用者資料與設定", ServiceCodeCategory.RESET, ServiceCodeAccess.RESET),
        SamsungServiceCode("自訂重設", "*2767*2878#", "可能清除使用者資料與應用程式", ServiceCodeCategory.RESET, ServiceCodeAccess.RESET),

        SamsungServiceCode("加密與安全資訊", "*#32489#", "依型號可能顯示安全資訊", ServiceCodeCategory.SECURITY, ServiceCodeAccess.COPY_ONLY)
    )

    fun availableOn(context: Context): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun dialIntent(entry: SamsungServiceCode): Intent {
        require(entry in entries && entry.access == ServiceCodeAccess.DIAL) { "Only reviewed read-only codes can be opened" }
        return Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", entry.code, null))
    }
}
