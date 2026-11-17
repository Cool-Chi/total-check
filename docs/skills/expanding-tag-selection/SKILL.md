---
name: expanding-tag-selection
description: 水平標籤列中，選中標籤放大至 1.08f 並以自訂 Layout 平滑推開鄰居，順序變動由彈簧補償，遠距點擊走 320ms 預熱過渡，尾端以 DstIn 漸層淡出。
---

# Expanding Tag Selection | 標籤擠開與遠距預熱滑動

> 來源：`ExpandingChecklistTabBar.kt`。

## 核心作用與適用場景

- **變化起點**：被選中（或被 Pager 滑動接近中）的標籤；展開量由連續的 `selectionFraction ∈ [0,1]` 決定。
- **操作落點**：標籤本體（點擊、方向鍵、長按拖曳）；右側可橫向捲動。
- **誰跟著回應**：選中標籤放大、內距變寬、計數文字淡入；前後鄰居被推開；選中項自動捲入視野。
- **周圍是否讓位**：是。自訂 `Layout` 在量測階段把放大後寬度與額外邊距計入 `xCursor`，鄰居以 1:1 位移讓位。
- 適用：Pager 頁籤、清單分頁、可排序標籤列、任何「選中即擴張」的膠囊群。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 |
| :--- | :--- |
| 選中縮放 | `ACTIVE_TAG_SCALE = 1.08f`；`scale = lerp(1f, 1.08f, t)` |
| 拖曳升起縮放 | `FLOATING_TAG_LIFT_SCALE = 1.06f`（彈簧 `0.8f / 420f`） |
| 選中分數 `t` | Pager 模式：`(1 - abs(currentPage + offsetFraction - index)).coerceIn(0,1)`；遠距模式：目標 `p`、來源 `1 - p`、其餘 0；順序凍結時選中=1、其餘=0 |
| 遠距過渡 | `DistantTabTransition(fromPage, toPage, progress: Animatable(0f))`；**320ms** 由呼叫端以 `progress.animateTo(1f, tween(320))` 驅動（時長常數位於 TabBar 之外的呼叫端，規格依任務定義） |
| 額外鄰居邊距 | `extraNeighborMarginPx = 4.dp * t`（左右各一份）；標籤間距 `baseGap = 8.dp`；首尾 padding `4.dp`；列首尾 Spacer `12.dp` |
| 內距 | 水平 `lerp(14.dp, 18.dp, t)`；垂直 `9.dp`；最小高度 `40.dp`；標題最大寬 `168.dp` |
| 計數文字淡入 | `revealAlpha = ((t - 0.16f) / 0.72f).coerceIn(0,1)`；`revealScale = lerp(0.84f, 1f, t)`；`t > 0.05f` 才擺放；原點 `(0f, 0.5f)`；寬度 `(6.dp + countWidth) * t` |
| 重排補償彈簧 `TabReflowAnim` | `spring(dampingRatio = 0.78f, stiffness = 460f)`，初始偏移 `= currentOffset + (prevNaturalLeft - naturalLeft)`，夾在 `±420f` |
| 補償觸發條件 | 僅 **列表順序/數量改變**（`orderChanged && hadPreviousOrder`）；單純 Pager 滑動造成的寬度伸縮 1:1 跟隨，避免雙重動效；被拖曳項與 `suppressedReflowId` 不補償 |
| 選中項捲入視野 | 邊距 `28.dp`，額外預估展開寬 `52.dp`；`animateScrollTo(spring(0.88f, 360f))`；`ReducedMotion` → `scrollTo` |
| 尾端淡化遮罩 | 寬 `32.dp`（`coerceAtMost(width)`），`Brush.horizontalGradient(Black → Transparent)`，`BlendMode.DstIn`；僅 `scrollState.value < maxValue` 時繪製 |
| 層級 | `zIndex = 1f + t`（選中項壓在鄰居上方），拖曳槽位 `0f` |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **`selectionFractionProvider: () -> Float`**：以 lambda 傳入，只在 `drawBehind` / `Layout` 的 measure / `BasicText(color = {...})` 內讀取，Pager 滑動時 composition 不重跑。
2. **自訂 `Layout` 量測階段決定位置**：`measurable.measure(Constraints())` 取得自然寬，按 `scale` 與 `extraMargin` 累加 `xCursor`；位置以 `placeRelativeWithLayer { scaleX/scaleY }` 套用，縮放走 GraphicsLayer 不觸發重新量測。
3. **重排彈簧手動逐幀驅動**：`TargetBasedAnimation` + `withFrameNanos` 寫入 `reflowFrameNanos`（`mutableLongStateOf`），該值**只在 Placement 區塊讀取**，因此只有 placement 失效，無文字重組。
4. **緩衝陣列重用**：`placeablesBuffer / fractionsBuffer / visualLefts ...` 預先配置並 `ensureCapacity` 倍增，量測路徑零物件分配。
5. **離屏合成僅用在遮罩**：外層 `graphicsLayer { compositingStrategy = Offscreen }` 讓 `DstIn` 只作用於這一列。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
const val ACTIVE_SCALE = 1.08f

class TagReflow(var offset: Float = 0f, var lastLeft: Float? = null,
                var anim: TargetBasedAnimation<Float, AnimationVector1D>? = null, var start: Long = 0L)

@Composable
fun <T> ExpandingTagRow(
    items: List<T>, id: (T) -> Long,
    fractionOf: (index: Int) -> Float,           // 0..1，於 Layout/Draw 階段讀取
    reflow: MutableMap<Long, TagReflow>, frameTick: () -> Long,
    scrollState: ScrollState, tag: @Composable (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (scrollState.value < scrollState.maxValue) {
                val w = 32.dp.toPx().coerceAtMost(size.width)
                drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent),
                    startX = size.width - w, endX = size.width),
                    Offset(size.width - w, 0f), Size(w, size.height), blendMode = BlendMode.DstIn)
            }
        }.horizontalScroll(scrollState)) {
        Layout(content = { items.forEach { key(id(it)) { tag(it) } } }) { ms, _ ->
            val ps = ms.map { it.measure(Constraints()) }
            val gap = 8.dp.toPx(); val extra = 4.dp.toPx()
            var x = 4.dp.toPx()
            val lefts = FloatArray(ps.size); val scales = FloatArray(ps.size); val ts = FloatArray(ps.size)
            ps.forEachIndexed { i, p ->
                val t = fractionOf(i).coerceIn(0f, 1f); ts[i] = t
                scales[i] = lerp(1f, ACTIVE_SCALE, t)
                val vw = p.width * scales[i]
                val vLeft = x + extra * t
                lefts[i] = vLeft + (vw - p.width) / 2f          // 未縮放的 layout 左緣
                x = vLeft + vw + extra * t + if (i < ps.lastIndex) gap else 0f
            }
            syncReflow(items.map(id), lefts, reflow)            // 順序改變才啟動 spring(0.78f, 460f)
            layout(x.roundToInt() + 4.dp.roundToPx(), ps.maxOf { it.height }) {
                frameTick()                                      // 只在 placement 訂閱逐幀 tick
                ps.forEachIndexed { i, p ->
                    p.placeRelativeWithLayer((lefts[i] + (reflow[id(items[i])]?.offset ?: 0f)).roundToInt(),
                        0, zIndex = 1f + ts[i]) {
                        scaleX = scales[i]; scaleY = scales[i]; transformOrigin = TransformOrigin(.5f, .5f)
                    }
                }
            }
        }
    }
}

fun syncReflow(ids: List<Long>, lefts: FloatArray, m: MutableMap<Long, TagReflow>) {
    ids.forEachIndexed { i, id ->
        val e = m.getOrPut(id) { TagReflow(lastLeft = lefts[i]) }
        val prev = e.lastLeft; e.lastLeft = lefts[i]
        if (prev != null && abs(prev - lefts[i]) > 0.5f) {
            val s = (e.offset + prev - lefts[i]).coerceIn(-420f, 420f)
            e.offset = s
            e.anim = TargetBasedAnimation(spring(0.78f, 460f), Float.VectorConverter, s, 0f); e.start = 0L
        }
    }
}
```

## Copyable Implementation Prompt (English)

```text
Build a horizontally scrollable tag row in Jetpack Compose where the selected tag expands and pushes its neighbors apart. Selection is a continuous fraction t in [0,1] supplied as a lambda: for a Pager use t = 1 - abs(currentPage + currentPageOffsetFraction - index) clamped to 0..1; for a distant (multi-page) jump use a transition object with an Animatable progress p (tween 320ms): target tag t = p, source tag t = 1 - p, others 0, so the row warms up smoothly instead of passing through every intermediate tag. Use one custom Layout: measure each tag with unbounded constraints, scale = lerp(1, 1.08, t), add extra neighbor margin 4dp * t on both sides, base gap 8dp, edge padding 4dp, and place with placeRelativeWithLayer (scaleX/scaleY, center origin, zIndex = 1 + t). Tag padding lerps horizontally from 14dp to 18dp, vertical 9dp, min height 40dp; a count label fades in with alpha = ((t - 0.16) / 0.72) clamped, scale lerp(0.84, 1, t), anchored at its left-center. Only when the item ORDER or COUNT changes (reorder, add, remove) start a per-item compensation spring(dampingRatio 0.78, stiffness 460) from offset (previousNaturalLeft - newNaturalLeft), clamped to +/-420px; plain pager-driven width changes must follow 1:1 with no extra animation. Drive these springs manually with TargetBasedAnimation + withFrameNanos and publish a frame tick that is read ONLY inside the layout placement block. Auto-scroll the selected tag into view with 28dp margin using spring(0.88, 360). Fade the trailing edge with a 32dp horizontal gradient (Black to Transparent) drawn with BlendMode.DstIn inside a graphicsLayer using CompositingStrategy.Offscreen, only while more content exists to scroll. Provide the t value to drawBehind and BasicText color lambdas so scrolling never recomposes. ReducedMotion: scale fixed at 1, no compensation springs, scrollTo instead of animateScrollTo. Accessibility: Role.Tab + selected, left/right arrow keys move focus, Enter/Space selects, custom accessibility actions for move-left / move-right, visible keyboard focus ring.
```
