# TotalCheck — 開發規格書 (SPEC.md)

> **相關基準文件**：[專案交接指南 (`HANDOFF.md`)](HANDOFF.md) ｜ [UI 元件正式命名對照表 (`docs/COMPONENTS.md`)](docs/COMPONENTS.md) ｜ [版本里程碑 (`Milestone.md`)](Milestone.md) ｜ [AI 開發規範 (`AGENT.md`)](AGENT.md)

## 1. 專案目標與核心原則
- **核心定位**：純離線（無須網路權限）、極速清點的隨身工具檢查板 Android App。
- **操作哲學**：最少點擊次數完成清點（Single-Screen 單頁架構，捨棄多層頁面跳轉與繁瑣彈窗）。
- **互動體感**：跟手（1:1 手勢追蹤）、靈動（Spring 物理阻尼）、無縫（狀態平滑插值）、邏輯合理（未帶項目視覺優先）。

---

## 2. 功能範圍

### 核心功能（MVP — 主程式優先鎖定）
- [x] **多清單管理**：透過**新增清單彈窗**、**清單改名彈窗**、**清單刪除彈窗**建立、重新命名、刪除清單；支援水平滑動（`HorizontalPager`）、頂部**清單頁籤列**（`ExpandingChecklistTabBar`）點擊／遠距預熱切換、雙擊已選中**清單頁籤**原地展開**頁籤改名輸入框**、右側常駐**頁籤新增鈕**，以及長按拖曳調整**清單頁籤**順序；**清單資訊卡**整合**資訊卡編輯鈕**（預留長按版面配置擴充接口）與**資訊卡刪除鈕**，刪除清單時透過**底部提示條**提供連同內部項目的復原（Undo），並由自訂**彈窗遮罩宿主**（`ScopedModalHost`）記憶鍵盤高度達成彈窗首幀零跳動展開。
- [x] **項目極速 CRUD**：**底部快速輸入列**（`QuickInputBar`）連續新增項目（隨軟鍵盤升降並自動上滑至頂部、向下捲動自動收合）、點擊**項目卡片**整列原地勾選、點擊**項目編輯鈕**進入行內編輯並顯示**項目編輯工具列**（支援 `BackHandler` 退出與軟鍵盤退場自動儲存）、長按**項目卡片**觸發**項目長按刪除環**（`Hold-to-Delete Perimeter Ring`）並於**底部提示條**提供復原。
- [x] **項目自訂拖拽排序（Drag-to-Reorder）**：支援拖曳**項目拖曳手把**（`DragIndicator`）調整**項目卡片**順序（含懸浮拖曳層、**項目拖曳佔位槽**與邊緣自動捲動），並支援鍵盤方向鍵及 TalkBack 無障礙上下移動操作與 Room 批次持久化。
- [x] **重置（Reset All）**：**清單資訊卡**右上角**資訊卡重置鈕**（寬度鎖定對齊下方兩顆圓鈕 `88.dp`）無二次確認彈窗立即歸零當前清單勾選狀態並觸發錯峰波浪動效（`Staggered Bulk Selection`），同時透過**底部提示條**提供 `復原 (Undo)` 機制。
- [x] **狀態持久化與多語系資源**：Room Database（`version = 4`，無損升級 `MIGRATION_1_2` ~ `MIGRATION_3_4`）本地儲存，維持**項目卡片**原始順序（點擊勾選原地狀態轉場，不隨勾選沉底）；提供純圖示**資訊卡篩選鈕**（短按切換「只看未帶」，長按開啟**標籤篩選彈窗**選擇全域實心或清單空心單一標籤疊加篩選，並由所選標籤圖示動態替換按鈕圖示）、雙層標籤系統（跨清單全域標籤〔實心圖示，預設內建「重要」實心五角星〕＋僅作用於單一清單之清單標籤〔空心圖示〕，單項最多 2 個標籤；任何項目皆可左右滑動狀態窗翻轉至**項目狀態窗・標籤面**，短按開啟**項目標籤彈窗**、長按快速切換該清單上一個添加的標籤）、長按**清單資訊卡**平滑 `1x ⇄ 2x` 展開並 1:1 向下推移**項目堆疊列表**之**資訊卡設定面板**（頂欄**設定面板分頁列**並列「全域」、「清單」、「封存」三頁與關閉鈕，內含**設定面板標籤管理區**與**設定面板封存區**），並完整維護三語系字串資源（`values`、`values-b+zh+Hant`、`values-zh` 全面確保台灣繁體中文一致性與零遺漏）。
- [x] **智慧清點提醒與項目自動復原（M9）**：透過**提醒排程彈窗**支援清單級與單品級精確排程提醒（單次與週一～日週期）、**3D 時間滾輪**（`TactileDrumWheel`，高門檻 `950 dp/s` 5 倍速快滑＋鍵盤四碼直輸）、**時間快捷與週期列**、**彈窗液態分頁列**（`LiquidTabBar`，單品彈窗支援「清點提醒」與「自動復原」獨立排程，啟用自動復原之**項目卡片**於列表僅以藍色外框與**項目狀態指示條**標示）；項目啟用提醒時**項目狀態窗・提醒面**鈴鐺改為實心同色且**項目提醒時間**置於狀態窗左側（色調對齊**項目編輯鈕**），**資訊卡提醒鈕**於清單提醒啟用時維持藍色高亮、無清單提醒但清單內有項目啟用提醒時自動轉為實心鈴鐺；支援長按**項目狀態窗・提醒面**快設 15 分鐘／略過一次／取消循環，以及靜默將項目定時恢復為待備之自動復原機制。
- [x] **雙軌色彩與主題引擎**：內建專為 HyperOS / 非標準 Monet 系統調校的高對比亮/暗色盤、**頂部標題列**整合**頂欄品牌標題**、**頂欄取色鈕**（Material 3 Dynamic Color 切換）與**頂欄主題鈕**（圓形波紋亮暗主題切換）、`ThemeConfig?` 冷啟動零閃爍初始化，以及**啟動骨架屏**（`TotalCheckSkeletonShimmer`）。
- [x] **桌面 Widget（M8+，原生 Direct RemoteViews 雙形態自適應桌面小工具群）**：
  - **網格桌面小工具（`TotalCheckWidgetReceiver` / `widget_info_checklist_grid.xml`，`2x2` 以上）**：鎖定 `minWidth="140dp"` / `minHeight="110dp"`，依據宿主寬高自適應三級版型（`Compact` 1 欄、`Standard` 2 欄、`Expanded` 雙層頂欄 + 3 欄），於雙槽位 `<ViewFlipper>` 內以 `<ListView>` 承載純 `<TextView>` Bento 膠囊列（API 31+ 使用 `RemoteCollectionItems` 搭配 `hasStableIds = true` 與 `PendingIntentTemplate` + `FillInIntent`；API 26–30 回退至 `TotalCheckWidgetViewsService`），同時保留 `260ms` 水波紋綻放延遲、`↺ ⇄ 確認?` 兩段式防誤觸重置、鎖定寬度之 `尚缺 X` 篩選 `<ViewFlipper>`、全部備齊完成徽章、`WidgetSyncManager` 跨進程即時同步與點擊標題/進度列帶 `EXTRA_WIDGET_CHECKLIST_ID` 跳轉定位主畫面分頁。
  - **計數條桌面小工具（`TotalCheckCounterWidgetReceiver` / `widget_info_counter_bar.xml`，`2x1` ~ `4x1`）**：嚴格遵守 Android 1 格高（`40dp`~`85dp`）水平縮放限制；左側內容區於 `2x1 ~ 4x1` 全尺寸常駐「上層（標題 + 右側精簡計數 `尚缺 X` / `✓ checked/total`）+ 下層圓角 `ProgressBar`」雙層結構，搭配雙槽位 `<ViewFlipper>` 180ms 淡入縮放過渡；右側操作區於 `2x1`（`< 220dp`）僅顯示 `< >` 切換，寬度 `>= 220dp`（`3x1` / `4x1`）時自動展開兩段式 `↺ ⇄ 確認?` 重置鈕，與**網格桌面小工具**共用 `WidgetSyncManager` 即時雙向同步。
  - **動態預覽與動態色彩**：兩款小工具皆具備三語系命名描述、Android 12–16/17 純靜態高擬真 `previewLayout`（`widget_preview_checklist_grid.xml`、`widget_preview_counter_bar.xml`）、API < 31 風格 C 向量預覽圖（`*_fallback.xml`）及系統日夜模式動態色票（`values-v31` / `values-night-v31`）。

### 擴充功能（M7+ 迭代，底層欄位已預埋）
- [x] **讀取格式檔案與配置字串快速載入列表（M7）**：內建高容錯雙模式解析引擎（`ChecklistTextParser`：支援極簡單行逗號／頓號／空白／直線分隔字串與多行 Markdown / 表格 / CSV / TSV / JSON 陣列／純文字清單），整合於**新增清單彈窗**之**彈窗液態分頁列**（`LiquidTabBar`）、AI 提示詞複製卡、剪貼簿貼上與 `.txt`/`.md`/`.csv` 本地檔案讀取，並於匯入前提供即時互動預覽（修改標題、切換待備/已備狀態、剔除單項）與 Sticky Header/Footer 防鍵盤遮擋排版。
- [x] **全域／清單雙層標籤系統、長按快切與資訊卡設定面板（M9-Part4 & Part5）**：已完整上線全域（實心圖示，預設「重要」五角星）與清單專屬（空心圖示，單清單隔離）雙層標籤管理、長按**清單資訊卡**平滑 `1x ⇄ 2x` 展開並 1:1 向下推移**項目堆疊列表**之**資訊卡設定面板**（頂欄**設定面板分頁列**含「全域／清單／封存」三頁，下方對應**設定面板標籤管理區**與**設定面板封存區**）、全狀態 3D 翻轉**項目狀態窗・標籤面**（短按開啟**項目標籤彈窗**、長按快速切換該清單上一個添加的標籤）、以及長按**資訊卡篩選鈕**開啟**標籤篩選彈窗**並與「只看未帶」疊加。後續可擴充資訊卡版面配置（**資訊卡編輯鈕**長按入口）與分類（`category`）／優先級（`priority`）篩選。
- [ ] **清單匯出備份**：支援 CSV / Markdown / 純文字格式匯出與分享（未來可結合 `item_status_logs` 稽核歷程）。

---

## 3. 資料結構與架構規範（Room `version = 4`，無損 Migration）

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
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("checklistId")]
)
data class CheckItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val label: String,
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,               // 自訂排序權重
    val category: String = "",            // 預埋：分類擴充
    val iconKey: String = "default",      // 預埋：Widget 圖標映射
    val isArchived: Boolean = false,      // M9-Part3: 是否封存（封存後不進入主清單與 Widget）
    val createdAt: Long = System.currentTimeMillis(), // M9-Part3: 建立時間戳
    val updatedAt: Long = System.currentTimeMillis(), // M9-Part3: 最後異動時間戳
    val lastCheckedAt: Long? = null,      // M9-Part3: 最近一次勾選完成時間戳
    val archivedAt: Long? = null,         // M9-Part3: 封存時間戳
    val tags: String = "",                // M9-Part3: 逗號分隔標籤（供 ChecklistFilterState 篩選）
    val note: String = "",                // M9-Part3: 預埋備註文字
    val quantity: Int = 1,                // M9-Part3: 預埋數量
    val priority: Int = 0                 // M9-Part3: 預埋優先級
)

// 3. 排程與自動復原資料表 (reminders)
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(entity = ChecklistEntity::class, parentColumns = ["id"], childColumns = ["checklistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CheckItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("checklistId"), Index("itemId")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val itemId: Long? = null,
    val hour: Int,
    val minute: Int,
    val repeatDaysBitmask: Int = 0,       // 0 = 單次，Bit 0..6 對應週一至週日
    val triggerEpochMillis: Long = 0L,
    val onlyIfUnchecked: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduleKind: String = KIND_REMINDER // "REMINDER" | "AUTO_RESTORE"
)

// 4. 項目狀態異動紀錄表 (item_status_logs)
@Entity(
    tableName = "item_status_logs",
    foreignKeys = [
        ForeignKey(entity = CheckItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ChecklistEntity::class, parentColumns = ["id"], childColumns = ["checklistId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("itemId"), Index("checklistId"), Index("occurredAt")]
)
data class ItemStatusLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val checklistId: Long,
    val actionType: String,               // CREATED / CHECKED / UNCHECKED / RENAMED / ARCHIVED / UNARCHIVED / AUTO_RESTORED / RESET / SCHEDULE_UPDATED
    val previousValue: String = "",
    val newValue: String = "",
    val occurredAt: Long = System.currentTimeMillis()
)
```

### 3.2 預設查詢排序與過濾邏輯 (DAO & FilterState)

* 查詢特定清單主列表項目時，排除 `isArchived = 1`，並強制使用 `ORDER BY sortOrder ASC, id DESC`；已封存項目另行查詢並於**資訊卡設定面板**之**設定面板封存區**（及備用**獨立封存彈窗**）檢索還原或永久刪除。
* **維持原始順序**：點擊**項目卡片**勾選時保持原位不動，僅進行原地狀態轉場（背景色、透明度 `0.45f`、**項目名稱**刪除線與 `64.dp` **項目狀態窗・提醒面**「待備 / 已備」過渡），避免連點清點時項目跳動造成誤觸。
* **可擴充篩選狀態（`ChecklistFilterState`）**：**清單資訊卡**操作區採統一 `40.dp` 純圖示圓鈕配置；**資訊卡篩選鈕**短按直接切換 `onlyUnchecked`（平滑隱藏 `isChecked = true` 項目），長按開啟**標籤篩選彈窗**（合併顯示跨清單**全域標籤〔實心圖示〕**與僅作用於當前清單的**清單標籤〔空心圖示〕**）設定 `selectedTags` 與 `selectedTagChecklistId` 並與 `onlyUnchecked` 疊加，且以對應實心／空心標籤圖示動態替換**資訊卡篩選鈕**圖示；切換至其他清單時自動清除跨清單無效之清單專屬標籤篩選（全域標籤篩選維持跨清單有效）；底層結構同步預留 `selectedCategory` 與 `minPriority` 供後續分類篩選擴充。



### 3.3 主程式與 Widget 解耦機制

* `AppDatabase` 必須採用 Double-Checked Locking Singleton 模式，確保未來主程式與 Widget 跨進程讀寫不發生 Lock 衝突。
* `UserPreferencesRepository` 與 `WidgetSyncManager` 以 `appWidgetId` 為主鍵隔離每個桌面小工具實例的狀態（`widget_selected_checklist_$appWidgetId`、`widget_show_unchecked_$appWidgetId`、`widget_reset_confirm_until_$appWidgetId`），所有 `PendingIntent` 均綁定專屬 `appWidgetId`、`requestCode` 與 URI，並於 `onDeleted` 清理已刪除實例偏好。切換清單、篩選或第一段重置僅透過 `DirectWidgetRenderer.renderSingleWidgetById()` 更新單一目標實例；在 `ChecklistRepository` 每次完成寫入（新增、勾選、刪除、重置）後，統一呼叫 `WidgetSyncManager.notifyDataChanged(context)` 透過 `DirectWidgetRenderer.renderAll()` 同步所有**網格桌面小工具**與**計數條桌面小工具**並保留各自獨立的面板狀態。App 切換 `ThemeConfig(isDarkMode, useDynamicColor)` 時同步呼叫 `WidgetSyncManager.notifyThemeChanged(context, themeConfig)`，由 `WidgetThemePalette.kt` 映射為原生 `<ripple>` Drawable 與多槽位 `ProgressBar`（嚴禁呼叫 `setColorStateList` 反射），使桌面 Widget 與 App 100% 同步雙軌色彩。而在桌面 Widget 點擊項目（`CMD_TOGGLE_ITEM`）時，由 `WidgetSyncManager.tryHandleFastCommand()` 採記憶體樂觀快取與 `toggleGeneration` 合併並給予 `260ms`（`TOGGLE_RIPPLE_DELAY_MS`）讓 `<ripple>` 水波紋完整綻放後，執行單一全量 `updateAppWidget`（於不觸發 `ViewFlipper.setDisplayedChild` 的前提下以 `hasStableIds = true` 原地更新 `<ListView>` 項目，維持捲動位置且零跳動）；點擊列表同行未填滿空槽位或底部空白列（`CMD_OPEN_APP`）則依 `<ListView>` 原生 Touch Slop 僅於輕點時進入 App，避免與垂直滑動衝突。由 Widget 點擊標題列／進度區／空白處啟動主程式時，透過 `MainActivity.EXTRA_WIDGET_CHECKLIST_ID` 與 `MainViewModel.selectAndScrollToChecklist()` 精準定位至對應清單；若透過 Widget `+ 新增` 或空清單入口啟動，額外夾帶 `MainActivity.EXTRA_AUTO_FOCUS_INPUT` 以在切頁後自動展開**底部快速輸入列**（`QuickInputBar`）並彈出軟鍵盤。

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
| **1. 亮暗主題切換（頂欄主題鈕）** | **Radial Theme Transition**<br><br>(多波次圓形主題切換) | 按壓**頂欄主題鈕**預擷取 `0.6f` 降採樣離屏快照（`LocalGraphicsContext` + `triggerMutex` 防抖；僅於轉場與預擷取期間錄製 `liveLayer`），點擊時以真實觸控座標 `(x, y)` 為圓心透過 `clipPath` 多波次 `RevealWave` 圓形遮罩擴散（`400ms`）揭示新主題圖層，不縮放**主畫面根容器**內容。 |
| **2. 清單頁籤列切換與重排** | **Gesture-Driven Pager + Expanding Checklist Tab Bar**<br><br>(零重組跟手分頁與頁籤拖拽讓位) | 頂部**清單頁籤列**（`ExpandingChecklistTabBar`）與 `HorizontalPager`（`beyondViewportPageCount = 1`）透過 `snapshotFlow` 雙向綁定；**頁籤新增鈕**常駐固定於最右側，左側可水平捲動之**清單頁籤**於向右仍有未顯示項目時施加 `32.dp` 尾部 `BlendMode.DstIn` 漸層淡化遮罩（懸浮拖曳層保持清晰不被裁切）。選中**清單頁籤**放大至 `1.08f` 並平滑推開相鄰頁籤，雙擊已選中頁籤原地展開**頁籤改名輸入框**，遠距點擊透過 `DistantTabTransition` 預熱鄰頁再平順滑動（`320ms`）；長按頁籤可懸浮拖曳重排（`1.06f`）並由 `TabReflowAnim` 彈簧補償讓位，清單改名／刪除亦整合於**資訊卡編輯鈕**、**資訊卡刪除鈕**與**底部提示條**復原。 |
| **3. 資訊卡進度條推進** | **Spring Stepper Progress**<br><br>(步驟條過衝回彈) | 位於**清單資訊卡**內的**資訊卡進度摘要**（`尚缺 X 項 / 共 Y 項`）與**資訊卡進度條**。當勾選項目使進度增加時，進度條先微幅過衝（Overshoot，`dampingRatio = 0.65f`）再回彈穩定至目標比例。 |
| **4. 單項勾選、狀態窗翻轉、提醒呈現與自動復原標示** | **Press Scale + 3D Capsule Icon Tag Flip & Quick-Toggle**<br><br>(實心同色鈴鐺、左側中性時間、實心全域／空心清單圖示、長按快切與藍色自動復原) | 點擊**項目卡片**整列任意處即可勾選。按壓瞬間卡片縮放至 `0.97f`，放開回彈 `1.0f` 並觸發震動；`64.dp` **項目狀態窗・提醒面**在「待備」狀態時點擊開啟**提醒排程彈窗**（含**彈窗液態分頁列**「清點提醒／自動復原」、**3D 時間滾輪**〔5 倍速快滑閾值 `950 dp/s`〕與**時間快捷與週期列**），在「已備」狀態時點擊維持切換勾選；啟用項目提醒時，**項目狀態窗・提醒面**鈴鐺改為**實心且與「待備」文字同色（`onTertiaryContainer`）**，**項目提醒時間**以單行等寬數字移至**狀態窗左側**並採用與**項目編輯鈕**一致之中性色調（`onSurfaceVariant`，支援點擊編輯與長按略過／取消）；**資訊卡提醒鈕**於清單提醒啟用時維持藍色高亮，若無清單提醒但清單內有未封存項目啟用提醒時維持中性底色並自動顯示**實心鈴鐺**。長按**項目狀態窗・提醒面**依序循環「快設 15 分鐘 → 週期提醒略過一次（顯示『已略過』）→ 關閉提醒」。無論項目是否已設標籤或已勾選，水平左右滑動狀態窗皆可以 3D `rotationY` 翻轉進入**項目狀態窗・標籤面**（底色與前景圖示統一採用與**項目編輯鈕**一致之 `surfaceContainerHighest` / `onSurfaceVariant` 色調；0 標籤顯示空心標籤輪廓、1 標籤置中單圖示、2 標籤以 `1.dp × 12.dp` 垂直細線分隔，並以**實心圖示**區分全域標籤、**空心圖示**區分清單專屬標籤）。在**項目狀態窗・標籤面**短按開啟合併列表之**項目標籤彈窗**、左右滑動翻回**項目狀態窗・提醒面**；**長按**則即時切換（未套用則添加、已套用則移除）該清單最近一次於**項目標籤彈窗**新增的標籤（若尚無紀錄則預設套用全域「重要」實心五角星標籤；若項目已滿 2 個標籤則不覆蓋並顯示**底部提示條**提示）。若項目已啟用「自動復原」，主列表中**僅將項目卡片外框與項目狀態指示條轉為藍色（`primary`）**，不額外疊加徽章。 |
| **5. 項目堆疊列表捲動與沉厚觸覺回饋** | **Stacked Card Scroll Layout + Layered Haptics**<br><br>(深度堆疊、跨項沉厚震動、邊界回彈震動與過捲動手風琴) | 以自訂 `Layout`（**項目堆疊列表**，`StackedCardScrollLayout`）取代傳統 `LazyColumn`：接近視窗頂/底緣的**項目卡片**自動壓縮為最多 3 層（`MAX_STACK_DEPTH = 3f`）的立體堆疊（每層縮放步階 `0.04f` 並依深度暗化遮罩），點擊壓縮卡片可自動展開捲入視野（`scrollToExpandItem`）；手動上下捲動跨越每個項目步距時由 `TactileHaptics` 觸發較**3D 時間滾輪**更沉厚的低頻震動（API 30+ 優先 `PRIMITIVE_LOW_TICK` 搭配 `32ms` 節流，API 26–29 降級 `18ms` 單發脈衝），且滑抵頂／底邊界拉出過捲動（`>= 14dp`）或甩動撞牆時額外觸發飽滿邊界回饋（優先 `PRIMITIVE_THUD`，降級 `26ms` 脈衝；程式自動捲動不誤觸）；項目增刪／排序異動觸發 `ItemReflowAnim`、勾選／改名觸發 `NeighborRippleWave` 鄰近波紋擴散；滑抵頂底邊界時呈現手風琴式過捲動拉伸回彈（Overscroll Accordion）。 |
| **6. 資訊卡重置鈕一鍵重置** | **Staggered Bulk Selection**<br><br>(錯峰批量狀態更新) | 點擊**資訊卡重置鈕**時，DB 狀態立即歸零，列表項目的取消勾選視覺與脈衝放大由第一項往下依序錯峰（`20ms + idx * 35ms`，上限 `560ms`，以 `resetTriggeredAtMillis` 對齊時序）執行，同時**底部提示條**提供 `復原` 操作。 |
| **7. 項目長按刪除環** | **Hold-to-Delete Perimeter Ring**<br><br>(加速連刪、主題同步色環與狀態切換即時撤銷) | 按住**項目卡片** `120ms` 後觸發長按震動回饋，沿 `24.dp` 膠囊外框順時針繪製填滿**資訊卡刪除鈕**同源配色（`errorContainer.copy(alpha = 0.72f)`）的**項目長按刪除環**（首刪 `800ms` 線性填滿；4 秒內連續刪除每筆遞減 `100ms`，最低 `450ms`），填滿後卡片縮放淡出（`160ms`）並執行刪除與**底部提示條**復原；若長按中途放開、滑出、進入編輯或觸發項目狀態切換（勾選/重置/子按鈕點擊），立即中斷長按並於 `300ms` 內將進度環退回至 `0f`。 |
| **8. 項目拖曳手把排序與左滑置底** | **Drag-to-Reorder & Swipe-Left Move-to-Bottom**<br><br>(手把拖曳重排與左滑置底接口) | 拖曳**項目拖曳手把**時，**項目卡片**升起進入懸浮層（縮放 `1.03f`、陰影 `10.dp`、`primary` 邊框），原位顯示**項目拖曳佔位槽**（`DashedDragPlaceholderSlot`）；拖曳至清單上下緣自動捲動。若按住**項目拖曳手把**橫向**向左滑動超過 `56.dp`** 後放手，則自動將該項目移至清單底部並預留 `DragSwipeBottomTransitionSpec` 過渡動畫接口。 |
| **9. 底部快速輸入列與行內編輯（IME 同步收合／原位零位移退出）** | **Seamless Focus Input & IME-Synced Zero-Jump Inline Edit**<br><br>(鍵盤與資訊卡 1:1 同步收合及編輯後原位歸位) | **底部快速輸入列**隨 `WindowInsets.ime` 平滑升降，送出後清空並自動上滑至頂部；列表向下捲動時自動平滑收合。點擊**項目編輯鈕**進入行內編輯時，右側切換為**項目編輯工具列** `[封存 32dp] [確認 ✓ 32dp] [取消 ✗ 34dp]`，確保確認鍵與原本**項目編輯鈕**位置重合；**清單資訊卡**收合／展開進度直接由動態校準之軟鍵盤高度 `imeExtra / observedImeHeight` 1:1 逐幀驅動（並於外接實體鍵盤無軟鍵盤時保留備援收合），與鍵盤上滑／下滑速度完全同步；進入編輯僅於項目會遭鍵盤遮擋時微幅捲動，結束編輯保留原捲動位置（並於必要時平滑復原 `preEditScrollOffset`），僅封存移除時啟用鄰居錨點交接，徹底杜絕中間項目編輯完成後位移至頂部的問題。 |
| **10. 啟動骨架屏** | **Startup Skeleton Shimmer** | 冷啟動等待 DataStore / Room 首幀資料期間（最少 `700ms`），以 `withFrameNanos` 驅動的微光掃描**啟動骨架屏**（`TotalCheckSkeletonShimmer`）銜接，消除載入跳動。 |
| **11. 資訊卡設定面板平滑推擠與彈窗遮罩宿主** | **Inline 2x Push-Down Settings Panel & Scoped Modal** | 全域按鈕與 Chip 採用 `SlideOutCancelModifier`（按下下沉 `0.94f` / `1.5.dp`，手指滑離 `44.dp` 內依曲線衰減並取消觸發，並附帶鍵盤焦點外框）；長按**清單資訊卡**平滑展開 `1x ⇄ 2x` **資訊卡設定面板**（`260ms FastOutSlowInEasing` 動畫高度，並透過可觀察之 `progressCardHeightState` 逐幀更新**項目堆疊列表**之 `topInsetPxProvider`，於零重組 Placement 階段將下方**項目卡片** 1:1 平滑向下推移）；頂欄**設定面板分頁列**並列「全域（實心圖示）」、「清單（空心圖示）」與「封存」三頁及關閉鈕，下方切換**設定面板標籤管理區**（全域標籤預設內建「重要（實心五角星）」，清單標籤嚴格隔離於所屬清單內生效）與**設定面板封存區**；**新增清單彈窗**／**清單改名彈窗**／**清單刪除彈窗**／**提醒排程彈窗**／**項目標籤彈窗**／**標籤篩選彈窗**皆由**彈窗遮罩宿主**跨會話記憶軟鍵盤高度，首幀即精準定錨於鍵盤上方零二次跳動且凍結背景 IME。 |
| **12. 後續擴充預留** | **Card Layout & Multi-Tag Expansion** | 依託 `TagCatalog`（全域／清單分層）、**資訊卡編輯鈕**長按接口（`onLongClickRenameChecklist`）、`docs/COMPONENTS.md` 49 項正式命名基準與 `ChecklistFilterState` / `CheckItemEntity` 擴充欄位，後續可直接擴充**清單資訊卡**版面自訂、多標籤複合篩選列與狀態歷程統計檢視。 |

---

## 6. 編譯環境與安全規範

* **SDK 版本**：`minSdk = 26` / `targetSdk = 36`（相容 Android 17 / API 37 實機測試，主畫面強制啟用 `enableEdgeToEdge()` 並正確處理 `WindowInsets` 避免遮擋）。
* **簽名保護**：Release 簽名資訊獨立存放於根目錄 `keystore.properties`，由 `app/build.gradle.kts` 動態讀取；`*.jks` 與 `keystore.properties` 嚴禁納入 Git 追蹤。
