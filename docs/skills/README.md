# 動效技能庫索引（Compose Motion Skills）

由 Total Check 專案內建 Jetpack Compose 動效逆向提煉，共 13 個去業務耦合、可跨專案複用的 `SKILL.md`。格式參考 [advanced-interaction-design](../advanced-interaction-design/SKILL.md)。

| # | 技能名稱 | 檔案路徑 | 核心物理參數摘要 | 適用場景 |
| :-: | :--- | :--- | :--- | :--- |
| 1 | Liquid Tab Indicator<br>液態分段指示器 | [liquid-tab-indicator](liquid-tab-indicator/SKILL.md) | 前後緣雙 `Animatable`；fast `ζ0.82 / k550`、slow `ζ0.88 / k380`；雙層文字 `clipPath` 反色；觸控暗化 `0.08` | 2~4 段模式切換、設定分段控制 |
| 2 | Expanding Tag Selection<br>標籤擠開與遠距預熱 | [expanding-tag-selection](expanding-tag-selection/SKILL.md) | 選中 `1.08f`；鄰距 `+4.dp×t`；重排 `ζ0.78 / k460`；遠距過渡 `320ms`；`DstIn` 32dp 漸層 | Pager 頁籤、可排序標籤列 |
| 3 | Radial Theme Transition<br>多波次圓形擴散 | [radial-theme-transition](radial-theme-transition/SKILL.md) | 快照 `0.6f`；`R=hypot(max(X,W−X),max(Y,H−Y))`；`400ms` (200~700) `EaseOut`；`Mutex` 防抖；多波疊加 | 亮暗主題切換、整頁視圖互換 |
| 4 | Slide-Out Cancel Tactile<br>按壓下沉與滑出取消 | [slide-out-cancel-tactile](slide-out-cancel-tactile/SKILL.md) | `0.94f` + `1.5.dp`；按下 `ζ0.85/k920`；回彈 `ζ0.82/k620`；鎖定 `70ms`；`44.dp`、指數 `1.35` | 所有按鈕/Chip/Tab 觸控回饋 |
| 5 | IME-Anchored Scoped Modal<br>鍵盤高度記憶定錨彈窗 | [ime-anchored-scoped-modal](ime-anchored-scoped-modal/SKILL.md) | 預設 IME `300.dp`、穩定 `180ms`；抬升 `ime×0.5`；`0.92f` + `20.dp`；遮罩 `200ms`、面板 `250ms`、退場 `150ms` | 輸入型 Dialog / Sheet |
| 6 | Startup Skeleton Shimmer<br>幀同步微光骨架屏 | [startup-skeleton-shimmer](startup-skeleton-shimmer/SKILL.md) | `withFrameNanos` 單時鐘；週期 `1000ms`；錯峰 `80ms×idx`；最短顯示 `700ms` | 冷啟動/首次載入占位 |
| 7 | Stacked Card Scroll<br>雙向卡片深度堆疊 | [stacked-card-scroll](stacked-card-scroll/SKILL.md) | `MAX_STACK_DEPTH 3`；縮放 `0.04/層`；peek `8.dp`；重排 `ζ0.8/k380`；展開捲動 `ζ0.85/k340` | 卡片/待辦長列表 |
| 8 | Overscroll Accordion Damping<br>邊界手風琴阻尼 | [overscroll-accordion-damping](overscroll-accordion-damping/SKILL.md) | 斜率 `0.55f`；max `72.dp`；整體/拉伸 `0.76/0.24`；範圍 5 項；回彈 `ζ1.0/k420`；Fling 係數 `0.55` | 自訂捲動容器邊界回饋 |
| 9 | Drag-to-Reorder<br>懸浮拖拽排序與虛線槽位 | [drag-to-reorder](drag-to-reorder/SKILL.md) | 升起 `1.06f`/`1.03f`；虛線 `[12,8]`/`[14,10]`；讓位 `ζ0.78/k460`；落位 `ζ0.8/k420`；邊緣捲動 `280.dp/s`、`t²` | 頁籤/清單/網格排序 |
| 10 | Staggered Bulk Selection<br>錯峰波浪批量切換 | [staggered-bulk-selection](staggered-bulk-selection/SKILL.md) | 延遲 `20+idx×35ms`（≤560）；有效窗 `delay+450ms`；脈衝 `1.045f`（`ζ0.7/k900` → `ζ0.45/k360`） | 全部重置/批量取消勾選 |
| 11 | Spring Stepper Progress<br>進度條物理過衝回彈 | [spring-stepper-progress](spring-stepper-progress/SKILL.md) | 進度 `ζ0.65/k320`（clamp 0..1）；過衝形變 `x0.08/y0.45`；徽章 `1.18/0.90` → `ζ0.4/k420` | 任務/步驟/上傳進度條 |
| 12 | Hold-to-Delete Perimeter Ring<br>長按外圈進度環刪除 | [hold-to-delete-perimeter-ring](hold-to-delete-perimeter-ring/SKILL.md) | 按住 `150ms`；填滿 `1000ms` 線性；退回 `≤300ms`（80~300）；退出 `160ms` / `0.88f`；`PathMeasure` | 免對話框的危險操作 |
| 13 | Neighbor Ripple Wave<br>鄰近項目波紋連動 | [neighbor-ripple-wave](neighbor-ripple-wave/SKILL.md) | 層延遲 `35ms`；持續 `320ms`；衰減 `exp(−0.48(d−1))`；最大距離 `9`；同時 `3` 波；推移 `6.dp` | 勾選/開關列表的連動回饋 |

## 通用約定

- **零重組**：動畫值以 `() -> Float` 或單一 `Animatable` 傳遞，只在 `graphicsLayer` / `drawBehind` / `drawWithContent` / `Layout` placement 讀取。
- **ReducedMotion**：每個技能都定義降級（`snapTo`、取消縮放/位移、略過波次）。
- **無障礙**：保留語意、鍵盤操作與自訂動作；手勢型動效必須有非手勢替代。
- **來源標註**：少數規格值（`DistantTabTransition` 320ms、批量延遲公式、骨架 700ms 門檻、進度彈簧）位於指定 8 個檔案之外的呼叫端，各 `SKILL.md` 已標明為規格值。
