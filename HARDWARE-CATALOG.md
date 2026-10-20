# 離線硬體目錄 / 0.6.7 本機修訂

使用「硬體 → 離線裝置與處理器目錄」搜尋市售名稱、完整型號、裝置代號或晶片代號。對照資料不需 Root、Shizuku 或網路。版本號及原有簽章維持不變。

## 資料與範圍

- Google Play 公開 certified-device CSV：完整保留品牌、Model、Device、Marketing Name 四欄，不將同名手機合併；不是所有已上市 Android 手機的完整清單。
- LineageOS Wiki：固定 commit `56a157866af6232edf646ae0f3e1b300e509c8e5` 的 766 個裝置檔案。只匯入明確列出的完整型號與 SoC 關係，不傳播到其他地區版本；保留多候選，不將 RAM／容量範圍當成實機讀數。
- 手工核對：`catalog/processors-curated.tsv` 與 `catalog/samsung-variants-curated.tsv`。每列有來源網址；Samsung CC 文件存在錯誤標籤（例如部分 S22 型號誤作 G 開頭），沒有自動照單匯入。只有完整 Google SKU 符合才加入手工地區規格。
- 最新計數、來源雜湊、品牌統計、缺漏說明：`catalog/coverage.json`，同一份也隨 APK 打包。
- 2018 年起 Android 手機核對：LineageOS 的 `type` 必須是 phone、foldable 或 phone (slider)，且有 2018 年起的 `release` 年份。`verified-phones-2018plus.tsv` 與 `processor-phone-usage-2018plus.tsv` 保留完整型號、處理器、年份與來源；`device-classifications.tsv` 只標記 Google 目錄中與來源完整型號相符的紀錄。原有名稱與處理器清單均不刪除；其餘年份／類型保持「待核對」。年份是手機上市年份，不是晶片發表年份。
- 目前已有 680 款不同品牌加完整型號的 2018 年起手機、78 款曾用於這些手機的處理器，另有 365 筆 Google 裝置名稱紀錄可核對為該時期手機。除了 LineageOS，`catalog/phones-2018-curated.tsv` 也可加入有完整型號與廠商來源的手機，包括 Xiaomi 18 Fold × 玄戒 O3。`catalog/phone-coverage-by-brand.tsv` 和 `catalog/processor-coverage-by-vendor.tsv` 記錄品牌缺口。53,014 筆 Google 裝置名稱包括舊機、平板與其他裝置，不能稱為 2018 年起手機總數。上述數量只表示已核對範圍，不代表所有 Android 手機已蒐齊。
- 不包含 DevCheck 或 AIDA64 的專有程式碼、素材或識別表。參考其分類與原始來源呈現方式。

## 辨識規則

1. 實機處理器識別依優先順序讀取 `Build.SOC_MODEL`、`Build.HARDWARE`、`Build.BOARD`，再核對固定唯讀 getprop 欄位、可讀 soc0 字串與 `/proc/cpuinfo` Hardware。全部在 repository 的 IO 工作執行緒處理，不高頻重讀。
2. 完整字串或已列出的別名才對照。`SM88500` 不等於 `SM8850`；`qcom` 不能決定是哪一款 Snapdragon。未知保留原文，不依頻率、核心數、市售名稱或地區猜測。
3. 共用料號保留候選，例如 SM8150（855／855+／860）、SM8250（865／865+／870）、S5E9945（2400／2400e）、MT6991（9400／9400+／9500s）。另一個實際晶片來源可縮小候選；來源衝突會明確標示，不靜默改寫。
4. 公開手機規格為獨立「規格參考」欄位，不能覆蓋本機 SoC、GPU renderer、頻率、記憶體等實際讀值。for Galaxy 是廠商品牌，並非已測出超頻。
5. 裝置名稱按品牌／製造商、完整 Model 查找，有歧義時再核對 Device。裝置代號不能單獨跨品牌辨識機型。DS／DSN 為已知雙 SIM 型號後綴；原始 Model 永遠另行顯示。
6. 同名不同地區版本分列；市場標籤不代表目前所在地或作用中 CSC。同一個完整型號仍可能有記憶體、容量、電信商與供應商批次差異，不推測未公開部分。

## 可重現更新

`python tools/build_hardware_catalog.py PATH_TO_CACHED_REFERENCES` 從已下載 CSV、固定版本 Wiki YAML 與人工 TSV 產生資產和 `ProcessorData.kt`。平常 Android 編譯不連網更新此資料庫。來源資料不可作為指令執行。

## 導覽修訂

玻璃選取框在未按壓時也保持凸起與折射，拖動僅輕微變形；移除 nav panel 與泡泡的實心底色，保留透明取樣、邊緣高光與柔和陰影。頁面捕捉來源延伸到導覽後方；改用清單尾端 padding 保證最後一筆操作仍能捲至選單上方。移除複製圖示／文字的捕捉層，避免重影。維持原本預設關閉與裝置不支援時的普通卡片 fallback。

Windows 的排版與互動測試不代表 Samsung GPU 上的 RuntimeShader 已完成實測；仍須手機確認亮邊、折射、拖動、底部無橫向斷層與手勢導覽區域。
