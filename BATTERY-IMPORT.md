# 電池報告匯入修正 · v0.6.4

使用者截圖顯示 ZIP 已讀取，但三個欄位皆未知，且超長行提醒重複三次。沒有取得使用者實際 ZIP，不能由截圖確認內容或保證該機型有所有欄位。

## 已確認的解析缺口

舊規則只接受 `mSavedBatteryAsoc: 96`，無法接受 Samsung 的單元素陣列 `mSavedBatteryAsoc: [96]`／`mSavedBatteryUsage: [12345]`。缺口可由合成原始格式重現；並不代表這就是使用者報告的唯一問題。

新版同時接受數字與完整方括號陣列、冒號／等號、空白與大小寫差異。明確區分未找到、來源未提供、格式／範圍無效、衝突及有效回報。負值、ASOC 超過 100、整數溢位、部分方括號、雜字及空陣列保持未知，保留原始依據。

多電池陣列若各值不同，不選第一顆、不取平均；多報告數值衝突也不選最新。有效與無效／不支援的回報同時存在時保持衝突未知。合法循環數 0、ASOC 原始 0 保留。

## 接受的來源與範圍

- ZIP／ZIP64 中檔名符合 bugreport／dumpstate 的 TXT 或 LOG，不分大小寫；含 Samsung dumpState_*.log。繼續只讀指定報告，其他附件不解壓。
- `DUMP OF SERVICE battery:`，含明確優先等級的標頭；其他服務（例如 batterystats）不得替代 battery。
- 無其他服務包覆的獨立 `Current Battery Service state:` 或 `BatteryInfoBackUp` 報告片段。若這些標頭出現在另一個服務內，不將那個服務提升為電池來源。
- 後續服務／分段標頭結束舊範圍；ZIP 不同檔案間清空區段狀態。無已辨識範圍的欄位只計入診斷，不採值。
- 循環次數接受明確的 cycle count／cycle_count／mCycleCount／batteryCycleCount 標籤；**不由 mSavedBatteryUsage ÷100 換算**。ASOC 仍是廠商原始回報，非本 App 實測的容量健康度。

格式研究參考 AOSP [BatteryService](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/services/core/java/com/android/server/BatteryService.java)、可公開檢視的 Samsung 韌體還原 [BattInfoManager](https://github.com/488315/samsung_framework/blob/main/services/sources/com/android/server/battery/batteryInfo/BattInfoManager.java)（ASOC／使用量為陣列型資料），以及公開工具對完整方括號形式的解析。韌體還原碼不是 Samsung 官方 API 承諾或 S26 Ultra 實測，不複製其實作、不引入特權，也不將其單位定義泛用到所有機型。

## 有界讀取與證據

保留 seekable ZIP 原地讀取、串流來源私有暫存、取消、CRC、16 GiB 文字／100,000 項／10 分鐘上限。非電池超長行略過並只記診斷；電池範圍內超長行停止，避免輸出不完整資料。

每欄原始依據最多 16 行，附 ZIP 路徑、文字行號及辨識範圍。重複欄位不再因超過 32 筆就整份失敗；仍檢查全部數值。超出儲存上限後第一次出現的衝突／無效值保留代表行，避免判定衝突卻看不到依據。候選數值僅需保存兩個相異值即可確認衝突，記憶體有界。

## 結果介面

結果摘要、三項欄位、簡短狀態；原始行按需展開。匯入時間以本地年月日時分秒顯示，保留「報告快照／裝置與產生時間未驗證」。共用的超長行／區段外欄位／讀取檔案／辨識區段統計只在「解析詳細資訊」出現一次，不重複或直接推論韌體沒提供。

使用者可主動複製解析摘要（統計及已辨識電池欄位的有限原始依據），供檢查新格式；不包含整份報告，也不自動上傳。報告檔名可能包含裝置資訊，使用者分享摘要前可自行檢視。

## 驗證限制

以標準 TXT、Samsung 方括號、獨立標頭、多電池、跨檔衝突、超長行、無效值及大型 ZIP／ZIP64 合成資料驗證。原生文件描述符測試仍確認可定位來源不建立整包副本。深／淺色及大字體畫面另有 Robolectric 渲染測試。

**未取得使用者的實際報告，也沒有手機連線。** 更新後應重新選取原 ZIP；若仍未知，可展開解析詳細資訊並由使用者選擇複製摘要。不能宣稱所有 Samsung 韌體／報告格式都已涵蓋。
