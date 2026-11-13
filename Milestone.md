# TotalCheck — 開發進度摘要 (Milestone.md)

> 本文件供新接手的 AI Agent 快速掌握專案當前實作狀態、架構邊界與後續待辦事項。

## 1. 專案概覽與技術棧
- **專案定位**：純本地離線工具清點板 Android App（`com.example.totalcheck`，嚴禁宣告 `INTERNET` 權限）。
- **環境與建置**：Kotlin + Jetpack Compose（Material 3）、KSP `2.3.6`、`minSdk = 26` / `targetSdk = 36` / `compileSdk = 36`。
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
| `v0.10.0` | **M5（項目欄位動畫、堆疊排列與互動重構）** | 自訂 `StackedCardScrollLayout`（`SubcomposeLayout` 深度堆疊壓縮、層次暗化、點擊展開與 `ItemReflowAnim` 重排補間）、`Hold-to-Delete Perimeter Ring`（150ms 延遲 + 1000ms 外圈進度環刪除 + 300ms 退回）、`Drag-to-Reorder` 拖曳排序與懸浮層（含鍵盤/TalkBack 無障礙移動與 `ChecklistDao.updateItems` 批次持久化）、滾動方向感知 `QuickInputBar` 自動隱藏與 `BackHandler` 退出編輯 |

## 3. 當前關鍵檔案結構
```text
app/src/main/kotlin/com/example/totalcheck/
├── MainActivity.kt                        # ThemeConfig? 延遲首幀 + SystemBarStyle 動態反轉 + RadialThemeTransitionContainer
├── data/
│   ├── local/                             # AppDatabase (Singleton), ChecklistEntity, CheckItemEntity, ChecklistDao
│   ├── preferences/UserPreferencesRepository.kt  # DataStore (ThemeConfig: isDarkMode, useDynamicColor)
│   ├── repository/ChecklistRepository.kt  # 封裝 DAO 寫入 + 批次排序持久化 + seedInitialDataIfNeeded + 重置快照 + WidgetSyncManager 通知
│   └── widget/WidgetSyncManager.kt        # 未來對接 Jetpack Glance updateAll() 的解耦鉤子
└── ui/
    ├── main/                              # MainViewModel, TotalCheckScreen, CapsuleComponents, StackedCardScrollLayout, SkeletonShimmer
    └── theme/                             # Color, Theme (Curated + Dynamic), Type, RadialThemeTransitionContainer
```

## 4. 後續待實作項目（M6+）
- **M6（其餘介面動畫效果優化與格式文檔快速載入列表）**：
  - 優化主列表項目欄位以外其餘介面（頂部膠囊頁籤列、清單管理彈窗、進度模組、`Snackbar` 與鍵盤過渡等）之動畫與視覺回饋。
  - 實作 App 支援讀取格式文檔（如 Markdown / CSV / 結構化純文字），快速解析並匯入建立清點列表。
- **M7（桌面小工具）**：（原 M5 順延）實作 `widget/` 模組（Jetpack Glance）並對接 `WidgetSyncManager.notifyDataChanged()`。
