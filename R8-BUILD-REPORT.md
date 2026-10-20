# Galaxy box v0.6.7 · R8 本機建置 · 2026-10-20

已啟用 Release R8 full mode、程式碼縮減／最佳化／混淆與資源縮減。使用 Android 官方最佳化預設規則，保留 Shizuku 動態 Context 建構子；選用 ZIP 解碼器僅精確排除四個缺少類別的警告，STORED／DEFLATE／ZIP64 讀取路徑保留。

另外完成所有品牌共用的硬體優先辨識：無法辨識時採完整地區型號，再以同品牌且無歧義的內部代號備援。實際讀值與規格衝突時保留實際讀值；共用平台代號可用已核對完整 SKU 確定細分晶片。小米 14T Pro 日本／國際／SoftBank 版本已補上天璣 9300+ 與 TSMC 第三代 4 nm、CPU 規格；其他品牌同樣使用這個流程。原始讀值、GPU／頻率／容量／溫度不會被公開規格取代。資料表仍有未收錄規格，沒有宣稱所有手機資料都已完整。

目錄含 287 個處理器名稱，其中 93 個有製程、172 個有 CPU 規格；完整型號與晶片有 1388 列、1302 個品牌＋型號組合。新增審閱官方規格共 52 個晶片（38 個製程、51 個 CPU 描述），另有一致的 LineageOS CPU 規格；沒有用伴隨 Wi-Fi 晶片製程填入 SoC。缺口清單另存 processor-spec-gaps.tsv。

## 產物

- Galaxy-box-v0.6.7-r8-20261020.apk：2,399,723 bytes（2.40 MB）。
- 舊 navigation Debug APK：14,582,501 bytes（14.58 MB）。
- APK 減少 83.54%；DEX 原始大小減少 90.76%。**此比較是 Debug 與 R8 Release，包含排除 Debug tooling 等差異，不能全部歸因於 R8。** 詳細分項見 verification.json。
- 版本 0.6.7 / code 14、原圖標與既有 Debug 簽章不變；Release 不可除錯。舊 APK 分開保留。沒有上傳 GitHub／雲端。

## 工具與修改

AGP 8.9.2 的舊縮減器無法完整解析 Kotlin 2.2 中繼資料；已依官方相容表固定 AGP 8.10.1（實際 R8 8.10.24），沿用 Gradle 8.11.1、Kotlin 2.2.21、compileSdk 35 和既有 App 依賴。此次成功紀錄沒有 Kotlin 中繼資料解析錯誤或 missing class 錯誤。未使用 AGP 8.12+ 才支援的新資源縮減管線開關。

- build.gradle.kts
- app/build.gradle.kts
- app/proguard-rules.pro
- .github/workflows/android.yml
- README.md
- GLASS-WIDGETS.md
- app/src/test/java/dev/galaxy/toolbox/data/BugreportParserTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/HardwareIdentityTest.kt
- app/src/main/java/dev/galaxy/toolbox/data/HardwareIdentity.kt
- app/src/main/java/dev/galaxy/toolbox/data/HardwareCatalog.kt
- app/src/main/java/dev/galaxy/toolbox/data/DeviceNames.kt
- app/src/main/java/dev/galaxy/toolbox/data/DeviceDetails.kt
- app/src/main/java/dev/galaxy/toolbox/widgets/GalaxyWidgetProvider.kt
- tools/build_hardware_catalog.py
- catalog/processors-curated.tsv
- catalog/additional-variants-curated.tsv
- app/src/main/java/dev/galaxy/toolbox/data/ProcessorData.kt
- app/src/main/assets/processors.tsv
- app/src/main/assets/device-variants.tsv
- catalog/coverage.json
- app/src/main/assets/hardware-catalog-coverage.json
- catalog/processor-fact-review.json
- catalog/processor-spec-gaps.tsv
- THIRD_PARTY_NOTICES.md
- app/src/main/assets/processor-sources.json
- R8-OPTIMIZATION.md
- HARDWARE-IDENTITY.md

## 實際驗證

- testDebugUnitTest：222 項通過，0 失敗／錯誤／跳過；含新增 STORED 匯入及選用 XZ／Zstandard 解碼器前置拒絕、全目錄超過千個 SKU 的共同備援、硬體優先、同代號不同晶片、跨品牌／地區隔離等測試。測試執行於未混淆測試類別，不是最佳化 APK 的執行證明。
- lintRelease：0 Error、0 Fatal、15 Warning；assembleRelease 成功。
- APK 簽章、版本、非 debuggable、16 KiB 對齊、Manifest 入口、小工具 XML、通知圖示與全部 assets 逐檔核對通過。
- 直接解析最佳化 DEX：Shizuku 服務類別和公開 Context 建構子仍存在；NavigationMotion 類別已實際混淆；DEX 類別由 18,551 降為 3,421。
- 保存本次 mapping／configuration／seeds／usage／資源報告（依工具輸出），以及 APK／原始碼／mapping 校驗和。
- 本輪沒有手機連線。尚未執行最佳化 APK 的啟動、玻璃 GPU 效果、Shizuku 跨程序、桌面小工具或大 ZIP64 真機測試，也未量測啟動／記憶體／FPS 改善。

實機請優先覆蓋安裝後檢查五個頁面、玻璃點按與反向拖曳、完整 Bugreport ZIP 匯入、Shizuku 授權讀取、充電通知和三種桌面小工具。R8 版不可除錯，之前 FrameMetrics 測試文件中的 run-as 匯出方法不適用；效能可使用系統追蹤／Perfetto，崩潰堆疊用本次 mapping 還原。

完整設定原理與官方來源見 R8-OPTIMIZATION.md。
