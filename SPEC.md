# TotalCheck — 開發規格書 (SPEC.md)

## 1. 專案目標與核心原則
- **核心定位**：純離線（無須網路權限）、極速清點的隨身工具檢查板 Android App。
- **操作哲學**：最少點擊次數完成清點（Single-Screen 單頁架構，捨棄多層頁面跳轉與繁瑣彈窗）。
- **互動體感**：跟手（1:1 手勢追蹤）、靈動（Spring 物理阻尼）、無縫（狀態平滑插值）、邏輯合理（未帶項目視覺優先）。

---

## 2. 功能範圍

### 核心功能（MVP — 主程式優先鎖定）
- [x] **多清單管理**：建立、重新命名、刪除清單，支援水平滑動（`HorizontalPager`）與頂部膠囊 Tab 點擊快速切換。
- [x] **項目極速 CRUD**：底部常駐輸入列連續新增項目（保持鍵盤開啟並自動上滑至頂部）、整列點擊切換勾選、行內編輯名稱、刪除與 `Snackbar` 復原（`Curved Card Deletion` 左滑動效預定於 M4 強化）。
- [x] **一鍵重置（Reset All）**：無二次確認彈窗立即歸零當前清單勾選狀態並提供底部 `Snackbar` `復原 (Undo)` 機制（錯峰波浪動效預定於 M4 疊加）。
- [x] **狀態持久化**：Room Database 本地儲存，維持項目原始順序（點擊勾選原地狀態轉場，不隨勾選沉底），並提供「只看未帶」過濾切換。
- [x] **雙軌色彩與主題引擎**：內建專為 HyperOS / 非標準 Monet 系統調校的高對比亮/暗色盤、原生 Material 3 Dynamic Color 切換開關，以及 `ThemeConfig?` 冷啟動零閃爍初始化。
- [ ] **桌面 Widget（UI/UX 待定稿，架構先行解耦）**：預留 `WidgetSyncManager` 觸發介面與 `DataStore` 狀態欄位，待小工具視覺與佈局定案後獨立實作 `widget/` 模組。

### 擴充功能（後續迭代，底層欄位已預埋）
- [ ] **分類 / 標籤篩選**：支援項目分類標籤與 `Expanding Tag Selection` 擠開動效。
- [ ] **自訂拖拽排序**：支援 `Drag-to-Reorder` 長按拖曳調整順序。
- [ ] **清單匯出 / 匯入**：支援 CSV / 純文字格式匯出與備份。

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
* 在 `ChecklistRepository` 每次完成寫入（新增、勾選、刪除、重置）後，統一呼叫 `WidgetSyncManager.notifyDataChanged(context)`（目前可先留空實作或僅發送基礎刷新，未來無縫對接 Glance）。

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
| **1. 亮暗主題切換** | **Radial Theme Transition**<br>

<br>(圓形主題切換) | 點擊頂部主題切換鈕時，擷取真實觸控座標 `(x, y)` 為圓心，計算至螢幕最遠角距離為最大半徑，使用 `clipPath` 圓形遮罩擴散揭示新主題圖層，不縮放頁面內容。 |
| **2. 清單組別切換** | **Gesture-Driven Pager**<br>

<br>(1:1 跟手分頁) | 頂部 `PrimaryScrollableTabRow` 與下方 `HorizontalPager` 雙向綁定；手指左右滑動時頁面與 Tab 指示器 1:1 跟手插值；長按 Tab 可觸發編輯/刪除該清單。 |
| **3. 清點進度條推進** | **Spring Stepper Progress**<br>

<br>(步驟條過衝回彈) | 位於 Tab 下方的進度條（顯示 `尚缺 X 項 / 共 Y 項`）。當勾選項目使進度增加時，進度條先微幅過衝（Overshoot，`dampingRatio = 0.65f`）再回彈穩定至目標比例。 |
| **4. 單項勾選與原地轉場** | **Press Scale + In-Place State Transition**<br>

<br>(按壓回饋與原地狀態轉場) | 點擊整列任意處即可勾選。按壓瞬間卡片縮放至 `0.97f`，放開回彈 `1.0f` 並觸發系統震動（`HapticFeedbackType.TextHandleMove`）；勾選後卡片**保持原位不動**，僅針對卡片背景色、文字透明度（`0.45f`）、刪除線以及「待備 / 已備」膠囊標籤進行平滑色彩與狀態過渡；進度列旁提供「只看未帶」FilterChip，開啟時透過 `Modifier.animateItem()` 平滑隱藏已勾選項目。 |
| **5. 一鍵重置清單** | **Staggered Bulk Selection**<br>

<br>(錯峰批量狀態更新) | 點擊「重置」按鈕時，DB 狀態立即歸零，但列表項目的取消勾選視覺與卡片亮起效果由第一項往下依序錯峰（每項延遲 `25ms~35ms`）帶微幅彈性放大執行，同時底部彈出 Snackbar 提供 `Undo` 復原。 |
| **6. 滑動刪除項目** | **Curved Card Deletion**<br>

<br>(卡片曲線刪除) | 左滑項目超過刪除閾值後，卡片沿微弧線軌跡向垃圾桶圖標移動，過程中同步縮小（Scale 降為 `0.8f`）、微幅旋轉並淡出（Opacity 降為 `0f`），動畫結束後才從列表移除；未達閾值放手則彈性歸位。 |
| **7. 底部極速新增列** | **Seamless Focus Input**<br>

<br>(無縫焦點連續輸入) | 常駐於畫面底部（隨軟鍵盤 `imePadding()` 自動頂起）。輸入名稱按下鍵盤 `Done` 後，**保持鍵盤開啟不收起**，輸入框瞬間清空，新項目於列表頂部以彈簧動畫展開滑入。 |
| **8. 後續擴充預留** | **Expanding Tag Selection** & **Drag-to-Reorder** | 標籤篩選列選中時微幅放大並平滑推開相鄰標籤；長按拖曳項目時脫離列表流，其餘項目即時計算索引並以彈簧動畫讓出空槽。 |

---

## 6. 編譯環境與安全規範

* **SDK 版本**：`minSdk = 26` / `targetSdk = 36`（相容 Android 17 / API 37 實機測試，主畫面強制啟用 `enableEdgeToEdge()` 並正確處理 `WindowInsets` 避免遮擋）。
* **簽名保護**：Release 簽名資訊獨立存放於根目錄 `keystore.properties`，由 `app/build.gradle.kts` 動態讀取；`*.jks` 與 `keystore.properties` 嚴禁納入 Git 追蹤。
