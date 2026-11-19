# TotalCheck — UI 元件正式命名對照表 (`docs/COMPONENTS.md`)

> **狀態：已核對定案（49 項正式名稱）**
> 本文件為 TotalCheck 全介面 UI 元件之**唯一正式命名基準**，對應 [SPEC.md](../SPEC.md)、[HANDOFF.md](../HANDOFF.md) 與程式碼實作。
> **溝通規範**：後續需求討論、規劃書、程式碼註解與文件更新，凡提及下列任何 UI 元件，一律統一使用本表之「**正式名稱**」，以確保開發與 AI 協作零歧義。

---

## 1. 全頁容器與頂部標題列

| 編號 | 正式名稱 | 程式碼元件 / `testTag` | 畫面位置與功能簡述 |
| --- | --- | --- | --- |
| `UI-01` | **主畫面根容器** | `TotalCheckScreen` | 整個 App 單頁根容器（含主題圓形轉場、清單分頁與彈窗遮罩宿主） |
| `UI-02` | **啟動骨架屏** | `TotalCheckSkeletonShimmer` | 冷啟動載入首幀資料時的微光掃描佔位畫面（最少 `700ms` 銜接） |
| `UI-03` | **頂部標題列** | `TopAppHeaderBar` | 畫面最上方品牌標題與主題控制列 |
| `UI-04` | **頂欄品牌標題** | `TopAppHeaderBar` (`header_brand_title`) | 左上角「隨身清點板」品牌標題文字 |
| `UI-05` | **頂欄取色鈕** | `TopAppHeaderBar` (`dynamic_color_button`) | 頂部標題列右側調色盤按鈕，切換精調色盤 / Material You 動態取色 |
| `UI-06` | **頂欄主題鈕** | `TopAppHeaderBar` (`theme_toggle_button`) | 頂部標題列最右側太陽 / 月亮按鈕，觸發圓形波紋亮暗主題切換 |

---

## 2. 頂部清單頁籤列

| 編號 | 正式名稱 | 程式碼元件 / `testTag` | 畫面位置與功能簡述 |
| --- | --- | --- | --- |
| `UI-07` | **清單頁籤列** | `ExpandingChecklistTabBar` | 頂部標題列下方可橫向捲動的膠囊頁籤導覽列（右側尾部 `32.dp` 漸層淡化） |
| `UI-08` | **清單頁籤** | `ChecklistTagPill` | 單一清單膠囊頁籤；點擊切換、長按拖曳排序、雙擊已選中頁籤進入行內改名 |
| `UI-09` | **頁籤改名輸入框** | `InlineEditingChecklistTag` | 雙擊已選中**清單頁籤**後原地展開的行內文字輸入框與確認鍵 |
| `UI-10` | **頁籤新增鈕** | `ExpandingChecklistTabBar` (`add_checklist_button`) | 固定於**清單頁籤列**最右側的圓形「+」按鈕，點擊開啟**新增清單彈窗** |

---

## 3. 清單資訊卡與設定面板

| 編號 | 正式名稱 | 程式碼元件 / `testTag` | 畫面位置與功能簡述 |
| --- | --- | --- | --- |
| `UI-11` | **清單資訊卡** | `CapsuleProgressModule` | **清單頁籤列**下方的主狀態卡片；長按非按鈕區 `350ms` 可將高度 `1x ⇄ 2x` 平滑翻倍展開**資訊卡設定面板** |
| `UI-12` | **資訊卡進度摘要** | `CapsuleProgressModule` | **清單資訊卡**左上角「尚缺 X / 共 Y 項」或「已備齊」狀態文字 |
| `UI-13` | **資訊卡進度條** | `CapsuleProgressModule` | **清單資訊卡**中段具備過衝彈簧效果的水平進度條 |
| `UI-14` | **資訊卡重置鈕** | `CapsuleProgressModule` (`reset_all_button`) | **清單資訊卡**右上角「重置」膠囊按鈕（寬度鎖定對齊下方兩顆圓鈕 `88.dp`） |
| `UI-15` | **資訊卡篩選鈕** | `CapsuleProgressModule` (`filter_unchecked_button`) | **清單資訊卡**左下 `40.dp` 圓鈕；短按切換「只看未帶」，長按開啟**標籤篩選彈窗**（選定後動態替換為對應實心／空心標籤圖示） |
| `UI-16` | **資訊卡提醒鈕** | `CapsuleProgressModule` (`checklist_reminder_button`) | **清單資訊卡**右下 `40.dp` 圓鈕（左 1）；清單啟用提醒時藍色高亮，僅清單內有項目啟用提醒時維持中性底色並自動轉為實心鈴鐺 |
| `UI-17` | **資訊卡編輯鈕** | `CapsuleProgressModule` (`rename_checklist_button`) | **清單資訊卡**右下 `40.dp` 圓鈕（左 2，鉛筆）；短按開啟**清單改名彈窗**，長按預留資訊卡版面配置擴充接口 |
| `UI-18` | **資訊卡刪除鈕** | `CapsuleProgressModule` (`delete_checklist_button`) | **清單資訊卡**右下 `40.dp` 圓鈕（最右，垃圾桶）；開啟**清單刪除彈窗** |
| `UI-19` | **資訊卡設定面板** | `InfoCardSettingsPanelContent` | 長按**清單資訊卡**向下翻倍展開並 1:1 向下推移**項目堆疊列表**之內嵌面板 |
| `UI-20` | **設定面板分頁列** | `InfoCardSettingsPanelContent` | **資訊卡設定面板**頂欄並列之「全域（實心）」、「清單（空心）」、「封存」三頁與關閉鈕 (`✕`) |
| `UI-21` | **設定面板標籤管理區** | `InfoCardSettingsPanelContent` | **資訊卡設定面板**「全域／清單」分頁內的圖示選擇器、新標籤輸入列、建立鈕與可刪除標籤晶片列表 |
| `UI-22` | **設定面板封存區** | `InfoCardSettingsPanelContent` | **資訊卡設定面板**「封存」分頁內的封存項目列表（含單項還原與永久刪除按鈕） |

---

## 4. 項目堆疊列表與項目卡片

| 編號 | 正式名稱 | 程式碼元件 / `testTag` | 畫面位置與功能簡述 |
| --- | --- | --- | --- |
| `UI-23` | **項目堆疊列表** | `BidirectionalStackedCardList` | **清單資訊卡**下方自訂佈局列表，具頂底 3 層立體堆疊、跨項沉厚震動與過捲動回彈 |
| `UI-24` | **項目卡片** | `CapsuleCheckItemRow` | 單一清單項目膠囊卡片；點擊整列原地勾選、長按觸發**項目長按刪除環**、啟用自動復原時外框與**項目狀態指示條**轉藍 |
| `UI-25` | **項目狀態指示條** | `CapsuleCheckItemRow` | **項目卡片**最左側垂直細條（待備強調色 / 已備淡化 / 自動復原藍色） |
| `UI-26` | **項目名稱** | `CapsuleCheckItemRow` | **項目卡片**中段品名文字（已備時淡化並加刪除線） |
| `UI-27` | **項目提醒時間** | `CapsuleCheckItemRow` | **項目狀態窗・提醒面**左側單行等寬提醒時間（與**項目編輯鈕**同色，支援點擊編輯與長按略過／取消） |
| `UI-28` | **項目狀態窗・提醒面** | `CapsuleCheckItemRow` (`reminder_item_button_*`) | `64.dp` 狀態膠囊正面；待備顯示「待備 + 實心／空心同色鈴鐺」（短按開啟**提醒排程彈窗**、長按快設 15 分／略過／取消），已備顯示「✓ 已備」 |
| `UI-29` | **項目狀態窗・標籤面** | `CapsuleCheckItemRow` (`reminder_item_button_*`) | 左右滑動狀態窗 3D 翻轉進入的純圖示背面（與**項目編輯鈕**同色調；0 標籤空心輪廓、1～2 標籤垂直細線分隔，全域實心／清單空心）；短按開啟**項目標籤彈窗**、長按快切該清單上一個添加的標籤 |
| `UI-30` | **項目編輯鈕** | `CapsuleCheckItemRow` (`edit_item_button_*`) | 狀態窗右側鉛筆圖示按鈕，點擊進入行內名稱編輯（並切出**項目編輯工具列**） |
| `UI-31` | **項目拖曳手把** | `CapsuleCheckItemRow` (`drag_handle_*`) | **項目卡片**最右側六點手把；上下拖曳排序、左滑超過 `56.dp` 移至清單底部 |
| `UI-32` | **項目編輯工具列** | `CapsuleCheckItemRow` | 進入行內編輯時取代右側元件的三顆按鈕：`[封存]` `[確認 ✓]` `[取消 ✗]` |
| `UI-33` | **項目拖曳佔位槽** | `DashedDragPlaceholderSlot` | 拖曳排序期間顯示於原始／目標插槽位置的虛線佔位框 |
| `UI-34` | **項目長按刪除環** | `CapsuleCheckItemRow` | 長按**項目卡片**時沿膠囊外框順時針填滿（採**資訊卡刪除鈕**同源配色）的刪除確認進度環 |

---

## 5. 底部快速輸入列與提示條

| 編號 | 正式名稱 | 程式碼元件 / `testTag` | 畫面位置與功能簡述 |
| --- | --- | --- | --- |
| `UI-35` | **底部快速輸入列** | `QuickInputBar` | 畫面最下方常駐新增項目列，隨軟鍵盤同步升降、向下捲動自動收合 |
| `UI-36` | **底部提示條** | `SnackbarHost` | 畫面下方浮動回饋提示（含刪除、重置、封存、刪標籤之「復原」操作與快速標籤提示） |

---

## 6. 懸浮彈窗與時間設定元件

| 編號 | 正式名稱 | 程式碼元件 / `testTag` | 畫面位置與功能簡述 |
| --- | --- | --- | --- |
| `UI-37` | **彈窗遮罩宿主** | `ScopedModalHost` / `ModalPanelContainer` | 全域半透明遮罩與自訂進出場動效彈窗容器（記憶鍵盤高度達成首幀零跳動定錨） |
| `UI-38` | **彈窗液態分頁列** | `LiquidTabBar` | 彈窗頂部滑動膠囊分頁切換條（用於「空白清單 / 快速匯入」及「清點提醒 / 自動復原」） |
| `UI-39` | **新增清單彈窗** | `ChecklistModalContent.CreateChecklist` | 建立空白清單，或從剪貼簿／檔案（`.txt`/`.md`/`.csv`）解析匯入清單並即時預覽 |
| `UI-40` | **清單改名彈窗** | `ChecklistModalContent.RenameChecklist` | 點擊**資訊卡編輯鈕**開啟的清單改名對話框 |
| `UI-41` | **清單刪除彈窗** | `ChecklistModalContent.DeleteChecklistConfirm` | 點擊**資訊卡刪除鈕**開啟、顯示項目數量的刪除二次確認視窗 |
| `UI-42` | **提醒排程彈窗** | `ChecklistModalContent.ConfigureReminder` / `ReminderConfigContent` | 清單級或單品級的「清點提醒 / 自動復原」雙分頁排程設定視窗 |
| `UI-43` | **3D 時間滾輪** | `TactileDrumWheel` | **提醒排程彈窗**內時／分 3D 圓柱滾輪（支援點擊直輸 4 位數與高門檻 `950 dp/s` 5 倍速快滑） |
| `UI-44` | **時間快捷與週期列** | `ReminderConfigContent` | **提醒排程彈窗**內的 `+15分 / +1時 / +4時 / 現在` 快捷列與星期循環按鈕群 |
| `UI-45` | **項目標籤彈窗** | `ChecklistModalContent.ItemTagPicker` | 短按**項目狀態窗・標籤面**開啟的合併標籤選取彈窗（最多選 2 個，以實心／空心圖示區分全域／清單） |
| `UI-46` | **標籤篩選彈窗** | `ChecklistModalContent.SelectFilterTag` | 長按**資訊卡篩選鈕**開啟的單一標籤篩選彈窗（含「全部項目」清除選項） |
| `UI-47` | **獨立封存彈窗** | `ChecklistModalContent.ArchivedItems` | 獨立彈窗版封存檢視器（日常入口已整合至**資訊卡設定面板**之**設定面板封存區**） |

---

## 7. 桌面小工具（Home Screen Widgets）

| 編號 | 正式名稱 | 程式碼元件 / 資源檔 | 畫面位置與功能簡述 |
| --- | --- | --- | --- |
| `UI-48` | **網格桌面小工具** | `TotalCheckWidgetReceiver` / `DirectWidgetRenderer` | `2x2` 以上自適應多欄桌面清點小工具（原地勾選、篩選、兩段式重置與提醒標記） |
| `UI-49` | **計數條桌面小工具** | `TotalCheckCounterWidgetReceiver` / `DirectWidgetRenderer` | `2x1`～`4x1` 單列精簡計數與進度條小工具（快速切清單與 `3x1+` 重置） |
