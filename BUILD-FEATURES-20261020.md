# Galaxy box 0.6.7 · 本機修訂交付 · 2026-10-20

此為前次建置紀錄。後續本機修訂已移除原生即時通知申請、開關與權限，保留一般充電通知；新增 Exynos 1680／880 與溫度圖座標。下列舊版 APK 大小、測試數及通知說明不適用於後續修訂。

本輪完成：硬體分類列保存滑動位置／保持選中項目可見；新舊晶片共用 CPU 詳細規格；音訊麥克風特徵及喇叭／聽筒端點計數；貼膜系統／手動 R 角、內縮與 PNG 共用繪製；桌面靜態玻璃材質；Android 16 原生充電即時通知與一般通知回退。

處理器目錄 287 個名稱，188 個有詳細 CPU 描述、188 個有核心數、83 筆逐項核對官方詳細規格；另含固定 LineageOS 參考資料。未宣稱所有晶片規格完整。

## 驗證

- testDebugUnitTest：241 項通過，0 失敗／錯誤／跳過。包含 Android 16 / API 36 的通知結構與權限決策測試（使用 Robolectric shadow，不等於廠商實測），分類列在插入音訊／網路按鈕與頁面返回時保留位置、CPU 共用代號、音訊去重、圓角繪圖、小工具深淺／大小／字體矩陣及完全透明重套清除。
- Android 16 通知測試分別驗證固定框架對標準樣式的拒絕／一般通知回退，以及模擬系統接受此樣式時的請求與 promotion 回讀；接受分支使用通知 eligibility shadow，不能作為現機可上島證明。
- lintRelease：0 Error／Fatal，19 Warning；assembleRelease、compileDebugUnitTestKotlin、assembleDebugAndroidTest 通過。
- 原圖標、versionName 0.6.7 / versionCode 14、原安裝簽章核對通過；Release 不可除錯，R8／資源縮減保留。APK 2,441,071 bytes。
- compile SDK 36、target 35、min 26。沒有新增浮窗權限。APK 簽章、16 KiB 對齊、Manifest 入口及逐檔 assets 核對通過；Shizuku Context 建構子於最佳化 DEX 仍保留。
- Android 16 測試框架原先下載停滯，停止該次測試後，以分段下載及官方 SHA-512 核對補齊，再重新跑完整測試；成功結果只採最終執行。
- 保留 APK、原始碼、測試 APK、R8 mapping、建置紀錄、校驗和與小工具渲染圖。前一個 R8 APK 分開保存，未上傳 GitHub／Google Drive。
- device-tests APK 為 Debug 組態的測試程式，需搭配同份原始碼另建的 Debug App，不能當成主 App 安裝；本輪僅編譯，未在手機執行。

## 仍需手機確認

未在實體手機啟動／安裝本次 APK；尚未確認三星 Now Bar 或小米島接受此充電情境。小米專用島需要廠商應用／場景審核，本版未取得資格，沒有宣稱已支援專用介面。系統 API 的請求與真正 promotion 回讀分開。桌面是靜態玻璃風格，沒有即時桌布折射。

系統公開音訊端點／聲道不等同實體喇叭數；麥克風數是韌體列出的唯一特徵，不保證清單完整。貼膜 R 角與內縮可在各手機實際調整，並非保護貼毫米版型。不同機型的 SoC 細分版本、系統角落回報、真機分類手勢、通知生命週期及深淺桌布下的小工具效果仍需確認。

請依 CPU-DETAILS-AND-LIVE-UPDATES.md 的實機步驟檢查。刷新率／App FPS 沒有在本輪新增量測，不宣稱動畫已達 120fps。

## 修改檔案

- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- .github/workflows/android.yml
- app/src/main/java/dev/galaxy/toolbox/charging/ChargingLiveUpdate.kt
- app/src/main/java/dev/galaxy/toolbox/charging/ChargingNotification.kt
- app/src/main/java/dev/galaxy/toolbox/charging/ChargingStatusService.kt
- app/src/main/java/dev/galaxy/toolbox/ui/ChargingNotificationCard.kt
- app/src/main/java/dev/galaxy/toolbox/widgets/WidgetBackgroundPainter.kt
- app/src/main/java/dev/galaxy/toolbox/widgets/WidgetSettings.kt
- app/src/main/java/dev/galaxy/toolbox/widgets/WidgetConfigurationActivity.kt
- app/src/main/java/dev/galaxy/toolbox/ui/HardwareCategoryTabs.kt
- app/src/main/java/dev/galaxy/toolbox/ui/HardwareDashboard.kt
- app/src/main/java/dev/galaxy/toolbox/ui/ToolboxApp.kt
- app/src/main/java/dev/galaxy/toolbox/ui/FilmAssistant.kt
- app/src/main/java/dev/galaxy/toolbox/data/AudioInventory.kt
- app/src/main/java/dev/galaxy/toolbox/data/ConnectionDetails.kt
- app/src/main/java/dev/galaxy/toolbox/data/CpuSpecs.kt
- app/src/main/java/dev/galaxy/toolbox/data/CpuSpecData.kt
- app/src/main/java/dev/galaxy/toolbox/data/ProcessorData.kt
- app/src/main/java/dev/galaxy/toolbox/data/DeviceDetails.kt
- app/src/main/java/dev/galaxy/toolbox/ui/HardwareCatalogSheet.kt
- tools/build_hardware_catalog.py
- tools/import_mediatek_cpu_details.py
- catalog/cpu-details-curated.tsv
- catalog/cpu-detail-coverage.json
- catalog/mediatek-cpu-detail-review.json
- catalog/processors-curated.tsv
- catalog/coverage.json
- catalog/processor-spec-gaps.tsv
- app/src/main/assets/hardware-catalog-coverage.json
- app/src/main/assets/cpu-details.tsv
- app/src/main/assets/processors.tsv
- app/src/main/assets/processor-sources.json
- app/src/test/java/dev/galaxy/toolbox/data/CpuSpecsTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/ChargingLiveUpdateTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/ChargingNotificationTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/HardwareCategoryTabsTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/AudioInventoryTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/FilmPatternTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/WidgetRenderingTest.kt
- README.md
- THIRD_PARTY_NOTICES.md
- GLASS-WIDGETS.md
- CPU-DETAILS-AND-LIVE-UPDATES.md
