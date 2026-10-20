# 底部液態導覽與刷新率 · 2026-10-20 本機修訂

版本維持 0.6.7 / versionCode 14、原圖標和既有 Debug 簽章。沒有推送 GitHub 或上傳雲端。

## 玻璃外觀

以原版 v0.6.7 的 `LiquidNavigation.kt` 為基準，保留 Backdrop 1.0.0 的背景捕捉、模糊、弧面折射、色散、高光、內陰影，以及圖示／文字的獨立放大捕捉。先前移除捕捉層會使玻璃變平，這次已恢復。

只移除外層導覽膠囊的實心填色／漸層；捕捉層內的膠囊也沒有填色或第二層陰影。玻璃泡泡停留時保持凸起與折射，按壓時輕微變形。泡泡和外框是兄弟圖層，外側保留溢出空間；清單內容延伸至導覽後方，以末端內容留白保護最後的按鈕，避免捕捉視窗提前結束造成橫向斷層。

液態玻璃仍預設關閉，Android／GPU 不支援或特效失敗時回退一般卡片。另有「導覽列背景」開關；關閉時，導覽本體不繪製底板、模糊、折射、背景陰影或高光，只保留圖示、文字和移動選取輪廓，點按和拖曳仍可用。

使用者提供的 ChatGPT Library 影片連結在目前瀏覽器要求登入，未能播放；本次依既有 v0.6.7 原始實作、圖片和文字操作要求整合，沒有宣稱逐幀比對該影片。

## 連續互動

- 一個共享位置控制器同步泡泡、頁面位移與圖示／文字強調狀態。
- 拖曳採直接位置更新，放開後以彈簧吸附最近項目；取消手勢返回上次已選頁。
- 點選、快速反向點選或接手未結束的動畫時，從當前位置繼續；新動畫取消舊任務。抓住泡泡不會瞬移到手指或舊的目標。
- 最多同時保留相鄰兩頁的畫面，各頁捲動位置分開保存；滑動中的頁面按鈕暫不接受誤觸。支援 RTL、尺寸改變和手勢取消。

## 刷新率策略與效能

只讀取目前螢幕的模式，找出**相同實體解析度**下最高的有效刷新率候選，沒有寫死 60／120 Hz。Android 30+ 使用視窗 `preferredRefreshRate` 偏好，不主動切解析度；Android 26–29 使用同解析度模式偏好。Compose 1.9.5 的 `preferredFrameRate(High)` 在支援的 Android 版本配合繪製提出高刷新偏好。

公開 API 沒有跨廠牌通用方法完整讀出使用者的刷新率上限／所有 OEM 限制，因此候選不代表系統一定允許。省電或 Android 系統熱狀態達嚴重以上時釋放偏好，其他情況也由系統與使用者設定裁定；沒有更改系統設定、隱藏 API、Root 或 ADB 強制鎖頻。

前景才註冊顯示／省電／熱狀態事件，離開前景恢復原視窗偏好並解除監聽。沒有持續呼叫 invalidate／requestLayout，沒有為了維持 Hz 的空轉動畫。Compose 動畫隨框架的畫面節奏更新，結束後停止。

硬體監控的兩秒取樣是資料更新頻率，並非動畫限制。檔案／硬體讀取在 IO 執行緒，溫度歷史整理移至背景計算執行緒，以單次狀態提交更新 UI；高頻動畫位置主要在圖層轉換階段讀取。這些是程式策略，實機幀時間仍需量測。

## 真機檢查方式

1. 安裝本次 App APK 與配套 androidTest APK。版本號未變，請認明檔名含 `navigation` 的新檔；簽章一致可覆蓋安裝。
2. 在總覽的顯示效果，分別測試：一般模式、玻璃開啟、導覽列背景關閉。確認玻璃開啟且背景開啟時，停留即有透底折射和放大，沒有實心底板或橫向斷層；泡泡頂端與左右端沒有被切掉。
3. 快速連點第一與第五頁、連續左右拖曳、中途反向、放開前取消、轉向／改視窗尺寸。泡泡與頁面應接續當前位置，沒有回到舊位置再開始；每頁捲動位置保留，背景關閉時操作同樣成立。
4. 淺／深色、1.0／1.6／2.0 字體縮放、窄視窗及 RTL 都檢查文字和可點範圍。玻璃折射必須在支援 RuntimeShader 的真機 GPU 確認，Windows 排版圖不能替代。
5. 分別以手機實際支援的更新率／解析度設定測試。開啟省電與返回桌面再回 App，檢查偏好釋放與恢復；不要為了測試製造過熱。

### 自動收集真實視窗幀

以下測試不使用 Compose 虛擬測試時鐘，會在真實視窗進行多次左右拖曳，中途打斷彈簧；保留目前玻璃偏好和真實硬體資料。

```text
adb install -r Galaxy-box-v0.6.7-navigation-20261020.apk
adb install -r Galaxy-box-v0.6.7-navigation-androidTest.apk
adb shell am instrument -w -e class dev.galaxy.toolbox.FramePacingDeviceTest dev.galaxy.toolbox.test/androidx.test.runner.AndroidJUnitRunner
adb shell run-as dev.galaxy.toolbox cat files/navigation-frame-metrics.json
```

測試輸出記錄當時系統回報的螢幕 Hz、視窗請求值、觀察時間、Window FrameMetrics 回呼數、幀處理時間 p50／p95、超過自身 deadline 的幀數和遺失的指標回報。測量使用與 FrameMetrics 同一種 monotonic 時鐘。每次執行會取代上一份 JSON，請分別保存一般／玻璃／無背景結果。

**三種數字不能混用：**

| 數值 | 能證明什麼 | 不能證明什麼 |
| --- | --- | --- |
| 系統回報螢幕 Hz／開發者選項更新率 | 系統回報的顯示模式 | App 每秒實際繪出多少張 |
| FrameMetrics 繪製回呼／測量秒數 | 此段操作中視窗產生回呼的平均密度 | 每張都成功呈現，或每個動畫瞬間都達該 FPS |
| Perfetto FrameTimeline 的實際呈現與 deadline | 此次操作的呈現間隔、掉幀和 jank | 所有裝置／所有頁面永遠相同 |

最後用 Android 系統追蹤／Perfetto（支援版本）檢查 FrameTimeline、UI thread、RenderThread、GPU 與 SurfaceFlinger。依當下螢幕周期比較預期 deadline 與實際呈現；不要只看顯示 120 Hz 就宣稱 120 fps。`dumpsys gfxinfo ... framestats` 可補充 UI 幀時間，也不能單獨代表實際呈現 FPS。靜止畫面本來就可能沒有新幀，不能將省電的停止繪製視為掉幀。

本機建置結果及仍未驗證項目以同目錄 `BUILD-REPORT.md` 為準。

## 官方定義參考

- [Android frame-rate requests](https://developer.android.com/media/optimize/performance/frame-rate)
- [Display modes and refresh rate](https://developer.android.com/reference/android/view/Display)
- [View requested frame rate](https://developer.android.com/reference/android/view/View)
- [Compose interruptible animation](https://developer.android.com/develop/ui/compose/animation/advanced)
- [Backdrop 1.0.0 source](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.0)
