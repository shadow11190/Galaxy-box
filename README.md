# Galaxy Box

Galaxy Box 是以 Samsung Galaxy 手機為主的 Android 硬體資訊工具箱，其他 Android 手機也可使用支援的功能。App 由個人獨立製作，與 Samsung 官方無關。

**最新更新：2026-11-20（v0.7.2）**

**目前所有 0.x 版本皆為技術預覽版；正式版將從 1.0.0 開始。** 功能與讀值會因手機、系統版本及權限而不同。

## 作者

Galaxy Box 由個人開發者製作，持續整理手機硬體資訊、電池資料與實用檢測功能。作者的 [酷安個人主頁](https://www.coolapk.com/u/39601996)。

## 下載

- [0.7.2 技術預覽版](https://github.com/shadow11190/Galaxy-box/releases/tag/v0.7.2)
- [0.7.1 技術預覽版](https://github.com/shadow11190/Galaxy-box/releases/tag/v0.7.1)
- [0.7.0 技術預覽版](https://github.com/shadow11190/Galaxy-box/releases/tag/v0.7.0)
- [0.6.7 首個技術預覽 APK](https://github.com/shadow11190/Galaxy-box/releases/tag/v0.6.7)

支援 Android 8.0 以上。預覽 APK 使用測試簽章；若安裝時提示無法覆蓋舊版，可能是既有安裝的簽章不同。

## 主要功能

- **裝置與硬體**：查看機型、處理器、記憶體、儲存、螢幕、相機及感測器資訊。
- **溫度監控**：分開顯示 CPU、SoC、GPU 與電池資料，並可查看來源與趨勢。
- **電池**：查看充放電狀態與充電功率趨勢，並使用 Shizuku 讀取裝置支援的循環及容量資料。
- **系統版本**：查看本機版本與公開韌體目錄連結。
- **實用工具**：提供硬體檢測、貼膜輔助、桌面小工具、充電通知、浮動監控和效能通知。

設定入口位於各主頁頂部的「關於」左側。介面風格、玻璃、導覽列背景與主題選項已集中於設定面板；另可選擇自訂背景、裁切與背景組合，以及繁簡中／英文和攝氏／華氏顯示。背景圖片保存在 App 私有儲存空間，不會上傳。GIF 裁切採非破壞性的顯示區域設定，以保留動畫。HEIC、AVIF 與部分 SVG 內容是否可解碼仍取決於 Android 版本、裝置解碼器及 AndroidSVG 支援範圍。

無法取得的資料會顯示未知。介面支援液態玻璃與採用開源 [Miuix v0.9.4](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4) 元件的 MIUIX 風格；玻璃效果可在設定中切換。建置需要 Android SDK 37.0、AGP 9.1.1 與 Gradle 9.3.1，詳見 [MIUIX-IMPLEMENTATION.md](MIUIX-IMPLEMENTATION.md)。

## 已知限制

- 部分手機不開放 CPU／GPU 溫度、電池健康度或循環次數；相關欄位可能顯示未知。
- 裝置與處理器資料仍在補齊，少數型號或地區版本可能無法顯示完整市售名稱。
- 離線裝置與處理器目錄的搜尋入口暫時關閉；資料仍用於 App 內的機型辨識。
- Shizuku 進階讀取需另外安裝、啟動與授權；部分系統仍可能連線逾時或不提供資料。
- 桌面小工具的更新時間受 Android 系統限制；液態玻璃在裝置不支援時會改用一般卡片。
- 公開韌體目錄僅供查閱，不代表手機已可收到官方系統更新。
- 簡體中文使用隨 App 附帶的文字與字形對照表，不在畫面切換時執行外部轉換庫；會套用到 App 文字、小工具內容與設定、充電通知及操作朗讀。硬體原始名稱與使用者自訂名稱保留原文。加入小工具前的系統選擇器預覽依手機系統語言顯示。英文已涵蓋主要頁面的固定文字與常見即時狀態；部分深層診斷值與動態說明仍待逐筆校對。

## 更新日誌

只列有提供 APK 的版本；未單獨發布 APK 的早期開發內容併入 0.6.7。詳見 [更新日誌](CHANGELOG.md)。

## 原始碼與致謝

專案使用 Kotlin 與 Jetpack Compose。使用 Android Studio 開啟本目錄即可建置。硬體資料的核對方式、來源與收錄缺口見 [硬體目錄說明](HARDWARE-CATALOG.md)、[SOGI 補錄紀錄](SOGI-CATALOG.md)及 `catalog/` 原始資料；App 的規格卡不顯示外部來源網址。

感謝 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)、[Shizuku API](https://github.com/RikkaApps/Shizuku-API) 及其他開源專案。授權與圖示來源見 [第三方聲明](THIRD_PARTY_NOTICES.md)；本專案原創程式碼採 [MIT License](LICENSE)。
