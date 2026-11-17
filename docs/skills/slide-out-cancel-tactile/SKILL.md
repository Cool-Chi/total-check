---
name: slide-out-cancel-tactile
description: 按鈕按壓時縮至 0.94f 並下沉 1.5dp，70ms 後鎖定手勢；手指滑出 44dp 安全半徑時按壓強度依曲線衰減回彈，放開即取消點擊。
---

# Slide-Out Cancel Tactile | 按鈕按壓下沉與滑出取消

> 來源：`SlideOutCancelModifier.kt`（`slideOutCancelClickable`、`computeSlideOutPressStrength`）。

## 核心作用與適用場景

- **變化起點**：手指按下的元件本身。
- **操作落點**：元件邊界內放開才觸發 `onClick`；滑出邊界後放開 = 取消。
- **誰跟著回應**：按下元件縮放 + 下移 + 疊加暗化遮罩；父層捲動/翻頁手勢在 70ms 後被本元件鎖定。
- **周圍是否讓位**：不讓位。
- 適用：所有按鈕、Chip、Icon Button、Tab；尤其是放在 Pager/捲動容器內需要「按住不被搶手勢」的元件。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 單一狀態 | `pressStrength: Animatable<Float>`，0 = 靜止，1 = 完整按下 |
| 縮放 | `PRESS_SINK_SCALE = 0.94f`：`scale = lerp(1f, 0.94f, p)`，原點 `(0.5, 0.5)` |
| 下沉 | `PRESS_SINK_TRANSLATION_Y = 1.5.dp`：`translationY = lerp(0, 1.5dp, p)` |
| 暗化遮罩 | `Color.Black.copy(alpha = maxAlpha × p)`；預設深色 `0.24f`、淺色 `0.14f`（以 `surface.luminance() < 0.5f` 判斷），可由 `maxDeepenAlpha` 覆寫 |
| 按下彈簧 | `spring(dampingRatio = 0.85f, stiffness = 920f, visibilityThreshold = 0.002f)` → 目標 1f |
| 回彈（放開/取消） | `spring(0.82f, 620f, 0.002f)` → 目標 0f |
| 滑回內部恢復 | `spring(0.85f, 880f, 0.002f)` → 目標 1f（僅當 `targetValue < 0.999f`） |
| 手勢鎖定 | `HOLD_LOCK_DELAY_MS = 70L`：按住滿 70ms 即 `isGestureLocked = true`，並 `consume()` Main pass 事件（阻止父層捲動搶手勢）；若 70ms 內水平位移 `> touchSlop` 先成立 → `exceededHorizontalSlopEarly`，不鎖定（讓父層正常橫向滑動） |
| 安全半徑 | `SLIDE_OUT_CANCEL_DISTANCE = 44.dp` |
| 衰減曲線 | `dx = 超出左右邊界的距離`，`dy = 超出上下邊界的距離`，`dist = hypot(dx, dy)`；`strength = (1 − dist / 44dp).coerceIn(0,1) ^ 1.35`；在邊界內恆為 1；半徑 ≤ 0.5px 回 0 |
| 滑出期間 | 不用彈簧，`snapTo(strength)` 1:1 跟隨手指，距離 ≥ 44dp 時完全回彈到靜止 |
| 觸發規則 | 放開時 `isInsideBounds && !longPressFired` → `onClick`；滑出邊界 → `onPressCancelled` |
| 長按 | `delay(viewConfig.longPressTimeoutMillis)` 後若仍在界內且未取消 → 先 `animateToRest()` 再 `onLongClick` |
| ReducedMotion | 不縮放不位移；`snapTo(if (inside) 1f else 0f)`，僅保留暗化回饋 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **`graphicsLayer { }` 區塊讀 `pressStrength.value`**：縮放與位移走 GraphicsLayer，按壓動畫期間 0 重組、0 重測。
2. **暗化遮罩在 `drawWithContent` 內繪製**：與縮放共用同一 `Animatable`，不建立額外 state。
3. **回呼一律 `rememberUpdatedState`**：`pointerInput` 不因 lambda 變動而重啟，避免手勢中斷。
4. **事件雙通道**：`PointerEventPass.Main` 判斷是否被別人消費並決定鎖定，`Final` 讀最終座標與放開。
5. **協程只負責動畫**：`animJob` / `longPressJob` 在 `DisposableEffect.onDispose` 取消。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
private val CANCEL_DIST = 44.dp
private const val SINK_SCALE = 0.94f
private val SINK_Y = 1.5.dp
private const val HOLD_LOCK_MS = 70L

fun computePressStrength(x: Float, y: Float, w: Float, h: Float, radius: Float): Float {
    val dx = when { x < 0f -> -x; x > w -> x - w; else -> 0f }
    val dy = when { y < 0f -> -y; y > h -> y - h; else -> 0f }
    if (dx == 0f && dy == 0f) return 1f
    if (radius <= 0.5f) return 0f
    return (1f - hypot(dx, dy) / radius).coerceIn(0f, 1f).pow(1.35f)
}

fun Modifier.tactileClickable(shape: Shape, reducedMotion: Boolean, onClick: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope(); val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val onClickState by rememberUpdatedState(onClick)
    val p = remember { Animatable(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val radius = with(density) { CANCEL_DIST.toPx() }; val sinkY = with(density) { SINK_Y.toPx() }
    fun go(to: Float, d: Float, k: Float) { job?.cancel()
        job = scope.launch { if (reducedMotion) p.snapTo(to) else p.animateTo(to, spring(d, k, 0.002f)) } }

    this.graphicsLayer {
        val v = p.value.coerceIn(0f, 1f)
        if (!reducedMotion && v > 0.001f) {
            scaleX = lerp(1f, SINK_SCALE, v); scaleY = scaleX; translationY = lerp(0f, sinkY, v)
        }
    }.clip(shape).drawWithContent {
        drawContent(); val v = p.value
        if (v > 0.001f) drawRect(Color.Black.copy(alpha = 0.14f * v))
    }.pointerInput(reducedMotion) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            var locked = false; var slopEarly = false; var inside = true
            go(1f, 0.85f, 920f)
            while (true) {
                val main = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == down.id }
                if (main == null || main.isConsumed) { go(0f, 0.82f, 620f); break }
                if (!locked && !slopEarly) {
                    if (main.uptimeMillis - down.uptimeMillis >= HOLD_LOCK_MS) locked = true
                    else if (abs(main.position.x - down.position.x) > slop) slopEarly = true
                }
                if (locked) main.consume()
                val c = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                val w = size.width.toFloat(); val h = size.height.toFloat()
                inside = c.position.x in 0f..w && c.position.y in 0f..h
                if (!c.pressed) { go(0f, 0.82f, 620f); if (inside) onClickState(); break }
                if (!inside) { job?.cancel(); scope.launch { p.snapTo(computePressStrength(c.position.x, c.position.y, w, h, radius)) } }
                else if (p.targetValue < 0.999f) go(1f, 0.85f, 880f)
            }
        }
    }
}
```

## Copyable Implementation Prompt (English)

```text
Create a reusable Modifier.slideOutCancelClickable for Jetpack Compose that gives buttons a tactile press with slide-out cancel. Keep ONE Animatable<Float> pressStrength (0..1). In graphicsLayer: scale = lerp(1, 0.94, p) and translationY = lerp(0, 1.5dp, p) with a center pivot; in drawWithContent overlay black with alpha = maxAlpha * p (0.14 light theme, 0.24 dark theme, overridable). On pointer down animate to 1 with spring(dampingRatio 0.85, stiffness 920, visibilityThreshold 0.002). On release or cancel animate to 0 with spring(0.82, 620, 0.002). Use awaitEachGesture with awaitFirstDown(requireUnconsumed = true), then loop reading PointerEventPass.Main (to detect consumption and to lock) and PointerEventPass.Final (final position and release). Gesture lock: after 70ms of holding, consume Main-pass changes so parent scroll/pager does not steal the gesture; but if horizontal movement exceeds touchSlop before 70ms, do not lock and let the parent scroll. While the finger is outside the element bounds, stop using springs and snapTo(strength) every move, where dx/dy are the distances outside the bounds, dist = hypot(dx, dy), strength = (1 - dist / 44dp).coerceIn(0,1) ^ 1.35; at 44dp or more the element is fully restored. If the finger returns inside, animate back to 1 with spring(0.85, 880). Fire onClick only when the pointer is released inside the bounds and no long press fired; releasing outside is a cancel (call onPressCancelled). Long press: after the system long-press timeout, if still inside, rest the animation and call onLongClick. Use rememberUpdatedState for all callbacks so pointerInput never restarts, and cancel animation jobs in onDispose. ReducedMotion: no scale or translation, snapTo 1 inside and 0 outside, keep only the dim overlay. Accessibility: focusable, Enter/NumpadEnter/Space (repeatCount 0) triggers onClick, semantics role + onClick + optional onLongClick + disabled state, and a visible keyboard focus ring (12% tint fill + 3dp outline) shown only after keyboard navigation.
```
