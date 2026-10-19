# Galaxy box v0.6.5 硬體與相機來源

參考使用者提供的 DevCheck 截圖之資訊分組與掃讀方式，使用 Galaxy box 既有配色、圓角卡片及可關閉的 Backdrop。未引入 DevCheck 程式、商標、圖示、資產或資料表。附件原圖僅 97×1280、136×1280，小字無法逐欄辨識；以下為本版實際實作範圍，非宣稱完整複製其他 App。

## 資料與畫面

Reading 新增可選 category / group，保留舊建構方式。明確分類優先於舊標籤推斷。HardwareSections 依語意分組；每核心、每 Camera ID、每感測器、每編解碼 MIME 各有自己的區塊。卡片先顯示主要欄位，可展開其餘欄位；長值的完整資料與來源在點擊後的詳細視窗顯示。字體 >1.3 倍或狹窄視窗改上下排列，不以縮小字體塞入。

- 處理器：沿用已核對的名稱／廠商／製程規格、ABI、執行位元與核心頻率；新增 /proc/cpuinfo 的 Features / flags 與核心識別欄位、online / present 啟動快照，以及 cpufreq 驅動／離散可用頻率表。檔案讀取上限 16 Ki 字元；不取 CPU 序號。kHz 依 cpufreq 定義轉 MHz。不可讀不由頻率猜核心架構或製程。
- 圖形：沿用離屏 EGL GLES2 Context 的 renderer、vendor、version、GLSL 與紋理上限，加入 Renderbuffer、頂點屬性、紋理單元上限與 GL_EXTENSIONS。這是該 Context 能力，不是 GLES 最高版本的全面驗證。系統宣告 GLES／Vulkan 另列；KGSL 即時頻率與忙碌比例沿用既有來源。
- 螢幕：目前系統模式、App 可用像素與 dp、邏輯密度、X/Y 回報密度、字體縮放、旋轉、寬色域、HDR、全部公開模式。API 34+ 使用 Display.Mode.supportedHdrTypes；API 26–33 使用 HdrCapabilities。沒有用 deprecated desired luminance 當實測亮度，沒有猜 OLED 型號、面板供應商、LTPO、PPI 或宣傳尺寸。
- RAM／儲存：ActivityManager.MemoryInfo 的已用／可用／總量、低記憶體旗標與門檻每 2 秒更新；App memoryClass／largeMemoryClass／低 RAM 配置、/proc/meminfo 的 SwapTotal／SwapFree／可選 Zram 為啟動快照。Linux kB 按 KiB×1024，保留 0，拒絕負值、未知單位及溢位。Zram 不存在時未知；不從 Swap 猜實體 RAM 或 RAM Plus。StatFs 為 App 資料分割區，非整顆快閃容量；無記憶體供應商項目。
- 裝置：保留精確型號對照的市售名稱與原始代號、系統版本；新增 NFC／藍牙 LE／Wi-Fi Direct／GNSS／指紋／USB／音訊／多指觸控／計步／氣壓等 PackageManager 功能宣告。宣告與功能實測分開。
- 感測器：每個 SensorManager 公開項目列廠商、stringType／type、版本、量程、解析度、標示功耗、取樣間隔、wake-up、回報模式、FIFO。量程／解析度單位依類型，不統一套單位；保留原始 float 精度。
- 編解碼：MediaCodecList.REGULAR_CODECS 依編碼／解碼與 MIME 分組；點擊可看公開的硬體／軟體／廠商旗標（API 29+）、影片寬高／幀率／位元率範圍、音訊聲道／取樣率、原始 Profile／Level 與色彩格式代碼。範圍不能當作所有組合都支援，非編解碼效能測試。

## Camera2

CameraManager.cameraIdList 為公開可開啟 ID，不能當鏡頭總數。API 28+ 只追蹤 physicalCameraIds 明確公開的子 ID；未猜測、掃描或暴力枚舉隱藏 ID。實體 ID 若不在可開啟清單，標為由邏輯 ID 公開、無法單獨開啟；來源／上層 ID 可查，沒有重複計數的鏡頭總數。逐一隔離讀取失敗，不因一個 ID 失敗丟掉其他鏡頭。

每鏡頭公開欄位包括：

- 最大一般 JPEG、實際焦距（非等效焦距）、可用光圈、感光元件 mm 尺寸、像素陣列、有效成像區、ISO、曝光範圍、Camera2 硬體等級。
- 自動對焦模式、最短對焦 diopter／校正、數位變焦上限、API 30+ zoomRatioRange、OIS／EIS 模式、閃光燈、方向、色彩濾光陣列。
- AE／AWB 模式與鎖定、補償步數／每步 EV、AE FPS 範圍、臉部偵測模式／數量、原始公開能力代碼名稱（未知代碼保留）。
- JPEG／YUV／RAW 輸出尺寸、MediaRecorder 尺寸及由最小幀間隔換算的理論上限、受限高速錄影的每尺寸 FPS 範圍。
- API 31+ 最大解析度模式像素陣列與 JPEG，與一般模式分列，不用較大者覆蓋一般模式；未公開即未知。

感光元件型號／廠商缺乏通用公開 Camera2 欄位，保持未知；不由手機、ID、尺寸或像素猜 IMX／ISOCELL。無法確認的鏡頭不標主鏡／長焦。沒有開啟相機或實際拍攝，也沒有聲稱已驗證 OIS、畫質或特定解析度可用。

API 29+ 的 getKeysNeedingPermission 可識別需相機權限的欄位；提供使用者主動觸發的 CAMERA runtime 權限按鈕，非首次啟動強制授權。拒絕時仍可使用公開資料。授權／從系統設定返回後變更權限會重新讀取；亦可手動刷新。相機功能宣告為 optional，不把無相機裝置排除。讀取在 IO dispatcher，cameraBusy 防止重複刷新。

## 官方定義（查核 2026-10-19）

- [CameraCharacteristics](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)：欄位語意、physicalCameraIds API 28、getKeysNeedingPermission API 29、最大解析度模式 API 31。
- [StreamConfigurationMap](https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap)：一般輸出、高速輸出及最小幀間隔。
- [Display.Mode](https://developer.android.com/reference/android/view/Display.Mode)：模式解析度與 API 34 supportedHdrTypes。
- [Linux CPU Performance Scaling](https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html)：cpufreq 政策、核心共享與 kHz 定義。

實際測試數字與 APK 驗證見 BUILD-REPORT.md。Robolectric metadata／排版測試不是 Samsung 實機資料；S26 Ultra 的鏡頭 ID、權限前後欄位與輸出上限仍需手機核對。
