# Galaxy box v0.6.3 · 液態玻璃與桌面小工具

## 固定且相容的版本

- `io.github.kyant0:backdrop:1.0.0`（Kyant、Apache-2.0），不使用動態版本。
- Kotlin／Compose compiler 2.2.21、Compose BOM 2025.11.01（UI／Foundation 1.9.4）、AGP 8.9.2、Gradle 8.11.1。
- compile／target SDK 35、min SDK 26；Java／JVM target 21，建置 JDK 21+，本次 JDK 22。沿用已核對 Maven AAR metadata 並成功建置的相容組合。

## 依使用者影片重做導覽

使用者 640 × 170、8.27 秒影片已抽取 25 個時間點檢視：主要特徵是透明玻璃泡浮起、圖示和文字一起放大扭曲、彩色邊緣與按壓／拖動回彈。v0.6.2 的 36 × 32 dp 小框無法呈現這些特徵，v0.6.3 改用完整光學層。

`LiquidNavigation.kt` 改編自 AndroidLiquidGlass **1.0.0 tag** 的 `LiquidBottomTabs.kt` 光學層及 `DampedDragAnimation.kt` 動態設計，固定上游 commit `eada9a8600142c1bd6a5c5fc143d06f599cdfaef`。不是從影片反向猜一組半透明色值。

- 上游來源：https://github.com/Kyant0/AndroidLiquidGlass/blob/eada9a8600142c1bd6a5c5fc143d06f599cdfaef/catalog/src/main/java/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
- 官方範例：https://github.com/Kyant0/AndroidLiquidGlass
- 官方捕捉教學：https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar
- Apache-2.0 完整授權及改編聲明均包含在 App 關於、assets/licenses 及 THIRD_PARTY_NOTICES.md。上游範例圖示、圖片、使用者影片均未打包。

## 捕捉層與光學效果

1. `ToolboxApp` 仍保留外部的獨立背景 Canvas 捕捉、真實 LazyColumn 內容捕捉，以及組合背景。不讓導航參與自己的外部來源捕捉。
2. 導覽底層：vibrancy → blur 8 dp → lens 24／24 dp，深色 #121212／淺色 #FAFAFA alpha .4，柔影與亮邊。按壓時背景略微膨脹，光斑跟隨玻璃泡。
3. 正常顯示的圖示與標籤只有一組互動／Tab 語意。
4. 另有隱藏同層 `tabsBackdrop`，記錄**強調色圖示及文字**，按壓時內容放大至 1.2。捕捉之前的外層 alpha=0，隱藏層清除語意、不附加點按處理，不造成重複朗讀或截取手勢。
5. 最上層玻璃泡採外部背景＋tabsBackdrop 組合來源。它包含完整單一分頁的圖示和文字，直接折射這一層，**沒有將普通圖示再覆蓋到玻璃泡頂上**。玻璃泡本身不在被捕捉來源內。
6. 玻璃泡不再套模糊：折射高度／量 10／14 dp × 按壓進度，啟用 chromaticAberration，加入同進度的高光、陰影及 8 dp 內陰影。透明程度依上游範例隨按壓變化，不使用實心薄荷按鈕底色。
7. 上游的 ContinuousCapsule 以 AndroidX RoundedCornerShape(50%) 適配，不新增形狀依賴。導覽本體 64 dp、玻璃泡及每頁內容 56 dp 高；過大字體自然增高，不裁掉標籤。
8. `GlassSurface.kt` 的一般資訊卡保留既有共用效果與普通卡片回退。本次主要重做使用者影片指定的底部光學導覽，硬體數字仍來自原本的來源。

## 動態與手勢

- 使用 press interaction 及水平拖動閾值，點按、長按停留也能觸發膨脹；未超過水平閾值時不搶走點按／垂直手勢。
- 阻尼追蹤採 spring(1,1000)。按壓 scale 目標採上游 78／56，X／Y 各自 spring(.6,250)／spring(.7,250)，拖動速度再產生有上限的非等比形變。
- 整個面板隨拖動位移輕微漂移，放開時與玻璃泡一起回復。放開吸附最近分頁才提交一次切頁，拖動中不反覆更換硬體頁面或啟停輪詢。
- 取消、寬度／文字方向／內容高度變更會回復目前選取；RTL 換算與首尾邊界保留。點按與 Tab／selected 輔助使用語意仍可用。
- 玻璃關閉時使用普通背景與較小的按壓回饋，點按／拖動仍可使用。這與開啟時的光學玻璃明確區分。

## 能力及驗證限制

玻璃**預設仍關閉**、保存使用者選項。需 API 33+、硬體加速、非低記憶體／省電模式及 AGSL 子著色器能力。進入前景／視窗附著／省電切換重新檢查；效果編譯失敗回報至 App 使用普通卡片，開關會明示狀態。

本機 Windows Robolectric 的 Skia 不支援 AGSL 子著色器 eval。JVM 渲染只能驗證回退版面、動畫座標及語意；故意要求光學層的測試只驗證來源隔離、語意與失敗保護，**不是折射畫質驗證**。裝置測試另驗證玻璃泡膨脹、包含標籤、取樣與畫面差異；本次無連接手機，不能宣稱已執行真機測試或與影片完全一致。

## AppWidget 渲染邊界

桌面以系統 RemoteViews 的 FrameLayout、LinearLayout、TextView、TextClock、ImageView 繪製，沒有將 Compose 或 Backdrop 放進 RemoteViews。玻璃模式是一張本應用繪製的靜態漸層／高光／圓角背景圖，不擷取桌布、不宣稱有即時折射。

完全透明具有獨立渲染分支：根背景資源設為 0，背景 ImageView 的 drawable 清空並 GONE；所有容器無底色、漸層、邊框、陰影或 elevation，刷新按鈕也沒有背景。保留文字、圖示、數字與資訊進度條。從玻璃切換透明時即使使用 RemoteViews.reapply，也會先清除舊背景。Launcher 自己提供的系統編輯框或外部陰影不屬於 App 小工具內容，仍需實機確認。

## 設定與資料

- 完整版：日期時間、機型、電量、電池溫度、記憶體與儲存空間。精簡版預設電量與溫度；既有資源版保留。
- `WidgetSettingsStore` 以 AppWidget ID 分開儲存背景模式、不透明度、背景／文字／強調色、日期時鐘／機型／欄位及點擊動作。刪除小工具只移除該 ID 設定。
- 設定 Activity 使用實際 RemoteViews 與本機快照即時預覽；方格只在預覽容器中，絕不輸出至桌面。取消不儲存，確認後更新桌面。可由 Launcher 重新設定或 App 總覽的「設定已加入的小工具」進入。
- 窄版及大字體改單欄，較矮時採緊湊列，不直接刪除已選欄位；過多欄位或較大字體請加大小工具。API 31+ 使用系統提供的尺寸清單，舊版提供直向／橫向 RemoteViews。
- 機型採本機公開名稱表，無法對應時保留 Build.MODEL，不拿內部代號當市售名。所有即時數據來自既有 HardwareRepository；讀取失敗顯示 —，進度條不以假 0 補值。
- 電池溫度來自 Android 電池 API。RAM 為系統使用量（含快取）；儲存為 App 資料所在分割區。CPU 欄位是可讀核心最高頻率，不是使用率。
- 可選流量為 TrafficStats.getTotalRxBytes + getTotalTxBytes，僅成功且非 UNSUPPORTED 時提供；以「自開機累計」標記期間，不等於行動電信帳單或每月統計，不要求額外敏感權限。
- 資料皆為附時間的快照。系統約 30 分鐘排程，可能延後；App 前景約 30 秒同步，↻ 可手動取樣。時鐘由 TextClock 的桌面端更新，沒有秒級背景輪詢、定時喚醒或常駐服務。

官方 AppWidget 依據：

- https://developer.android.com/reference/android/widget/RemoteViews
- https://developer.android.com/develop/ui/views/appwidgets/layouts
- https://developer.android.com/develop/ui/views/appwidgets/configuration
- https://developer.android.com/reference/android/net/TrafficStats

## 驗證範圍

必要純 Kotlin 測試、Robolectric API 34 原生圖形測試、Lint 及 App／Android 測試 APK 建置，結果統一記於 BUILD-REPORT.md。本機渲染圖是測試環境，不是 Samsung 手機截圖；硬體數據範例只存在測試來源。

必須留待手機：One UI Launcher 的放置／縮放／重新設定、背景排程、桌布上透明視覺效果，以及 Samsung GPU 上即時 Backdrop 折射的流暢度。不能以 JVM 渲染取代實機驗收。

本機 Robolectric API 34 的 Windows 原生 Skia 不支援 AGSL child shader `eval`（上游 issue #9691）。此情況由公開 RuntimeShader 能力探測攔截並回退一般卡片；不將此結果誤認為 Samsung 實機相容性結果。另有特效編譯錯誤回退，避免資訊頁崩潰。

- https://github.com/robolectric/robolectric/issues/9691


## v0.6.6 電池底部修正

主要 LazyColumn 使用 Scaffold 的完整上下留白並 clipToBounds。互動卡片與匯入按鈕不再進入底部導覽的捕捉區；導覽仍取樣本 App 背景層與隔離的圖示／標籤層。深／淺色與 1.0／1.6 倍字體以實際 AppScrollContent＋GlassNavigation 驗證邊界。玻璃預設關閉；本機圖片僅驗證回退與排版，仍不代表 Samsung AGSL 畫質實測。


## v0.6.7 導覽泡泡裁切

背景膠囊、放大圖示捕捉與玻璃泡泡改同層。背景的 drawBackdrop shape 只處理自身，不再當泡泡祖先；外層 clip=false 並依字體／泡泡高度保留上下溢出。隱藏捕捉來源涵蓋整個溢出容器，放大標籤不再被原框剪掉。靜止玻璃泡為槽位寬約 1.06、高 1.22；按壓目標約 1.2／1.4，加上有界速度形變；一般關閉模式保留較小回饋。字體 1.0／1.6／2.0、首尾分頁及按壓座標加入實際元件測試。背景與泡泡共用 Backdrop 1.0.0，不換成假玻璃素材；本機仍無 AGSL 真機畫質驗證。
