# v0.6.7 驗證紀錄

此為第一個公開版本的驗證紀錄。使用最初圖標，沿用已驗證的 0.6.7 功能原始碼與 debug APK；未修改應用功能。GitHub 發布完成與否以 Release 頁面為準。

現有功能程式碼驗證：

| 檢查 | 結果 |
| --- | --- |
| 單元／本機渲染測試 | 189 通過，0 失敗，0 跳過 |
| Lint | 0 錯誤、15 警告 |
| 裝置測試 | 12 項已編譯、0 項已執行 |
| 本機渲染圖 | 60 張，含深淺色、文字放大與導覽邊界 |
| 現有 debug APK | 簽章、ZIP 對齊、4 個 ABI 的 16 KiB 原生頁面對齊已通過 |

警告類型：GradleDependency 7、ImplicitSamInstance 1、ObsoleteSdkInt 1、UsableSpace 1、UseKtx 5。

沒有連接手機或模擬器。尚未實測 Samsung 上的感測器可讀性／物理對應、Shizuku、玻璃折射、各診斷硬體、充電通知、啟動器小工具與使用者個別 Bugreport。不可將本機模擬資料來源測試當作手機讀取成功。

APK：`Galaxy-box-v0.6.7-debug.apk`，13,453,561 bytes。

SHA-256：`d937b839600fb998377b77dc51b13bb729f2f4c682bb42aa619521f5a4ea6195`。

簽署憑證 SHA-256：`9aac0ab8155b24f92b297aced697219e5c48fc3af3f062a9d20a2601d4df50da`。本次為 debug 建置，保留既有簽章以維持相同簽章安裝的更新相容性。未包含私人金鑰。
