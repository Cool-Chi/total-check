# TotalCheck

純本地離線、極速清點的工具檢查板 Android App。

## 技術棧
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3（雙軌色彩引擎、自訂 `StackedCardScrollLayout` 堆疊捲動與物理彈簧動效）
- **資料層**: Room Database + DataStore Preferences
- **Widget**: 原生 `DirectWidgetRenderer` 雙桌面小工具（`網格桌面小工具 2x2+` 與 `計數條桌面小工具 2x1~4x1`，按 `appWidgetId` 實例解耦並即時雙向同步）
- **目標平台**: Android 16 / 17（`minSdk = 26` / `targetSdk = 36`）

## 開發環境
- Windows 11 / JDK 21 / Android SDK 36
- Gradle (Kotlin DSL)
- 測試裝置: Pixel 7 / GrapheneOS (API 37)

## 簽名金鑰設定
Release 金鑰（`*.jks`）與 `keystore.properties` **不納入版本控制**。本地建置 Release APK 前，請複製 `keystore.properties.example` 為 `keystore.properties` 並填入本機路徑與密碼。

## 專案文件索引
- [HANDOFF.md](HANDOFF.md)：專案完整交接指南（給下一輪 AI Studio Vibe-Coding 對話的架構地圖、開發紅線與啟動提示）
- [docs/COMPONENTS.md](docs/COMPONENTS.md)：全介面 49 項 UI 元件正式命名對照表（已核對定案，所有開發與溝通之唯一名稱基準）
- [SPEC.md](SPEC.md)：功能需求、資料結構與 UI/UX 動效規格書
- [Milestone.md](Milestone.md)：版本里程碑紀錄（M0～M9 結案詳錄）與 M10+ 待辦規劃
- [AGENT.md](AGENT.md)：AI Agent 開發約束與自動驗證規範
- [WORKFLOW.md](WORKFLOW.md)：Antigravity 工具箱與指令手冊
