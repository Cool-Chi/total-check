---
name: spring-stepper-progress
description: 進度條以欠阻尼彈簧推進產生微幅過衝再回彈，內部真實進度精準 clamp 在 0f..1f，連動完成徽章脈衝與單一 Animatable 的原地色彩轉場。
---

# Spring Stepper Progress | 進度條物理過衝回彈

> 來源：`CapsuleComponents.kt`（`CapsuleProgressModule`：`progressProvider`、`badgePulse`、過衝視覺）。
> 進度彈簧 `dampingRatio = 0.65f, stiffness = 320f` 位於提供 `progressProvider` 的上層，不在讀取範圍內，列為規格值；模組本身負責過衝呈現。

## 核心作用與適用場景

- **變化起點**：完成數量變動（勾選 / 取消 / 重置）。
- **操作落點**：進度條填充端；填充端走過頭再彈回。
- **誰跟著回應**：數量大標題（`badgePulse`）、主題色（未完成 tertiary → 全完成 primary）、均分刻度線。
- **周圍是否讓位**：不讓位。
- 適用：任務完成度、步驟條、上傳進度、評分條。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 進度彈簧（規格） | `spring(dampingRatio = 0.65f, stiffness = 320f)`，欠阻尼 → 約 6~7% 過衝（`exp(−πζ/√(1−ζ²)) ≈ 0.07`） |
| 真實值 | 業務層永遠 `0f..1f`；彈簧值（可 > 1 或 < 0）只用於顯示 |
| 填充繪製 | `clamped = progress.coerceIn(0,1)`；`fillWidth = size.width × clamped`；以圓角軌道 `clipPath(trackPath)` 後畫 `topLeft = Offset(fillWidth − width, 0)` 的整條圓角矩形，使填充端保持圓頭 |
| 最小可見 | `clamped > 0.002f` 才繪製填充 |
| 過衝形變 | `over = (progress − 1).coerceAtLeast(0)`；`scaleX = 1 + over × 0.08`、`scaleY = 1 + over × 0.45`，原點中心（過衝時整條輕微變粗） |
| 刻度線 | `totalCount in 2..20` 時每個 `i/total` 畫 `2.5.dp` 寬分隔線，色 `surfaceContainerHigh` |
| 軌道 | 高 `14.dp`，圓角 = 高/2，色 `surfaceContainerHighest` |
| 徽章脈衝 | `badgePulse: Animatable(1f)`：完成數增加 `snapTo(1.18f)`、減少 `snapTo(0.90f)`，再 `animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))`；原點 `(0f, 0.7f)` |
| 色彩轉場 | `animateColorAsState(spring(0.8f, 380f))`；`LocalThemeTransitioning \|\| ReducedMotion` → `snap()` 且直接用目標色 |
| 完成條件 | `uncheckedCount == 0 && totalCount > 0` → 文案「All set」、主色 `primary` |
| ReducedMotion | 脈衝 `snapTo(1f)`、色彩 `snap()`；進度仍可平順但不過衝 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **`progressProvider: () -> Float` 取代 `progress: Float`**：值只在 `Canvas`/`graphicsLayer` 內讀取，彈簧每幀只重繪，Composable 不重組。
2. **`graphicsLayer { }` 內計算過衝形變**，不產生額外狀態。
3. **`badgePulse` 為單一 `Animatable`**，只在 `graphicsLayer` 讀取。
4. **`Path` 以 `remember` 持有**，每次 draw 只 `reset()` + `addRoundRect`。
5. **數量/狀態變更才重組一次**（文字、`LaunchedEffect` key），動畫期間不再重組。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
@Composable
fun rememberSpringProgress(target: Float): () -> Float {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target) {
        anim.animateTo(target.coerceIn(0f, 1f),               // 真實值精準 clamp
            spring(dampingRatio = 0.65f, stiffness = 320f))   // 欠阻尼 → 過衝
    }
    return remember { { anim.value } }                         // lambda：僅 Draw 階段讀取
}

@Composable
fun SpringProgressBar(progressProvider: () -> Float, accent: Color, track: Color,
                      segments: Int, divider: Color, modifier: Modifier = Modifier) {
    val trackPath = remember { Path() }
    Canvas(modifier.fillMaxWidth().height(14.dp).graphicsLayer {
        val over = (progressProvider() - 1f).coerceAtLeast(0f)
        scaleX = 1f + over * 0.08f; scaleY = 1f + over * 0.45f
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    }) {
        val corner = CornerRadius(size.height / 2f)
        trackPath.reset(); trackPath.addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner))
        drawRoundRect(track, cornerRadius = corner)
        val p = progressProvider().coerceIn(0f, 1f)
        if (p > 0.002f) clipPath(trackPath) {
            drawRoundRect(accent, Offset(size.width * p - size.width, 0f), Size(size.width, size.height), corner)
        }
        if (segments in 2..20) for (i in 1 until segments) {
            val w = 2.5.dp.toPx()
            drawRect(divider, Offset(size.width * i / segments - w / 2f, 0f), Size(w, size.height))
        }
    }
}

@Composable
fun rememberBadgePulse(done: Int, reduced: Boolean): Animatable<Float, AnimationVector1D> {
    val pulse = remember { Animatable(1f) }; var prev by remember { mutableIntStateOf(done) }
    LaunchedEffect(done, reduced) {
        val old = prev; prev = done
        if (reduced) { pulse.snapTo(1f); return@LaunchedEffect }
        if (done != old) pulse.snapTo(if (done > old) 1.18f else 0.90f)
        pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
    }
    return pulse      // 使用端：Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value; transformOrigin = TransformOrigin(0f, 0.7f) }
}
```

## Copyable Implementation Prompt (English)

```text
Build a spring-driven progress bar in Jetpack Compose that overshoots slightly and settles. Keep the real progress strictly within 0f..1f in business logic and animate a display value with one Animatable using spring(dampingRatio = 0.65, stiffness = 320) so it is underdamped (about 7% overshoot). Pass the value to the UI as a () -> Float lambda (progressProvider) that is read only inside Canvas draw and graphicsLayer blocks, so the spring causes redraws but never recomposition. Draw a 14dp-tall pill track (surfaceContainerHighest); the fill is a full-width rounded rect translated by (fillWidth - width) and clipped to the track path so the leading edge stays round; draw the fill only when clamped progress > 0.002. In graphicsLayer apply over = max(progress - 1, 0): scaleX = 1 + over * 0.08, scaleY = 1 + over * 0.45 around the center so the bar swells slightly on overshoot. When total is 2..20 draw 2.5dp divider lines at i/total. Add a count headline with a badgePulse Animatable: when the completed count increases snapTo 1.18, when it decreases snapTo 0.90, then animateTo(1) with spring(dampingRatio 0.4, stiffness 420), transform origin (0, 0.7). Switch the accent color from tertiary to primary when uncheckedCount == 0 && total > 0, via animateColorAsState with spring(0.8, 380), but use snap() while a theme transition is running or ReducedMotion is on. Reuse a single Path object. ReducedMotion: pulse snapTo(1), no overshoot (clamp the display value), colors snap. Accessibility: expose progressBarRangeInfo semantics with the clamped value, a text description such as "3 of 8 done", and announce completion once when it reaches 100%.
```
