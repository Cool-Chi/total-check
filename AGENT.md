# TotalCheck — AI Agent 開發規範 (AGENT.md)

## 1. 專案身份與真相來源 (Source of Truth)
- **專案名稱**：TotalCheck（純本地離線工具清點板 Android App）
- **套件名稱**：`com.example.totalcheck`
- **必讀基準**：每次執行任務前，必須優先對齊 `SPEC.md`（功能與動效規格）與 `WORKFLOW.md`（環境與工具鏈）。若存在互動動效需求，必須查閱 `docs/advanced_interaction_design.md`。
- **模型協作原則**：日常 UI 元件、CRUD 邏輯與編譯驗證由主力模型快速推進；涉及複雜手勢軌跡、自訂 `clipPath` 遮罩或跨進程同步時，需嚴格遵守物理數學邊界，不允許使用簡陋的固定時間線性動畫敷衍。

## 2. 開發環境與部署驗證邊界 (Windows 11 + Pixel 7)
- **終端機環境**：Windows 11 PowerShell，所有 Gradle 指令必須使用 `.\gradlew` 格式。
- **最高優先級約束 — 部署與驗證邊界**：每次修改程式碼後，Agent 僅執行以下兩項指令將應用部署至實機並結束任務，**嚴禁自行呼叫 `android screen`、`android layout` 或任何 UI 自動化點擊/輸入測試**，統一交由使用者在側邊欄鏡像螢幕手動驗證：
  1. `.\gradlew :app:installDebug` 編譯並安裝至實機。
  2. `adb shell am start -n com.example.totalcheck/.MainActivity` 啟動應用。

## 3. 技術棧與開發紅線 (Hard Constraints)
1. **純離線架構**：`AndroidManifest.xml` **嚴禁**宣告 `android.permission.INTERNET` 或引入任何網路請求套件。
2. **原生 Material 3 開發**：不使用 XML Layout，也不等待外部 Figma 設計稿，所有介面直接使用 `androidx.compose.material3` 實作。
3. **API 36/37 全螢幕適配**：`minSdk = 26`、`targetSdk = 36`，實機測試為 Android 17 (API 37)。`MainActivity` 必須啟用 `enableEdgeToEdge()`，並正確處理 `WindowInsets`（狀態列、導覽列與 `imePadding()`）。
4. **雙軌色彩引擎**：必須同時支援「預設精調亮/暗色盤（適配 HyperOS）」與「原生 Material 3 Dynamic Color 切換開關」，禁止寫死單一顏色值（如 `Color.Black` / `Color.White`）。
5. **物理動效基準**：優先使用 `spring(dampingRatio = 0.8f, stiffness = 380f)` 物理彈簧模型，並尊重系統無障礙動畫縮放設定（Reduced Motion Fallback）。
6. **資料庫防鎖死**：`AppDatabase` 必須實作 Singleton 模式，並透過單一 `ChecklistRepository` 存取，為未來桌面 Widget 跨進程讀寫保留擴充性。

## 4. 安全與版本控制規範
- **金鑰隔離**：`app/build.gradle.kts` 必須從根目錄的 `keystore.properties` 讀取 Release 簽名設定，**嚴禁**在任何追蹤檔案中寫入明文密碼或金鑰路徑。
- **最高優先級約束 — Git 版控紀律**：所有任務指令結束後，僅在確定通過使用者或實機驗證時，依指示呼叫本地 `git-workflow-and-versioning` Skill（`C:\Users\weiqi\.gemini\config\skills\git-workflow-and-versioning\SKILL.md`）執行原子化提交與版本標記；**嚴禁**未經授權手動執行 `git commit` 或 `git tag`。
