---
name: stacked-card-scroll
description: 自訂 Layout 在視窗頂底邊緣把卡片壓縮成最多 3 層立體堆疊（縮放步階 0.04f、深度暗化），點擊壓縮卡自動捲入展開，並以彈簧補間增刪重排。
---

# Stacked Card Scroll | 雙向卡片深度堆疊滾動

> 來源：`StackedCardScrollLayout.kt`（`BidirectionalStackedCardList`、`StackedCardListState`）、`CapsuleComponents.kt`（深度暗化遮罩）。

## 核心作用與適用場景

- **變化起點**：捲動位置；離開視窗頂/底的卡片被「釘」在邊緣，依溢出距離轉成深度 `depth`。
- **操作落點**：捲動手勢；點擊壓縮卡（`depth > 0.35f`）→ `scrollToExpandItem`。
- **誰跟著回應**：邊緣卡片縮小、淡出、變暗；`slotState.depthState` 供卡片內容自行降級（如停用按鈕）。
- **周圍是否讓位**：是。增刪項目時其餘卡片用 `ItemReflowAnim` 彈簧補間滑向新位置。
- 適用：清單、待辦、訊息卡片列；需要「看得到上下還有內容」的深度提示。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 最大堆疊層數 | `MAX_STACK_DEPTH = 3f`；實際 `activeMaxDepth = min(3, maxHeadroom / peekStep)`，`maxHeadroom = (availableInnerH − 78.dp) / 2` |
| 每層露出 | `peekStep = 8.dp` |
| 縮放步階 | `SCALE_STEP_PER_LAYER = 0.04f`：`scale = max(0.75f, 1 − 0.04 × depth)`；ReducedMotion 為半步階 `0.02f` 且最多 3 層 |
| 淡出 | `FADE_OUT_DEPTH = 4f`；`depth ≤ 3` alpha=1，其後 `alpha = 1 − (depth − 3)`；`depth > 4.1` 不擺放；超出 `activeMaxDepth` 的部分以 `peekStep × 0.5` 縮進 |
| 壓縮判定 | `COMPRESSED_STACK_INTERACTION_THRESHOLD = 0.35f` |
| 堆疊釘位 | `topPinY = topBound + peek × clamp(scroll / effectiveStride, 0, activeMaxDepth)`；`bottomPinBottomY = bottomBound − peek × clamp(remainingScroll / effectiveStride, ...)`；`effectiveStride = avgStride − peekStep` |
| 深度計算 | 頂：`(topPinY − rawTop) / (h + gap)`；底：`(rawBottom − bottomPinBottomY) / (h + gap)`；取較大者決定上/下堆 |
| 變換原點 | 頂堆 `(0.5, 0f)`、底堆 `(0.5, 1f)`、正常 `(0.5, 0.5)`；`zIndex = 10 − depth` |
| 深度暗化 | 疊加黑色遮罩 `alpha = clamp(depth × step, 0, max)`：深色 `step 0.11 / max 0.42`，淺色 `step 0.055 / max 0.22`；`depth` 夾 `0..4` |
| 展開捲動 | `scrollToExpandItem(index)`：若卡片頂在上堆區 → 把它捲到 `topBound + peek × min(index, activeMax)`；否則把底部捲到 `bottomBound − peek × min(剩餘數, activeMax)`；`animateScrollDelta` 預設 `spring(0.85f, 340f)`；`\|delta\| < 1f` 忽略 |
| 置中捲動 | `scrollToCenterItem`：`spring(0.88f, 320f)` |
| 重排補間 `ItemReflowAnim` | `spring(dampingRatio = 0.8f, stiffness = 380f)`，初始偏移 `prevNaturalTop − naturalTop`；僅 key 列表變化且非 ReducedMotion 時啟動 |
| 固定展開項 | `pinnedExpandedKey`（如編輯中）：`scale=1, alpha=1, zIndex=20, depth=0`，夾在可視範圍內 |
| 鍵盤/無障礙 | PageUp/PageDown 捲動 `viewport × 0.75`；上下鍵捲動 `62.dp + gap`；`verticalScrollAxisRange` + `scrollBy` 語意 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **直接使用 `Layout` 而非 `SubcomposeLayout`**：子項目一次性組合，捲動中不做子組合。
2. **所有堆疊計算放在 placement 區塊**：`scrollOffsetState`、`visualOverscrollState`、`animFrameNanos`（`MutableFloatState/LongState`）只在 `layout { }` 內讀取；捲動與鍵盤升降 0 重組、0 重測。
3. **預配置緩衝陣列**（`placedTopYsBuffer`、`scalesBuffer`、`depthsBuffer`...）+ 容量倍增，零分配。
4. **`placeRelativeWithLayer { scaleX; scaleY; alpha; transformOrigin }`**：縮放、淡出走 GraphicsLayer。
5. **`StackedCardSlotState.depthState` 寫入帶 `0.004f` 去抖**；卡片以 `stackDepthProvider: () -> Float` lambda 讀取，暗化遮罩在 `graphicsLayer { alpha }` 內計算。
6. **逐幀動畫手動驅動**：`reflowSignal`（`DROP_OLDEST` Flow）喚醒 `withFrameNanos` 迴圈，無動畫時迴圈自動結束。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
const val MAX_DEPTH = 3f; const val FADE_DEPTH = 4f; const val SCALE_STEP = 0.04f

/** 於 layout{} placement 區塊呼叫：回傳 (placedTop, scale, alpha, originY, depth) */
fun stackItem(
    rawTop: Float, h: Float, gap: Float, peek: Float, activeMax: Float,
    topPinY: Float, bottomPinBottomY: Float, enabled: Boolean,
): FloatArray {
    val stride = (h + gap).coerceAtLeast(1f)
    val topD = if (enabled && rawTop < topPinY) (topPinY - rawTop) / stride else 0f
    val botD = if (enabled && rawTop + h > bottomPinBottomY) (rawTop + h - bottomPinBottomY) / stride else 0f
    val isTop = topD > 0f && topD >= botD; val isBottom = !isTop && botD > 0f
    val depth = if (isTop) topD else if (isBottom) botD else 0f
    val scale = (1f - SCALE_STEP * depth.coerceIn(0f, FADE_DEPTH)).coerceAtLeast(0.75f)
    val alpha = if (depth <= MAX_DEPTH) 1f else (1f - (depth - MAX_DEPTH)).coerceIn(0f, 1f)
    val vis = depth.coerceAtMost(activeMax); val tuck = (depth - activeMax).coerceAtLeast(0f) * peek * 0.5f
    return when {
        isTop -> floatArrayOf(topPinY - peek * vis + tuck, scale, alpha, 0f, depth)
        isBottom -> floatArrayOf(bottomPinBottomY + peek * vis - tuck - h, scale, alpha, 1f, depth)
        else -> floatArrayOf(rawTop, scale, alpha, 0.5f, depth)
    }
}

@Composable
fun <T> StackedList(items: List<T>, key: (T) -> Any, state: StackedState, peek: Dp = 8.dp, gap: Dp = 10.dp,
                    row: @Composable (T, StackedSlot) -> Unit) {
    Layout(Modifier.fillMaxSize().clipToBounds().scrollable(state.scrollable, Orientation.Vertical, reverseDirection = true),
        content = { items.forEach { key(key(it)) { row(it, state.slot(key(it))) } } }) { ms, c ->
        val ps = ms.map { it.measure(c.copy(minHeight = 0)) }
        val tops = FloatArray(ps.size); var y = 0f
        ps.forEachIndexed { i, p -> tops[i] = y; y += p.height + gap.toPx() }
        layout(c.maxWidth, c.maxHeight) {
            state.frameTick.longValue                               // 只在 placement 訂閱
            val peekPx = peek.toPx(); val maxScroll = (y - gap.toPx() - c.maxHeight).coerceAtLeast(0f)
            val scroll = state.scroll.floatValue.coerceIn(0f, maxScroll)
            val stride = ((y / ps.size) - peekPx).coerceAtLeast(1f)
            val topPin = peekPx * (scroll / stride).coerceIn(0f, MAX_DEPTH)
            val botPin = c.maxHeight - peekPx * ((maxScroll - scroll) / stride).coerceIn(0f, MAX_DEPTH)
            ps.forEachIndexed { i, p ->
                val r = stackItem(tops[i] + state.reflowOffset(i) - scroll, p.height.toFloat(), gap.toPx(),
                    peekPx, MAX_DEPTH, topPin, botPin, maxScroll > 0.5f)
                state.slot(i).depth.floatValue = r[4]
                p.placeRelativeWithLayer(0, r[0].roundToInt(), zIndex = 10f - r[4]) {
                    scaleX = r[1]; scaleY = r[1]; alpha = r[2]; transformOrigin = TransformOrigin(0.5f, r[3])
                }
            }
        }
    }
}
```

## Copyable Implementation Prompt (English)

```text
Build a bidirectional stacked-card scroll container in Jetpack Compose using a single custom Layout (no SubcomposeLayout). Own the scroll offset in a state object (MutableFloatState) driven by Modifier.scrollable with reverseDirection = true; read all scroll/overscroll/animation-tick states ONLY inside the layout placement block so scrolling and keyboard-inset changes cause zero recomposition and zero remeasure. Cards are measured once at full width and laid out linearly with a 10dp gap. In placement, cards that leave the viewport edges are pinned and compressed into at most 3 stacked layers: peekStep = 8dp per layer, topPinY = topBound + peek * clamp(scroll / effectiveStride, 0, 3), bottomPinBottomY = bottomBound - peek * clamp(remainingScroll / effectiveStride, 0, 3), effectiveStride = averageStride - peek. Depth = overflow distance / (cardHeight + gap), taking the larger of top and bottom. MAX_STACK_DEPTH = 3 (reduced further if the viewport is too small to leave 78dp for the center), scale = max(0.75, 1 - 0.04 * depth), alpha stays 1 up to depth 3 then falls to 0 at depth 4 (hide beyond 4.1), extra depth beyond the active max tucks in by peek * 0.5. Use transform origin (0.5, 0) for top stack, (0.5, 1) for bottom stack, zIndex = 10 - depth. Apply via placeRelativeWithLayer. Expose each card's depth through a per-key slot state (write only when it changes by more than 0.004) and let cards read a stackDepthProvider lambda to draw a black dimming overlay inside graphicsLayer: alpha = clamp(depth * step, 0, max) with step 0.11 / max 0.42 in dark theme and 0.055 / 0.22 in light theme. A card with depth > 0.35 counts as compressed: tapping it must call scrollToExpandItem(index), which scrolls it fully into view while leaving room for the remaining stack layers, animated with spring(dampingRatio 0.85, stiffness 340). When the item key list changes (insert, delete, reorder) start a per-key reflow spring(0.8, 380) from offset (previousNaturalTop - newNaturalTop), driven by a withFrameNanos loop that exits when idle. A pinned expanded key (e.g. the item being edited) is forced to scale 1, alpha 1, zIndex 20 and clamped inside the visible bounds. Pre-allocate and reuse buffers for per-item values. ReducedMotion: half scale step, no reflow springs, no overscroll. Accessibility: expose verticalScrollAxisRange and a scrollBy semantic action, PageUp/PageDown scroll 75% of the viewport, Arrow keys scroll one card, and compressed cards remain operable through their expand action.
```
