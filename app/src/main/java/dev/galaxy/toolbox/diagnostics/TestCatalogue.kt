package dev.galaxy.toolbox.diagnostics

import android.content.Context
import android.content.pm.PackageManager as P
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent

enum class DeviceTest(val title: String, val mark: String) {
    SYSTEM("系統軟體", "OS"), BATTERY("電池狀態", "BAT"), NFC("NFC 標籤", "NFC"), SIM("SIM 卡", "SIM"),
    MOBILE("行動網路", "LTE"), SENSORS("即時感測器", "XYZ"), CAMERA("相機拍攝", "CAM"), LOCATION("定位誤差估計", "GPS"),
    WIFI("Wi-Fi", "Wi-Fi"), BLUETOOTH("藍牙", "BT"), PROXIMITY("距離感測器", "PROX"), KEYS("音量按鍵", "KEY"),
    VIBRATION("震動", "VIB"), SPEAKER("揚聲器／聲道", "L R"), MICROPHONE("麥克風", "MIC"), BIOMETRIC("生物辨識", "BIO"),
    DISPLAY("螢幕色彩", "RGB"), TOUCH("多點觸控", "TOUCH"), STYLUS("觸控筆輸入", "PEN"), CHARGING("充電連接", "PWR"), HEADPHONES("耳機連接", "AUX")
}

data class TestCapabilities(val nfc: Boolean = false, val telephony: Boolean = false, val sensors: Boolean = false,
    val camera: Boolean = false, val location: Boolean = false, val wifi: Boolean = false, val bluetooth: Boolean = false,
    val proximity: Boolean = false, val keys: Boolean = false, val vibrator: Boolean = false, val audio: Boolean = false,
    val microphone: Boolean = false, val biometric: Boolean = false, val touchscreen: Boolean = false, val stylus: Boolean = false)

object TestCatalogue {
    fun available(c: TestCapabilities): List<DeviceTest> = DeviceTest.entries.filter {
        when (it) {
            DeviceTest.SYSTEM, DeviceTest.BATTERY, DeviceTest.DISPLAY, DeviceTest.CHARGING -> true
            DeviceTest.NFC -> c.nfc; DeviceTest.SIM, DeviceTest.MOBILE -> c.telephony
            DeviceTest.SENSORS -> c.sensors; DeviceTest.CAMERA -> c.camera; DeviceTest.LOCATION -> c.location
            DeviceTest.WIFI -> c.wifi; DeviceTest.BLUETOOTH -> c.bluetooth; DeviceTest.PROXIMITY -> c.proximity
            DeviceTest.KEYS -> c.keys; DeviceTest.VIBRATION -> c.vibrator; DeviceTest.SPEAKER, DeviceTest.HEADPHONES -> c.audio
            DeviceTest.MICROPHONE -> c.microphone; DeviceTest.BIOMETRIC -> c.biometric; DeviceTest.TOUCH -> c.touchscreen
            DeviceTest.STYLUS -> c.stylus
        }
    }
    fun read(context: Context): TestCapabilities {
        val pm = context.packageManager
        val sm = context.getSystemService(SensorManager::class.java)
        fun feature(name: String) = pm.hasSystemFeature(name)
        return TestCapabilities(feature(P.FEATURE_NFC), feature(P.FEATURE_TELEPHONY),
            sm?.getSensorList(Sensor.TYPE_ALL)?.isNotEmpty() == true, feature(P.FEATURE_CAMERA_ANY), feature(P.FEATURE_LOCATION),
            feature(P.FEATURE_WIFI), feature(P.FEATURE_BLUETOOTH), sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null,
            KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_VOLUME_UP) || KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_VOLUME_DOWN),
            @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.hasVibrator() == true,
            feature(P.FEATURE_AUDIO_OUTPUT), feature(P.FEATURE_MICROPHONE),
            Build.VERSION.SDK_INT >= 28 && (feature(P.FEATURE_FINGERPRINT) || feature("android.hardware.biometrics.face") || feature("android.hardware.biometrics.iris")),
            feature(P.FEATURE_TOUCHSCREEN), feature("com.sec.feature.spen_usp") || InputDevice.getDeviceIds().any { id ->
                InputDevice.getDevice(id)?.supportsSource(InputDevice.SOURCE_STYLUS) == true
            })
    }
}
