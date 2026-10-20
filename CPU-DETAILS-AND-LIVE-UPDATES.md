# CPU 詳細規格與先前通知實驗紀錄 · 2026-10-20

2026-10-20 後續修訂：原生即時通知申請、開關與權限已從 App 移除。本檔下方「原生充電即時通知」段落只記錄已撤回的實驗，不描述目前 APK。一般充電狀態列通知仍可用。

版本維持 0.6.7 / code 14；此為本機修訂，未發布 GitHub／雲端。

## CPU：同一流程支援新舊款與各品牌

本機可讀硬體代號優先，其次精確地區型號／無歧義的內部代號規格備援，沿用 HardwareIdentityResolver，不從同名手機跨地區套用晶片。CPU 公開規格與本機頻率、ABI、執行位元、指令旗標分開，沒有增加一般畫面的「辨識依據」列。

新增 CPU 公開規格卡：微架構世代、核心組成、規格核心數、架構、參考公版頻率及快取。只顯示有資料的欄位，可點選查看來源。處理器離線目錄搜尋也可展開這些資訊；不是只在 S26 特判 UI。

- SM8850 / Snapdragon 8 Elite Gen 5：第三代 Oryon，2 Prime + 6 Performance；官方公版頻率與 for Galaxy 本機頻率分列。
- Snapdragon 8 Elite：第二代 Oryon；官方另有 7 核版本，不能對所有共用 SM8750 代號一律宣告 8 核。
- Snapdragon 8 Elite Gen 6 / Extreme Gen 6：依官方已公開核心、頻率及 Flex Cache 填入；官方資料未確認數字世代時，不能由 Gen 6 推成第四代 Oryon。
- 舊款 Kryo／Krait／Cortex、Exynos、Dimensity、Helio、Tensor 等沿用同一份結構化目錄；LineageOS 一致的 CPU、核心數、架構與核心群組可作規格參考，衝突的細項保留空白。
- Arm64／64 位元規格不代表已確認 Armv9 的細分版本；啟用哪些指令仍以 `/proc/cpuinfo` 為準。沒有把截圖中的頻率範圍當成規格或本機量測。

`catalog/cpu-details-curated.tsv` 保存逐筆詳細規格，`tools/build_hardware_catalog.py` 產生同內容的 `CpuSpecData.kt` 與 `assets/cpu-details.tsv`。`catalog/cpu-detail-coverage.json` 記錄實際涵蓋範圍，並非所有已上市晶片的完整清冊。MediaTek CPU 規格 section 與原始頁 SHA-256 另存 `catalog/mediatek-cpu-detail-review.json`；匯入工具只處理核對的 CPU 規格區塊，不將 Wi-Fi 晶片製程、GPU 頻率等當成 CPU 資料。

官方來源逐列存於資料表，主要包括：

- [Qualcomm Snapdragon 8 Elite Gen 5 product brief](https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Snapdragon-8-Elite-Gen-5-product-brief.pdf)
- [Qualcomm Snapdragon 8 Elite product brief](https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Snapdragon-8-Elite-Platform-Product-Brief.pdf)
- [Qualcomm Snapdragon 8 Gen 2 product brief](https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/images/company/news-media/media-center/press-kits/summit-2022/day-1/documents/Snapdragon_8_Gen_2_Product_Brief.pdf)
- [Samsung Exynos](https://semiconductor.samsung.com/processor/mobile-processor/)
- [MediaTek smartphone processors](https://www.mediatek.com/products/smartphones)
- [LineageOS Wiki 固定來源](https://github.com/LineageOS/lineage_wiki/tree/56a157866af6232edf646ae0f3e1b300e509c8e5/_data/devices)

## 已撤回：原生充電即時通知實驗

保留既有使用者主動開啟的一般充電通知，新增獨立的「系統原生即時通知」開關，預設關閉。Android 16 / API 36+ 才顯示選項；相容三星、小米及其他廠牌走同一 Android 公開 API。沒有額外浮窗、無障礙、通知存取權、Root 或機型白名單繞過。

只有使用者開啟、系統允許、來源新鮮且實際狀態為充電中時請求 promotion。暫停、充滿、未接電或舊資料回一般通知；關閉開關立即通知既有服務更新。通知沿用 silent／LOW channel、標準模板、狀態／電量／電池淨功率與停止按鈕。狀態 chip 優先用短功率文字，不可讀則顯示電量或充電中，沒有拿輸出功率推算充電器瓦數。

原生要求使用官方 AndroidX 相同的 `android.requestPromotedOngoing` extra、Manifest `POST_PROMOTED_NOTIFICATIONS`，並在 API 36 呼叫 `setShortCriticalText`、`hasPromotableCharacteristics`、`canPostPromotedNotifications`。Core 維持 1.15.0；SDK 36 原生方法有版本保護，不假裝存在 `Notification.Builder.setRequestPromotedOngoing` 方法。

「已請求」與系統實際回傳 `FLAG_PROMOTED_ONGOING` 分開呈現；只查詢本 App 的 activeNotifications，不要求通知監聽權限。系統顯示位置／形狀、鎖定畫面、三星 Now Bar 或小米島皆由 OS 控制，App 不能替系統畫 Backdrop 特效。使用者刪除通知會停止監控，避免不停重新貼回。原有 5 秒／30 秒／未接電 60 秒的 battery-only 更新策略保留，未增加高頻硬體輪詢或開機自啟動。

固定的 Android 16 測試框架仍採較早的 eligibility 定義，會拒絕目前官方指南允許的非 colorized 標準樣式。App 尊重實際 API 回答並顯示「系統未接受即時通知格式，保留一般通知」，不強制套用不同版本的舊樣式要求。測試分開驗證真實固定框架的拒絕／回退，以及模擬 OS 接受標準樣式時的 request 與 promotion 回讀；後者明確使用通知 eligibility shadow，不能作為三星／小米現機可上島的證據。

小米專用 `miui.focus.param` 接入需要官方在架應用、場景審核、方案及證書登記。本專案尚未取得該資格，因此本版沒有偽造白名單或宣稱已取得小米專用超級島支援；有公開 Android 16 Live Updates 能力的系統才可提出原生請求。三星／小米是否在特定版本接受充電監控情境，需要實機與廠商政策確認。

- [Android Live Updates 官方定義](https://developer.android.com/develop/ui/views/notifications/live-update)
- [AndroidX 官方 promotion extra 實作](https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/app/NotificationCompat.java)
- [小米官方接入流程](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132)
- [小米官方開發指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)

## 本輪另外修正

硬體分類列的 LazyListState 由 ToolboxApp 持有，分類列有獨立穩定 key，音訊／網路上方按鈕的插入不再改變它的身份。分類改變時只將被裁掉的選項移到可視範圍；已完整可見時不強制置中或重設位置。點選、資料更新、底部分頁離開再返回都沿用原有水平位置。

音訊頁頂部依 AudioManager.getDevices 列出喇叭、聽筒、內建麥克風與全部輸入／輸出端點數；安全音量喇叭路徑另列，不與一般喇叭路徑相加當作實體單體。Android 9+ 同時嘗試 getMicrophones，依唯一特徵 ID 列出內建麥克風數；空清單、無法辨認唯一 ID、權限或讀取失敗保持未知，不猜 0。這些是系統提供的清單，不能保證韌體列出全部實體零件；未由立體聲兩個聲道推成兩個實體喇叭。官方定義：https://developer.android.com/reference/android/media/AudioManager#getMicrophones()

貼膜輔助優先使用 Android 12+ WindowInsets 的 RoundedCorner 回報（若有），可切換成手動上下 R 角並調整水平／垂直內縮。加入圓角預設及重設按鈕；設定面板依圓角預留側邊空間。預覽與匯出 PNG 共用 FilmPattern 圓角框與內縮邏輯，格線也裁在自訂框內；極小視窗會限制內縮、R 角與框寬，不產生負尺寸。沒有宣稱此設定是面板原生毫米尺寸或保護貼裁切版型。

## 實機檢查步驟

CPU：以 S26 Ultra、S25、舊款 Snapdragon 845／865、Exynos／MediaTek 手機對照實際 SoC 代號與公開規格卡；確認本機 cpufreq 未被公版時脈取代、共用代號沒有錯選細分版本、未讀取指令未填入。離線目錄可先搜尋 SM8850、Snapdragon 845、Exynos 9825、Dimensity 9300+。

通知：Android 16+ 前景開啟充電通知與即時通知開關，在系統設定允許 Live Updates，接電後檢查「已請求」與「系統已提升」；再檢查鎖定畫面／狀態列。測試拒絕系統權限、暫停／充滿／拔線、資料過期、停止與手動刪除，應維持一般通知或停止，不要強制重貼。Android 8／14／15 只能使用原有一般通知。記錄實際機型、One UI／HyperOS 及 Android 版本；不能只憑送出 extra 宣稱 Now Bar 或小米島成功。

小工具：深淺桌布、200／320 dp 與縮放尺寸、1.0／1.6 倍字體；將玻璃改為純色再完全透明，檢查邊框／高光／底色／背景陰影均清除。可讀的 0°C／0% 保留，未知顯示「—」。桌面效果仍是靜態風格。

以上系統 UI 與實體硬體結果本輪尚未實測。Robolectric 的 API 36 權限 shadow 只驗證 App 決策與通知結構，不是廠商允許／實際上島證明。

## 可用的後續功能方向（尚未實作）

- 自訂電量目標提醒：沿用使用者已開啟的充電監控，達到目標時通知；不宣稱能控制或切斷充電。
- 快速設定磁磚：快速開啟充電通知、貼膜白底或手動檢測，避免把永久硬體監控硬塞進即時通知。
- Bugreport 匯入與檢測進度：使用一般進度通知提供取消／返回入口；是否適合 Live Updates 仍需依系統情境規範評估。
