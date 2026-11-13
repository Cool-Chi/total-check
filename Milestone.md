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

## 3. 當前關鍵檔案結構
```text
app/src/main/kotlin/com/example/totalcheck/
├── MainActivity.kt                        # enableEdgeToEdge(SystemBarStyle) + RadialThemeTransitionContainer
├── data/
│   ├── local/                             # AppDatabase (Singleton), ChecklistEntity, CheckItemEntity, ChecklistDao
│   ├── preferences/UserPreferencesRepository.kt  # DataStore (isDarkMode, useDynamicColor)
│   ├── repository/ChecklistRepository.kt  # 封裝 DAO 寫入 + seedInitialDataIfNeeded + WidgetSyncManager 通知
│   └── widget/WidgetSyncManager.kt        # 未來對接 Jetpack Glance updateAll() 的解耦鉤子
└── ui/
    ├── main/                              # MainViewModel, TotalCheckScreen, CapsuleComponents
    └── theme/                             # Color, Theme (Curated + Dynamic), Type, RadialThemeTransitionContainer
```

## 4. 後續待實作項目（M3+）
- **M3（進階 CRUD 與互動動效）**：
  - 項目滑動刪除（`Curved Card Deletion`）、行內編輯名稱。
  - 清單建立 / 重新命名 / 刪除（長按 Tab）。
  - 一鍵重置搭配錯峰消退（`Staggered Bulk Selection`）與底部 Snackbar `復原 (Undo)`（呼叫 `restoreCheckedStates`）。
- **M4（桌面小工具）**：實作 `widget/` 模組（Jetpack Glance）並對接 `WidgetSyncManager.notifyDataChanged()`。
