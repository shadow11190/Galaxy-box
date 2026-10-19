# Galaxy Box

**主要為 Samsung Galaxy 三星手機設計**的繁體中文原生 Android 硬體工具箱，使用 Kotlin 與 Jetpack Compose。**v0.6.7 為本專案第一個公開版本**。支援 Android 8.0（API 26）以上，基本功能免 Root、免 Shizuku，部分進階讀取可自行啟用 Shizuku。

部分通用功能可在其他 Android 裝置使用，但三星專屬讀值、機型對照與診斷不保證適用。

## 功能

- **裝置與硬體**：機型名稱、處理器、GPU 驅動、CPU 頻率、記憶體、儲存、顯示器、Camera2 相機與感測器資訊。
- **溫度監控**：CPU、SoC、GPU、電池分開呈現；保留來源、原始值、讀取狀態與最近五分鐘趨勢。未知節點、保護狀態與疑似門檻另列診斷，不混入測溫摘要。
- **電池**：電量、溫度、電流、電壓與可取得的循環資料；匯入完整 Bugreport ZIP／ZIP64、TXT／LOG，在手機本機解析。可選擇使用 Shizuku 讀取固定的唯讀電池資訊。
- **充電通知**：可自行開啟一般狀態列通知，顯示充電狀態、電量與可取得的電池淨功率；實際狀態列樣式由系統控制。
- **系統更新**：顯示本機系統版本，依實際型號連至公開第三方韌體頁面；不代替官方 OTA 檢查。
- **硬體檢測與貼膜輔助**：依裝置能力顯示可執行的手動檢測；提供全螢幕色彩、格線及對位輔助。
- **桌面小工具**：完整與精簡資訊、獨立設定、靜態玻璃／純色／完全透明背景、欄位及色彩設定。資料為快照，更新受 Android 背景限制。
- **可選液態玻璃**：整合固定版本 Backdrop 1.0.0；預設關閉，不支援時使用一般卡片。桌面小工具使用靜態玻璃風格。

## 安裝

從 [v0.6.7 Release](https://github.com/shadow11190/Galaxy-box/releases/tag/v0.6.7) 下載 `Galaxy-box-v0.6.7-debug.apk`。本次公開版本沿用現有開發簽署 APK，為 **debug 建置**，不是商店正式簽署包；來源碼與 SHA-256 一併提供。相同簽章的既有安裝可覆蓋更新，簽章不同時 Android 會拒絕覆蓋。

## 資料與權限

讀取能力取決於手機、韌體與權限。無可用讀值時不以示範數字或零補值，也不以電池溫度代替 CPU／GPU。Shizuku 為選用功能，需使用者自行安裝、啟動及授權。Bugreport 可能包含私人資訊，App 於本機處理，不要求上傳；請勿將個人報告附在公開 Issue。相機、麥克風、定位及通知等功能僅在需要時請求相關權限。

## 從原始碼建置

使用 JDK 21、Android SDK 35 與 Build Tools 35.0.0。Gradle Wrapper 固定 8.11.1，AGP 8.9.2，Kotlin 2.2.21。首次建置需要下載相依套件。

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Windows 使用 `gradlew.bat`。自行建置的 debug APK 使用本機開發憑證，可能無法覆蓋其他憑證簽署的安裝版本。專案不包含私人簽署金鑰。

## 驗證與限制

目前功能程式碼已通過 189 項單元／渲染測試，Lint 為 0 錯誤、15 警告；12 項裝置測試已編譯，尚未在手機執行。深淺色、放大文字與導覽幾何已有本機渲染檢查，實際玻璃折射、Samsung 感測器、通知及啟動器行為仍需手機實測。本次保留最初圖標，功能原始碼與已驗證的 0.6.7 APK 相同。詳見 [驗證紀錄](BUILD-REPORT.md)。

## 致謝與授權

感謝 [Kyant / AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)、[Shizuku API](https://github.com/RikkaApps/Shizuku-API)、AndroidX、Apache Commons 與其他相依專案。完整聲明及資料來源見 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，授權原文隨 App 提供。

本專案原創程式碼採 [MIT License](LICENSE)；明確標示的第三方改作及資料仍依其原授權與聲明。Galaxy Box 是個人獨立製作的工具，與 Samsung／三星官方無隸屬、授權或背書關係；商標屬各權利人所有。硬體讀值與手動檢測供參考，不構成官方檢測、維修或安全認證。
