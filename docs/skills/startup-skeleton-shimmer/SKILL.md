---
name: startup-skeleton-shimmer
description: 以 withFrameNanos 單一時鐘驅動的零重組微光掃描骨架屏，各區塊按序錯峰，並以 700ms 最短顯示門檻防止冷啟動畫面閃爍。
---

# Startup Skeleton Shimmer | 幀同步微光骨架屏

> 來源：`SkeletonShimmer.kt`（`TotalCheckSkeletonShimmer`、`shimmerBlock`）。
> 注意：**700ms 最短顯示門檻不在此檔**，屬於持有「是否已載入」狀態的上層邏輯；下方以通用寫法補上，標示為規格值。

## 核心作用與適用場景

- **變化起點**：App 冷啟動或資料首次載入，畫面骨架立即出現。
- **操作落點**：無互動；骨架以 `liveRegion = Polite` 向無障礙服務宣告載入中。
- **誰跟著回應**：所有骨架區塊共用同一個時鐘，依 `blockIndex × staggerMillis` 依序起跑。
- **周圍是否讓位**：骨架佈局 1:1 複製真實 UI（隱形 `alpha(0f)` 的真實文字撐開尺寸），載入完成後不產生位移跳動。
- 適用：列表/卡片/分頁列初次載入、任何需要避免「空白 → 內容」閃爍的畫面。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 時鐘 | 單一 `elapsedMillis: MutableLongState`，由 `LaunchedEffect` 內 `while(true){ withFrameNanos{ ... } }` 每幀更新：`(frameNanos − startNanos) / 1_000_000` |
| 掃描週期 | `durationMillis = 1000` |
| 錯峰 | `staggerMillis = 80`；`delayMs = blockIndex × 80`；`localElapsed = elapsed − delayMs`，小於 0 不畫微光 |
| 進度 | `progress = (localElapsed % duration) / duration`（循環） |
| 光帶位置 | `startX = lerp(−2w, 2w, progress)`，`endX = startX + 2w`；`Brush.linearGradient(0f→Transparent, 0.5f→sheen, 1f→Transparent)` 水平方向 |
| 區塊編號 | 分頁列 `0..1`；進度模組 `2..3`；清單列 `4 + rowIndex.coerceAtMost(3)`（上限 7） |
| 色彩 | 深色：區塊 `White@0.06`、光帶 `White@0.10`；淺色：區塊 `RGB(9,11,16)@0.09`、光帶 `White@0.20` |
| 標題寬度偽隨機 | `0.45 + (len.coerceIn(2,16)/16) × 0.42`，夾在 `0.45..0.87` |
| 最短顯示門檻（規格） | `MIN_SKELETON_MS = 700`：`remaining = max(0, 700 − (now − shownAt))`，資料就緒後 `delay(remaining)` 才切換真實畫面 |
| ReducedMotion | 不啟動時鐘、`elapsed = 0`；只畫靜態底色 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **時鐘 state 不被 Composition 讀取**：只暴露 `elapsedProvider = remember { { elapsedMillis } }`，lambda 於 `drawBehind` 內才讀取，因此每幀只失效 Draw，不重組任何骨架區塊。
2. **`withFrameNanos` 與 vsync 對齊**：動畫相位由實際幀時間決定，掉幀時不累積誤差。
3. **一個時鐘服務所有區塊**：沒有 N 個 `InfiniteTransition`；錯峰靠公式減法。
4. **`clip(shape)` + `drawBehind`**：不建立額外 Layer；光帶只用一個 `Brush`。
5. **骨架語意收斂**：外層一次宣告 `stateDescription`，內部全部 `clearAndSetSemantics {}`。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
private const val MIN_SKELETON_MS = 700L

@Composable
fun rememberShimmerClock(enabled: Boolean): () -> Long {
    var elapsed by remember { mutableLongStateOf(0L) }
    if (enabled) LaunchedEffect(Unit) {
        var start = 0L
        while (true) withFrameNanos { n ->
            if (start == 0L) start = n
            elapsed = ((n - start) / 1_000_000L).coerceAtLeast(0L)
        }
    } else elapsed = 0L
    return remember { { elapsed } }                  // lambda：僅在 Draw 階段呼叫
}

fun Modifier.shimmerBlock(
    index: Int, base: Color, sheen: Color, reduced: Boolean, clock: () -> Long,
    shape: Shape, durationMs: Int = 1000, staggerMs: Int = 80,
): Modifier = clip(shape).drawBehind {
    drawRect(base)
    if (reduced || size.width <= 0f || durationMs <= 0) return@drawBehind
    val local = clock() - index.toLong() * staggerMs
    if (local < 0L) return@drawBehind
    val progress = (local % durationMs).toFloat() / durationMs
    val startX = lerp(-2f * size.width, 2f * size.width, progress)
    drawRect(Brush.linearGradient(
        colorStops = arrayOf(0f to Color.Transparent, 0.5f to sheen, 1f to Color.Transparent),
        start = Offset(startX, 0f), end = Offset(startX + 2f * size.width, 0f)))
}

/** 最短顯示時間門檻：資料太快就緒也要讓骨架至少停留 700ms */
@Composable
fun rememberSkeletonGate(dataReady: Boolean): Boolean {
    val shownAt = remember { SystemClock.uptimeMillis() }
    var show by remember { mutableStateOf(true) }
    LaunchedEffect(dataReady) {
        if (dataReady) {
            delay((MIN_SKELETON_MS - (SystemClock.uptimeMillis() - shownAt)).coerceAtLeast(0L))
            show = false
        }
    }
    return show
}
```

## Copyable Implementation Prompt (English)

```text
Implement a startup skeleton screen with a frame-synchronized shimmer in Jetpack Compose. Use ONE clock: a mutableLongStateOf elapsedMillis updated inside a LaunchedEffect loop with withFrameNanos ((frameNanos - startNanos) / 1_000_000). Expose it to children only as a () -> Long lambda that is invoked inside drawBehind, so the clock never triggers recomposition. Write a Modifier.shimmerBlock(blockIndex, baseColor, sheenColor, shape, ...) that clips to the shape, fills the base color, then computes localElapsed = elapsed - blockIndex * 80ms; if negative draw nothing extra; otherwise progress = (localElapsed % 1000) / 1000, startX = lerp(-2w, 2w, progress), endX = startX + 2w, and draw a horizontal linearGradient with stops 0 Transparent, 0.5 sheen, 1 Transparent. Colors: dark theme base White@6%, sheen White@10%; light theme base rgb(9,11,16)@9%, sheen White@20%. Assign block indexes top to bottom (tab strip 0-1, summary card 2-3, list rows 4 + min(row, 3)). Mirror the real layout 1:1 by placing the real text with alpha(0f) inside each block so no layout shift happens when real content replaces the skeleton; vary placeholder title width as 0.45 + (labelLength.coerceIn(2,16)/16)*0.42, clamped to 0.45..0.87. Add a minimum display gate: record the uptime when the skeleton first appears; when data becomes ready, delay for max(0, 700ms - elapsedSinceShown) before switching to real content, so cold starts never flash. Use cached real data for labels when available. Semantics: the container sets liveRegion = Polite plus a loading stateDescription, every inner block uses clearAndSetSemantics {}. ReducedMotion: do not start the clock, draw only the static base color. Disable user scrolling inside the skeleton lists and make sure it is not focusable.
```
