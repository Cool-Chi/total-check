---
name: liquid-tab-indicator
description: 雙模態/多段分段切換時，以前後緣非對稱彈簧驅動的液態膠囊指示器，搭配雙層文字像素級反色裁切與觸控壓暗回饋。
---

# Liquid Tab Indicator | 液態分段指示器

> 來源：`LiquidTabBar.kt`（指示器本體）、`SlideOutCancelModifier.kt`（觸控回饋，`sinkScale=1f`、`maxDeepenAlpha=0.08f`）。

## 核心作用與適用場景

- **變化起點**：被點選的分段（Tab）；指示膠囊從舊分段的左右邊界「拉伸 → 收攏」到新分段邊界。
- **操作落點**：容器內任一分段，點擊即切換；快速連點時從當前飛行位置接續，不重置。
- **誰跟著回應**：選中文字（以第二層文字疊圖裁切成膠囊形狀，白字只出現在膠囊內）、未選中文字維持底層色。
- **周圍是否讓位**：不讓位。分段寬度固定（等寬 `weight(1f)` 或變寬），僅指示器移動。
- 適用：空白清單／快速匯入這類 2~4 段模式切換、設定頁分段控制、可橫向捲動的變寬標籤列。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 |
| :--- | :--- |
| 狀態 | `leadingEdge: Animatable<Float>`、`trailingEdge: Animatable<Float>`（單位 px，相對軌道） |
| 快彈簧 `fastSpring` | `dampingRatio = 0.82f`、`stiffness = 550f`、`visibilityThreshold = 0.5f` |
| 慢彈簧 `slowSpring` | `dampingRatio = 0.88f`、`stiffness = 380f`、`visibilityThreshold = 0.5f` |
| 方向判定 | `movingRight = targetCenter >= currentCenter`，中心 = `(leading + trailing) / 2` |
| 右移 | 前緣（leading=left）用 slow、後緣（trailing=right）用 fast → 右緣先衝、左緣拖尾，膠囊先被拉長 |
| 左移 | 前緣用 fast、後緣用 slow（對稱反向） |
| 直接 snap 條件 | 首次初始化、`ReducedMotion`、`safeSelectedIndex == lastSelectedIndex`（僅尺寸變動） |
| 膠囊幾何 | `left = min(lead, trail)`、`right = max(lead, trail)`（夾在 `0..trackWidth`），圓角半徑 `= height / 2`；寬度 ≤ 0.5px 不繪製 |
| 容器 | 外框 `RoundedCornerShape(50)`、`containerPadding = 4.dp`、`itemSpacing = 4.dp`、`itemContentPadding = 12.dp × 8.dp` |
| 色彩 | 底層文字 `unselectedContentColor`、疊層文字 `selectedContentColor`（不做色彩插值，靠裁切達成像素級過渡） |
| 觸控回饋 | 每段用 `slideOutCancelClickable(sinkScale = 1f, sinkTranslationY = 0.dp, maxDeepenAlpha = 0.08f)`：僅暗化 8%，不縮放 |
| 語意 | `useTabSemantics` → `Role.Tab` + `selected` + `selectableGroup()`；否則 `Role.Button`。疊層 `clearAndSetSemantics {}` 避免重複朗讀 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **兩個 `Animatable` 只在 Draw 階段讀值**：`drawBehind { leadingEdge.value ... }` 與 `drawWithContent { ... }` 內讀取，動畫每幀只觸發重繪，不觸發 recomposition。
2. **雙層文字疊圖**：同一份 `LiquidTabContentRow` 畫兩次，上層 `interactive = false`，用 `clipPath(indicatorPath) { drawContent() }` 裁出膠囊區域；`Path` 以 `remember` 持有並 `reset()` 重用，零分配。
3. **邊界量測不走重組**：`onGloballyPositioned` 只在邊界真正改變時才寫入 `mutableStateMapOf`，只有 `LaunchedEffect` 的 key 會因此重啟。
4. **彈簧可被中斷**：新目標直接 `animateTo` 覆蓋，速度由 `Animatable` 保留，自然接續。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
@Composable
fun LiquidSegmentedControl(
    labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    indicatorColor: Color, selectedColor: Color, unselectedColor: Color,
    modifier: Modifier = Modifier, reducedMotion: Boolean = false,
) {
    val bounds = remember(labels.size) { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val lead = remember { Animatable(0f) }
    val trail = remember { Animatable(0f) }
    var inited by remember { mutableStateOf(false) }
    var last by remember { mutableIntStateOf(selectedIndex) }
    val target = bounds[selectedIndex]

    LaunchedEffect(selectedIndex, target, reducedMotion) {
        val (l, r) = target ?: return@LaunchedEffect
        if (!inited || reducedMotion || selectedIndex == last) {
            lead.snapTo(l); trail.snapTo(r); inited = true; last = selectedIndex; return@LaunchedEffect
        }
        val right = (l + r) / 2f >= (lead.value + trail.value) / 2f
        last = selectedIndex
        val fast = spring<Float>(0.82f, 550f, 0.5f)
        val slow = spring<Float>(0.88f, 380f, 0.5f)
        coroutineScope {
            launch { lead.animateTo(l, if (right) slow else fast) }
            launch { trail.animateTo(r, if (right) fast else slow) }
        }
    }

    val path = remember { Path() }
    Box(modifier.clip(RoundedCornerShape(50)).padding(4.dp)) {
        Box(Modifier.clipToBounds().drawBehind {
            if (!inited) return@drawBehind
            val a = min(lead.value, trail.value).coerceIn(0f, size.width)
            val b = max(lead.value, trail.value).coerceIn(a, size.width)
            if (b - a > 0.5f) drawRoundRect(indicatorColor, Offset(a, 0f), Size(b - a, size.height),
                CornerRadius(size.height / 2))
        }) {
            Row(Modifier.fillMaxWidth()) { // 底層：可互動
                labels.forEachIndexed { i, t ->
                    Box(Modifier.weight(1f)
                        .onGloballyPositioned { c -> val x = c.positionInParent().x
                            bounds[i] = x to (x + c.size.width) }
                        .clickable { onSelect(i) }.padding(12.dp, 8.dp), Alignment.Center
                    ) { Text(t, color = unselectedColor) }
                }
            }
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}.drawWithContent { // 疊層：只在膠囊內可見
                if (!inited) return@drawWithContent
                val a = min(lead.value, trail.value); val b = max(lead.value, trail.value)
                path.reset(); path.addRoundRect(RoundRect(a, 0f, b, size.height, CornerRadius(size.height / 2)))
                clipPath(path) { this@drawWithContent.drawContent() }
            }) { labels.forEach { t -> Box(Modifier.weight(1f).padding(12.dp, 8.dp), Alignment.Center) { Text(t, color = selectedColor) } } }
        }
    }
}
```

## Copyable Implementation Prompt (English)

```text
Implement a liquid segmented control in Jetpack Compose. A pill-shaped indicator slides between 2-4 segments. Drive it with two independent Animatable<Float> values, leadingEdge (left) and trailingEdge (right), measured in pixels relative to the track. Use asymmetric springs: fast = spring(dampingRatio 0.82, stiffness 550, visibilityThreshold 0.5) and slow = spring(dampingRatio 0.88, stiffness 380, visibilityThreshold 0.5). When moving right, animate the leading edge with slow and the trailing edge with fast (the pill stretches, then settles); when moving left, swap the two. Detect direction by comparing the center of the current pill with the center of the target segment. Snap (no animation) on first layout, when only the segment size changed, or when ReducedMotion is on. If the user taps again mid-flight, call animateTo on the same Animatables so velocity carries over. Read the Animatable values only inside drawBehind / drawWithContent, never during composition. Render the label row twice: a bottom interactive row with the unselected color, and a top non-interactive row (clearAndSetSemantics) with the selected color, clipped via clipPath to the indicator's rounded rect (corner radius = height / 2, reuse a single Path, skip drawing if width <= 0.5px). Measure segment bounds with onGloballyPositioned and only write to state when the value changes. Container: fully rounded, 4dp padding, 4dp spacing, 12dp x 8dp item padding. Touch feedback per segment: darken overlay up to alpha 0.08, no scale, cancel the tap if the finger slides out. Accessibility: Role.Tab + selected + selectableGroup (or Role.Button for navigation use), Enter/Space activates the focused segment, and a visible keyboard focus ring.
```
