# 溫度監控 v0.5.0

本版依使用者要求重做讀取、辨識、統計與畫面，維持 Kotlin / Compose、StateFlow 及免 Root / 免 Shizuku 架構。深藍與薄荷綠品牌風格沿用；溫度值使用中性色，沒有通用紅黃綠風險門檻。

## 畫面與資料流

HardwareRepository 在 IO worker 讀取 Android 電池、系統熱狀態及可選檔案來源；TemperatureFiles 回傳明確讀取結果，TemperatureClassifier 判定用途／單位／可信程度，TemperatureConversion 再轉換。ViewModel 的 TemperatureHistory 保存最後成功時間及各來源最近五分鐘樣本。TemperatureSelection 只選有資格且新鮮的量測。TemperaturePanel 呈現摘要、可展開分類、進階診斷與詳細資料。

溫度頁不再走原本 Reading 字串清單每十筆拆卡的分支。清單各列由 LazyColumn 延遲載入，展開狀態留在 Compose。其他硬體類別保留原呈現方式。

## 每筆資料

TemperatureDescriptor 保留原始名稱、來源家族、實際路徑或 API、來源單位、規則 ID、用途、分類、辨識依據、可信程度、摘要資格與可讀設定。TemperatureReading 保留本次原始值、可適用的攝氏換算、嘗試時間、最後成功時間、讀取狀態、錯誤依據及上次有效溫度。

用途是量測、門檻、保護狀態候選或用途待確認。未辨識節點與 BCL 不自動換成 °C。資料成功只表示當次讀值符合其來源／分類契約，不代表已實測感測器物理位置。

## 分類優先規則

1. 明確來源診斷不當量測。
2. BCL lvl0 / 1 / 2 是保護狀態類候選，不判定正常／異常；對 BCL 的門檻路徑也保留原始單位。
3. 實際 trip_point_*_temp 是門檻欄位，永不進目前最高溫。
4. 讀 temp 的 cpu-hw-trip-0 / 1 先列用途待確認；名稱與相同門檻數字都不是確認用途的證據。探索時記錄 temp 路徑、可讀 trip_point 設定、driver / compatible / uevent / of_node 名稱。沒有驗證前不把 105000 當 CPU 105°C。
5. VerifiedTemperatureRule 可加入有文件依據的精確型號、原始名稱、完整路徑、來源家族與單位；目前沒有預載 S26 Ultra 溫度物理對應表。
6. CPU 驅動名稱與來源單位共同辨識 CPU 相關測點，可信程度為中；支援多段編號、-usr 與 cpullc，不使用 zone 編號，不聲稱涵蓋每核心。SoC 只採 soc 明確標籤，不把 xo / quiet / 任意 tsens 當 SoC。GPU 可由本機 driver / compatible、本機 qcom,tzone-names、精確驗證規則，或公開 GPUSS 命名＋本機 Qualcomm / QTI 回報或精確晶片代碼佐證辨識。KGSL 則須精確 temp 路徑與本機 qcom,kgsl-3d compatible 共同符合公開契約；只有名稱／driver 字串仍無資格。一般規則皆為中可信度，沒有確認本機感測器物理位置。
7. 電池 API 為高可信公開來源。其他 battery 標籤只作詳細資料。camera / video / ddr / skin / usb 只稱相關測點，不對應零件表面或顆粒位置。
8. 其他名稱保留原文、原始值與診斷資訊；不自動假定 °C。

VerifiedTemperatureRule 是程式內的擴充點，不是從網路下載或自動套用的規格表。建立精確規則時須附可查核的證據，不應僅由手機市售名稱推定溫度單位或物理位置。

## 摘要與優先順序

- 電池只採 Android EXTRA_TEMPERATURE，來源定義為 0.1°C；其他電池來源不平均、不代用。
- CPU 僅取具摘要資格、成功且距最後成功讀取不超過 10 秒的 CPU 測量最高值，顯示「CPU 測點最高溫」。用途待確認、門檻、保護狀態、失敗或過期都排除。
- SoC 獨立取成功且 10 秒內有效的最高相關測點，沒有來源即無可用資料，不借 CPU、GPU、晶振或電池溫度。
- GPU 優先序：精確機型來源規則 0 → 本機裝置樹綁定 10 → 驅動綁定 thermal 20 → 公開 GPUSS 命名＋本機晶片佐證 25 → 驅動綁定 hwmon 30 → 精確 KGSL 路徑＋本機 compatible 契約 35。同序先依原始名稱再來源 ID 選取，不挑最大值、不平均。GPUSS 規則依公開 Qualcomm GPU 熱區名稱與來源 ABI，加上使用者提供的 S26 Ultra 畫面作補充；畫面不代表本 App 已在手機驗證。
- 不以電池代替 CPU／GPU，不計不同元件的全體平均「手機溫度」。無可靠來源顯示無可用資料。
- 系統熱狀態單獨顯示 PowerManager API 29+ 等級 0–6，為未回報熱壓力、輕度、中度、嚴重、危急、緊急、關機等級；不支援即明示。它不參與 °C 統計，且不代表已確認所有元件均未降頻。可點擊查看原始常數與最後成功時間。

## 單位、錯誤與時間

只依來源定義換算。thermal / hwmon 的已辨識測量用毫攝氏度；Android 電池為十分之一攝氏度。未知用途、BCL、未驗證 KGSL 或單位不明者不換算。hwmon 型態／旗標權限失敗、type=4 或其他不支援型態、fault / enable 異常均排除有效溫度；原始值及旗標仍可查。

Android 生產讀取器以 Os.open / read 的 errno 區分 EACCES / EPERM、ENOENT / ENOTDIR、ENODATA / EAGAIN，分別顯示權限不足、來源不存在、暫無讀值。空讀值是暫無讀值；格式不能解析另列。其他錯誤顯示無法取得，不從錯誤文字猜原因。所有檔案只讀且必定關閉，O_CLOEXEC 有 API 27 版本保護。

失敗的本次攝氏值為空，不用上次成功值冒充；上次有效值只在詳細頁標示過期。成功資料超過 10 秒或時鐘逆轉會視為過期並退出摘要。0 不直接當無效，也不因高溫或長期不變刪除測點；分類／用途判斷先於數值解讀。

## 五分鐘趨勢與效能

來源 ID 包含家族、canonical 路徑、原始名稱、單位及規則 ID。不同電池來源、切換來源或解釋規則不接成一條曲線。每來源保留最近五分鐘、最多 160 筆，只統計有效量測的最小／最大；失敗為空點、超過六秒間隔不連線。

App 前景迴圈約每兩秒取樣一次，IO 與 UI 分離。總覽或硬體溫度頁可見時才掃描／輪詢檔案；切換其他頁後下一次迴圈停止檔案輪詢。App onStop 取消前景工作，已在執行的同步讀取可能先完成當批，不會再啟動下一批。基本電池 API 沿用既有頁面與小工具所需的取樣。小工具單次取樣不掃熱區，也不啟動常駐服務。

探索有讀取長度、目錄與節點數上限；記錄來源後只讀已發現節點，重新偵測才重建。趨勢與讀值只留程序記憶體，總來源上限 768。溫度監控不需要新增敏感權限、系統寫入、網路上傳或追蹤。獨立的充電即時通知需使用者通知授權與前景服務權限，僅在使用者手動啟用的充電期間讀電池 API，不在背景掃 thermal／hwmon。

v0.5 每個 thermal 根目錄最多探索 256 區，實際測量與診斷門檻預算分開，避免前面的大量 trip 設定排擠後面 GPU／CPU。全域來源超過上限時優先保留非門檻來源。GPUSS 與 CPU 名稱中的數字是驅動原始標籤；從不以 thermal_zone 編號推定元件。JSON 匯出保留所有已收集來源的原始值、實際路徑、依據、狀態與時間；匯出時重新核對過期狀態，只寫使用者選取的位置。

## v0.5 畫面與熱壓力預估

四格摘要採獨立圓角卡；點卡切換該來源五分鐘圖，圖示選中邊框只代表選取，沒有溫度危險色。CPU／SoC 最高來源改變時只顯示新來源自己的歷史，不接上前一來源。分類感測器可展開為緊湊雙欄，每個測點數字靠右，點閱完整原始診斷。

PowerManager.getThermalHeadroom(10) 從 API 30 起可讀，前景溫度頁／總覽最多每 15 秒呼叫，與 2 秒攝氏取樣分開。非負有限值才顯示，包括合法 0 和超過 1 的數字；NaN 列暫無讀值、不猜不支援原因。超過 30 秒顯示過期。1.0 對應嚴重熱壓力參考點，沒有推定其他固定門檻，也不轉百分比。API 35+ 在可用時保留系統回報的各熱狀態預估門檻供詳細頁查核。

v0.6.0 改用 Backdrop 1.0.0，與其他資訊卡共用液態玻璃元件；另有完整一般卡片回退。背景捕捉與限制見 GLASS-WIDGETS.md。文字不經過特效，硬體來源與溫度分類規則不變；手機 GPU 繪製效能仍需實測。

## 測試及實機界線

TemperaturePipelineTest 使用真實測試檔案夾、注入 errno／權限失敗與固定時間，覆蓋單位、CPU / GPU / 電池選取、hw-trip、BCL、零度、高溫不任意過濾、未知節點、門檻路徑、權限／解析／失效、最後成功時間、來源隔離、GPU 優先序、趨勢空點與五分鐘裁剪、分組、系統熱狀態常數及探索快取。

TemperaturePanelTest 提供摘要排除 105°C／BCL、展開原始狀態及查看未確認 hw-trip 的三項 Compose 裝置測試。編譯成功不等於已執行。實際建置、測試數量與尚未實機驗證部分見 BUILD-REPORT.md。

仍需在 S26 Ultra 上核對每個 temp / trip 實際路徑、可讀設定、CPU／GPU 綁定、BCL 語意、sdr0 / mmw 的實際 errno、UI 操作與背景切換。未宣稱確認任何測點的物理位置。

## 官方依據

- [PowerManager 熱狀態：API 29 與常數](https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus())
- [AOSP BatteryService：batteryTemperatureTenthsCelsius](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/BatteryService.java)
- [Linux thermal temp／trip point ABI](https://kernel.org/doc/html/v5.7/driver-api/thermal/sysfs-api.html)
- [Linux hwmon 介面與單位例外](https://docs.kernel.org/hwmon/sysfs-interface.html)
- [公開 Android KGSL 參考驅動](https://android.googlesource.com/kernel/msm/+/730cf84987cc34f1f0e53cbdd4153a2227563dc7/drivers/gpu/msm/kgsl_pwrctrl.c)：只作歷史介面出處，不作 S26 Ultra 的本機契約驗證。
- [Qualcomm GPU 裝置樹的 tzone-names](https://android.googlesource.com/kernel/msm-extra/devicetree/+/refs/tags/android-s-v2-beta-1_r0.5/qcom/lito-gpu.dtsi)
- [公開 Qualcomm GPUSS thermal 設定](https://android.googlesource.com/kernel/common/+/refs/tags/android16-6.12-2025-09_r1/arch/arm64/boot/dts/qcom/sm8550.dtsi)：用作名稱家族與 GPU 角色的依據，不是 S26 Ultra 的實際裝置樹。
- [熱壓力預估 API 30+](https://developer.android.com/reference/android/os/PowerManager#getThermalHeadroom(int))
- [devfreq 頻率 ABI](https://www.kernel.org/doc/Documentation/ABI/testing/sysfs-class-devfreq)
- [KGSL busy / total 時間介面](https://android.googlesource.com/kernel/msm/+/refs/heads/android-msm-crosshatch-4.9-s-beta-5/drivers/gpu/msm/kgsl_pwrctrl.c)
