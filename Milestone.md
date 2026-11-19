# TotalCheck — 開發進度摘要 (Milestone.md)

> 本文件供新接手的 AI Agent 快速掌握專案當前實作狀態、架構邊界與後續待辦事項。

## 1. 專案概覽與技術棧
- **專案定位**：純本地離線工具清點板 Android App（`com.example.totalcheck`，嚴禁宣告 `INTERNET` 權限）。
- **環境與建置**：Kotlin + Jetpack Compose（Material 3）、原生 `RemoteViews` 直出桌面小工具引擎（`DirectWidgetRenderer`）、KSP `2.3.6`、`minSdk = 26` / `targetSdk = 36` / `compileSdk = 36`。
- **資料與狀態**：Room `2.8.5`（`AppDatabase` 單例 + `ChecklistRepository`）、Preferences DataStore `1.3.0`（`UserPreferencesRepository`）、`MainViewModel`（`StateFlow`）。
- **核心設計**：風格 C（`24.dp` 觸感膠囊模組、大型進度數字、飽和色「待備 / ✓ 已備」標籤）、原地勾選狀態轉場（不隨勾選沉底）。

## 2. 已完成里程碑（`v0.0.1` ~ `v0.21.0` / M0 ~ M9 全數結案）

| 版本標籤 | 里程碑 | 核心交付內容 |
|---|---|---|
| `v0.0.1` ~ `v0.1.0` | **M0（UI 外殼定型）** | 建立 `SPEC.md`、`AGENT.md`、`WORKFLOW.md`；鎖定「風格 C」觸感膠囊介面、原地狀態轉場、`0.97f` 按壓縮放與「只看未帶」過濾切換 |
| `v0.2.0` | **M1（資料持久化基底）** | 串接 Room（`checklists` / `check_items`）、DataStore、首次啟動種子資料與 `WidgetSyncManager` 解耦空殼 |
| `v0.3.0` ~ `v0.4.0` | **M2（Edge-to-Edge、雙軌色彩與圓形主題切換）** | `SystemBarStyle` 動態反轉、精調雙色盤（Dark `#0E1116` / Light `#F8FAFC` / Tertiary Amber）+ **頂欄取色鈕** Dynamic Color、**頂欄主題鈕** `RadialThemeTransitionContainer` 與 `rememberReducedMotion()` |
| `v0.5.0` ~ `v0.6.1` | **M3（多清單分頁、項目 CRUD 與重置 Undo）** | `HorizontalPager` 1:1 分頁、清單與項目 CRUD、亮色冷啟動零閃爍、**底部快速輸入列**、**資訊卡重置鈕**與**底部提示條**復原閉環 |
| `v0.7.0` ~ `v0.9.0` | **M4（高階物理動效與啟動骨架屏）** | **啟動骨架屏**（`TotalCheckSkeletonShimmer`）、`0.6f` 降採樣多波次 `RevealWave` 主題擴散、**資訊卡進度條** `Spring Stepper Progress`、`Staggered Bulk Selection` 錯峰重置波浪、`DistantTabTransition` 遠距預熱滑動 |
| `v0.10.0` | **M5（項目堆疊列表與互動重構）** | 自訂**項目堆疊列表**（`StackedCardScrollLayout`：深度堆疊壓縮、點擊展開、`ItemReflowAnim` / `NeighborRippleWave`）、**項目長按刪除環**、**項目拖曳手把**拖曳排序（含**項目拖曳佔位槽**）與批次持久化 |
| `v0.11.0` | **M6（頂部導覽與全域交互優化）** | **清單頁籤列**長按拖曳重排、`SlideOutCancelModifier`（`0.94f` 下沉與 `44.dp` 滑出取消）、**彈窗遮罩宿主**（記憶鍵盤高度首幀零跳動）、邊界手風琴過捲動（Overscroll Accordion）與三語系資源 |
| `v0.12.0` ~ `v0.12.1` | **M7（格式文檔解析與快速匯入）** | 高容錯雙模式解析器 `ChecklistTextParser`、**新增清單彈窗**「空白清單 / 快速匯入」雙模態切換（**彈窗液態分頁列**、AI 提示詞卡、剪貼簿貼上、`.txt`/`.md`/`.csv` 讀取、0 延遲即時預覽微調）及 App 圖標 |
| `v0.13.0` ~ `v0.16.0` | **M8（原生 Direct RemoteViews 雙桌面小工具與雙軌主題同步）** | 以原生 `DirectWidgetRenderer` 實作**網格桌面小工具**（`2x2+` 三級響應式 + `<ListView>`）與**計數條桌面小工具**（`2x1~4x1`）；按 `appWidgetId` 隔離多實例狀態；`WidgetThemePalette` 無反射同步四象限主題色彩；`260ms` 水波紋延遲合併、底部空白列點擊入 App 與 `+ 新增` 自動聚焦**底部快速輸入列** |
| `v0.17.0` ~ `v0.21.0`（現行建置版本） | **M9（智慧清點提醒、雙層標籤系統、資訊卡設定面板與基準定案）** | Part 1（`v0.17.0`）～ Part 5（`v0.21.0`）：完成 Room `v1 → v4` 無損升級（`reminders`、`isArchived`、時間戳擴充欄位與 `item_status_logs`）、**3D 時間滾輪**與四碼直輸、**提醒排程彈窗**雙模式、**資訊卡設定面板**（頂欄**設定面板分頁列**三頁並列且展開時 1:1 向下推移**項目堆疊列表**）、全域（實心）／清單（空心）雙層標籤與**項目狀態窗・標籤面**長按快切、列表沉厚觸覺分層、實心同色提醒鈴鐺與左側**項目提醒時間**，以及 49 項正式 UI 命名定案（`docs/COMPONENTS.md`）與 `HANDOFF.md` |

## 3. 當前關鍵檔案結構
```text
app/src/main/kotlin/com/example/totalcheck/
├── MainActivity.kt                        # ThemeConfig? 延遲首幀 + SystemBarStyle + RadialThemeTransitionContainer + Widget/Notification Intent 攔截
├── data/
│   ├── local/                             # AppDatabase (v4 Singleton + MIGRATION_1_2/2_3/3_4), ChecklistEntity, CheckItemEntity, ReminderEntity, ItemStatusLogEntity, ChecklistDao, ReminderDao, ItemStatusLogDao
│   ├── parser/ChecklistTextParser.kt      # 單行與多行 Markdown/表格/CSV/TSV/JSON 高容錯清單解析引擎
│   ├── preferences/UserPreferencesRepository.kt  # DataStore (ThemeConfig + 按 appWidgetId 隔離之 Widget 狀態 + 全域/清單雙層標籤池)
│   ├── reminder/                          # ReminderScheduler, ReminderFormatter, NotificationHelper, ReminderBroadcastReceiver, ReminderBootReceiver (支援 REMINDER 與 AUTO_RESTORE)
│   ├── repository/ChecklistRepository.kt  # 封裝 DAO 寫入 + 狀態異動紀錄 + 提醒/自動復原排程連動 + WidgetSyncManager 通知
│   └── widget/                            # WidgetSyncManager, WidgetThemePalette, DirectWidgetRenderer, TotalCheckWidgetReceiver, TotalCheckCounterWidgetReceiver, TotalCheckWidgetViewsService
└── ui/
    ├── main/                              # MainViewModel, ChecklistFilterState, TagCatalog, TotalCheckScreen, CapsuleComponents, ReminderConfigContent, ExpandingChecklistTabBar, LiquidTabBar, StackedCardScrollLayout, ScopedModalOverlay, SlideOutCancelModifier, SkeletonShimmer, TactileHaptics, ItemMoveToBottomTransition
    └── theme/                             # Color, Theme (Curated + Dynamic), Type, RadialThemeTransitionContainer

```

## 4. 已結案里程碑詳錄：Milestone 9（智慧清點提醒、雙層標籤系統與設定面板）

M9 採開放式多階段迭代架構，包含 **Part 1（功能基底與系統閉環）**、**Part 2（3D 時間滾輪與物理動效）**、**Part 3（封存、自動復原與觸感效率）**、**Part 4（標籤與零跳動編輯）** 與 **Part 5（資訊卡設定面板、雙層標籤、觸覺分層與基準定案）**，全數已完成。

### 🔹 M9 - Part 1（`v0.17.0`）：功能基底、系統排程、通知互動與 Widget 狀態同步（已完成）

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
* **網格桌面小工具（`2x2+`）與計數條桌面小工具（`2x1~4x1`）**：當所選清單具有啟用中的清單級提醒（或內部含有啟用中的單品提醒）時，於 Widget 頂欄標題旁或狀態區顯示精簡的 `🔔` 狀態標記（如 `🔔 08:00` 或 `🔔` 圖示，依尺寸 Tier 自適應不擠壓標題），並在鬧鐘觸發/設定變更時透過 `WidgetSyncManager.notifyDataChanged()` 即時刷新。



### 🔹 M9 - Part 2（`v0.18.0`）：頂級時間互動與物理動效打磨（已完成）

* 實作 `TactileDrumWheel` **3D 時間滾輪**（含點擊選中時間直接輸入四位數字、星期循環 Tab 與快速時間偏移按鈕 `+15m` / `+1h` / `+4h` / `Now`）與物理彈簧回饋，並重構**項目狀態窗・提醒面**寬度至 `64.dp` 與鈴鐺滑入整合排版。

### 🔹 M9 - Part 3（`v0.19.0`）：交互效率極致優化、封存檢索與自動復原（已完成，十項修改簡要紀錄）

1. **加速長按刪除與連續刪除遞減機制**：長按刪除改為 `120ms` 起環 + `800ms` 基準填滿，於 `4 秒` 內連續刪除每筆遞減 `100ms`（最低 `450ms`），大幅提升批量清理效率。
2. **狀態膠囊「待備」點擊區拓展**：當項目處於「待備」狀態時，整個 `64.dp` 狀態膠囊整合為單品清點提醒設定入口；處於「✓ 已備」狀態時，點擊膠囊維持切換勾選狀態以防誤觸。
3. **狀態膠囊長按循環管理（快設 / 略過一次 / 取消）**：無提醒時長按狀態膠囊（或待備狀態對應區）快設 15 分鐘後提醒；已有單次提醒時長按即取消；若為週期性提醒，首次長按推進至下一週期觸發日並於膠囊與徽章顯示「已略過」提示，再次長按則完全關閉提醒。
4. **拖曳手把左滑移至列表底部**：按住右側六點拖曳手把向左滑動超過 `56.dp` 後放手，立即將該項目移動至清單底部，並於元件內預留 `DragSwipeBottomTransitionSpec` 過渡動畫介面供後續套用專業物理動效。
5. **項目行內封存與「已封存項目」檢索面板**：項目僅於點擊鉛筆進入行內編輯模式時顯示封存按鈕（`isArchived = true`），封存瞬間透過錨點交接與凍結鍵盤確保清單不亂跳動；資訊卡提醒按鈕左側新增 `40.dp` 封存按鈕，開啟初版「已封存項目」面板提供瀏覽、單項還原與單項永久刪除（封存期間自動停用所屬排程，還原時復原）。
6. **資訊卡「篩選」與「提醒」按鈕純圖示統一**：移除資訊卡「只看未帶」與「提醒」按鈕文字，統一為 `40.dp` 圓形純圖示按鈕並對齊改名／刪除按鈕樣式，依容器高亮色與圖示狀態區分啟用狀態。
7. **重新定義「篩選」按鈕與可擴充狀態架構**：原「只看未帶」正式定義為「篩選」按鈕，建立獨立 `ChecklistFilterState`（涵蓋 `onlyUnchecked`、`selectedTags`、`selectedCategory`、`minPriority`）並將清單項目過濾邏輯遷移至該狀態模型，保留點擊直接切換未帶篩選與未來擴充能力。
8. **項目時間戳、擴充欄位與狀態異動紀錄表（Room v4，零 UI 更動）**：於 `CheckItemEntity` 新增 `createdAt`、`updatedAt`、`lastCheckedAt`、`archivedAt`、`tags`、`note`、`quantity`、`priority`，並新增獨立 `item_status_logs`（`ItemStatusLogEntity` / `ItemStatusLogDao`）由 `ChecklistRepository` 自動記錄建立、勾選／取消、改名、封存／還原、自動復原、重置與排程異動（`MIGRATION_3_4` 無損升級，現有 UI 保持不變）。
9. **長按刪除進度環套用資訊卡刪除按鈕填充色**：將 `Hold-to-Delete` 外框進度環填充色統一改採資訊卡刪除按鈕內部填充色（`colorScheme.errorContainer.copy(alpha = 0.72f)`），強化整體破壞性操作之視覺語意一致性。
10. **定時週期項目自動復原功能**：於 `ReminderEntity` 加入 `scheduleKind`（`"REMINDER" | "AUTO_RESTORE"`）複用 `AlarmManager` 精確排程與 `TactileDrumWheel` 滾輪／週期選擇；單品彈窗頂部改用 `LiquidTabBar` 切換「清點提醒」與「自動復原」並各自保留編輯草稿；到點時靜默將項目恢復為待備、寫入 `AUTO_RESTORED` 紀錄並同步 Widget（不推送通知），已啟用自動復原之項目在主列表**僅將外框與左側指示條改為藍色（`primary`）**，不額外添加徽章。

### 🔹 M9 - Part 4（`v0.20.0`）：項目標籤池、標籤篩選、零跳動編輯與長按刪除修復（已完成，第 11～14 項修改簡要紀錄）

11. **項目標籤功能與編輯模式對齊**：建立跨清單共用標籤池（`TagCatalog` + DataStore 持久化，預設內建「重要」標籤，最多每項 2 個標籤且支援建立新標籤並從 12 款預設圓角圖示自選）；標籤按鈕僅於點擊鉛筆進入行內編輯後顯示，右側按鈕順序重排為「`標籤` → `封存` → `確認(✓)`（精準對齊原鉛筆位置）→ `取消(✗)`（對齊原拖曳手把位置）」；主列表中支援點按後左右水平滑動 `64.dp` 狀態膠囊以 3D `rotationY` 翻轉顯示 1～2 個標籤。
12. **長按篩選鈕標籤篩選**：長按資訊卡「篩選」純圖示鈕開啟單一標籤選擇彈窗（含「全部項目」清除標籤篩選），與短按「只看未帶」自由疊加運作；選定標籤時篩選按鈕圖示即時替換為該標籤對應圖示，並支援點擊「編輯標籤」與跨清單共用。
13. **行內編輯完成零跳動優化**：完成或退出項目名稱編輯時於呼叫層同步關閉軟鍵盤與清除焦點，將 `editBottomInsetFloorState` 退場改為無條件 `300ms` 平滑衰減曲線、延長 `beginExitAnchorLock` 鎖定視窗至 `900ms` 覆蓋整個彈簧回彈週期，並統一 `maxScroll` 與邊界判定基準，消除儲存／取消編輯時的項目垂直位移。
14. **修復長按刪除中途切換狀態殘留進度環**：於 `CapsuleCheckItemRow` 新增 `cancelHoldAndRewind()` 並監聽 `item.isChecked`、`visualChecked`、`isEditing` 及手勢期間子元件按壓／狀態變化，確保長按到一半切換項目狀態或進入編輯時立即中斷長按動畫並順暢退回刪除進度環。

### 🔹 M9 - Part 5（`v0.21.0`）：資訊卡設定面板、雙層標籤系統、觸覺分層與基準文件定案（已結案，第 15～30 項修改簡要紀錄）

15. **長按清單資訊卡展開資訊卡設定面板（管理標籤池 & 封存項目）**：長按**清單資訊卡**非按鈕區域（`350ms`）即可在原卡片展開**資訊卡設定面板**，面板內提供**設定面板標籤管理區**（新增自選圖示標籤、刪減既有標籤並自動自相關已套用項目移除，同時透過**底部提示條**提供一鍵復原標籤池與項目套用狀態）與**設定面板封存區**（檢視、單項還原與永久刪除）；同時將**項目標籤彈窗**精簡為純選取既有標籤（圖示＋名稱），移除彈窗內的建立新標籤區塊。
16. **清單資訊卡按鈕精簡、資訊卡編輯鈕長按預留與資訊卡重置鈕寬度對齊**：移除原本**清單資訊卡**下排獨立的封存圓鈕（底部保留左起**資訊卡篩選鈕**與右側**資訊卡提醒鈕**、**資訊卡編輯鈕**、**資訊卡刪除鈕**）；為**資訊卡編輯鈕**預留長按擴充入口（`onLongClickRenameChecklist`，供後續開放資訊卡版面配置）；將右上角按鈕改名為「重置」（**資訊卡重置鈕**），並將寬度鎖定對齊為下方兩顆圓鈕加間距的總寬度（`88.dp` = `40.dp + 8.dp + 40.dp`）。
17. **清單頁籤列雙擊選中清單行內改名**：於頂部**清單頁籤列**雙擊當前已選中的**清單頁籤**，該頁籤即原地轉為**頁籤改名輸入框**（`InlineEditingChecklistTag`，含 `BasicTextField` 與確認 `✓` 按鈕），支援鍵盤 `Done`、返回鍵或點擊外部直接完成清單名稱修改。
18. **資訊卡設定面板高度翻倍推擠與淡入淡出過渡（附繁體中文完整化）**：**資訊卡設定面板**直接建立於原**清單資訊卡**（`CapsuleProgressModule`）上，開啟時卡片垂直高度向下拓展翻倍（`normalCardHeightDp * 2f`）並同步向下推移下方**項目堆疊列表**起點（`topOverlayInsetPxProvider`），右上角提供關閉（`✕`）按鈕，開啟／關閉採用 `AnimatedContent` 淡入淡出過渡；同時全面校對並統一 `values-b+zh+Hant` 與 `values-zh` 為 100% 完整繁體中文文本。
19. **項目狀態窗・標籤面純圖示化與精簡項目編輯工具列**：`64.dp` **項目狀態窗・標籤面**全面改為純圖示顯示，最多同時顯示 2 個標籤圖示（0 標籤顯示置中空心標籤輪廓 `Icons.Outlined.LocalOffer`、1 標籤置中顯示單圖示、2 標籤併排並以 `1.dp × 12.dp` 垂直細線分隔）；同步移除行內編輯模式的標籤按鈕，**項目編輯工具列**精簡保留 `[封存] [確認(✓) 對齊原項目編輯鈕位] [取消(✗) 對齊項目拖曳手把位]`。
20. **修復中間項目編輯結束位移至頂部並同步軟鍵盤與清單資訊卡收縮速度**：取消一般編輯退出時錯誤觸發 `beginExitAnchorLock` 強制推高 `scrollOffset` 的機制（改為僅於編輯中封存項目時交接鄰居錨點），進入編輯時記錄 `preEditScrollOffset` 且僅於項目會遭鍵盤遮擋時最小幅度捲動、結束編輯時平滑恢復至原位；同時以動態校準之實際軟鍵盤高度 `imeExtra / observedImeHeight` 1:1 逐幀驅動**清單資訊卡**收合與展開進度（外接無軟鍵盤裝置保留 220ms 備援），確保軟鍵盤上滑／下滑與**清單資訊卡**收縮／展開速度完全同步。
21. **全狀態 3D 翻轉進入項目狀態窗・標籤面、短按編輯標籤與長按觸發架構**：解除狀態窗水平滑動翻轉的標籤數量與勾選狀態限制，即使項目尚未設定標籤亦可左右滑動翻轉進入**項目狀態窗・標籤面**；於標籤面**短按**直接開啟**項目標籤彈窗**（整合原編輯模式移除之標籤設定入口），左右滑動翻回**項目狀態窗・提醒面**，並對接長按快速切換功能。
22. **項目狀態窗・標籤面色調統一對齊項目編輯鈕**：**項目狀態窗・標籤面**全面改採與旁邊**項目編輯鈕**一致之中性柔和色調（`surfaceContainerHighest` 底色、`outlineVariant.copy(alpha = 0.72f)` 邊框與 `onSurfaceVariant` 前景圖示及垂直分隔線），消除多色衝突。
23. **標籤分層基礎架構與命名整理**：將原有標籤池命名整理並保留跨清單全域標籤架構，完成第 24～26 項雙層分域標籤實作。
24. **全域標籤與清單專屬標籤雙層設定面板（設定面板分頁列三頁並列）**：將**設定面板分頁列**升級為「全域（實心圖示）」、「清單（空心圖示）」、「封存」三頁並列加右側關閉鈕；全域標籤預設內建「重要」標籤（實心五角星圖示），跨所有清單共用；清單標籤獨立儲存於 `checklist_tag_pools_json` 並以 `#list:` token 隔離，僅在所屬清單內生效、篩選與刪除（切換清單自動清除他頁清單標籤篩選）；**項目標籤彈窗**與**標籤篩選彈窗**採單一合併列表呈現，僅以實心／空心圖示自然區分。
25. **長按項目狀態窗・標籤面快速切換上一個添加的標籤**：在**項目標籤彈窗**中替該清單任一項目新增標籤時，自動記錄該清單最近一次添加的標籤（`lastAddedTagTokenByChecklist`）；在同清單任一項目的**項目狀態窗・標籤面**執行**長按**即可一鍵快速切換（未套用且未滿 2 個時即時添加、已套用時再次長按即時移除）；若該清單尚無紀錄則預設套用全域「重要（五角星）」標籤，若目標項目已滿 2 個標籤則不覆蓋並由**底部提示條**提示。
26. **全域實心圖示／清單空心圖示全介面一致化**：於 `TagCatalog` 為 12 組預設圖示成對配置實心（`Icons.Rounded.*`）與空心（`Icons.Outlined.*`）向量圖示，全面套用於**設定面板分頁列**、**設定面板標籤管理區**、合併式**項目標籤彈窗**與**標籤篩選彈窗**、**資訊卡篩選鈕**動態圖示及**項目狀態窗・標籤面**。
27. **展開資訊卡設定面板平滑向下推移項目堆疊列表**：將**清單資訊卡**的 `1x ⇄ 2x` 高度切換改為 `260ms FastOutSlowInEasing` 動畫高度，並將 `ChecklistPageContent` 的 `progressCardHeightHolder` 升級為可觀察的 `MutableFloatState`（`progressCardHeightState`），讓**項目堆疊列表**於零重組的 Placement 階段逐幀讀取最新 `topInsetPxProvider`，使展開／收合**資訊卡設定面板**時下方**項目卡片**如同軟鍵盤升降般 1:1 平滑向下推移與回升。
28. **項目堆疊列表沉厚震動回饋與 3D 時間滾輪快滑閾值提升**：新增硬體觸覺輔助工具 `TactileHaptics`（零配置預建 `VibrationEffect`；API 30+ 優先使用低頻沉厚 `PRIMITIVE_LOW_TICK` 與邊界 `PRIMITIVE_THUD`，API 26–29 降級為 `18ms`/`26ms` 單發脈衝並尊重系統觸覺開關），於**項目堆疊列表**實作跨項目步距沉厚震動（`32ms` 節流，程式自動捲動不誤觸）及頂底邊界過捲動／甩動撞牆回饋；同時將**3D 時間滾輪**進入 5 倍速快速滑動的門檻由 `450 dp/s` 提高至 `950 dp/s` 並微調速度平滑係數，大幅降低微調分鐘時的誤觸跳速。
29. **提醒實心同色鈴鐺、左側項目提醒時間與資訊卡提醒鈕連動**：**項目狀態窗・提醒面**於提醒啟用時改為純實心鈴鐺（`Icons.Rounded.Notifications`）且改採與「待備」文字同色（`onTertiaryContainer`，移除藍色）；**項目提醒時間**由**項目名稱**下方移至狀態窗左側，以單行等寬數字搭配**項目編輯鈕**同調中性色（`onSurfaceVariant`）呈現並保留點擊編輯與長按略過／取消；**資訊卡提醒鈕**於清單提醒啟用時維持藍色高亮，若清單本身未設提醒但該清單內尚有未封存項目啟用提醒時，**資訊卡提醒鈕**維持中性底色並自動將鈴鐺圖示轉為實心。
30. **繁體中文全面檢核、49 項 UI 正式命名定案與交接文件建置**：徹底校對 `values-zh` 與 `values-b+zh+Hant` 字串資源並統一台灣繁體中文慣用語（「設定面板」、「檔案」、「通知」、「擴充座」等，三語系 224 項鍵值 100% 一致）；核對定案 [docs/COMPONENTS.md](docs/COMPONENTS.md) 49 項 UI 元件正式名稱，全面同步 `SPEC.md`、`Milestone.md`、`README.md`、`AGENT.md` 與 `WORKFLOW.md`，並新建 [HANDOFF.md](HANDOFF.md) 供下一階段開發無縫交接。

### 🔹 後續待辦方向（M10+ 預留）
* **清單資訊卡**版面配置自訂（對接**資訊卡編輯鈕**長按 `onLongClickRenameChecklist` 擴充接口）與**資訊卡設定面板**進階動效細節。
* 標籤類型分類與進階多標籤篩選列（對接 `TagCatalog`、`ChecklistFilterState` 與 `category` / `priority`）。
* **項目拖曳手把**左滑置底視覺回饋與過渡動畫（實作 `ItemMoveToBottomTransition.kt` 中之 `DragSwipeBottomTransitionSpec`）。
* 項目歷程統計檢視（`item_status_logs`）與清單匯出備份（Markdown / CSV）。



