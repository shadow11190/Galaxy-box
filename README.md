# Galaxy box

原生 Android v0.7.0，Kotlin + Jetpack Compose，繁體中文介面。最低 Android 8.0（API 26），target API 35 / compile API 36。免 Root，基本功能免 Shizuku、進階讀取可選 Shizuku；主要面向三星，其他 Android 裝置亦可使用，獨立專案，與 Samsung 無隸屬關係。

圖標以立體工具盒、星軌與星光組成，採深藍 / 薄荷綠 / 天藍配色。包含 Android 8+ adaptive icon 與 Android 13+ monochrome themed icon；全部以原生向量資源製作。

## v0.7.0 更新

- 首頁目前裝置卡依可辨識品牌顯示透明圖示；未能辨識時不留空框。收斂為 32 個有實際圖示資產的品牌，含 Black Shark；來源與授權列於 `branding/BRAND-ASSETS-0.7.0.tsv` 及第三方聲明。
- 電池讀值新增電流正負反轉、電壓估算及雙電芯解讀選項。僅在有來源依據時自動套用，仍可逐項手動調整；避免將部分廠商回報的非容量欄位誤作容量估算。
- 擴充三星與其他 Android 品牌的本機裝置／處理器對照資料。公開規格僅作辨識備援，不替代本機實際測量。
- 保留原有玻璃導覽及硬體監控。液態玻璃預設關閉，使用者可自行開啟。

## v0.6.7 更新

- 本機 R8 修訂：Release 預設啟用程式碼最佳化／混淆／縮減及資源縮減，保留 Shizuku 動態入口；工具鏈更新為相容 Kotlin 2.2 的 AGP 8.10.1。版本仍維持 0.6.7。設定、簽章與驗證限制見 [R8-OPTIMIZATION.md](R8-OPTIMIZATION.md)。
- 所有品牌共用硬體優先、完整地區 SKU、唯一內部代號的辨識與備援流程。補上 Xiaomi 14T Pro 日本／國際／SoftBank 版本與天璣 9300+、4 nm 製程；批次擴充其他處理器的製程及 CPU 規格。公開規格不覆蓋本機即時讀值，詳見 [HARDWARE-IDENTITY.md](HARDWARE-IDENTITY.md)。
- GPU 一般讀取增加已核對單位的 KGSL gpuclk、clock_mhz、百分比、可辨識 devfreq／platform 路徑與驗證後的 symlink；優先一般讀取，Shizuku 放在可選備援。診斷可查原始值、權限／格式錯誤及最後嘗試時間，可複製。詳見 GPU-SOURCES.md。
- 玻璃導覽的背景、捕捉層、泡泡改成同層，泡泡不再被背景膠囊裁切。靜止略高於原框，按壓再膨脹，預留動態空間並涵蓋放大的文字。
- 恢復一般狀態列充電通知，Nowbar 維持移除。電池頁主動開啟及授權；通知只列狀態、電量、電池淨功率及停止。使用者啟動的前景服務只取電池資料；亮屏充電每 5 秒、熄屏 30 秒、未接電源 60 秒，沒有喚醒鎖、GPU 掃描或 Shizuku 背景工作。詳見 CHARGING-NOTIFICATION.md。
- APK、原始碼及驗證報告預設留在電腦；依使用者要求，未收到明確指示不再上傳雲端。

## v0.6.6 更新

- 電池頁與 GPU 頁隱藏不可讀項目，循環 0 明示來源回報；底部保留完整導覽高度，避免按鈕被玻璃取樣。
- 可選 Shizuku 13.1.5，支援無線偵錯／ADB 或 Root 啟動，讀取設計／滿充電荷、能量、循環及 Samsung 原始欄位；可讀性取決於手機。App 不內建 ADB 配對。詳見 DIAGNOSTICS.md。
- 新增網路、音訊裝置分類及可選 2／5／10 秒更新的 /proc/meminfo 原文；離開前景停止輪詢。

## 已實作

- **儀表板**：裝置摘要、電池 / RAM / 儲存 / 核心頻率數字卡、CPU 各核心頻率格與最近 60 次 RAM 使用走勢。前景每 2 秒取樣，離開 App 停止前景監控。
- **硬體分類 v0.6.6**：處理器、圖形、溫度、記憶體、螢幕、相機、感測器、編解碼、裝置。依元件與用途分組，可展開詳細能力，點按資料查看完整值與來源，不再任意每十筆切卡。補充 CPU 指令旗標／核心原始識別／調頻驅動／頻率表、GPU Context 能力與擴充、顯示密度／寬色域／模式 HDR、RAM 壓力／門檻／Swap 與 App 額度、感測器詳細規格、編解碼器 MIME 分組與能力。所有可選來源不可讀即未知；不增加記憶體供應商猜測。詳見 HARDWARE-SOURCES.md。
- **相機詳細資訊**：逐一列出公開 Camera ID，補充公開的實體 ID 關係；每鏡頭可展開焦距／光圈／尺寸／ISO／曝光／對焦／防手震／能力／輸出尺寸與錄影限制。一般模式 JPEG、最大解析度模式與像素陣列分開；不以 ID 猜主鏡或感光元件型號。提供可選相機權限，只讀中繼資料、不啟動拍攝；授權後可重新讀取。
- **市售裝置名稱與詳細規格**：首頁優先顯示市售名、處理器名稱與 GPU；裝置頁另列完整型號、內部代號、主機板、品牌、Bootloader、核心與系統 Build 指紋。內建 3,376 筆 Google Play 公開目錄三星名稱紀錄，依完整型號對照；歧義無法解決時未知。資料取自 2026-10-19，支援範圍不代表所有三星裝置。
- **溫度監控 v0.5**：CPU、SoC、GPU、電池四格圓角摘要，可切換每來源五分鐘趨勢；分類清單採可展開圓角雙欄，原始數值及來源仍可點閱。支援使用者截圖的多段 CPU、cpullc、GPUSS 名稱，GPU 依本機綁定或有公開文件的名稱＋晶片廠商佐證辨識。保留資料種類、依據、可信程度、時間與明確失敗原因。新增 Android 系統熱狀態、10 秒熱壓力預估（每 15 秒讀取）、溫度診斷 JSON 匯出。其他頁及桌面小工具不做熱區掃描。
- **液態玻璃 UI**：依 AndroidLiquidGlass 官方文件整合 Backdrop 1.0.0。底部導覽、總覽主卡、資訊卡與溫度資料卡共用背景捕捉及樣式；獨立背景層避免自己取樣自己。Android 13+、硬體加速、非低記憶體及非省電模式啟用模糊與折射，其餘採一般卡片。特效預設關閉，由使用者自行開啟並保存，支援跟隨系統／深色／淺色。詳見 GLASS-WIDGETS.md。
- **v0.6.1 SoC 來源**：依使用者 S26 Ultra 的 DevCheck「SoC (video)」畫面補上限定型號、廠商與來源單位的相容分類，明示影音相關測點及低可信度，不假稱整顆晶片中心溫度。另有明確 SoC 標籤／本機 TSENS 綁定規則與可點閱的來源診斷；詳見 SOC-SOURCES.md。
- **v0.6.3 液態導覽重做**：依使用者影片與 Kyant LiquidBottomTabs 範例改編。透明玻璃泡包含圖示與文字的獨立捕捉／放大／彩色折射，加入按壓膨脹、阻尼追蹤、速度形變及放開回彈。保留單一互動語意、拖動放開切頁、玻璃預設關閉與能力回退；參見 GLASS-WIDGETS.md 及開源改編聲明。
- **GPU 即時參數**：圖形頁另列可選 KGSL devfreq 頻率與 gpubusy 忙碌比例，使用來源定義單位；不可讀或格式不符時隱藏；另可選 Shizuku 單次快照，不以頻率代替負載。
- **桌面小工具**：完整版（日期時間、機型、RAM、儲存、電量與電池溫度）、精簡版（電量、溫度及可選資訊），保留資源版。每個小工具獨立設定三種背景、背景不透明度、文字／強調色、日期時鐘／機型／欄位與點擊動作，提供實際 RemoteViews 即時預覽。完整透明會移除背景圖層及邊框／高光。流量為可取得時的 TrafficStats 自開機收送累計；缺值顯示 —。
- **電池**：電量、充放電狀態、電池溫度、電壓、瞬時電流、剩餘電荷、系統健康狀態、Android 14+ 循環次數。每筆資料顯示來源與限制。
- **大型 Bugreport ZIP／ZIP64 匯入**：系統文件選取器接受整包報告，不需手動解壓。可定位檔案直接透過檔案描述符及 SeekableByteChannel 讀 ZIP 目錄與報告，不複製整包、不解壓其他附件。串流來源才建立可取消的私有暫存；完成／失敗／取消會刪除。分階段顯示已讀大小、報告路徑與進度，另接受 TXT／LOG。不將原始檔或報告上傳。
- **充電速度**：電池頁顯示帶正負號的電池端淨功率（µA × mV ÷ 10⁹ W），不是充電器輸入或額定功率；Nowbar 維持移除；v0.6.7 依使用者更正恢復獨立的一般充電通知，背景服務僅由使用者啟動。
- **系統 / 韌體**：本機 Android、安全性更新與 Build 版本。自動驗證 Samsung 本機型號，點擊連至 SamFW 對應機型的公開目錄；移除手動型號／CSC 輸入及不可讀卡片。目錄為第三方跨地區參考，不代表本機 OTA 資格。
- **檢測**：依本機能力最多 21 類模組，支援逐項檢查與手動結果複製。系統、電池、連線、SIM、NFC、定位、感測器、音量鍵、震動、喇叭、麥克風、生物辨識、螢幕、觸控、筆輸入、充電與耳機等；詳見 DIAGNOSTICS.md。
- **貼膜輔助**：參考使用者提供的小米工具箱功能，自行實作除塵白底、邊緣檢視、格線對位、背景與邊框顏色、0–24 px 邊框、中央文字／十字、PNG 儲存。可全螢幕隱藏面板，雙擊／長按或返回鍵重新開啟設定；「結束」退出。預設跟隨系統亮度，可調本視窗亮度；離開或暫停時還原亮度、系統列及保持亮屏設定。沒有自動除塵、毫米校準或裁切版型。

- **關於本應用**：右上角「關於」可查看版本、個人製作／與 Samsung 無關聲明、開源致謝與授權原文、數據限制及報告隱私說明。

## 數據原則與目前限制

1. **未知即未知**：缺少欄位、API 不支援、權限限制或解析衝突，顯示「未知」。不以 0、網路規格或猜測補值；真實回報的循環數 0 仍可顯示。
2. **CPU / SoC / GPU 溫度**：普通 App 只能讀韌體開放的來源，先辨識用途再換算。CPU／SoC 各自取成功且 10 秒內的已辨識測點最高值；SoC 無獨立來源時顯示無可用資料，不從 CPU 或其他測點平均猜出來。GPU 優先採機型規則、本機裝置樹、驅動綁定，其次是公開 GPUSS 類型＋本機 Qualcomm 晶片佐證；最後才採精確 KGSL temp 路徑＋本機 compatible 佐證的契約。名稱／截圖不是物理位置驗證，信心與依據皆可查。電池固定用 Android API。溫度一般監控不要求 Root 或 Shizuku，不設定通用危險溫度。詳見 TEMPERATURE-DESIGN.md。
3. **容量健康度**：`BATTERY_HEALTH_GOOD` 是系統故障狀態，不等於 100% SOH；`CHARGE_COUNTER` 是剩餘電荷，不是滿充容量。首版不以電量百分比推算健康度，通用 SOH 不推算；設計／滿充容量可選 Shizuku 讀取驅動原始欄位，不可讀則隱藏。
4. **Bugreport 支援格式**：v0.6.4 補上 Samsung `[96]` 等方括號／多電池陣列、獨立 Current Battery Service state／BatteryInfoBackUp 標頭及 ZIP 裡的 dumpState_*.log。仍依電池區段、明確欄位與完整數值解析，拒絕無關服務、缺值／無效值／跨來源衝突不推估。來源、行號與狀態可展開，共用診斷不重複。詳細規則見 BATTERY-IMPORT.md。
5. **Samsung 原始欄位**：ASOC 只顯示 0–100 的廠商原始值；不是本 App 實測的 SOH。`mSavedBatteryUsage` 單位未驗證，不除以 100 換算成循環數。不合併或覆蓋即時數據。同欄位多個不同值即未知。
6. **匯入限制**：取消舊 1 GiB 壓縮檔／512 MiB 文字限制。直接 ZIP 不受舊壓縮大小上限；報告文字合計最多 16 GiB、ZIP 最多 100,000 項、解析最多 10 分鐘。單行最多保留 16 Ki 字元，非電池區段超長行略過並統一註記，電池區段超長行停止。多報告衝突維持未知。結果只在記憶體；不支援 ZIP 再包 ZIP／加密報告／分卷壓縮檔。串流來源需足夠私有暫存空間，本機可定位來源不需整包副本。
7. **CSC / One UI**：CSC 是地區／電信商相關的韌體設定代碼。無可靠讀取來源時移除顯示與輸入，不用 SIM 國家猜測作用中 CSC。
8. **公開韌體 ≠ OTA 資格**：更新頁由本機型號連至第三方 SamFW，不自動查詢、下載或刷寫韌體。原有 Samsung XML／官方紀錄解析器與測試保留作底層工具，沒有手動查詢入口。
9. **手動檢測**：需使用者自行判讀；送出震動指令不等於馬達通過。螢幕格線沒有毫米校準，不提供保護貼版型、裁切尺寸或自動對位。
10. 相機測試開啟系統相機；麥克風／喇叭為手動診斷。尚無完整自動檢測認證及持久歷史資料庫。溫度診斷可匯出 JSON；不是全部效能歷史記錄。小米 APK 的完整 35 項功能對照與缺口見 FEATURE-PARITY.md，尚未全部移植。
11. **頻率比例不是 CPU 使用率**：核心橫條只以 scaling_cur_freq / cpuinfo_max_freq 計算，兩個來源任一不可讀時不畫數值橫條。CPU 小工具顯示可讀核心中的最高當前頻率，不以它推算系統負載。
12. **桌面顯示快照**：每張小工具標示月日與取樣時間。使用 Android AppWidgetProvider / RemoteViews，不常駐背景服務、不要求忽略省電限制或精確鬧鐘權限。系統週期排程為 30 分鐘，可能因休眠、省電或強制停止而延後；App 在前景時約每 30 秒同步已取得的快照。手動刷新可隨時發起。並非背景秒級監控。
13. **規格與實測分開**：優先本機 SOC_MODEL / HARDWARE 等回報；無法辨識時，使用品牌＋完整地區 SKU 的公開規格，再以唯一內部代號備援。名稱、晶片有歧義時不任選候選，不以同名手機推全部地區版本。本機與公開規格衝突時保留本機讀值。製程 / CPU 品牌及架構是公開參考規格；GPU 名稱直接顯示 EGL 驅動回報，ANGLE 或軟體 renderer 不會被資料表覆蓋。系統宣告能力與實際 Context 分列。完整流程見 HARDWARE-IDENTITY.md。
14. **單位與讀取狀態**：Android 電池溫度依 AOSP 定義為 0.1°C；已辨識 thermal / hwmon 溫度按各介面定義的毫攝氏度換算，不看數值大小猜倍率。用途／單位未確認則保留原始值。hwmon type=4、未知 type 或無法讀取其型態時不猜換算；fault / enable 失敗或異常亦退出有效溫度。Android 檔案讀取依 errno 區分權限不足、來源不存在與暫無讀值；無法明確辨識時顯示無法取得。格式錯誤另列；不以錯誤文字猜原因。
15. **S26 Ultra**：目錄包含 SM-S9480 / SM-S948B 等完整型號。晶片表新增 SM8850 的 Snapdragon 8 Elite Gen 5 與明確部分後綴，仍須由手機實際 SOC_MODEL / HARDWARE 匹配；S26 Ultra + 已確認晶片才顯示 for Galaxy。GPU 名稱保持驅動實際回報，不直接填入網路 GPU 規格。

## 溫度摘要、分類與進階資料

- 電池摘要只採 Android 電池溫度；thermal / hwmon 的其他電池來源留在詳細頁，不取平均或混接。
- CPU／SoC 各自以「測點最高溫」呈現，並非所有核心或整顆晶片完整測溫。GPU 的完整來源優先序見 TEMPERATURE-DESIGN.md，同序依原始名稱、來源 ID 固定選取；不選全體最高溫充當 GPU。
- 系統熱狀態按 PowerManager API 29+ 的 0–6 常數顯示未回報熱壓力、輕度、中度、嚴重、危急、緊急、關機等級；不支援時明示。它是系統壓力等級，不能證明所有元件未降頻。可點摘要狀態查看原始常數、時間與定義。
- cpu-hw-trip-0 / 1 若實際讀 temp，先列為用途待確認、保留 raw，不把 105000 宣稱為 CPU 105°C，也不僅由名稱確定它是門檻。探索時記錄可讀的 trip_point_*_temp / type、driver、compatible、uevent 與裝置節點名稱；實際 trip_point_*_temp 才列為已確認門檻欄位。符合門檻的數字不等於證實 temp 的用途。
- pm…-bcl-lvl0 / 1 / 2 為保護狀態類候選，只顯示 raw，不顯示 °C、不翻成正常／異常、不進最高溫。
- camera、video、ddr 等以相關測點命名，不宣稱相機表面、記憶體顆粒或其他實體位置。未知名稱保留原文；thermal_zone 編號只保留在來源路徑作診斷。
- 最近五分鐘趨勢按來源／路徑／名稱／單位／規則 ID 分開，只收有效測量。失敗樣本為空值，超過六秒的取樣缺口不連線；不補成 0。沒有統一紅黃綠溫度門檻。
- 失敗不保留為本次溫度；詳細頁另列上次有效值與最後成功時間。超過十秒或時鐘逆轉的成功資料退出摘要並標過期。
- 檔案探索有數量及讀取長度上限，後續只輪詢已發現的來源；「重新偵測」於下一次可見頁面的取樣重建來源。原始與趨勢只留程序記憶體，不寫入長期資料庫或上傳。

尚無 S26 Ultra 實測；cpu-hw-trip、BCL、sdr0 / mmw、GPU 綁定與所有相關測點的實體位置仍待手機／核心資料驗證。分類是有出處的軟體判讀，非實體感測器認證。

## 加入桌面小工具

在首頁向下捲到「桌面小工具」，選擇樣式並在桌面啟動器視窗確認。若啟動器不支援 App 內新增，長按桌面空白處 → 小工具 → Galaxy box，選擇其中一種樣式。

長按已加入的小工具可調整大小。當空間較窄或字體較大時，可拉寬或拉高以顯示更多資料；無法讀取的值仍顯示未知，未知電量不會畫成 0%。

本版參考硬體監控工具常見的摘要、分類與快速掃讀原則；版面、配色、圖示及小工具程式為 Galaxy box 自行製作，沒有引入 DevCheck 的資產或資料庫。

## 開啟與編譯

用 Android Studio 開啟本目錄，安裝 Android SDK Platform 35、Build Tools 35.0.0，使用 JDK 21 或相容的 Gradle JDK（本次以 JDK 22 建置）。固定 AGP 8.10.1、Gradle 8.11.1、Kotlin／Compose compiler 2.2.21、Compose BOM 2025.11.01（UI／Foundation 1.9.5）、Backdrop 1.0.0。Backdrop 的公開 class target 為 Java 21，因此 App 同步使用 JVM target 21。Gradle wrapper 固定 distribution SHA-256。

Windows：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

macOS / Linux：

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

設定 `ANDROID_HOME` 或由 Android Studio 產生 `local.properties` 的 `sdk.dir`。不要提交機器專用路徑或金鑰。

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`。這是開發測試版，以 debug key 簽署；正式發行需自有簽署金鑰與額外發布驗證。

R8 最佳化版本：`./gradlew lintRelease assembleRelease`，預設產出 unsigned Release APK。此次本機覆蓋安裝可加 `-PgalaxyBox.localCompatibleSigning=true` 沿用既有 Debug 簽章；金鑰不隨原始碼分發。每次應保存 `app/build/outputs/mapping/release/mapping.txt`，供還原崩潰堆疊。

連接已授權的測試裝置或啟動模擬器後，可執行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

專案包含解析器 / CSC 輸入測試與 Compose 導覽測試；實際執行紀錄見 `BUILD-REPORT.md`。`.github/workflows/android.yml` 提供後續 CI 建置設定；本次沒有建立或發布遠端儲存庫。

另外有 Robolectric API 34 原生圖形測試，檢查三種小工具背景、完全透明清除、獨立設定、深淺色與 1.6 倍文字縮放，以及大型 ZIP／ZIP64 直接讀取；本機渲染不替代 Samsung Launcher 和實機折射測試。也包含資料範圍 / 百分比 / 長整數計數、核心頻率比例、小工具未知與真零值、快照日期時區等單元測試。Android 測試涵蓋小工具 RemoteViews 套用、窄寬尺寸與 Provider 註冊，需連接裝置才能執行；成功編譯測試 APK 不等於已執行裝置測試。

## 架構

```text
app/src/main/java/dev/galaxy/toolbox/
  MainActivity.kt             Activity 前景 / 背景取樣生命週期
  ToolboxViewModel.kt         StateFlow、取樣、匯入與查詢狀態
  model/Models.kt             來源標註、快照、證據、型號 / CSC
  data/HardwareRepository.kt  公開 Android API 與可選唯讀頻率
  data/GpuInfo.kt             一次性離屏 EGL GPU 資訊
  data/BugreportParser.kt     有界串流與欄位證據解析
  data/FirmwareRepository.kt  HTTPS、回應限制、XML 與官方紀錄備援
  data/SamsungChangelogParser.kt  同型號頁面驗證與紀錄欄位解析
  ui/ToolboxApp.kt            Material 3 首頁及各功能頁
  ui/Diagnostics.kt           螢幕、貼膜、觸控、震動與感測器
  ui/HardwareDashboard.kt     數字卡、核心格、分類資料表與來源面板
  ui/WidgetActions.kt         桌面小工具新增入口
  widgets/WidgetPresentation.kt  快照格式化與未知值處理
  widgets/GalaxyWidgetProvider.kt  三種 Provider、手動刷新與 RemoteViews
```

目前採單一 app module，資料來源、狀態管理及 UI 分層，可再按功能拆模組。沒有直接呼叫 su 或反射隱藏 API。一般硬體監控停止於背景；使用者可選擇啟動獨立的電池充電通知前景服務，隨時停止。可選 Shizuku 接受 ADB UID 2000 或 Root UID 0，以固定唯讀 UserService 單次讀取電池／GPU，無任意命令入口。相機、麥克風、定位與 Android 12+ 藍牙在使用時授權；NFC、網路狀態、生物辨識等為對應功能所需宣告。匯入使用系統文件選取器，不要求整個儲存空間存取權。

## 官方依據

- [BatteryManager 欄位、單位與循環 API](https://developer.android.com/reference/android/os/BatteryManager)
- [PowerManager 熱節流狀態](https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus())
- [HardwarePropertiesManager 的特權限制](https://developer.android.com/reference/android/os/HardwarePropertiesManager)
- [AGP 8.10 相容版本](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
- [Kotlin Compose compiler plugin 設定](https://developer.android.com/develop/ui/compose/compiler)
- [Samsung 官方更新紀錄範例：SM-S9280 / TGY](https://doc.samsungmobile.com/SM-S9280/TGY/doc.html)
- [Android 桌面小工具更新規則](https://developer.android.com/develop/ui/views/appwidgets/advanced)
- [DevCheck 官方功能介紹（資訊呈現參考）](https://play.google.com/store/apps/details?id=flar2.devcheck)

三星公開 XML 端點為實作中的公開查詢來源，未視為 Samsung 承諾穩定的第三方開發 API。
# 2026-10-20 本機修訂（版本維持 0.6.7）

新增共用 CPU 詳細規格卡及離線目錄詳細資訊，涵蓋新舊 Oryon／Kryo／Cortex 等已收錄晶片；製程、世代、核心組成、公版時脈、快取與本機測量分開。已逐筆核對 83 個晶片的詳細官方 CPU 資料，並保留一致的 LineageOS 規格；完整涵蓋範圍及缺口以 catalog/cpu-detail-coverage.json 為準，沒有宣稱所有處理器資料都完整。

硬體分類列的滑動位置移到頁面外層保存，切到音訊／網路、資料更新及離開返回都不應歸零；選中分類會保持可見。音訊新增麥克風特徵與喇叭／聽筒端點數量摘要，端點及聲道數不當成實體喇叭顆數。貼膜輔助加入系統回報／手動上下 R 角、水平／垂直內縮及圓角預設，預覽與 PNG 共用繪製資料。

桌面使用透明中心與雙層高光的靜態玻璃風格，完整透明模式仍移除整個背景；不宣稱小工具能即時取樣桌布。使用者可自行開啟一般狀態列充電通知，顯示充電狀態、電量及電池淨功率。未提供 Now Bar／小米島功能。桌面效果說明見 GLASS-WIDGETS.md。

本輪維持 R8 最佳化、原圖標、原版本與既有本機安裝簽章。compile SDK 為 36，target 35、min 26。沒有發布 GitHub／雲端。

