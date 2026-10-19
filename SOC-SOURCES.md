# SoC 溫度來源核對 · v0.6.1

2026-10-19。使用者提供的 DevCheck 6.60 APKS 為靜態分析參考，不包含於本專案，未複製其程式或資產。

## 使用者畫面提供的關鍵資訊

S26 Ultra 的「顯示溫度」來源選擇器明確顯示 **SoC (video) — 41°C**。另列 GPU (gpuss-0)、CPU (cpu-1-1-0) 等不同來源。這是軟體分類與原始標籤的佐證，不能藉此推定 video 測點的物理位置，亦不能證明所有區域版本韌體相同。

## DevCheck 讀取流程

- 分析本機 APKS 中的 base.apk，SHA-256：6c33a7734f3df6ed07024140579994a3928272d6e90ab1134382a696de9bf58a。
- DEX `qv1.h/i` 探索 `/sys/class/thermal` 及 `/sys/devices/virtual/thermal` 的 type／temp，`qv1.g` 探索 hwmon，`qv1.f` 優先用一般檔案讀取。Galaxy box 已有相同類型的公開來源探索，不需要新增特權。
- `qv1.c` 將包括 video 的若干子系統標籤歸入 SoC 類，與使用者畫面相符。因此先前沒有 SoC 摘要，至少可能是分類差異，不能直接結論為手機無感測器。
- DevCheck 存在 Root／Shizuku 相關分支；Galaxy box 不引入。其通用數值範圍過濾或按量級猜單位也不採用。合法 0°C 保留、BCL 不轉攝氏、hw-trip 仍排除。
- 靜態分析與截圖未取得該手機 temp 的完整路徑／本機驅動綁定，不宣稱已真機讀取成功。

## Galaxy box 規則

1. 明確 SoC 驅動標籤延伸支援 soc-temp-sensor-1／soc_die_0 等組合，不用 thermal_zone 編號作對應。
2. TSENS 標籤須有**同來源本機** compatible（qcom,tsens-v0_1／v1／v2）或精確 qcom-tsens／qcom_tsens 驅動，且為 thermal／hwmon 的毫攝氏來源，才歸 SoC。僅知道 Qualcomm 廠商或看到 tsens 名稱不夠。
3. 使用者 S26 Ultra 系列：完整型號須在現有目錄 SM-S9480／B／C／N／Q／U／U1／W／Z 白名單、本機 Qualcomm 廠商佐證、THERMAL 來源、名稱**精確 video**、已定義毫攝氏度，才使用 `soc-s26-video-category` 相容分類。標為**低可信度**，原始值依來源換算；顯示「SoC 相關測點最高溫」及「video 影音相關測點、實體位置未確認」提示。不將此規則套用至其他型號、相機、DDR 或不明單位。這些型號是系列匹配範圍，並非每個都已實測。
4. 只有本次成功且十秒內、分類允許的溫度測量進入摘要。CPU、GPU、電池及保護節點互不替代，沒有全體平均「手機溫度」。
5. 「查看 SoC 來源與讀取狀態」列出已辨識來源、其他未確認來源與探索記錄。來源錯誤保持各自實際狀態，不將整個目錄的權限錯誤套到成功讀取的節點。可點擊查看原始值／路徑／依據／時間，也可匯出診斷 JSON。

公開依據：[Linux TSENS 綁定](https://github.com/torvalds/linux/blob/v6.12/Documentation/devicetree/bindings/thermal/qcom-tsens.yaml)、[TSENS 驅動](https://github.com/torvalds/linux/blob/v6.12/drivers/thermal/qcom/tsens.c)、[thermal sysfs ABI](https://docs.kernel.org/driver-api/thermal/sysfs-api.html)。TSENS 文件明確將其描述為 Qualcomm SoC 溫度感測器，驅動將內部讀值轉成毫攝氏度；不是由 App 猜倍率。

## 尚待手機

安裝後確認 SoC 摘要採到 video、點擊來源取得實際路徑及讀取狀態；不同區域型號／韌體與 TSENS 可讀綁定仍需實機。若 video 讀取失敗，維持無可用資料並顯示實際錯誤，不以 CPU 或電池補值。尚未證實測點位置、中心溫度或完整晶片覆蓋率。
