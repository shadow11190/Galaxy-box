# 音訊與馬達檢測（本機預覽）

音訊頁新增前景一秒更新的活動摘要。播放工作階段數來自 `AudioManager.getActivePlaybackConfigurations()`；Android 13 以上的媒體輸出端點來自 `getAudioDevicesForAttributes(USAGE_MEDIA)`。後者是**新建媒體音軌的預估路由**，不是 Spotify 等第三方 App 實際使用的喇叭單體或路由。裝置清單中的聲道數、端點數也不等於實體喇叭顆數。

錄音工作階段使用 `getActiveRecordingConfigurations()` 顯示公開的輸入端點、App 請求格式與裝置格式。其他 App 的資料可能被系統隱藏；不能單憑端點判斷哪顆實體麥克風在工作。使用者自行啟動本 App 的麥克風測試後，`AudioRecord.getActiveMicrophones()` 可提供裝置回報的作用中麥克風描述、群組與聲道映射，並顯示本次錄音的實際端點與格式。測試不寫入音檔，退到背景即停止。

音訊解碼能力讀取 `MediaCodecList` 中系統宣告為硬體加速的音訊解碼器，並列出各格式回報的取樣率、位元率範圍與聲道上限。這些是解碼器宣告能力，不代表目前串流碼率或實際啟用硬體解碼。Android 14 以上從輸出端點的 `AudioProfile` 檢查 `ENCODING_DSD`；有 DSD 輸出宣告仍不能推論硬體 DSD 解碼器，未列出也不能斷言不支援。公開 API 不提供 SINAD、DNR、SNR、串擾量測，App 明示無資料，不以位元深度估算。

馬達測試加入可調力度、時長與有限回彈波形；力度調節只在 `Vibrator.hasAmplitudeControl()` 回報支援時開放。最大幅度按鈕送出 255、1 秒的 API 指令，裝置可因系統設定限制實際震動。回彈的阻尼與間隔只是 App 產生的脈衝包絡，不是馬達物理參數。頁面離開或 App 停止時呼叫 `cancel()`。

官方 API：[AudioManager](https://developer.android.com/reference/android/media/AudioManager)、[AudioRecordingConfiguration](https://developer.android.com/reference/android/media/AudioRecordingConfiguration)、[AudioRecord 作用中麥克風](https://developer.android.com/reference/android/media/AudioRecord#getActiveMicrophones())、[AudioDeviceInfo](https://developer.android.com/reference/android/media/AudioDeviceInfo)、[MediaCodecInfo](https://developer.android.com/reference/android/media/MediaCodecInfo)、[VibrationEffect](https://developer.android.com/reference/android/os/VibrationEffect)。

## 藍牙 A2DP 編碼（Android 13 以上預覽）

音訊卡會區分 Android 音訊端點公布的格式、A2DP 播放狀態、目前協商的藍牙編碼，以及耳機可選編碼。前景時可接收 A2DP 編碼變更廣播；廣播不是黏性資料，開啟 App 後不一定會收到。使用者也可透過系統畫面，將目前連線的傳統藍牙耳機與本 App 建立伴侶裝置關聯。程式只接受與該 A2DP 裝置相同 MAC 位址的關聯，之後嘗試讀取編碼狀態、提出編碼類型偏好，並讀回實際狀態；提供解除關聯入口。關聯不等於重新配對，也不是取得全面藍牙權限。

[AOSP BluetoothA2dp](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/framework/java/android/bluetooth/BluetoothA2dp.java) 將 `getCodecStatus` 與 `setCodecConfigPreference` 標為隱藏的系統 API，但其藍牙服務允許 `BLUETOOTH_CONNECT` 加指定耳機的 [CompanionDeviceManager 關聯](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)作為特權權限的替代條件。Galaxy Box 使用反射嘗試呼叫；[Android 對非公開 API 的限制](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces)或廠商實作仍可使其失敗。可選能力不代表目前編碼；切換呼叫不代表耳機已採用，須以讀回結果為準。此頁不推算串流實際位元率，也不涵蓋 LE Audio 編碼。尚未在實機與藍牙耳機完成驗證。
