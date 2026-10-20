# Galaxy box 0.6.7 · 液態導覽修訂 · 2026-10-20

本次 APK 使用 navigation 檔名，與較早 local 快照分開保存。版本維持 0.6.7 / versionCode 14，原圖標、既有 Debug 簽章不變；沒有推送 GitHub 或上傳雲端。

## 修改結果

- 以原版 v0.6.7 為基準恢復 panel／圖示／文字捕捉、折射放大、色散、高光與陰影。選取泡泡停留時即保持凸起。移除外層膠囊實心填色與漸層；捕捉層同樣沒有實心填色，並避免重複陰影。
- 保留泡泡外緣空間，沒有膠囊形父層裁切；內容捕捉延伸至導覽後方，使用清單末端留白保護操作空間，避免原捕捉區邊界出現橫向斷層。
- 點按／拖曳／連續反向操作共用位置；拖曳直接跟手，放開彈簧吸附，打斷動畫從當前位置接續。頁面、圖示和文字同步過渡，各頁保留捲動位置。背景完全關閉時仍可操作。
- 前景依目前螢幕與解析度請求最高候選刷新率，尊重省電、熱狀態和系統設定；離開前景釋放請求，沒有空轉重繪來維持 Hz。硬體檔案讀取與溫度歷史整理在背景執行緒，資料仍每兩秒取樣。
- 普通硬體畫面移除「處理器辨識依據」與原始代號來源診斷列；實際處理器辨識、離線資料庫和硬體功能保留。玻璃仍預設關閉，依賴固定 Backdrop 1.0.0，沒有增加第三方依賴。

## 實際驗證

- testDebugUnitTest：208 項，0 失敗、0 錯誤、0 跳過。包含快速連點、接手彈簧、反向拖曳、共享頁面位置、RTL、取消、縮放、兩種主題、字體縮放、背景關閉，以及同解析度動態刷新率選擇與 API 26／34 釋放偏好。
- lintDebug：0 Error、0 Fatal、9 Warning；未把既有警告視為沒有警告。
- assembleDebug、assembleDebugAndroidTest：成功。真機 FrameMetrics 測試已編譯，**沒有執行**；Robolectric 動畫測試使用虛擬時鐘，只證明互動／排版邏輯，不能證明真機 FPS。
- APK 版本、原圖標、既有憑證、16 KiB 頁面對齊與所有打包 assets 一致性已檢查；兩個 APK 簽章均已驗證。原生庫 ABI：arm64-v8a, armeabi-v7a, x86, x86_64。
- adb devices 本次結果：沒有已連接手機或模擬器。未宣稱 Samsung 真機折射外觀、感測器位置或 120 fps 已驗證。

## 限制與實機驗證

使用者提供的 ChatGPT Library 影片在目前瀏覽器要求登入，無法播放。本次依原版 v0.6.7 實作及文字／圖片要求還原，沒有宣稱逐幀比對影片。需在支援 RuntimeShader 的手機確認折射、色散、透明底板、左右端與頂端沒有裁切；Windows 的軟體繪製只用於一般卡片排版及失敗回退。

顯示的螢幕 Hz 是系統回報的顯示模式；App 繪製回呼與實際呈現 FPS 是另外兩種量測。沒有使用顯示 120 Hz 作為達到 120 fps 的證據。詳見 NAVIGATION-PERFORMANCE.md 的真機手勢測試、FrameMetrics JSON 與 Perfetto FrameTimeline 方法。

處理器／裝置對照資料沿用前次本機擴充，仍非所有 Android 地區版本的完整資料庫。既有 catalog/coverage.json 與 samsung-spec-gaps.tsv 保留；收錄名稱不等於每一款硬體規格都已證實。

## 本次主要修改檔案

- app/src/main/java/dev/galaxy/toolbox/ui/LiquidNavigation.kt
- app/src/main/java/dev/galaxy/toolbox/ui/NavigationMotion.kt
- app/src/main/java/dev/galaxy/toolbox/ui/NavigationPages.kt
- app/src/main/java/dev/galaxy/toolbox/ui/ToolboxApp.kt
- app/src/main/java/dev/galaxy/toolbox/ui/AppScrollContent.kt
- app/src/main/java/dev/galaxy/toolbox/ui/RefreshStatus.kt
- app/src/main/java/dev/galaxy/toolbox/ui/AboutApp.kt
- app/src/main/java/dev/galaxy/toolbox/performance/RefreshPolicy.kt
- app/src/main/java/dev/galaxy/toolbox/performance/HighRefreshController.kt
- app/src/main/java/dev/galaxy/toolbox/MainActivity.kt
- app/src/main/java/dev/galaxy/toolbox/ToolboxViewModel.kt
- app/src/main/java/dev/galaxy/toolbox/data/DeviceDetails.kt
- app/src/test/java/dev/galaxy/toolbox/data/NavigationInteractionTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/RefreshPolicyTest.kt
- app/src/test/java/dev/galaxy/toolbox/data/HighRefreshControllerTest.kt
- app/src/androidTest/java/dev/galaxy/toolbox/FramePacingDeviceTest.kt
- app/build.gradle.kts (resolved Compose version comment only)
- app/src/main/assets/licenses/Backdrop-Catalog-NOTICE.txt
- THIRD_PARTY_NOTICES.md
- NAVIGATION-PERFORMANCE.md
- BUILD-REPORT.md
