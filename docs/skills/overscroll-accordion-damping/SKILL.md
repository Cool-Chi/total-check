---
name: overscroll-accordion-damping
description: 自訂捲動容器滑抵頂底邊界時以漸近斜率阻尼拉伸（初始斜率 0.55f），位移按 0.76f/0.24f 分配成整體平移與前 5 項手風琴間距拉伸，Fling 撞界動量由臨界阻尼彈簧吸收回彈。
---

# Overscroll Accordion Damping | 列表邊界手風琴阻尼回彈

> 來源：`StackedCardScrollLayout.kt`（`computeVisualOverscrollPx`、`consumeScrollDelta`、`runOverscrollSpringToZero`、`overscrollFlingBehavior`）。

## 核心作用與適用場景

- **變化起點**：手指或 Fling 在頂/底邊界仍有剩餘位移。
- **操作落點**：捲動容器；手指拖曳時 1:1 累積「原始過界量」，顯示量經阻尼壓縮。
- **誰跟著回應**：整份列表整體位移，越靠近邊界的前 5 項額外被拉開間距（手風琴感）。
- **周圍是否讓位**：是，靠近邊界的項目位移較大、遠端較小，產生間距伸展。
- 適用：不想用系統 `OverscrollEffect`（Android 12 的拉伸）而需要自訂、可與堆疊/重排共存的邊界回饋。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 最大視覺過界 | `maxOverscrollPx = 72.dp` |
| 原始→視覺阻尼 | `scaled = 0.55 × \|raw\|`；`visual = max × scaled / (max + scaled)`（雙曲漸近線，永不超過 max） |
| 初始斜率 | `OVERSCROLL_INITIAL_SLOPE = 0.55f`（`raw` 很小時 `visual ≈ 0.55 × raw`） |
| 原始量上限 | `maxRaw = maxVisual × 14f` |
| 反函數 | `raw = max × v / (0.55 × (max − v))`，`v` 夾在 `max × 0.96`，分母 ≥ 0.001 |
| 即時導數 | `slope = 0.55 × (max / (max + 0.55 × \|raw\|))²`，用於把 Fling 速度換算成視覺速度 |
| 回推收回 | 手勢反向時先消耗過界量：`rawUnwind = min(\|delta\|, \|visual\| / 0.55)`，視覺量以 0.55 斜率減少；`< 0.05` 視為 0 |
| 累積條件 | 邊界剩餘位移 `> 0.001`，且 `!reducedMotion && !isFlinging && !isProgrammaticScroll`；`nextRaw = (raw − leftover).coerceIn(±maxRaw)` |
| 位移分配 | 單項位移 `= visual × (0.76f + 0.24f × easeOut(t))`，`BASE_TRANSLATION_RATIO = 0.76f`、`ACCORDION_STRETCH_RATIO = 0.24f` |
| 手風琴範圍 | `OVERSCROLL_ACCORDION_SPAN_ITEMS = 5f`；`t = (距邊界項數 / 5).coerceIn(0,1)`；`easeOut(t) = 1 − (1 − t)²`；頂部用 `index`，底部用 `count − 1 − index` |
| 放手回彈 | `spring(dampingRatio = 1.0f, stiffness = 420f, visibilityThreshold = 0.25f)`（臨界阻尼，無二次過衝） |
| Fling 撞界 | 衝擊係數 `OVERSCROLL_FLING_IMPACT_FACTOR = 0.55f`：`impactVel = −remainingVelocity × 0.55`，夾在 `±(max × 22f)`；觸發條件 `remainingVelocity` 超過 `±80f` 且位於對應邊界 |
| 已在過界時 Fling | 背向列表：`visualVel = (−v × slope).coerceIn(±1400f)` → 彈簧回零；朝向列表：先走一般 Fling，結束若仍有過界再回彈 |
| 撞界起始 | 從 0 起算的彈簧，每幀以 `computeVisualOverscrollPx(value / 0.55, max)` 轉換，避免瞬間跳出阻尼曲線 |
| 停用 | `ReducedMotion` 或存在 `pinnedExpandedKey` 時 visual = 0 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **兩個 `MutableFloatState`（raw / visual）只在 Layout placement 讀取**：過界期間不重組、不重測。
2. **位移在 `placeRelativeWithLayer` 的 y 上直接相加**：與堆疊座標同一次計算。
3. **`ScrollableState { delta -> consumeScrollDelta(delta) }`**：不依賴 Compose 預設 overscroll，邏輯集中在 state 類。
4. **自訂 `FlingBehavior`**：在 fling 結束時才攔截殘餘速度，不額外建立偵測器。
5. **`isScrollInProgress` 的 `snapshotFlow`**：手指離開且仍有過界時自動 `settleOverscrollIfIdle`。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
const val SLOPE = 0.55f; const val BASE_RATIO = 0.76f; const val STRETCH_RATIO = 0.24f; const val SPAN = 5f

fun visualOf(raw: Float, max: Float): Float {
    if (abs(raw) < 0.01f || max <= 0f) return 0f
    val s = SLOPE * abs(raw)
    return sign(raw) * (max * s) / (max + s)
}
fun rawOf(visual: Float, max: Float): Float {
    if (abs(visual) < 0.01f || max <= 0f) return 0f
    val v = abs(visual).coerceAtMost(max * 0.96f)
    return sign(visual) * (max * v) / (SLOPE * (max - v)).coerceAtLeast(0.001f)
}
fun itemOffset(visual: Float, index: Int, count: Int): Float {
    if (abs(visual) <= 0.05f) return 0f
    val dist = if (visual > 0f) index else (count - 1 - index).coerceAtLeast(0)
    val t = (dist / SPAN).coerceIn(0f, 1f); val ease = 1f - (1f - t) * (1f - t)
    return visual * (BASE_RATIO + STRETCH_RATIO * ease)
}

class OverscrollState(val maxVisual: Float) {
    val scroll = mutableFloatStateOf(0f); var maxScroll = 0f
    val raw = mutableFloatStateOf(0f); val visual = mutableFloatStateOf(0f)
    var flinging = false

    val scrollable = ScrollableState { delta ->
        var rest = delta
        val cur = visual.floatValue
        if (abs(cur) > 0.001f && (cur > 0f) == (rest > 0f)) {            // 反向 → 先收回
            val use = min(abs(rest), abs(cur) / SLOPE)
            val nv = if (cur > 0f) (cur - use * SLOPE).coerceAtLeast(0f) else (cur + use * SLOPE).coerceAtMost(0f)
            visual.floatValue = if (abs(nv) < 0.05f) 0f else nv; raw.floatValue = rawOf(visual.floatValue, maxVisual)
            rest -= sign(rest) * use
        }
        val target = (scroll.floatValue + rest).coerceIn(0f, maxScroll)
        val leftover = rest - (target - scroll.floatValue); scroll.floatValue = target
        if (abs(leftover) > 0.001f && !flinging) {                        // 邊界 → 阻尼累積
            raw.floatValue = (raw.floatValue - leftover).coerceIn(-maxVisual * 14f, maxVisual * 14f)
            visual.floatValue = visualOf(raw.floatValue, maxVisual); delta
        } else delta - leftover
    }

    suspend fun springToZero(velocity: Float) = animate(visual.floatValue, 0f, velocity,
        spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = 0.25f)) { v, _ ->
        visual.floatValue = v.coerceIn(-maxVisual, maxVisual); raw.floatValue = rawOf(visual.floatValue, maxVisual) }
}
// FlingBehavior：撞界後 springToZero((-remaining * 0.55f).coerceIn(-maxVisual * 22f, maxVisual * 22f))
```

## Copyable Implementation Prompt (English)

```text
Implement a custom overscroll for a custom scroll container in Jetpack Compose (do not use the platform OverscrollEffect). Use a ScrollableState whose consume callback clamps scroll to [0, maxScroll] and accumulates any leftover delta at the boundary into a raw overscroll value (clamped to +/- maxVisual * 14, with maxVisual = 72dp) only while the finger is dragging (not during fling or programmatic scroll). Convert raw to visual with an asymptotic damping curve: scaled = 0.55 * |raw|; visual = maxVisual * scaled / (maxVisual + scaled), so the initial slope is 0.55 and visual never exceeds maxVisual; also implement the inverse function (clamp visual to 96% of max) and the derivative slope = 0.55 * (max / (max + 0.55 * |raw|))^2. When the gesture reverses direction while overscrolled, consume the overscroll first at slope 0.55 before scrolling normally. Distribute the visual offset across items as an accordion: each item's translation = visual * (0.76 + 0.24 * easeOut(t)), t = clamp(distanceFromEdgeInItems / 5, 0, 1), easeOut(t) = 1 - (1 - t)^2, counting from the top for positive overscroll and from the bottom for negative. Apply it in the layout placement block by adding to each item's y, reading the overscroll state only there (zero recomposition). On release animate visual to 0 with spring(dampingRatio 1.0, stiffness 420, visibilityThreshold 0.25), updating raw via the inverse function each frame. Provide a custom FlingBehavior: if already overscrolled and flinging away from the list, convert velocity with the current derivative (clamped to +/-1400 px/s) and spring back; otherwise run the default fling with an isFlinging flag, then if residual velocity (> 80 px/s) hits a boundary, spring from impactVelocity = -residual * 0.55 clamped to +/-(maxVisual * 22), mapping each frame through the damping curve so it never jumps outside it. Auto-settle when isScrollInProgress becomes false with leftover overscroll. Disable everything under ReducedMotion or when an item is pinned for editing. Accessibility: keep semantic scrollBy and axis range, keyboard PageUp/PageDown, and never let overscroll hide focused content.
```
