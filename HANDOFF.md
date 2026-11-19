# TotalCheck — 專案交接指南 (`HANDOFF.md`)

> **用途**：本文件專為協助下一輪 **Google AI Studio Vibe-Coding** 對話視窗無縫接手 **TotalCheck** 而撰寫。
> 新對話啟動時，請先完整閱讀本文件與 [`docs/COMPONENTS.md`](docs/COMPONENTS.md)，即可精準掌握架構邊界、49 項正式 UI 元件稱呼、高階物理動效實作細節與踩坑紅線，直接切入 **M10+** 開發。

---

## 1. 專案快照與當前狀態

| 屬性 | 現行設定 / 狀態 |
| --- | --- |
| **App 名稱** | TotalCheck（繁體中文介面顯示：**隨身清點板**） |
| **定位** | 100% 純離線、極速清點、高觸感物理動效之單頁工具檢查板 Android App |
| **Package / Application ID** | `com.example.totalcheck`（**嚴禁變更**） |
| **版本號** | `versionCode = 10`、`versionName = "0.21.0"` |
| **SDK / Toolchain** | `minSdk = 26`、`targetSdk = 36`、`compileSdk = 36`、JVM 17、Kotlin + Jetpack Compose (M3) |
| **資料庫版本** | Room `AppDatabase` `version = 4`（含無損遷移 `MIGRATION_1_2`、`MIGRATION_2_3`、`MIGRATION_3_4`） |
| **里程碑進度** | **M0 ～ M9（含 Part 1 ～ Part 5 第 1～30 項）全數完成**，編譯驗證通過，無待修遺留 Bug |
| **語系與文案狀態** | `values/strings.xml`、`values-zh/strings.xml`、`values-b+zh+Hant/strings.xml` 100% 同步（224 項鍵值，全為台灣繁體中文，零簡體字） |

---

## 2. 接手必讀文件與閱讀順序

新 AI Agent 接手後，請依下列順序快速校準上下文：
1. **`HANDOFF.md`（本文件）**：掌握架構全景、紅線約束、踩坑避雷與 M10+ 擴充接口。
2. **[`docs/COMPONENTS.md`](docs/COMPONENTS.md)**：熟記已核對定案之 **49 項正式 UI 元件名稱**（`UI-01` ～ `UI-49`）與對應 Composable / `testTag`。
3. **[`SPEC.md`](SPEC.md)**：查閱完整功能規格、Room v4 Schema、Widget 解耦機制與 12 條進階互動動效標準。
4. **[`Milestone.md`](Milestone.md)**：回溯 M0～M9 設計演進脈絡，避免覆寫使用者先前精心調校的體感細節。
5. **[`AGENT.md`](AGENT.md)**：確認開發硬性約束與本機／雲端建置差異。

---

## 3. UI 正式命名階層地圖（唯一溝通基準）

> **強制規則**：與使用者溝通、撰寫規劃書（`implementation_plan.md`）或更新文件時，**一律使用 [`docs/COMPONENTS.md`](docs/COMPONENTS.md) 內之 49 項正式名稱**。

```text
主畫面根容器 (TotalCheckScreen) ── 冷啟動過渡：啟動骨架屏 (TotalCheckSkeletonShimmer)
├── 頂部標題列 (TopAppHeaderBar)
│   ├── 頂欄品牌標題
│   ├── 頂欄取色鈕
│   └── 頂欄主題鈕
├── 清單頁籤列 (ExpandingChecklistTabBar)
│   ├── 清單頁籤 (ChecklistTagPill) ── 雙擊展開 → 頁籤改名輸入框 (InlineEditingChecklistTag)
│   └── 頁籤新增鈕 (+) ── 點擊開啟 → 新增清單彈窗
├── 清單資訊卡 (CapsuleProgressModule) ── 長按 350ms 翻倍展開 ↓
│   ├── 一般模式：資訊卡進度摘要 / 資訊卡進度條 / 資訊卡重置鈕
│   │            資訊卡篩選鈕（短按只看未帶、長按開「標籤篩選彈窗」）
│   │            資訊卡提醒鈕 / 資訊卡編輯鈕（長按預留版面配置） / 資訊卡刪除鈕
│   └── 資訊卡設定面板 (InfoCardSettingsPanelContent)（展開時 1:1 向下推移項目堆疊列表）
│       ├── 設定面板分頁列（全域實心 / 清單空心 / 封存 / 關閉 ✕）
│       ├── 設定面板標籤管理區
│       └── 設定面板封存區
├── 項目堆疊列表 (BidirectionalStackedCardList)
│   ├── 項目卡片 (CapsuleCheckItemRow) ── 長按外框進度：項目長按刪除環
│   │   ├── 項目狀態指示條（自動復原啟用時與外框同步轉藍）
│   │   ├── 項目名稱
│   │   ├── 項目提醒時間（位於狀態窗左側，與項目編輯鈕同色）
│   │   ├── 項目狀態窗・提醒面 ⇄（左右滑動 3D 翻轉）⇄ 項目狀態窗・標籤面（短按開「項目標籤彈窗」、長按快切上一個標籤）
│   │   ├── 項目編輯鈕（鉛筆）── 點擊切換右側為 → 項目編輯工具列 [封存][✓][✗]
│   │   └── 項目拖曳手把（上下拖曳排序 / 左滑 > 56dp 移至底部）
│   └── 項目拖曳佔位槽 (DashedDragPlaceholderSlot)
├── 底部快速輸入列 (QuickInputBar)
├── 底部提示條 (SnackbarHost)
└── 彈窗遮罩宿主 (ScopedModalHost) ── 內含：彈窗液態分頁列 (LiquidTabBar)
    ├── 新增清單彈窗 / 清單改名彈窗 / 清單刪除彈窗
    ├── 提醒排程彈窗（內含：3D 時間滾輪、時間快捷與週期列）
    ├── 項目標籤彈窗 / 標籤篩選彈窗
    └── 獨立封存彈窗（獨立備用版）

桌面小工具 (Native RemoteViews / DirectWidgetRenderer)
├── 網格桌面小工具 (2x2+, TotalCheckWidgetReceiver)
└── 計數條桌面小工具 (2x1~4x1, TotalCheckCounterWidgetReceiver)
```

---

## 4. 程式碼架構地圖（40 支 Kotlin 原始檔）

```text
app/src/main/kotlin/com/example/totalcheck/
├── MainActivity.kt                        # ThemeConfig? 延遲首幀零閃爍、enableEdgeToEdge、SystemBarStyle、RadialThemeTransitionContainer、Widget/通知 Intent 導頁
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt                 # Room v4 Double-Checked Locking Singleton + MIGRATION_1_2/2_3/3_4
│   │   ├── ChecklistEntity.kt             # checklists 表
│   │   ├── CheckItemEntity.kt             # check_items 表（含 isArchived, tags, 時間戳與預埋 category/iconKey/note/quantity/priority）
│   │   ├── ReminderEntity.kt              # reminders 表（scheduleKind = "REMINDER" | "AUTO_RESTORE"）
│   │   ├── ItemStatusLogEntity.kt         # item_status_logs 異動紀錄表
│   │   └── ChecklistDao.kt / ReminderDao.kt / ItemStatusLogDao.kt
│   ├── parser/
│   │   └── ChecklistTextParser.kt         # 單行分隔／Markdown／表格／CSV／TSV／JSON 雙模式容錯解析引擎
│   ├── preferences/
│   │   └── UserPreferencesRepository.kt   # DataStore：ThemeConfig、按 appWidgetId 隔離之 Widget 狀態、全域標籤池 (tag_pool_json)、清單專屬標籤池 (checklist_tag_pools_json)
│   ├── reminder/
│   │   ├── ReminderScheduler.kt           # AlarmManager.setExactAndAllowWhileIdle 排程與單次／週期推算
│   │   ├── ReminderFormatter.kt           # 提醒時間／星期／已略過文字格式化
│   │   ├── NotificationHelper.kt          # 通知頻道、有缺才響推播與 Direct Actions
│   │   ├── ReminderBroadcastReceiver.kt   # 鬧鐘觸發、延後 15 分、一鍵標記已備與 AUTO_RESTORE 靜默復原
│   │   └── ReminderBootReceiver.kt        # 開機／改時間／權限變更重掛鬧鐘
│   ├── repository/
│   │   └── ChecklistRepository.kt         # 單一真相資料層：封裝 Room 寫入、狀態紀錄、排程連動與 WidgetSyncManager 同步
│   └── widget/
│       ├── DirectWidgetRenderer.kt        # 原生 RemoteViews 直出渲染引擎（嚴禁 Glance）
│       ├── WidgetSyncManager.kt           # 跨進程快取、260ms 水波紋延遲合併與單一／全量刷新
│       ├── WidgetThemePalette.kt          # App 雙軌色彩（4 象限）映射到原生 <ripple> Drawable（嚴禁反射）
│       ├── TotalCheckWidgetReceiver.kt    # 網格桌面小工具廣播接收器
│       ├── TotalCheckCounterWidgetReceiver.kt # 計數條桌面小工具廣播接收器
│       └── TotalCheckWidgetViewsService.kt    # API 26–30 ListView Adapter 回退
└── ui/
    ├── main/
    │   ├── MainViewModel.kt               # 單一 StateFlow 狀態樹、Undo 快照、雙層標籤管理、lastAddedTagTokenByChecklist 記憶、封存與篩選
    │   ├── TotalCheckScreen.kt            # 主畫面根容器、頂部標題列、HorizontalPager、IME 校準與彈窗路由
    │   ├── CapsuleComponents.kt           # 清單資訊卡、資訊卡設定面板、項目卡片、狀態窗雙面 3D 翻轉、底部快速輸入列
    │   ├── StackedCardScrollLayout.kt     # 自訂 Layout 項目堆疊列表（頂底 3 層堆疊、手風琴過捲動、進度卡高度 1:1 推移、沉厚觸覺、拖曳排序）
    │   ├── ExpandingChecklistTabBar.kt    # 清單頁籤列、清單頁籤拖曳排序、雙擊頁籤改名輸入框與遠距預熱動畫
    │   ├── ScopedModalOverlay.kt          # 彈窗遮罩宿主（記憶軟鍵盤高度首幀零跳動與背景 IME 凍結）
    │   ├── ReminderConfigContent.kt       # 提醒排程彈窗、3D 時間滾輪（快滑門檻 950 dp/s）與時間快捷與週期列
    │   ├── LiquidTabBar.kt                # 彈窗液態分頁列
    │   ├── TagCatalog.kt                  # 12 組實心（全域）/ 空心（清單）圖示映射、預設「重要」五角星與 `#list:` Token 編解碼
    │   ├── ChecklistFilterState.kt        # 可擴充篩選狀態模型（onlyUnchecked / selectedTags / selectedTagChecklistId / 預留 category & minPriority）
    │   ├── TactileHaptics.kt              # 零配置硬體觸覺封裝（PRIMITIVE_LOW_TICK / PRIMITIVE_THUD 與 API 26–29 降級）
    │   ├── ItemMoveToBottomTransition.kt  # 項目拖曳手把左滑置底過渡動畫預留接口（DragSwipeBottomTransitionSpec）
    │   ├── SlideOutCancelModifier.kt      # 全域按鈕 0.94f 下沉與 44dp 滑出取消修飾符
    │   └── SkeletonShimmer.kt             # 啟動骨架屏
    └── theme/
        ├── Color.kt / Theme.kt / Type.kt  # 雙軌精調色盤 + Material You Dynamic Color
        └── RadialThemeTransitionContainer.kt # 0.6f 降採樣多波次圓形主題擴散轉場
```

---

## 5. 絕對不可違反的紅線與踩坑筆記（Architecture Invariants & Pitfalls）

### 5.1 權限、隱私與建置安全
1. **100% 純離線**：`AndroidManifest.xml` **嚴禁**宣告 `android.permission.INTERNET` 或引入任何網路套件。僅允許 `POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、`USE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`、`VIBRATE`。
2. **禁止動到簽名與 Keystore**：絕對不可修改 `debug.keystore`、`debug.keystore.base64` 或 `app/build.gradle.kts` 中的 `signingConfigs` / `buildTypes` 簽名掛載。
3. **Room 嚴禁破壞性升級**：目前 `version = 4`。任何新增欄位或資料表都必須寫明確的 `Migration`（如 `MIGRATION_4_5`），**嚴禁**使用 `fallbackToDestructiveMigration()`。
4. **桌面小工具嚴禁 Glance 與反射**：`data/widget/` 採原生 `RemoteViews`（`DirectWidgetRenderer` + `WidgetThemePalette`），嚴禁混入 Jetpack Glance 或呼叫 `setColorStateList` 反射。

### 5.2 關鍵 UI 物理與零跳動不變量（極易踩坑！）
1. **行內編輯與軟鍵盤同步（Item 20 血淚修復）**：
   - **清單資訊卡**的收縮／展開進度直接由 `imeExtra / observedImeHeight` 1:1 逐幀驅動（實體鍵盤無軟鍵盤時保留 `220ms` 備援）。
   - `openSettingsChecklistIds` 或 `isTabInlineRenameActive` 開啟時，必須凍結資訊卡收合邏輯，避免編輯**設定面板標籤管理區**或**頁籤改名輸入框**時卡片意外塌縮。
   - **一般結束編輯絕不可呼叫 `beginExitAnchorLock`**（會把中間項目錯誤頂到最上方！）；僅在編輯過程中執行「封存」移除該項目時才啟用鄰居錨點交接，一般結束編輯應平滑恢復 `preEditScrollOffset`。
2. **資訊卡設定面板 1:1 向下推移項目（Item 27）**：
   - `ChecklistPageContent` 的 `progressCardHeightState` 是可觀察的 `MutableFloatState`，由**清單資訊卡**量測更新並傳給**項目堆疊列表**的 `topInsetPxProvider`，於零重組 Placement 階段平滑推移**項目卡片**。切勿改回一般非 State 變數或改為觸發整頁重組。
3. **雙層標籤 Token 與圖示規範（Items 24–26）**：
   - **全域標籤**：純字串 token（如 `"重要"`），預設內建實心五角星 `"重要"`，跨所有清單共用，全介面使用 **實心圖示（`Icons.Rounded.*`）**。
   - **清單標籤**：以 `"#list:<name>"` 編碼存入該清單專屬 DataStore 池，僅在該清單下生效；切換清單時若當前篩選為清單標籤須自動清除。全介面使用 **空心圖示（`Icons.Outlined.*`）**。
   - **項目標籤彈窗**與**標籤篩選彈窗**必須合併為單一列表，僅以實心／空心圖示區分，不另做分組標題。
   - 長按**項目狀態窗・標籤面**時快速切換（未套用則加入、已套用則移除）`lastAddedTagTokenByChecklist`；無紀錄時預設全域 `"重要"`，已滿 2 標籤時不覆蓋並以**底部提示條**提示。
4. **提醒視覺呈現分層（Item 29）**：
   - **項目卡片**：啟用提醒時，**項目狀態窗・提醒面**顯示實心且與「待備」文字同色（`onTertiaryContainer`）之鈴鐺，**項目提醒時間**固定顯示於狀態窗左側且採**項目編輯鈕**同色（`onSurfaceVariant`）。
   - **清單資訊卡**：清單本身啟用提醒時**資訊卡提醒鈕**維持藍色高亮；若清單未設提醒但清單內任一未封存項目啟用提醒，**資訊卡提醒鈕**維持中性底色並自動轉為實心鈴鐺。
   - 單品啟用「自動復原」時，列表僅將**項目卡片**外框與**項目狀態指示條**轉為藍色（`primary`），絕不額外疊加徽章。
5. **觸覺回饋與時鐘快滑閾值（Item 28）**：
   - **項目堆疊列表**上下捲動使用 `TactileHaptics` 預建之沉厚震動（跨項 `PRIMITIVE_LOW_TICK` + `32ms` 節流、邊界 `PRIMITIVE_THUD`），且程式自動捲動不可觸發。
   - **3D 時間滾輪**進入 5 倍速快滑的門檻鎖定於 `FAST_STEP_VELOCITY_THRESHOLD = 950.dp`（避免微調分鐘誤觸飛滑）。

### 5.3 三語系繁體中文同步與 AI Studio 路徑避雷
- 所有使用者可見文字都必須存在於 `strings.xml`，且**嚴禁硬編碼字串**。
- 三份字串檔（`app/src/main/res/values/strings.xml`、`values-zh/strings.xml`、`values-b+zh+Hant/strings.xml`）鍵值數量與內容必須 100% 一致，且一律採用**台灣繁體中文慣用語**（例如：設定面板、檔案、通知、擴充座、資訊卡，禁止簡體字或大陸用語）。
- **AI Studio 工具避雷**：部分檔案工具不支援含 `+` 字元的路徑（`values-b+zh+Hant/strings.xml`）。修改完 `values-zh/strings.xml` 後，請使用 `run_command` 搭配 `python3` 將其同步至 `values-b+zh+Hant/strings.xml` 並驗證三份 XML 鍵值集合完全一致。

---

## 6. 已預留之 M10+ 擴充接口與規劃方向

下列底層掛鉤與資料欄位均已在 M9 預先鋪好，可直接無痛展開 M10+ 迭代：

1. **清單資訊卡版面自訂**：`CapsuleProgressModule` 已預留**資訊卡編輯鈕**長按接口 `onLongClickRenameChecklist`（`TotalCheckScreen.kt`），可直接接上版面精簡／資訊密度切換或模組配置。
2. **項目左滑置底專屬物理過渡動畫**：`ItemMoveToBottomTransition.kt` 已定義 `DragSwipeBottomTransitionSpec` 介面，當使用者透過**項目拖曳手把**左滑超過 `56.dp` 觸發置底時，可直接注入自訂滑出／落底彈簧動效。
3. **進階篩選與多維度分類**：`CheckItemEntity` 已預埋 `category`、`iconKey`、`note`、`quantity`、`priority`；`ChecklistFilterState` 已預埋 `selectedCategory`、`minPriority` 與多標籤集合 `selectedTags: Set<String>`。
4. **清點歷程統計與匯出備份**：`item_status_logs`（`ItemStatusLogEntity` / `ItemStatusLogDao`）已在每次勾選、改名、重置、封存、自動復原時自動寫入事件，可直接擴充歷史統計視圖與 Markdown / CSV 匯出分享功能。

---

## 7. 給下一個 AI Studio 對話視窗的建議開場 Prompt

開啟新的 AI Studio 對話視窗後，您可以直接貼上以下開場白，讓新 Agent 瞬間對齊所有脈絡：

```text
請先閱讀專案根目錄的 HANDOFF.md 與 docs/COMPONENTS.md，確認 TotalCheck 的架構、開發紅線與 49 項正式 UI 元件名稱。
後續所有溝通、規劃與修改，請一律使用 docs/COMPONENTS.md 的「正式名稱」與台灣繁體中文。
閱讀完畢後請簡短回覆「已就緒」，接著我們開始進行 M10 的需求規劃與實作：
[在此填入您的下一階段需求]
```
