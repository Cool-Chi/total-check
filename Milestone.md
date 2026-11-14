# TotalCheck — 開發進度摘要 (Milestone.md)

> 本文件供新接手的 AI Agent 快速掌握專案當前實作狀態、架構邊界與後續待辦事項。

## 1. 專案概覽與技術棧
- **專案定位**：純本地離線工具清點板 Android App（`com.example.totalcheck`，嚴禁宣告 `INTERNET` 權限）。
- **環境與建置**：Kotlin + Jetpack Compose（Material 3）、Jetpack Glance `1.1.1`、KSP `2.3.6`、`minSdk = 26` / `targetSdk = 36` / `compileSdk = 36`。
- **資料與狀態**：Room `2.8.5`（`AppDatabase` 單例 + `ChecklistRepository`）、Preferences DataStore `1.3.0`（`UserPreferencesRepository`）、`MainViewModel`（`StateFlow`）。
- **核心設計**：風格 C（`24.dp` 觸感膠囊模組、大型進度數字、飽和色「待備 / ✓ 已備」標籤）、原地勾選狀態轉場（不隨勾選沉底）。

## 2. 已完成里程碑

| 版本標籤 | 里程碑 | 核心交付內容 |
|---|---|---|
| `v0.0.1` | 初始化 | 建立 `SPEC.md`、`AGENT.md`、`WORKFLOW.md` 與專案骨架 |
| `v0.0.2` ~ `v0.1.0` | **M0（UI 外殼定型）** | 鎖定「風格 C」觸感膠囊介面、原地狀態轉場、`0.97f` 按壓縮放與「只看未帶」過濾切換 |
| `v0.2.0` | **M1（資料持久化基底）** | 串接 Room（`checklists` / `check_items`）、DataStore、首次啟動種子資料與 `WidgetSyncManager` 解耦空殼 |
| `v0.3.0` | **M2-A（Edge-to-Edge 與雙軌色彩）** | `SystemBarStyle` 動態反轉、`Scaffold` insets 對齊、精調雙色盤（Dark `#0E1116` / Light `#F8FAFC` / Tertiary Amber）與 Dynamic Color 切換鈕 |
| `v0.4.0` | **M2-B（Radial Theme Transition）** | `RadialThemeTransitionContainer`（單一活體樹 + `GraphicsLayer` 離屏快照 + `ClipOp.Difference` 反向圓形挖空）、真實觸控座標擷取與 `rememberReducedMotion()` 無障礙降級 |
| `v0.5.0` ~ `v0.5.1` | **M3-A（多清單分頁與清單 CRUD）** | `HorizontalPager` 1:1 跟手分頁、頂部風格 C 膠囊 Tab 列、清單建立/改名/刪除（含最後一份保護）、修正新建清單跳頁競態與 `ThemeConfig?` 亮色冷啟動零閃爍 |
| `v0.6.0` ~ `v0.6.1` | **M3-B（項目行內 CRUD 與重置 Undo）** | 底部常駐連續輸入列（`adjustResize` + `imePadding`、保持鍵盤開啟、新項目自動上滑至頂部）、項目行內改名/刪除、一鍵重置與 `Snackbar` 復原閉環 |
| `v0.7.0` ~ `v0.8.0` | **M4（高階物理動效與手勢管線優化）** | `RadialThemeTransitionContainer` PointerDown 預擷取、`Spring Stepper Progress` 過衝回彈、`HorizontalPager` 零重組手勢管線、`Staggered Bulk Selection` 錯峰重置波浪與初期左滑曲線刪除 |
| `v0.9.0` | **啟動骨架屏與轉場效能優化** | `TotalCheckSkeletonShimmer` 啟動骨架屏、`0.6f` 降採樣快照與多波次 `RevealWave` 主題擴散、`CapsuleComponents` `Animatable` + `drawBehind` 零重組色彩插值、`DistantTabTransition` 遠距分頁預熱滑動 |
| `v0.10.0` | **M5（項目欄位動畫、堆疊排列與互動重構）** | 自訂 `StackedCardScrollLayout`（深度堆疊壓縮、層次暗化、點擊展開與 `ItemReflowAnim` / `NeighborRippleWave` 重排與鄰近波紋）、`Hold-to-Delete Perimeter Ring`（150ms 延遲 + 1000ms 外圈進度環刪除 + 300ms 退回）、`Drag-to-Reorder` 拖曳排序與懸浮層（含鍵盤/TalkBack 無障礙移動與 `ChecklistDao.updateItems` 批次持久化）、滾動方向感知 `QuickInputBar` 自動隱藏與 `BackHandler` 退出編輯 |
| `v0.11.0` | **M6（頂部導覽列與其餘介面交互動效優化）** | 頂部 `ExpandingChecklistTabBar` 長按拖曳排序與 `TabReflowAnim` 彈簧讓位、`SlideOutCancelModifier`（`0.94f` 按壓下沉與 `44.dp` 滑出衰減取消）、自訂 `ScopedModalOverlay`（記憶軟鍵盤高度達成輸入彈窗首幀零二次跳動、背景 IME 凍結）、進度卡整合清單改名/刪除與刪除清單 `Snackbar` 連同項目復原、`StackedCardScrollLayout` 邊界過捲動手風琴拉伸回彈（Overscroll Accordion），以及中英雙語字串資源（`values` / `values-b+zh+Hant` / `values-zh`） |
| `v0.12.0` ~ `v0.12.1` | **M7（格式文檔解析引擎與即時預覽快速匯入）** | 新增高容錯雙模式解析器 `ChecklistTextParser`、`ChecklistDao.createChecklistWithItems` 批次匯入交易、`ScopedModalOverlay`「空白清單 / 快速匯入」雙模態切換（含 `LiquidTabBar`、AI 提示詞卡、剪貼簿貼上、`.txt`/`.md`/`.csv` 讀取、0 延遲即時預覽微調與 Sticky Header/Footer 防遮擋）及應用程式圖標 |
| `v0.13.0` | **M8（桌面小工具 Jetpack Glance）** | 新增 `TotalCheckAppWidget`（`SizeMode.Responsive` 四級響應式膠囊矩陣版型）與 `TotalCheckWidgetReceiver`、擴充 `UserPreferencesRepository` 持久化 Widget 專屬狀態（選定清單 ID / 只看未帶 / 兩段式防誤觸重置到期時間）、打通 `WidgetSyncManager.notifyDataChanged` 即時雙向同步、`MainActivity.EXTRA_WIDGET_CHECKLIST_ID` 與 `MainViewModel.selectAndScrollToChecklist` 點擊跳轉定位及三語系 Widget 字串資源 |

## 3. 當前關鍵檔案結構
```text
app/src/main/kotlin/com/example/totalcheck/
├── MainActivity.kt                        # ThemeConfig? 延遲首幀 + SystemBarStyle 動態反轉 + RadialThemeTransitionContainer + Widget Intent 跳轉攔截
├── data/
│   ├── local/                             # AppDatabase (Singleton), ChecklistEntity, CheckItemEntity, ChecklistDao
│   ├── parser/ChecklistTextParser.kt      # 單行與多行 Markdown/表格/CSV/TSV/JSON 高容錯清單解析引擎
│   ├── preferences/UserPreferencesRepository.kt  # DataStore (ThemeConfig + Widget 狀態: widgetSelectedChecklistId, widgetShowUncheckedOnly, widgetResetConfirmUntilMillis)
│   ├── repository/ChecklistRepository.kt  # 封裝 DAO 寫入 + 批次匯入與排序持久化 + 刪除清單快照復原 + seedInitialDataIfNeeded + 重置快照 + WidgetSyncManager 通知
│   └── widget/
│       ├── WidgetSyncManager.kt           # 對接 Jetpack Glance updateAll() 與 3 秒重置超時恢復的跨進程同步通知器
│       ├── TotalCheckAppWidget.kt         # Jetpack Glance 單一響應式桌面小工具 (SizeMode.Responsive: Tier 1 ~ Tier 4) + ActionCallback
│       └── TotalCheckWidgetReceiver.kt    # GlanceAppWidgetReceiver 廣播接收器
└── ui/
    ├── main/                              # MainViewModel, TotalCheckScreen, CapsuleComponents, ExpandingChecklistTabBar, LiquidTabBar, StackedCardScrollLayout, ScopedModalOverlay, SlideOutCancelModifier, SkeletonShimmer
    └── theme/                             # Color, Theme (Curated + Dynamic), Type, RadialThemeTransitionContainer
```

## 4. M8 已完成交付說明（桌面小工具 Jetpack Glance）

- **單一全域動態 Widget（`SizeMode.Responsive` 四級自適應版型）**：
  - **Tier 1 (`Micro`, 寬 `< 180.dp` 且 高 `< 110.dp`, 對應 `2x1`)**：純儀表板（清單名稱、大型 `尚缺 X` / `✓ 全部備齊` 膠囊、`< >` 組別切換鈕，點擊主體直接進入 App）。
  - **Tier 2 (`Compact`, 寬 `< 240.dp` 或 高 `< 140.dp`, 對應 `2x2` / `3x1`)**：迷你頂欄（清單名 + `尚缺 X` 切換過濾 + `< >`）+ 單欄緊湊膠囊列（1-Col `LazyColumn`）。
  - **Tier 3 (`Standard`, 寬 `>= 240.dp` 且 高 `>= 140.dp`, 對應 `3x2` / `4x2`)**：完整頂欄（清單名 + `尚缺 X` 過濾切換 + 兩段式防誤觸重置 `↺` + `< >`）+ 雙欄小方塊網格（2-Col Grid）。
  - **Tier 4 (`Expanded`, 寬 `>= 320.dp` 且 高 `>= 220.dp`, 對應 `4x3` / `4x4`+)**：雙層頂欄（額外展開視覺化 `LinearProgressIndicator` 膠囊進度條與 `+ 新增` 快速入口）+ 2~3 欄（寬 `>= 340.dp` 時 3 欄）高密度方塊矩陣。
- **互動與同步規範**：
  - **原地狀態轉場 + 只看未帶過濾**：項目維持 `ORDER BY sortOrder ASC, id DESC` 原始順序；未勾選顯示高對比 `surfaceVariant` 膠囊與強調色 `●`，已勾選顯示沉降 `surface` 膠囊、`✓` 與刪除線文字。
  - **兩段式防誤觸重置（`↺`）**：點擊第一下轉為紅底（`errorContainer`）`確認?`（維持 3 秒有效期限並排程自動復原），期限內再次點擊才執行歸零。
  - **空面板與全部備齊徽章**：當清單無項目時顯示「尚無清點項目，點擊新增」入口；當 `尚缺 0` 且開啟「只看未帶」時，中央顯示 `✓ 全部備齊 (共 N 項)` 膠囊徽章與 `[ ↺ 重新清點 ]` / `[ 顯示全部項目 ]` 快捷鈕。
  - **色彩與點擊連動**：採用 Android 系統日夜模式色盤（`GlanceTheme`）；平時獨立記憶 `widgetSelectedChecklistId`，直接點擊 Widget 標題／進度列進入 App 時透過 `EXTRA_WIDGET_CHECKLIST_ID` 同步定位主畫面 `HorizontalPager`。
