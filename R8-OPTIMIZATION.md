# R8 最佳化建置

2026-10-20：Release 預設啟用程式碼縮減、最佳化、名稱混淆，以及資源縮減，使用 `proguard-android-optimize.txt`。Debug 保留原本的除錯能力。

## 相容版本

- Android Gradle Plugin 固定 8.10.1，沿用 Gradle 8.11.1、Kotlin／Compose Compiler 2.2.21、compileSdk 35、minSdk 26。
- 初次試用 AGP 8.9.2 的縮減器時出現 Kotlin 中繼資料解析警告，因此更新至相容工具。實際使用的 R8 版本會記錄在匯出的 mapping 檔頭。
- AGP 8.0+ 預設為 R8 full mode，本專案沒有設定 `android.enableR8.fullMode=false`。
- 採用此版 AGP 支援的正常資源縮減。新版「optimized resource shrinking」管線需要 AGP 8.12+，本專案未加入對目前版本無效的開關，也未為此擴大工具鏈升級。
- Backdrop 1.0.0、Shizuku 13.1.5 與 Commons Compress 1.28.0 等 App 依賴不變。

## 保留規則

`app/proguard-rules.pro` 保留 Shizuku 動態啟動的 `DiagnosticUserService` 類別及公開 `Context` 建構子。其他 Binder 覆寫方法與呼叫鏈仍可最佳化；AIDL 交易編號和協議沒有修改。Shizuku 的 BinderContainer Parcelable 規則由依賴的 consumer rules 提供。

AndroidManifest 內的 Application、Activity、Service、Provider、Widget Receiver，以及程式參照的 RemoteViews XML／通知圖示，由 Android 建置工具產生入口保留規則。沒有以 `-keep class ** { *; }` 全部保留，也沒有全域 `-dontwarn`、關閉最佳化或移除應用日誌。

Commons Compress 參照選用的 XZ／Zstandard 解碼器。本 App 的可定位 ZIP 報告讀取流程在開啟項目前就限制壓縮方法為 STORED (0) 或 DEFLATE (8)，其他格式提供錯誤訊息；ZIP64 改變的是容器大小欄位，不需要 XZ／Zstandard。因此僅對實際缺少的四個選用類別加入精確 `-dontwarn`，不額外打包未使用的原生解碼器。測試涵蓋 STORED、DEFLATE、ZIP64，以及 XZ／Zstandard 在建構解碼器前被拒絕。

硬體名稱／處理器資料、授權文字等 assets 保留原檔，會逐檔核對 APK。R8 不會直接縮減這些資料檔。

## 建置與簽章

一般 Release 建置：

```text
./gradlew :app:assembleRelease
```

未提供正式簽章設定時輸出 unsigned APK。此次為了能覆蓋使用者現有安裝，使用明確的本機選項並沿用既有 Debug 憑證：

```text
./gradlew :app:assembleRelease -PgalaxyBox.localCompatibleSigning=true
```

這是**Release 程式碼／資源經 R8 縮減、不可除錯，但使用既有 Debug 憑證簽署**的本機測試檔，不是更換正式發布金鑰。versionName 仍為 0.6.7、versionCode 14；沒有上傳 GitHub 或雲端。金鑰不包含在原始碼壓縮包。

## 檢查與限制

打包後檢查：簽章與前版一致、版本、不可除錯、16 KiB 對齊、Manifest 入口、桌面小工具版面、通知圖示及全部 assets。直接檢查最佳化 DEX 中 Shizuku 公開 Context 建構子仍存在；保存 `mapping.txt`、`configuration.txt`、`usage.txt`、`seeds.txt` 和資源縮減報告（若工具輸出）。每次發布都應把該次 mapping 與該 APK 一起存檔，以便還原混淆後的崩潰堆疊。

單元測試是在未混淆的測試類別上執行，驗證原始程式邏輯；它不等於執行過最佳化 APK。沒有連接手機時，仍需在實機確認：啟動／五個分頁、液態玻璃拖曳、硬體與溫度、完整 ZIP64 報告匯入、Shizuku 授權與讀取、充電通知，以及各種小工具／設定／透明背景。體積下降不等於已測得開啟速度或 FPS 提升。

體積比較會分別列出壓縮 APK、解壓後 DEX 與資源大小。若拿前一版 Debug APK 作對照，差異同時包含 Release 排除了 Debug tooling 等內容，不能全歸因於 R8。

實際數值、驗證結果及修改檔案見本次匯出的 `R8-BUILD-REPORT.md`。

## 官方參考

- [R8 啟用與版本行為](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [Kotlin 與 AGP／R8 相容表](https://developer.android.com/build/kotlin-support)
- [AGP 8.10.1 修正與 Gradle 相容性](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
- [Shizuku UserService 動態建構子](https://github.com/RikkaApps/Shizuku-API/blob/master/demo/src/main/java/rikka/shizuku/demo/service/UserService.java)
