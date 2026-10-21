# SOGI 查缺補漏紀錄（2026-10-21）

SOGI 手機王用來尋找台灣已上市手機、平板及其上市年份、Android 版本與處理器。App 仍使用離線對照資料辨識裝置，目錄搜尋入口暫時關閉；手機不會直接爬取 SOGI。公開規格不能覆蓋本機實際讀值。

## 本批納入

- 手機 16 個完整型號：Samsung Galaxy A26/A36/A56（5 個型號）、OPPO Find X9/X9 Pro/X9s/X9 Ultra 與 Reno15/15 Pro（6 個）、vivo X200 FE/X300/X300 Pro（3 個）、Xiaomi 15T Pro 與 REDMI Note 15 Pro 5G（2 個）。
- 平板 3 個完整型號：Xiaomi Pad Mini、REDMI Pad 2 Pro Wi-Fi/5G。
- 每筆在 `catalog/phones-2018-curated.tsv` 或 `catalog/tablets-2018-curated.tsv` 保留 SOGI 產品頁與品牌官方來源；完整型號及裝置代號必須吻合 Google 裝置清單。國際版須另有當地官方規格或手冊，不按市售名稱批量延伸至所有地區後綴。
- 紅米「Dimensity 7400-Ultra」按小米公開名稱另列，未將無法證明等同的代號直接合併進一般 Dimensity 7400。

## 核對時發現的差異

- Samsung 的一份 A36/A56 比較頁把 A56 處理器列為 Exynos 1480；[Samsung 2025 半年度報告](https://images.samsung.com/is/content/samsung/assets/global/ir/docs/2025_Half_Interim_Report.pdf)與[SOGI A56 頁](https://www.sogi.com.tw/products/samsung-galaxy-a56-5g/17189)列為 Exynos 1580。本批只收有完整型號對照的版本，保留來源供再核對，不向其他 A56 後綴外推。
- [SOGI Redmi Pad 2 Pro 5G 頁](https://www.sogi.com.tw/products/xiaomi-redmi-pad-2-pro-5g/17291)的 GPU 名稱與[小米官方規格](https://www.mi.com/tw/product/redmi-pad-2-pro-5g/specs/)不一致；此次只匯入雙方相符的 Snapdragon 7s Gen 4，不將該 GPU 欄位加入目錄。
- [SOGI Galaxy Tab S12+ 頁](https://www.sogi.com.tw/products/samsung-galaxy-tab-s12-plus-wifi/17458)目前仍標「未上市」，本批未把它算入已上市、已驗證平板。

## 尚未涵蓋

這是以 SOGI 發現缺漏後完成的首批核對，並非 SOGI 全站完整匯入。站上未列的國際市場機種需持續查廠商官網；只有市售名稱、找不到完整型號或出廠 Android 版本的記錄暫不作裝置規格判定。2018 年起上市年份也不等於確定支援 Android 9 以上。
