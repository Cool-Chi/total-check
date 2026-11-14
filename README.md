# TotalCheck

純本地離線、極速清點的工具檢查板 Android App。

## 技術棧
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3（雙軌色彩引擎、自訂 `StackedCardScrollLayout` 堆疊捲動與物理彈簧動效）
- **資料層**: Room Database + DataStore Preferences
- **Widget**: Jetpack Glance（架構已解耦，待 M8 接入）
- **目標平台**: Android 16 / 17（`minSdk = 26` / `targetSdk = 36`）

## 開發環境
- Windows 11 / JDK 21 / Android SDK 36
- Gradle (Kotlin DSL)
- 測試裝置: Pixel 7 / GrapheneOS (API 37)

## 簽名金鑰設定
Release 金鑰（`*.jks`）與 `keystore.properties` **不納入版本控制**。本地建置 Release APK 前，請複製 `keystore.properties.example` 為 `keystore.properties` 並填入本機路徑與密碼。

## 專案文件索引
- [SPEC.md](SPEC.md)：功能需求、資料結構與 UI/UX 動效規格書
- [Milestone.md](Milestone.md)：版本里程碑紀錄與 M7 / M8 開發規劃
- [AGENT.md](AGENT.md)：AI Agent 開發約束與自動驗證規範
- [WORKFLOW.md](WORKFLOW.md)：Antigravity 工具箱與指令手冊
