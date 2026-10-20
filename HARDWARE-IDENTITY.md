# 硬體優先與地區型號關聯

所有品牌共用 `HardwareIdentityResolver`，不是只對 Samsung／Xiaomi 套用例外；首頁與硬體頁讀取相同結果，小工具也共用市售名稱備援。

1. 優先採本機 `SOC_MODEL`、硬體平台、唯讀系統屬性和 SoC 節點的已辨識結果。
2. 共用料號若對應多顆商用版本，可用**已核對的完整 SKU** 與候選取交集。只有相容的已知型號才縮小候選。
3. 硬體讀不到或不在代碼表中時，採品牌＋完整型號的公開規格，連到處理器、製程與 CPU 規格。
4. 完整型號沒有對照時，同品牌的內部代號僅在名稱和晶片規格無歧義時作為備援；不從代號推地區，也不從同名手機複製所有地區規格。
5. 本機已明確回報的晶片與公開規格不同時，保留本機資料並在來源詳細資訊註記衝突；普通畫面不額外加入「辨識依據」或原始 getprop 診斷卡。

公開規格補充的製程不是 App 可以量測到的物理特徵。GPU renderer、核心即時頻率、RAM 容量、電池與溫度仍讀本機，沒有把網路規格填成即時值。

## Xiaomi 14T Pro 範例

| 完整型號 | 內部代號 | 市售名稱 | 地區版本 | 處理器 |
| --- | --- | --- | --- | --- |
| 2407FPN8ER | rothko | Xiaomi 14T Pro | 日本 SIM-free | MediaTek Dimensity 9300+ |
| 2407FPN8EG | rothko | Xiaomi 14T Pro | 國際版 | MediaTek Dimensity 9300+ |
| A402XM | rothko | Xiaomi 14T Pro | 日本 SoftBank | MediaTek Dimensity 9300+ |

天璣 9300+：TSMC 第三代 4 nm；1× Cortex-X4 最高 3.4 GHz、3× Cortex-X4 最高 2.85 GHz、4× Cortex-A720 最高 2.0 GHz，均是公開規格。硬體只回報共用 `MT6989` 時，完整 SKU 可選定 9300+；只有一個未知型號及該共用料號時，仍保留其他晶片候選。

來源：[日本官方規格](https://www.mi.com/jp/product/xiaomi-14t-pro/specs/)、[國際版官方規格](https://www.mi.com/global/product/xiaomi-14t-pro/specs/)、[SoftBank 官方規格](https://www.softbank.jp/mobile/products/smartphone/xiaomi-14t-pro/)、[Xiaomi 日本版快速指南（電信商託管，標示 2407FPN8ER）](https://support.mineo.jp/contract/pdf/xiaomi_14t_pro_01.pdf)、[聯發科晶片規格](https://www.mediatek.com/products/smartphones/mediatek-dimensity-9300-plus)。

## 資料更新

`catalog/processors-curated.tsv` 保存有出處的晶片規格；完整 SKU、地區與晶片關聯另存 `samsung-variants-curated.tsv`／`additional-variants-curated.tsv`。公開裝置名稱表與 LineageOS 規格由已快取的來源生成；有一致 CPU 描述時補入參考規格，不覆蓋已核對的廠商規格。

執行 `python tools/build_hardware_catalog.py <本機 references 路徑>` 產生 assets 和 `ProcessorData.kt`，不在 Android 建置時爬資料。每筆型號與晶片仍保留來源，coverage 記錄實際收錄量。

這次修正的是所有品牌共用的關聯與備援流程，並擴充有資料的規格；名稱表數量不等於完整硬體規格覆蓋率。公開來源沒有的型號／規格仍留空，不能宣稱所有手機都已收齊。沒有用 SIM 國家、目前所在地或型號字尾規則推斷版本。

本次資料：287 個處理器名稱／849 個別名，93 個有製程、172 個有 CPU 規格。38 個製程與 51 個 CPU 架構的新增廠商資料已核對官方產品頁，另從 LineageOS 補入一致的 CPU 描述；審閱與來源雜湊見 `catalog/processor-fact-review.json`。有些頁面的 4 nm 指的是獨立 Wi-Fi／藍牙晶片，沒有拿來填 SoC 製程；9400+ 的 3 nm 另以 Xiaomi 15T Pro 官方規格確認，9500 的製程／CPU 則來自聯發科專題頁。仍缺製程或 CPU 描述的處理器列在 `catalog/processor-spec-gaps.tsv`。

裝置名稱表 53,014 列，完整型號與晶片規格 1,388 列（1,302 個品牌＋型號組合），其中三星 504 列規格參考。這是資料列與型號數量，不是「全數手機已完成」或真機驗證數量。
