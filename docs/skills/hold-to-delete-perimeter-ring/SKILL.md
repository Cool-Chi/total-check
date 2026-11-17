---
name: hold-to-delete-perimeter-ring
description: 按住 150ms 後觸發長按震動，沿圓角膠囊外框順時針以 PathMeasure 於 1000ms 線性繪製紅色進度環，中途放開 300ms 內線性退回，填滿後 160ms 縮放淡出並刪除。
---

# Hold-to-Delete Perimeter Ring | 長按外圈進度環刪除

> 來源：`CapsuleComponents.kt`（`CapsuleCheckItemRow` 長按刪除、`buildClockwisePerimeterPath`）。

## 核心作用與適用場景

- **變化起點**：卡片按下點；按住滿 150ms 才進入「保持中」。
- **操作落點**：整張卡片；紅色弧線沿外框跑滿一圈 = 確認刪除。
- **誰跟著回應**：外框底環變淡、紅環順時針推進、放開/移出時紅環倒退；刪除時卡片縮放淡出。
- **周圍是否讓位**：本動效不讓位；刪除後由外層列表重排補位。
- 適用：不想跳確認對話框的危險操作（刪除、清空）；用「時間成本」取代二次確認。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 狀態機 | `Idle → Pressed(<150ms) → HoldEngaged(環填充) → Deleting` ；任何時刻放開/移出/被消費 → `Rewind → Idle` |
| 起始延遲 | `delay(150L)` 後仍未取消才 `holdEngaged = true` 並 `HapticFeedbackType.LongPress` |
| 填充 | `holdRingProgress: Animatable(0f)`；`fillDuration = (1 − start) × 1000ms`（從當前值接續）；`tween(LinearEasing)` → 1f |
| 退回 | `rewindDuration = (300 × current).roundToInt().coerceIn(80, 300)`；`tween(LinearEasing)` → 0f |
| 觸發刪除 | 填充完成且 `progress ≥ 0.999f && !isDeleting` → 觸覺 `TextHandleMove`；`deleteExitProgress.animateTo(1f, tween(160, Linear))` 後呼叫 `onDelete`；ReducedMotion 直接刪除 |
| 退出視覺 | `scale = pressScale × lerp(1f, 0.88f, exit)`；`alpha = lerp(1f, 0f, exit)` |
| 按下縮放 | `animateFloatAsState`：按下 `0.97f`、拖曳浮起 `1.03f`、其餘 `1f`；`spring(0.8f, 380f)` |
| 環幾何 | 圓角 `24.dp`；線寬 `2.5.dp`，`StrokeCap.Round`；路徑內縮 `strokeWidth/2`；起點為**頂邊中點**，順序：頂邊→右上弧→右邊→右下弧→底邊→左下弧→左邊→左上弧→close |
| 弧線角度 | 右上 `-90° +90°`、右下 `0° +90°`、左下 `90° +90°`、左上 `180° +90°`；`r = cornerRadius.coerceIn(0, min(w,h)/2)` |
| 進度繪製 | `pathMeasure.getSegment(0, totalLen × progress, dest, startWithMoveTo = true)`，色 `colorScheme.error` |
| 底環 | 環出現時邊框改為 `outlineVariant@0.45`；平常邊框寬 `lerp(1.5, 1, checked).dp`（拖曳時 `2.dp`） |
| 取消條件 | 手指移出邊界、事件被他人消費、放開（未刪除）；移動超過 `touchSlop` 放開則不視為點擊 |
| 衝突處理 | 子按鈕 `isChildButtonPressed` 時略過；壓縮堆疊卡改走展開點擊，不啟動長按 |
| 快取 | 尺寸改變才 `buildClockwisePerimeterPath` + `pathMeasure.setPath` |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **`holdRingProgress` 只在 `drawBehind` 讀取**：1 秒的環動畫期間 0 重組。
2. **`Path` / `PathMeasure` / 區段 `Path` 皆 `remember` 持有**，尺寸不變時完全不重建；每幀只 `reset()` + `getSegment`。
3. **刪除退出走 `graphicsLayer { }`**（scale + alpha），不使用 `AnimatedVisibility`。
4. **`isPressed` 為僅有的重組狀態**，驅動 `pressScale` 的 `animateFloatAsState`（讀取點在 graphicsLayer）。
5. **協程管理**：`holdAnimJob` 單一引用，新手勢與 `onDispose` 皆取消。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
fun buildClockwisePath(w: Float, h: Float, cornerR: Float, stroke: Float, out: Path) {
    out.reset()
    val half = stroke / 2f
    val l = half; val t = half; val r = (w - half).coerceAtLeast(l + 1f); val b = (h - half).coerceAtLeast(t + 1f)
    val cr = cornerR.coerceIn(0f, min((r - l) / 2f, (b - t) / 2f))
    out.moveTo((l + r) / 2f, t); out.lineTo(r - cr, t)
    if (cr > 0f) out.arcTo(Rect(r - 2 * cr, t, r, t + 2 * cr), -90f, 90f, false)
    out.lineTo(r, b - cr)
    if (cr > 0f) out.arcTo(Rect(r - 2 * cr, b - 2 * cr, r, b), 0f, 90f, false)
    out.lineTo(l + cr, b)
    if (cr > 0f) out.arcTo(Rect(l, b - 2 * cr, l + 2 * cr, b), 90f, 90f, false)
    out.lineTo(l, t + cr)
    if (cr > 0f) out.arcTo(Rect(l, t, l + 2 * cr, t + 2 * cr), 180f, 90f, false)
    out.close()
}

fun Modifier.holdToDelete(ring: Animatable<Float, AnimationVector1D>, exit: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope, haptic: HapticFeedback, ringColor: Color, cornerR: Float, strokePx: Float,
    onDelete: () -> Unit): Modifier = composed {
    val path = remember { Path() }; val seg = remember { Path() }; val pm = remember { PathMeasure() }
    var cached by remember { mutableStateOf(Size.Zero) }; var job by remember { mutableStateOf<Job?>(null) }
    fun rewind() { job?.cancel(); job = scope.launch { val c = ring.value
        if (c > 0f) ring.animateTo(0f, tween((300 * c).roundToInt().coerceIn(80, 300), easing = LinearEasing)) } }

    this.graphicsLayer { val e = exit.value; scaleX = lerp(1f, 0.88f, e); scaleY = scaleX; alpha = 1f - e }
    .drawBehind {
        val p = ring.value.coerceIn(0f, 1f)
        if (p > 0.001f) {
            if (cached != size) { cached = size; buildClockwisePath(size.width, size.height, cornerR, strokePx, path); pm.setPath(path, false) }
            seg.reset(); pm.getSegment(0f, pm.length * p, seg, true)
            drawPath(seg, ringColor, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
    }.pointerInput(Unit) { awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true); var engaged = false; var cancelled = false
        job?.cancel()
        val hold = scope.launch {
            delay(150L); if (cancelled) return@launch
            engaged = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            ring.animateTo(1f, tween(((1f - ring.value) * 1000f).roundToInt().coerceAtLeast(1), easing = LinearEasing))
            if (!cancelled && ring.value >= 0.999f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                exit.animateTo(1f, tween(160, easing = LinearEasing)); onDelete()
            }
        }
        job = hold
        while (true) {
            val c = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
            val inside = c != null && c.position.x in 0f..size.width.toFloat() && c.position.y in 0f..size.height.toFloat()
            if (c == null || c.isConsumed || !inside || !c.pressed) { cancelled = true; if (engaged || !inside) rewind() else hold.cancel(); break }
        }
    } }
}
```

## Copyable Implementation Prompt (English)

```text
Implement a hold-to-delete perimeter ring for a rounded-rect card (corner radius 24dp) in Jetpack Compose. On pointer down start a 150ms timer; if the finger is still pressed inside the card after 150ms, fire LongPress haptic feedback and animate a single Animatable ringProgress from its current value to 1 with LinearEasing and duration (1 - current) * 1000ms. Draw the ring in drawBehind: build a clockwise perimeter Path inset by strokeWidth/2 starting at the top-edge midpoint (top edge, top-right arc -90deg sweep 90, right edge, bottom-right arc 0/90, bottom edge, bottom-left arc 90/90, left edge, top-left arc 180/90, close), cache the Path and PathMeasure and rebuild only when the size changes, then each frame pathMeasure.getSegment(0, length * progress, segmentPath, startWithMoveTo = true) and drawPath with colorScheme.error, stroke 2.5dp, StrokeCap.Round. While the ring is visible, dim the base border to outlineVariant at 45% alpha. If the user releases, leaves the card bounds, or the event is consumed by someone else before completion, cancel the fill and rewind linearly with duration clamp(300ms * currentProgress, 80, 300ms). A release before 150ms with movement <= touchSlop is a normal tap. When progress reaches 0.999, fire TextHandleMove haptics, animate exitProgress 0 to 1 over 160ms linear (scale lerp(1, 0.88), alpha lerp(1, 0)) and only then call onDelete(); under ReducedMotion call onDelete() immediately. Card press scale: 0.97 while pressed, 1.0 otherwise, spring(0.8, 380). Read ring and exit values only in drawBehind / graphicsLayer, keep a single Job reference cancelled on new gestures and on dispose, and skip the gesture when a child button is pressed or when the card is in a compressed/stacked state. Accessibility: the hold gesture must have a non-gesture alternative (custom accessibility action "Delete" or a delete button with confirmation), keyboard users get Delete/Backspace on a focused card, visible focus ring, and announce the removal afterwards.
```
