# 一般充電通知 · v0.6.7

依使用者更正，恢復一般狀態列充電通知；Nowbar 不恢復。電池頁「狀態列充電通知→開啟充電通知」由使用者主動選擇，預設關閉。Android 13+ 請求通知權限，拒絕時不啟動；整體或該 channel 關閉時顯示設定入口。

通知標題只顯示系統充電狀態與可讀電量，內容只顯示帶正負號的「電池淨功率」；沒有計算公式、長篇解釋或宣稱充電器輸入瓦數。功率來自原有 BatteryManager 電流與電池廣播電壓，缺值顯示 —，真實 0 保留。狀態依 ACTION_BATTERY_CHANGED 的 status，不因接上電源就假稱充電中。點擊開啟 App，並有「停止監控」動作；關閉會儲存為停用。

通知使用 IMPORTANCE_LOW、靜音、僅提醒一次、不震動、普通 CATEGORY_STATUS；沒有 promoted notification／Nowbar 權限或 API。系統決定狀態列圖示及通知版面，不能保證狀態列直接呈現全部數字。

背景由使用者開啟的非匯出前景 Service 處理，API 34+ 使用 specialUse 類型並宣告具體用途。不是 dataSync／connectedDevice 偽裝。啟動僅在 App 前景，沒有 boot receiver 或背景廣播重新啟動。已啟用時重新開 App 可恢復，START_NOT_STICKY 不要求系統強制重生；OS 強制停止後需使用者重新開啟。

服務只讀電池：亮屏且接電每 5 秒、熄屏接電每 30 秒、未接電每 60 秒，動態監聽電源及亮屏變更以重新取樣。不持有喚醒鎖、不要求忽略電池最佳化，深度休眠或系統管理可能延後。未接電仍顯示目前狀態並低頻等待，讓既有服務能在下次插電更新；使用者可隨時停止。此服務不讀 thermal／GPU、不執行 Shizuku／ADB、不記錄歷史或上傳資料。

新增 POST_NOTIFICATIONS、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE。桌面小工具仍遵守原本排程，不因本功能變成背景高頻。公開發行至 Google Play 時需另提交 specialUse 用途供平台審查，本次僅本機 debug APK，未發布商店。

官方定義：
- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- https://developer.android.com/develop/background-work/services/fgs/launch
- https://developer.android.com/develop/ui/compose/notifications/notification-permission

本機測試可核對通知文字、channel、停止動作、週期與宣告；沒有連接手機，Samsung 實際顯示、通知授權、背景續行、插拔充電及系統停止仍需實測。
