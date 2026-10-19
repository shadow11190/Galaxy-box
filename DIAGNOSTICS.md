# v0.6.6 唯讀進階資料與裝置檢查

## Shizuku

基本功能免 Root／免 Shizuku。進階電池與 GPU 卡片可選 Shizuku；固定 API／Provider 13.1.5（MIT），僅接受 ADB UID 2000，不接受 Root 模式。Android 11+ 可先在官方 Shizuku 完成無線偵錯配對／啟動，再返回 App 授權並點「讀取」。本 App 沒有內建 ADB 配對或 ADB TCP client。

UserService 非常駐 daemon，無 exported Android Service。AIDL 只提供電池／GPU 收集與銷毀，不接受外部命令或路徑。服務核對呼叫 UID；固定檔案白名單，每檔最多 4096 字元。只有 battery 使用 `/system/bin/dumpsys -t 5 battery`（無 set/reset）；輸出最多 48 Ki 字元、程序限 7 秒，整體 15 秒。取消、退到背景、服務失聯或讀取結束會解綁並要求銷毀。沒有週期性特權輪詢。

先讀 power_supply/battery 或 bms 的 type，只有 Battery 才採用容量欄位。charge_full_design／charge_full 依 Linux 定義 µAh 換 mAh；energy_full_design／energy_full 依 µWh 換 mWh，兩者不混用。cycle_count 按原值顯示，0 不被刪除；Android 廣播的 0 標為系統回報，不能證明從未循環。capacity 0 不當作有效設計容量。batt_asoc 只接受 0–100 的 Samsung 原始欄位；battery_cycle／mSavedBatteryUsage 保留原始值，不除以 100。不計算跨來源 SOH 或平均值。dumpsys 與 sysfs 各自列來源，不覆寫廣播值。

KGSL devfreq cur/min/max_freq 按 Hz 換 MHz；gpubusy 為 busy／total 的比例，分母須大於零。不可讀或格式不符退出數值區，失敗來源在可展開診斷。Shizuku 結果總是「上次手動讀取」快照及時間，不當作連續即時值。

## 裝置檢查

依 PackageManager、SensorManager、InputDevice 與系統宣告顯示最多 21 類：系統、電池、NFC、SIM、行動網路、感測器、相機、定位、Wi-Fi、藍牙、距離、音量鍵、震動、喇叭、麥克風、生物辨識、螢幕、觸控、筆輸入、充電、耳機。只有系統／電池／螢幕／充電屬一般可用入口，其餘有能力守門；未宣告則不顯示。宣告不等於通過，實際 API 失敗明示。

「逐項檢查」依當前可用清單開啟每個模組，必須由使用者確認正常／問題／略過。結果僅在 UI 記憶體及 saved instance state 保存，可主動複製，不作自動硬體認證。相機開啟系統相機；不假稱逐顆隱藏鏡頭測試。生物辨識使用系統提示，不承諾獨立測試臉部／指紋。按鍵限 App 可收到的音量鍵，不攔截電源或 Home。

喇叭短音由使用者按下才播放；可選左右／雙聲道，低幅度數位測試音仍受系統音量控制，顯示實際路由。麥克風需 RECORD_AUDIO 授權並再次手動開始；PCM 只在記憶體計算 dBFS，不錄檔、不當作校正聲壓分貝。NFC 僅列標籤技術類型，不讀內容／ID、不寫入或驗證付款。定位只在前景最多 20 秒，顯示系統估計誤差／來源／時間，不顯示或保存座標。藍牙只讀狀態與已配對數，不自動掃描或連接。

切到背景／離開模組停止麥克風、喇叭、NFC、定位、感測器與音訊裝置回呼；震動在背景取消。充電使用廣播回報的實際來源（AC／USB／無線／底座），不由機型推論已測過無線充電。筆輸入只處理實際 stylus／eraser 事件與壓力／傾斜，不測 S Pen 遙控。

## 其他資料與隱私

網路只讀目前連線、公開介面／DNS／MTU 與估計連線頻寬（非實測網速）；TrafficStats 為自開機累計。音訊頁為公開裝置能力，不啟用麥克風。/proc/meminfo 原文最多 32 Ki 字元，手動或前景 2／5／10 秒刷新。主頁沒有新增背景高頻工作。權限可拒絕，並提供 App 權限設定入口。

CSC 代表地區／電信商相關韌體設定。無可靠來源時不顯示、無手動輸入；本機 Samsung Build.MODEL 驗證後連至 SamFW 目錄，不從 SIM 或國家猜 CSC。開啟外部網站才交出機型，不上傳電池／定位／音訊。SamFW 是第三方資料且含多地區版本，本機是否能 OTA 仍以系統更新為準。

## 驗證邊界

本機測試可驗證解析、來源隔離、UI、能力守門與使用者操作，不能證實 Samsung 韌體允許 shell 讀取容量、循環或 GPU。未連接手機時，不宣稱 Shizuku Binder 真機流程、實際感測器位置、音訊／NFC／定位／生物辨識或玻璃 GPU 畫質已驗證。最終結果見 BUILD-REPORT.md。

## 官方定義

- Shizuku API：https://github.com/RikkaApps/Shizuku-API
- 無線偵錯啟動：https://shizuku.rikka.app/guide/setup/
- Linux 電源欄位：https://www.kernel.org/doc/html/latest/power/power_supply_class.html
- BatteryManager：https://developer.android.com/reference/android/os/BatteryManager
- NFC：https://developer.android.com/reference/android/nfc/NfcAdapter
- AudioRecord：https://developer.android.com/reference/android/media/AudioRecord
- LocationManager：https://developer.android.com/reference/android/location/LocationManager
- 生物辨識：https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt
- SamFW 本機型號目錄範例：https://samfw.com/firmware/SM-S9480
