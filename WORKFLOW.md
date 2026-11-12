# Android App 開發工作流程（Antigravity Agent）

> 適用於：Antigravity IDE + Android 實體裝置開發
> 語言：Kotlin + Jetpack Compose
> 測試裝置：Pixel 7 / GrapheneOS / Android 17（API 37）

---

## 環境概覽

| 項目 | 規格 |
|---|---|
| OS | Windows 11 |
| JDK | 21（全局安裝） |
| Android SDK | Platform 36 + Build-tools 36.0.0 |
| 裝置 | Pixel 7，GrapheneOS，API 37，USB 偵錯已開啟 |
| IDE | Antigravity（含 Android CLI Plugin、Figma MCP、Google Developer Knowledge Plugin） |

---

## 工具箱

| 工具 | 用途 |
|---|---|
| `android screen` | 截圖 / 查看當前畫面 |
| `android layout` | UI 層級結構分析 |
| `android docs` | 查詢官方 Android 文件 |
| `adb` | 裝置控制、安裝 APK、啟動 Activity |
| `.\gradlew :app:installDebug` | 建置並安裝 Debug APK |
| `.\gradlew :app:assembleRelease` | 打包 Release APK |
| Figma MCP | 讀取 Figma 設計稿 → 生成 Composable |

---

## 開發循環（每個功能）

```
1. 使用者提出任務需求
        ↓
2. Agent 實作（寫 Kotlin/Compose 程式碼，修改 Gradle）
        ↓
3. Agent 執行 gradlew installDebug + adb am start 安裝到手機
        ↓
4. 使用者在 Android Device Hub 右側面板查看畫面 / 操作驗證
        ↓
5. 使用者給出反饋（功能正確 or 需修改）
        ↓
6. Agent 修改 → 重複步驟 3-5，直到通過
        ↓
7. git commit（atomic，一功能一 commit）
```

---

## 專案結構（Android）

```
total-check/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/totalcheck/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/           # Composable 畫面
│   │   │   ├── data/         # Room DB、Repository
│   │   │   └── widget/       # Jetpack Glance Widget
│   │   └── res/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── .gitignore
├── README.md
├── SPEC.md
└── WORKFLOW.md  ← 本文件
```

---

## 技術棧選型

| 層級 | 選擇 | 備註 |
|---|---|---|
| UI | Jetpack Compose + Material 3 | 不用 XML layout |
| 狀態管理 | ViewModel + StateFlow | |
| 資料持久化 | Room Database | 取代 SharedPreferences |
| 桌面 Widget | Jetpack Glance | Compose 風格寫 Widget |
| 動畫 | AnimatedVisibility / animate*AsState | |
| 主題 | Material 3 Dynamic Color | |

---

## 打包 Release APK

1. 建立 Keystore（一次性）
   ```powershell
   keytool -genkeypair -v -keystore my-release-key.jks -keyAlg RSA -keySize 2048 -validity 10000 -alias my-key-alias
   ```

2. 在 `app/build.gradle.kts` 配置簽名（密碼填入後不入 git）
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("C:\\Users\\weiqi\\my-release-key.jks")
           storePassword = "YOUR_STORE_PASSWORD"   // ← 填入
           keyAlias = "my-key-alias"
           keyPassword = "YOUR_KEY_PASSWORD"       // ← 填入
       }
   }
   ```

3. 打包
   ```powershell
   .\gradlew :app:assembleRelease
   ```
   輸出：`app/build/outputs/apk/release/app-release.apk`

> ⚠️ `*.jks` 和密碼絕對不能 push 到 GitHub

---

## UI 設計流程（Figma → Compose）

1. 在 Figma 建立設計稿（建議使用 Auto Layout + Material 3 Kit）
2. 提供 Figma 節點 URL 給 Agent
3. Agent 透過 Figma MCP 讀取設計 → 生成對應 `@Composable`
4. 安裝到手機驗證

---

## Git 規範

```
feat: 新功能
fix: 修復問題
chore: 環境/設定調整
refactor: 重構（不改功能）
```

每個功能一個 commit，保持原子性。

---

## 常見問題排解

| 問題 | 解法 |
|---|---|
| `android run` 失敗 | 改用 `.\gradlew :app:installDebug` + `adb shell am start` |
| SDK 版本找不到 | 用 `platforms/android-36`，targetSdk=36 |
| Widget 不更新 | 移除 App 重新安裝，或 `adb shell am broadcast` 強制更新 |
| 簽名錯誤 | 確認 keystore 路徑和密碼正確，storeFile 用絕對路徑 |

---

## 插件 / 工具安裝清單（Antigravity）

- [x] Android CLI Plugin
- [x] Figma MCP
- [x] Google Developer Knowledge Plugin
- [ ] Firebase（未來雲端同步用）
