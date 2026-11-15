# TotalCheck — 開發規格書 (SPEC.md)

## 1. 專案目標與核心原則
- **核心定位**：純離線（無須網路權限）、極速清點的隨身工具檢查板 Android App。
- **操作哲學**：最少點擊次數完成清點（Single-Screen 單頁架構，捨棄多層頁面跳轉與繁瑣彈窗）。
- **互動體感**：跟手（1:1 手勢追蹤）、靈動（Spring 物理阻尼）、無縫（狀態平滑插值）、邏輯合理（未帶項目視覺優先）。

---

## 2. 功能範圍

### 核心功能（MVP — 主程式優先鎖定）
- [x] **多清單管理**：建立、重新命名、刪除清單，支援水平滑動（`HorizontalPager`）、頂部 `ExpandingChecklistTabBar` 點擊／遠距預熱切換與長按拖曳調整頁籤順序；進度卡整合清單改名／刪除按鈕，刪除清單時透過 `Snackbar` 提供連同內部項目的復原（Undo），並由自訂 `ScopedModalHost` 記憶鍵盤高度達成彈窗首幀零跳動展開。
- [x] **項目極速 CRUD**：底部常駐輸入列連續新增項目（隨軟鍵盤升降並自動上滑至頂部、向下捲動自動收合）、整列點擊切換勾選、行內編輯名稱（支援 `BackHandler` 退出與軟鍵盤退場自動儲存）、長按外圈進度環刪除（`Hold-to-Delete Perimeter Ring`）與 `Snackbar` 復原。
- [x] **項目自訂拖拽排序（Drag-to-Reorder）**：支援拖曳右側手把（`DragIndicator`）調整項目順序（含懸浮拖曳層、虛線目標槽位與邊緣自動捲動），並支援鍵盤方向鍵及 TalkBack 無障礙上下移動操作與 Room 批次持久化。
- [x] **一鍵重置（Reset All）**：無二次確認彈窗立即歸零當前清單勾選狀態並觸發錯峰波浪動效（`Staggered Bulk Selection`），同時提供底部 `Snackbar` `復原 (Undo)` 機制。
- [x] **狀態持久化與多語系資源**：Room Database 本地儲存，維持項目原始順序（點擊勾選原地狀態轉場，不隨勾選沉底）、提供「只看未帶」過濾切換，並完整抽離中英雙語字串資源（`values`、`values-b+zh+Hant`、`values-zh`）。
- [x] **雙軌色彩與主題引擎**：內建專為 HyperOS / 非標準 Monet 系統調校的高對比亮/暗色盤、原生 Material 3 Dynamic Color 切換開關、`ThemeConfig?` 冷啟動零閃爍初始化，以及啟動骨架屏（`TotalCheckSkeletonShimmer`）。
- [x] **桌面 Widget（M8+，原生 Direct RemoteViews 雙形態自適應桌面小工具群）**：
  - **工具清單網格（`TotalCheckWidgetReceiver` / `widget_info_checklist_grid.xml`，`2x2` 以上）**：鎖定 `minWidth="140dp"` / `minHeight="110dp"`，依據宿主寬高自適應三級版型（`Compact` 1 欄、`Standard` 2 欄、`Expanded` 雙層頂欄 + 3 欄），於雙槽位 `<ViewFlipper>` 內以 `<ListView>` 承載純 `<TextView>` Bento 膠囊列（API 31+ 使用 `RemoteCollectionItems` 搭配 `hasStableIds = true` 與 `PendingIntentTemplate` + `FillInIntent`；API 26–30 回退至 `TotalCheckWidgetViewsService`），同時保留 `85ms` 水波紋綻放延遲、`↺ ⇄ 確認?` 兩段式防誤觸重置、鎖定寬度之 `尚缺 X` 篩選 `<ViewFlipper>`、全部備齊完成徽章、`WidgetSyncManager` 跨進程即時同步與點擊標題/進度列帶 `EXTRA_WIDGET_CHECKLIST_ID` 跳轉定位主畫面分頁。
  - **清單極簡計數條（`TotalCheckCounterWidgetReceiver` / `widget_info_counter_bar.xml`，`2x1` ~ `4x1`）**：嚴格遵守 Android 1 格高（`40dp`~`85dp`）水平縮放限制；左側內容區於 `2x1 ~ 4x1` 全尺寸常駐「上層（標題 + 右側精簡計數 `尚缺 X` / `✓ checked/total`）+ 下層圓角 `ProgressBar`」雙層結構，搭配雙槽位 `<ViewFlipper>` 180ms 淡入縮放過渡；右側操作區於 `2x1`（`< 220dp`）僅顯示 `< >` 切換，寬度 `>= 220dp`（`3x1` / `4x1`）時自動展開兩段式 `↺ ⇄ 確認?` 重置鈕，與網格小工具共用 `WidgetSyncManager` 即時雙向同步。
  - **動態預覽與動態色彩**：兩款小工具皆具備三語系命名描述、Android 12–16/17 純靜態高擬真 `previewLayout`（`widget_preview_checklist_grid.xml`、`widget_preview_counter_bar.xml`）、API < 31 風格 C 向量預覽圖（`*_fallback.xml`）及系統日夜模式動態色票（`values-v31` / `values-night-v31`）。

### 擴充功能（M7+ 迭代，底層欄位已預埋）
- [x] **讀取格式文檔與配置字串快速載入列表（M7）**：內建高容錯雙模式解析引擎（`ChecklistTextParser`：支援極簡單行逗號／頓號／空白／直線分隔字串與多行 Markdown / 表格 / CSV / TSV / JSON 陣列／純文字清單），整合 `LiquidTabBar` 液態分頁切換、AI 提示詞複製卡、剪貼簿貼上與 `.txt`/`.md`/`.csv` 本地文檔讀取，並於匯入前提供即時互動預覽（修改標題、切換待備/已備狀態、剔除單項）與 Sticky Header/Footer 防鍵盤遮擋排版。
- [ ] **分類 / 標籤篩選**：支援項目分類標籤與 `Expanding Tag Selection` 擠開動效。
- [ ] **清單匯出備份**：支援 CSV / Markdown / 純文字格式匯出與分享。

---

## 3. 資料結構與架構規範（一步到位，免 Migration）

### 3.1 Room Entities
```kotlin
// 1. 清單資料表 (checklists)
@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// 2. 清點項目資料表 (check_items)
@Entity(
    tableName = "check_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.Companion.CASCADE
        )
    ],
    indices = [Index("checklistId")]
)
data class CheckItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val label: String,
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,          // 預埋：自訂排序權重
    val category: String = "",       // 預埋：後續分類/標籤擴充
    val iconKey: String = "default"  // 預埋：未來桌面 Widget 小方塊圖標映射
)

```

### 3.2 預設查詢排序與過濾邏輯 (DAO)

* 查詢特定清單項目時，強制使用 `ORDER BY sortOrder ASC, id DESC`：
* **維持原始順序**：點擊勾選時卡片保持原位不動，僅進行原地狀態轉場（背景色、透明度 `0.45f`、刪除線與「待備 / 已備」膠囊標籤過渡），避免連點清點時項目跳動造成誤觸。
* **只看未帶過濾**：進度列旁提供「只看未帶」過濾切換（FilterChip），開啟時平滑隱藏 `isChecked = true` 的項目，讓未帶項目集中顯示。



### 3.3 主程式與 Widget 解耦機制

* `AppDatabase` 必須採用 Double-Checked Locking Singleton 模式，確保未來主程式與 Widget 跨進程讀寫不發生 Lock 衝突。
* `UserPreferencesRepository` 與 `WidgetSyncManager` 以 `appWidgetId` 為主鍵隔離每個桌上小工具實例的狀態（`widget_selected_checklist_$appWidgetId`、`widget_show_unchecked_$appWidgetId`、`widget_reset_confirm_until_$appWidgetId`），所有 `PendingIntent` 均綁定專屬 `appWidgetId`、`requestCode` 與 URI，並於 `onDeleted` 清理已刪除實例偏好。切換清單、篩選或第一段重置僅透過 `DirectWidgetRenderer.renderSingleWidgetById()` 更新單一目標實例；在 `ChecklistRepository` 每次完成寫入（新增、勾選、刪除、重置）後，統一呼叫 `WidgetSyncManager.notifyDataChanged(context)` 透過 `DirectWidgetRenderer.renderAll()` 同步所有小工具並保留各自獨立的面板狀態。而在桌面 Widget 點擊項目（`CMD_TOGGLE_ITEM`）時，由 `WidgetSyncManager.tryHandleFastCommand()` 採記憶體樂觀快取與 `toggleGeneration` 合併並給予 `85ms`（`TOGGLE_RIPPLE_DELAY_MS`）讓 `<ripple>` 水波紋完整綻放後，執行單一全量 `updateAppWidget`（於不觸發 `ViewFlipper.setDisplayedChild` 的前提下以 `hasStableIds = true` 原地更新 `<ListView>` 項目，維持捲動位置且零跳動）。由 Widget 點擊標題列／進度區啟動主程式時，透過 `MainActivity.EXTRA_WIDGET_CHECKLIST_ID` 與 `MainViewModel.selectAndScrollToChecklist()` 精準定位至對應清單。

---

## 4. 雙軌色彩系統規範 (`ui/theme/`)

1. **預設精調色盤（Curated Palette — 預設啟用，確保 HyperOS 與各廠牌視覺一致）**
* **Dark Mode（OLED 層次深色）**：
* 背景基底 `Surface`: `#0E1116`（深石板灰，避免純黑拖影）
* 未勾選項目卡片 `SurfaceContainerHigh`: `#1C222B`（高對比立體容器）
* 已勾選項目卡片 `SurfaceContainerLow`: `#12161C`（融入背景，文字 Alpha `0.45f`）
* 主強調色 `Primary`: `#38BDF8`（電光青）或 `#F59E0B`（琥珀橘，用於未帶數量警示與進度條）


* **Light Mode（戶外高可視暖白）**：
* 背景基底 `Surface`: `#F8FAFC`
* 未勾選項目卡片 `SurfaceContainerHigh`: `#FFFFFF`（搭配微邊框 `#E2E8F0`）
* 已勾選項目卡片 `SurfaceContainerLow`: `#F1F5F9`




2. **原生動態取色（Material 3 Dynamic Color）**
* 提供設定開關 `useDynamicColor: Boolean`（持久化於 DataStore）。開啟且系統為 Android 12+ 時，切換為 `dynamicDarkColorScheme` / `dynamicLightColorScheme`。



---

## 5. 主畫面 UI 佈局與進階交互動效規範

全面採用物理彈簧模型（預設基準：`spring(dampingRatio = 0.8f, stiffness = 380f)`），禁止生硬的線性 Tween 過渡，並遵守系統無障礙動畫縮放設定（Reduced Motion Fallback）。

| 界面區塊 / 觸發場景 | 對應動效模式 (Advanced Interaction Design) | 具體實作與狀態變化行為 |
| --- | --- | --- |
| **1. 亮暗主題切換** | **Radial Theme Transition**<br><br>(多波次圓形主題切換) | 按壓預擷取 `0.6f` 降採樣離屏快照（`LocalGraphicsContext` + `triggerMutex` 防抖；僅於轉場與預擷取期間錄製 `liveLayer`），點擊時以真實觸控座標 `(x, y)` 為圓心透過 `clipPath` 多波次 `RevealWave` 圓形遮罩擴散（`400ms`）揭示新主題圖層，不縮放頁面內容。 |
| **2. 清單組別切換與頁籤重排** | **Gesture-Driven Pager + Expanding Checklist Tab Bar**<br><br>(零重組跟手分頁與頁籤拖拽讓位) | 頂部 `ExpandingChecklistTabBar` 與 `HorizontalPager`（`beyondViewportPageCount = 1`）透過 `snapshotFlow` 雙向綁定；選中頁籤放大至 `1.08f` 並平滑推開相鄰頁籤，遠距點擊透過 `DistantTabTransition` 預熱鄰頁再平順滑動（`320ms`）；長按頁籤可懸浮拖曳重排（`1.06f`）並由 `TabReflowAnim` 彈簧補償讓位，清單改名／刪除則整合於進度卡操作鈕與刪除清單 `Snackbar` 復原。 |
| **3. 清點進度條推進** | **Spring Stepper Progress**<br><br>(步驟條過衝回彈) | 位於 Tab 下方的進度條（顯示 `尚缺 X 項 / 共 Y 項`）。當勾選項目使進度增加時，進度條先微幅過衝（Overshoot，`dampingRatio = 0.65f`）再回彈穩定至目標比例。 |
| **4. 單項勾選與原地轉場** | **Press Scale + In-Place State Transition**<br><br>(按壓回饋與原地狀態轉場) | 點擊整列任意處即可勾選。按壓瞬間卡片縮放至 `0.97f`，放開回彈 `1.0f` 並觸發系統震動（`HapticFeedbackType.TextHandleMove`）；勾選後卡片**保持原位不動**，透過單一 `Animatable`（`checkedProgress`）與 `lerpColor` / `drawBehind` 驅動背景色、文字透明度（`0.45f`）、刪除線與「待備 / 已備」膠囊標籤過渡，零重組切換。 |
| **5. 堆疊卡片捲動排列** | **Stacked Card Scroll Layout**<br><br>(深度堆疊、重排補間與過捲動手風琴) | 以自訂 `Layout`（`StackedCardScrollLayout`）取代傳統 `LazyColumn`：接近視窗頂/底緣的卡片自動壓縮為最多 3 層（`MAX_STACK_DEPTH = 3f`）的立體堆疊（每層縮放步階 `0.04f` 並依深度暗化遮罩），點擊壓縮卡片可自動展開捲入視野（`scrollToExpandItem`）；項目增刪／排序異動觸發 `ItemReflowAnim`、勾選／改名觸發 `NeighborRippleWave` 鄰近波紋擴散；滑抵頂底邊界時呈現手風琴式過捲動拉伸回彈（Overscroll Accordion）。 |
| **6. 一鍵重置清單** | **Staggered Bulk Selection**<br><br>(錯峰批量狀態更新) | 點擊「重置」按鈕時，DB 狀態立即歸零，列表項目的取消勾選視覺與脈衝放大由第一項往下依序錯峰（`20ms + idx * 35ms`，上限 `560ms`，以 `resetTriggeredAtMillis` 對齊時序）執行，同時底部彈出 `Snackbar` 提供 `Undo` 復原。 |
| **7. 長按外圈進度環刪除** | **Hold-to-Delete Perimeter Ring**<br><br>(外框進度環長按刪除) | 按住項目卡片 `150ms` 後觸發長按震動回饋，沿 `24.dp` 膠囊外框順時針繪製紅色進度環（`PathMeasure` 於 `1000ms` 線性填滿），填滿後卡片縮放淡出（`160ms`）並執行刪除與 `Snackbar` 復原；中途放開、滑出或取消則於 `300ms` 內線性退回。 |
| **8. 拖曳排序項目** | **Drag-to-Reorder**<br><br>(手把拖曳重排) | 拖曳項目右側 `DragIndicator` 手把時，卡片升起進入懸浮層（縮放 `1.03f`、陰影 `10.dp`、`primary` 邊框），原位顯示 `DashedDragPlaceholderSlot` 虛線槽位；拖曳至清單上下緣時自動捲動，放手後即時寫入 Room `sortOrder`；同步支援鍵盤方向鍵與 TalkBack 無障礙上下移動。 |
| **9. 底部極速新增列** | **Seamless Focus Input**<br><br>(無縫焦點連續輸入) | 常駐於畫面底部並隨 `WindowInsets.ime` 平滑升降。輸入名稱按下鍵盤 `Done` 後保持鍵盤開啟、輸入框瞬間清空並自動上滑至頂部；列表向下捲動時自動平滑收合讓出可視空間，向上捲動或切換分頁時重新顯現。 |
| **10. 啟動骨架屏** | **Startup Skeleton Shimmer** | 冷啟動等待 DataStore / Room 首幀資料期間（最少 `700ms`），以 `withFrameNanos` 驅動的微光掃描骨架屏（`TotalCheckSkeletonShimmer`）銜接，消除載入跳動。 |
| **11. 滑出取消觸感與鍵盤定錨彈窗** | **Slide-Out Cancel & Scoped Modal** | 全域按鈕與 Chip 採用 `SlideOutCancelModifier`（按下下沉 `0.94f` / `1.5.dp`，手指滑離 `44.dp` 內依曲線衰減並取消觸發，並附帶鍵盤焦點外框）；新增/改名/刪除清單採用 `ScopedModalHost` 跨會話記憶軟鍵盤高度，首幀即精準定錨於鍵盤上方零二次跳動且凍結背景 IME。 |
| **12. 後續擴充預留** | **Expanding Tag Selection** | 標籤篩選列選中時微幅放大並平滑推開相鄰標籤（待後續標籤功能迭代接入）。 |

---

## 6. 編譯環境與安全規範

* **SDK 版本**：`minSdk = 26` / `targetSdk = 36`（相容 Android 17 / API 37 實機測試，主畫面強制啟用 `enableEdgeToEdge()` 並正確處理 `WindowInsets` 避免遮擋）。
* **簽名保護**：Release 簽名資訊獨立存放於根目錄 `keystore.properties`，由 `app/build.gradle.kts` 動態讀取；`*.jks` 與 `keystore.properties` 嚴禁納入 Git 追蹤。
