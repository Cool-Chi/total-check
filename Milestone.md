# TotalCheck — 開發進度摘要 (Milestone.md)

> 本文件供新接手的 AI Agent 快速掌握專案當前實作狀態、架構邊界與後續待辦事項。

## 1. 專案概覽與技術棧
- **專案定位**：純本地離線工具清點板 Android App（`com.example.totalcheck`，嚴禁宣告 `INTERNET` 權限）。
- **環境與建置**：Kotlin + Jetpack Compose（Material 3）、原生 `RemoteViews` 直出桌面小工具引擎（`DirectWidgetRenderer`）、KSP `2.3.6`、`minSdk = 26` / `targetSdk = 36` / `compileSdk = 36`。
- **資料與狀態**：Room `2.8.5`（`AppDatabase` 單例 + `ChecklistRepository`）、Preferences DataStore `1.3.0`（`UserPreferencesRepository`）、`MainViewModel`（`StateFlow`）。
- **核心設計**：風格 C（`24.dp` 觸感膠囊模組、大型進度數字、飽和色「待備 / ✓ 已備」標籤）、原地勾選狀態轉場（不隨勾選沉底）。

## 2. 已完成里程碑（`v0.0.1` ~ `v0.16.0`）

| 版本標籤 | 里程碑 | 核心交付內容 |
|---|---|---|
| `v0.0.1` ~ `v0.1.0` | **M0（UI 外殼定型）** | 建立 `SPEC.md`、`AGENT.md`、`WORKFLOW.md`；鎖定「風格 C」觸感膠囊介面、原地狀態轉場、`0.97f` 按壓縮放與「只看未帶」過濾切換 |
| `v0.2.0` | **M1（資料持久化基底）** | 串接 Room（`checklists` / `check_items`）、DataStore、首次啟動種子資料與 `WidgetSyncManager` 解耦空殼 |
| `v0.3.0` ~ `v0.4.0` | **M2（Edge-to-Edge、雙軌色彩與圓形主題切換）** | `SystemBarStyle` 動態反轉、精調雙色盤（Dark `#0E1116` / Light `#F8FAFC` / Tertiary Amber）+ Dynamic Color、`RadialThemeTransitionContainer` 與 `rememberReducedMotion()` |
| `v0.5.0` ~ `v0.6.1` | **M3（多清單分頁、項目 CRUD 與重置 Undo）** | `HorizontalPager` 1:1 分頁、清單與項目 CRUD、亮色冷啟動零閃爍、常駐連續輸入列、一鍵重置與 `Snackbar` 復原閉環 |
| `v0.7.0` ~ `v0.9.0` | **M4（高階物理動效與啟動骨架屏）** | `TotalCheckSkeletonShimmer`、`0.6f` 降採樣多波次 `RevealWave` 主題擴散、`Spring Stepper Progress`、`Staggered Bulk Selection` 錯峰重置波浪、`DistantTabTransition` 遠距預熱滑動 |
| `v0.10.0` | **M5（堆疊捲動佈局與互動重構）** | 自訂 `StackedCardScrollLayout`（深度堆疊壓縮、點擊展開、`ItemReflowAnim` / `NeighborRippleWave`）、`Hold-to-Delete Perimeter Ring`（外圈進度環長按刪除）、`Drag-to-Reorder` 拖曳排序與批次持久化 |
| `v0.11.0` | **M6（頂部導覽與全域交互優化）** | `ExpandingChecklistTabBar` 長按拖曳重排、`SlideOutCancelModifier`（`0.94f` 下沉與 `44.dp` 滑出取消）、`ScopedModalOverlay`（記憶鍵盤高度首幀零跳動）、邊界手風琴過捲動（Overscroll Accordion）與三語系資源 |
| `v0.12.0` ~ `v0.12.1` | **M7（格式文檔解析與快速匯入）** | 高容錯雙模式解析器 `ChecklistTextParser`、`ScopedModalOverlay`「空白清單 / 快速匯入」雙模態切換（`LiquidTabBar`、AI 提示詞卡、剪貼簿貼上、`.txt`/`.md`/`.csv` 讀取、0 延遲即時預覽微調）及 App 圖標 |
| `v0.13.0` ~ `v0.16.0` | **M8（原生 Direct RemoteViews 雙桌面小工具與雙軌主題同步）** | 以原生 `DirectWidgetRenderer` 實作「工具清單網格（`2x2+` 三級響應式 + `<ListView>`）」與「清單極簡計數條（`2x1~4x1`）」；按 `appWidgetId` 隔離多實例狀態；`WidgetThemePalette` 無反射同步四象限主題色彩；`260ms` 水波紋延遲合併、底部空白列點擊入 App 與 `+ 新增` 自動聚焦鍵盤 |

## 3. 當前關鍵檔案結構
```text
app/src/main/kotlin/com/example/totalcheck/
├── MainActivity.kt                        # ThemeConfig? 延遲首幀 + SystemBarStyle + RadialThemeTransitionContainer + Widget/Notification Intent 攔截
├── data/
│   ├── local/                             # AppDatabase (Singleton), ChecklistEntity, CheckItemEntity, ReminderEntity (M9), ChecklistDao, ReminderDao (M9)
│   ├── parser/ChecklistTextParser.kt      # 單行與多行 Markdown/表格/CSV/TSV/JSON 高容錯清單解析引擎
│   ├── preferences/UserPreferencesRepository.kt  # DataStore (ThemeConfig + 按 appWidgetId 隔離之 Widget 狀態)
│   ├── reminder/                          # (M9 新增) ReminderScheduler, NotificationHelper, ReminderBroadcastReceiver, ReminderBootReceiver
│   ├── repository/ChecklistRepository.kt  # 封裝 DAO 寫入 + 提醒排程連動 + WidgetSyncManager 通知
│   └── widget/                            # WidgetSyncManager, WidgetThemePalette, DirectWidgetRenderer, TotalCheckWidgetReceiver, TotalCheckCounterWidgetReceiver, TotalCheckWidgetViewsService
└── ui/
    ├── main/                              # MainViewModel, TotalCheckScreen, CapsuleComponents, ExpandingChecklistTabBar, LiquidTabBar, StackedCardScrollLayout, ScopedModalOverlay, SlideOutCancelModifier, SkeletonShimmer
    └── theme/                             # Color, Theme (Curated + Dynamic), Type, RadialThemeTransitionContainer

```

## 4. 當前開發目標：Milestone 9（智慧清點提醒與系統通知推送系統）

M9 採開放式多階段迭代架構，分為 **Part 1（功能基底與系統閉環）**、**Part 2（頂級時間選擇與物理動效注入）** 及 **Part 3+（後續延伸功能擴充）**。

### 🔹 M9 - Part 1：功能基底、系統排程、通知互動與 Widget 狀態同步（當前首要任務）

1. **資料庫升級與 Schema（`AppDatabase` `version = 1` → `version = 2`）**：
* 新增 `ReminderEntity`（`reminders` 表，外鍵 `CASCADE` 綁定 `ChecklistEntity.id` 與可選 `CheckItemEntity.id`）與 `ReminderDao`。
* 支援 Room 自動遷移或安全升級（保留既有清單資料），欄位涵蓋：`id`, `checklistId`, `itemId` (`null` 為清單級提醒、非 `null` 為單品提醒), `hour`, `minute`, `repeatDaysBitmask` (`0` = 單次提醒，Bit 0..6 對應週一至週日), `triggerEpochMillis`, `onlyIfUnchecked` (預設 `true`), `isEnabled`, `createdAt`。


2. **系統鬧鐘排程與重開機恢復（`data/reminder/`）**：
* 宣告本地權限：`POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、`USE_EXACT_ALARM`（或動態檢查 `AlarmManager.canScheduleExactAlarms()` 並平滑回退）、`RECEIVE_BOOT_COMPLETED`。**嚴禁宣告 `INTERNET` 權限**。
* `ReminderScheduler`：使用 `AlarmManager.setExactAndAllowWhileIdle()` 計算並註冊下一次精確觸發時間。
* **單次 vs 週期掛載規則（嚴格鎖定）**：
* `repeatDaysBitmask == 0`（單次提醒）：鬧鐘到點觸發後，自動將 `isEnabled` 設為 `false` 並**保持關閉**；後續使用者點擊清單「↺ 一鍵重置」時，**絕不重新啟動已關閉的單次提醒**。
* `repeatDaysBitmask > 0`（週期提醒）：鬧鐘到點觸發後，自動推算下一個符合星期幾的 `triggerEpochMillis` 並重新掛載至 `AlarmManager`。


* `ReminderBootReceiver`：監聽 `BOOT_COMPLETED`、`TIME_SET`、`TIMEZONE_CHANGED` 與 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`，重讀 DB 中所有 `isEnabled = true` 的提醒重新掛載（若單次提醒的 `triggerEpochMillis` 已過期則補發或標為關閉）。


3. **智慧過濾通知與通知欄零跳轉操作（`NotificationHelper` & `ReminderBroadcastReceiver`）**：
* **「有缺才響」智慧過濾**：到點喚醒時查詢 Room DB。若 `onlyIfUnchecked == true`：清單級提醒在該清單 `尚缺 0 項` 時靜默略過本次推播（週期提醒仍排程下一次）；單品級提醒在該項目 `isChecked == true` 時靜默略過。
* **通知內容與 Direct Actions**：
* 清單提醒：標題顯示清單名稱與尚缺數量，內文列出前 3 項未帶物品；提供 Action 按鈕 `[ 延後 15 分鐘 ]` 與 `[ ✓ 全部標為已備 ]`（點擊直接更新 Room DB、關閉通知並呼叫 `WidgetSyncManager.notifyDataChanged()` 刷新桌面小工具）。
* 單品提醒：顯示單品名稱與所屬清單；提供 Action 按鈕 `[ 延後 15 分鐘 ]` 與 `[ ✓ 已帶此項 ]`（背景直接打勾該項目並同步桌面 Widget）。
* 點擊通知本體：攜帶 `EXTRA_WIDGET_CHECKLIST_ID` 啟動 `MainActivity` 並自動滑動定位至該清單。




4. **主畫面設定入口與基礎配置彈窗（UI Part 1）**：
* **清單級提醒**：於 `CapsuleProgressModule` 操作列新增「🔔 提醒」按鈕；若該清單有啟用中的提醒，顯示精簡時間膠囊徽章（如 `🔔 08:00` 或 `🔔 週一~五 08:00`）。
* **單品級提醒**：於 `CapsuleCheckItemRow` 新增單品提醒觸發入口與精簡時間標記（如小鈴鐺 + `08:00`）。
* **基礎設定彈窗（`ScopedModalOverlay` 新增 `ReminderConfigModal`）**：Part 1 先以直覺穩定的小時/分鐘設定、週一至週日多選 Chip（全不選即為單次提醒）、「僅在尚有未帶項目時提醒」Switch、刪除/關閉提醒按鈕，以及「發送測試通知」按鈕完成閉環，並處理 Android 13+ 通知權限請求。


5. **桌面雙 Widget 提醒狀態顯示（`WidgetSyncManager` & `DirectWidgetRenderer`）**：
* `WidgetUiSnapshot` 新增啟用中提醒的摘要映射（如 `activeReminderByChecklist: Map<Long, ChecklistEntityReminderSummary>` 與單品提醒標記）。
* **工具清單網格（`2x2+`）與清單極簡計數條（`2x1~4x1`）**：當所選清單具有啟用中的清單級提醒（或內部含有啟用中的單品提醒）時，於 Widget 頂欄標題旁或狀態區顯示精簡的 `🔔` 狀態標記（如 `🔔 08:00` 或 `🔔` 圖示，依尺寸 Tier 自適應不擠壓標題），並在鬧鐘觸發/設定變更時透過 `WidgetSyncManager.notifyDataChanged()` 即時刷新。



### 🔹 M9 - Part 2：頂級時間互動與物理動效打磨（待 Part 1 穩定後由使用者提供專屬 Skill 規範接入）

* 預定針對時間滾輪/刻度選擇器、星期標籤選擇動效、開關聯動回饋與鈴鐺/徽章狀態過渡注入高階物理動效。

### 🔹 M9 - Part 3+：更多進階提醒與清單擴充功能（預留迭代）

* 依使用者後續提出的 M9 延伸需求持續擴充。
