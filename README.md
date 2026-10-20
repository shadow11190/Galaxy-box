# Galaxy Box

Galaxy Box 是以 Samsung Galaxy 手機為主的 Android 硬體資訊工具箱，其他 Android 手機也可使用支援的功能。App 由個人獨立製作，與 Samsung 官方無關。

**目前所有 0.x 版本皆為技術預覽版；正式版將從 1.0.0 開始。** 功能與讀值會因手機、系統版本及權限而不同。

## 下載

- [0.7.0 技術預覽版](https://github.com/shadow11190/Galaxy-box/releases/tag/v0.7.0)
- [0.6.7 首個技術預覽 APK](https://github.com/shadow11190/Galaxy-box/releases/tag/v0.6.7)

支援 Android 8.0 以上。預覽 APK 使用測試簽章；若安裝時提示無法覆蓋舊版，可能是既有安裝的簽章不同。

## 主要功能

- **裝置與硬體**：查看機型、處理器、記憶體、儲存、螢幕、相機及感測器資訊。
- **溫度監控**：分開顯示 CPU、SoC、GPU 與電池資料，並可查看來源與趨勢。
- **電池**：查看充放電狀態與可取得的循環資料；可匯入完整 Bugreport ZIP，或自行選用 Shizuku 進階讀取。
- **系統版本**：查看本機版本與公開韌體目錄連結。
- **實用工具**：提供硬體檢測、貼膜輔助、桌面小工具、充電通知及可選的液態玻璃介面。

無法取得的資料會顯示未知，不會用猜測值冒充實測。Bugreport 在手機本機解析，不會由 App 上傳。液態玻璃預設關閉，可自行開啟。

## 更新日誌

只列有提供 APK 的版本；未單獨發布 APK 的早期開發內容併入 0.6.7。詳見 [更新日誌](CHANGELOG.md)。

## 原始碼與致謝

專案使用 Kotlin 與 Jetpack Compose。使用 Android Studio 開啟本目錄即可建置；建置設定與詳細資料來源見專案內技術文件。

感謝 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)、[Shizuku API](https://github.com/RikkaApps/Shizuku-API) 及其他開源專案。授權與圖示來源見 [第三方聲明](THIRD_PARTY_NOTICES.md)；本專案原創程式碼採 [MIT License](LICENSE)。
