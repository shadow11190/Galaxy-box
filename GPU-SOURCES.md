# GPU 一般讀取 · v0.6.7

## 實際發現與修正

v0.6.6 的一般 GPU 頻率只嘗試 class KGSL/devfreq/cur_freq 與 max_freq，負載只有 gpubusy。這不足以判定手機必須用 ADB。新版本先擴充普通 App 唯讀來源，Shizuku 仍為可選備援且放在資訊後方，不會自動授權／啟動。

使用者 DevCheck 6.60 APKS 的 base.apk（SHA-256 6c33a7734f3df6ed07024140579994a3928272d6e90ab1134382a696de9bf58a）靜態核對：h8 路徑表包含 KGSL gpuclk、多個 platform／devfreq 來源；dh0 探索頻率與獨立百分比，再嘗試 gpubusy。未執行第三方 App，也未複製其程式／素材。其依字串長度猜頻率單位、負載數值修整或 runtime_active_time 推估並未採用。

## 有來源定義的契約

| 介面 | 原始單位／解析 | 說明 |
|---|---|---|
| KGSL devfreq cur_freq／min_freq／max_freq | Hz → MHz | 現有 class 路徑，另探索能辨識為 KGSL 的 devfreq 別名；不由寬鬆 gpu 字串套單位。 |
| KGSL gpuclk／max_gpuclk | Hz → MHz | 增加 driver 直接介面，與 devfreq 分開列來源；優先成功來源，不平均。 |
| KGSL clock_mhz／min_clock_mhz／max_clock_mhz | MHz | 不看數字大小改倍率。 |
| KGSL gpu_busy_percentage | 0–100%，可有百分號 | 0.5 就是 0.5%，不擅自乘 100；不去除任意雜字硬轉。 |
| KGSL gpubusy | busy / total × 100% | 兩個非負整數、busy≤total、total>0；0 / 100 為合法閒置，0 / 0 無有效比例。 |
| /sys/kernel/gpu/gpu_clock 等 | 依已驗證的 KGSL symlink 目標 | 必須本機 canonical target 對到上述已知 KGSL 節點，否則不猜 MHz 或百分比。 |

每類按固定優先序選取第一個有效來源，成功後優先續讀該來源。失敗立即退出本次數值並嘗試候選；不沿用舊數字當最新資料。零頻率保留，沒有任意合理範圍或排除長時間固定值。上限／下限為驅動介面的頻率限制，不代表遊戲速度、FPS 或使用率。

目錄僅首次／手動重新偵測列舉，限定四個 platform 根目錄、兩個 devfreq 根；每個根最多取有限候選、最多 108 個介面規格，不遞迴全機、不讀 debugfs、不執行命令。標準資料在既有 IO 取樣流程；前景每 2 秒，App 背景停止。一般充電通知只讀電池，不帶起 GPU 掃描。

每次嘗試保留 metric、來源 path、單位、raw、status、timestamp 與選取結果；目錄失敗與檔案結果分開，未將目錄權限錯誤套到成功節點。曾嘗試但本次未重讀的項目保留自己的舊時間，不假裝更新；數值區僅用本次選取，超過 10 秒或時鐘倒退退出。診斷預設收合，可主動複製，不上傳。

## 溫度

原有 thermal／hwmon、GPUSS 與本機綁定分類保留。GPU 頁也啟動溫度取樣，成功且有效的 GPU 溫度可直接點查看來源。增加有界探索到的 KGSL temp 與已確認 symlink；仍需 qcom,kgsl-3d compatible，僅看名稱／路徑不批准摘要。公開 KGSL 驅動 temp 在未取得測點時可能回傳 INT_MIN（-2147483648），只對該介面標「暫無讀值」；一般 0°C 保留。CPU hw-trip／BCL 等規則不變。沒有把頻率／busy 當作溫度，也沒有猜感測器物理位置。

## 暫不採用

DevCheck 還列出 gpufreq、Mali、gpusysfs、gpubusy_pct 等來源。本次未取得足夠對應驅動／單位依據者不直接套用；不把所有 cur_freq 都當 Hz。此版擴充著重 Qualcomm／Adreno KGSL，不能宣稱已涵蓋所有 Exynos／Xclipse／Mali。也未使用私有 KGSL ioctl 或 DevCheck native library。

## 來源與驗證邊界

- Qualcomm KGSL 公開驅動（frequency、percentage、MHz、alias、temp sentinel）：https://android.googlesource.com/kernel/msm/+/bb9406fd87124400118f8034c745d8523267f1a5/drivers/gpu/msm/kgsl_pwrctrl.c
- Linux devfreq：https://docs.kernel.org/driver-api/devfreq.html
- Android SELinux：https://source.android.com/docs/security/features/selinux

公開驅動證明介面定義與可能存在，不證明 S26 Ultra 某韌體開放讀取。未連接手機，不能宣稱已成功讀出 GPU 數字或已確定必須 ADB。安裝後「硬體→圖形→查看 GPU 來源診斷」可取得本機實際來源與錯誤；手機測試仍待完成。
