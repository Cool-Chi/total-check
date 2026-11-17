---
name: radial-theme-transition
description: 以觸控點為圓心、最遠角為半徑的多波次圓形擴散主題切換；PointerDown 預擷取 0.6f 降採樣離屏快照，用 ClipOp 裁出新舊主題而不縮放頁面。
---

# Radial Theme Transition | 多波次圓形擴散主題切換

> 來源：`RadialThemeTransitionContainer.kt`。

## 核心作用與適用場景

- **變化起點**：使用者按下主題切換鈕的實際觸控座標（`originInRoot`）。
- **操作落點**：整個容器畫面；新主題以圓形從起點往外揭露，舊主題以凍結快照留在底下。
- **誰跟著回應**：整個內容樹（新主題已同步提交，圓形內是即時畫面、圓形外是舊快照）。
- **周圍是否讓位**：不讓位、不縮放，兩層尺寸位置完全一致，僅裁切。
- 適用：亮/暗主題切換、兩個完整視圖互換、需要連點多次仍不閃爍的全域換膚。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 快照降採樣 | `SNAPSHOT_SCALE = 0.6f`，`snapW = round(w × 0.6)`，`drawLayer(liveLayer)` 於 `scale(0.6, pivot = Zero)` 內錄製到 `snapshotLayer`；顯示時以 `FilterQuality.Low` 放大回 `dstSize` |
| 預擷取 | `onPreCapture`（PointerDown 時呼叫）：非 ReducedMotion、`!triggerMutex.isLocked`、尺寸 > 0 才執行；`scope.async { snapshotLayer.toImageBitmap() }`；`onCancelPreCapture` 取消 |
| 預擷取有效條件 | `capturedEpoch == committedThemeEpoch && drawnThemeEpoch == committedThemeEpoch`（主題世代一致才可用） |
| 防抖 | `triggerMutex: Mutex`：預擷取未完成時，`triggerMutex.withLock { 必要時 withFrameNanos{} → await 或現擷 }`；已鎖定時預擷取直接跳過 |
| 半徑 | `R = hypot(max(X, W − X), max(Y, H − Y))`（觸控點到最遠角距離）；圓心不在 `0..W × 0..H` 或 `fromClick=false` → 退為畫面中心 |
| 時長 / 曲線 | `durationMillis` 預設 `400`，`coerceIn(200, 700)`；`tween(EaseOut)`；動畫前先 `withFrameNanos {}` 對齊一幀 |
| 波次 | `RevealWave(id, origin, maxRadius, radiusAnim, frozenBitmap, job)`；新波加入時，前一波（頂層）若無 `frozenBitmap` 就拿到本次快照作為被覆蓋的舊畫面；`baseSnapshotBitmap` 為最底層 |
| 波次完成 | 移除所有更舊波、取消其 job；剩餘無波則清 `baseSnapshotBitmap`，否則把 `promotedBitmap` 升為 base |
| 挖空語意 | 繪製順序：底圖 → 每一波 `clipPath(Oval(origin, radius), ClipOp.Intersect) { frozenBitmap 或 liveLayer }`。每一波的「外側」等於被後一波覆蓋前的舊內容，等價於舊畫面以反向圓形（`ClipOp.Difference`）被挖空；規格要求之反向挖空可改寫為 `clipPath(path, ClipOp.Difference) { drawImage(old) }` |
| 降級 | `ReducedMotion` → 清除所有波與快照、`fallbackAlpha.snapTo(1f)`、直接切換；尺寸為 0 → 可選 `fallbackAlpha 0.55 → 1`（`tween(120, EaseOut)`） |
| ReducedMotion 判定 | `!ValueAnimator.areAnimatorsEnabled() \|\| ANIMATOR_DURATION_SCALE == 0f`，以 `ContentObserver` 即時監聽 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **內容樹只錄製一次**：`drawWithContent` 內把 `drawContent()` 錄進 `liveLayer`（`GraphicsLayer`），動畫期間內容不重組、不重測。
2. **半徑由 `Animatable` 在 Draw 階段讀取**：覆蓋層 `Spacer.drawBehind` 直接讀 `wave.radiusAnim.value`，每幀只重繪。
3. **`Path` 重用**：單一 `clipPath` 物件以 `rewind()` + `addOval` 重建。
4. **昂貴工作離開主執行緒前置**：快照在 PointerDown 就開始 `toImageBitmap()`，點擊完成時通常已就緒（`fastBitmap` 快路徑，零等待）。
5. **`LocalThemeTransitioning` CompositionLocal** 通知子元件凍結自己的色彩動畫（`snap()`），避免與圓形揭露疊加。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
private const val SNAP_SCALE = 0.6f
private class Wave(val origin: Offset, val maxR: Float, val r: Animatable<Float, AnimationVector1D>,
                   var frozen: ImageBitmap? = null, var job: Job? = null)

@Composable
fun RadialRevealHost(onToggle: () -> Unit, durationMs: Int = 400,
    content: @Composable (trigger: (Offset) -> Unit, preCapture: () -> Unit) -> Unit) {
    val gc = LocalGraphicsContext.current; val d = LocalDensity.current; val ld = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val live = remember(gc) { gc.createGraphicsLayer() }; val snap = remember(gc) { gc.createGraphicsLayer() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val waves = remember { mutableStateListOf<Wave>() }
    var base by remember { mutableStateOf<ImageBitmap?>(null) }
    var pre by remember { mutableStateOf<Deferred<ImageBitmap>?>(null) }
    val mutex = remember { Mutex() }
    val path = remember { Path() }

    fun record() = snap.record(d, ld, IntSize((size.width * SNAP_SCALE).roundToInt().coerceAtLeast(1),
        (size.height * SNAP_SCALE).roundToInt().coerceAtLeast(1))) {
        scale(SNAP_SCALE, SNAP_SCALE, pivot = Offset.Zero) { drawLayer(live) }
    }
    fun start(bmp: ImageBitmap, o: Offset) {
        val w = size.width.toFloat(); val h = size.height.toFloat()
        val origin = if (o.isSpecified && o.x in 0f..w && o.y in 0f..h) o else Offset(w / 2, h / 2)
        val maxR = hypot(max(origin.x, w - origin.x), max(origin.y, h - origin.y))
        val top = waves.lastOrNull()
        if (top != null && top.frozen == null) top.frozen = bmp else if (base == null) base = bmp
        val wave = Wave(origin, maxR, Animatable(0f)).also { waves.add(it) }
        onToggle()                                       // 先凍結舊畫面，再同步提交新主題
        wave.job = scope.launch {
            withFrameNanos { }
            wave.r.animateTo(maxR, tween(durationMs.coerceIn(200, 700), easing = EaseOut))
            val idx = waves.indexOf(wave)
            for (i in 0 until idx) waves[i].job?.cancel()
            if (idx > 0) waves.removeRange(0, idx)
            waves.remove(wave); if (waves.isEmpty()) base = null else wave.frozen?.let { base = it }
        }
    }
    val trigger: (Offset) -> Unit = { o -> scope.launch { mutex.withLock {
        val bmp = pre?.takeIf { !it.isCancelled }?.await() ?: run { record(); snap.toImageBitmap() }
        pre = null; start(bmp, o) } } }
    val preCapture = { if (!mutex.isLocked && size.width > 0) { record(); pre = scope.async { snap.toImageBitmap() } } }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Box(Modifier.fillMaxSize().drawWithContent {
            live.record { this@drawWithContent.drawContent() }
            if (waves.isEmpty()) drawLayer(live)
        }) { content(trigger, preCapture) }
        val b = base
        if (b != null && waves.isNotEmpty()) Spacer(Modifier.fillMaxSize().drawBehind {
            val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
            drawImage(b, dstSize = dst, filterQuality = FilterQuality.Low)
            waves.forEach { w -> if (w.r.value > 0f) {
                path.rewind(); path.addOval(Rect(w.origin, w.r.value))
                clipPath(path, ClipOp.Intersect) { w.frozen?.let { drawImage(it, dstSize = dst, filterQuality = FilterQuality.Low) } ?: drawLayer(live) }
            } }
        })
    }
}
```

## Copyable Implementation Prompt (English)

```text
Implement a multi-wave radial theme transition container in Jetpack Compose. Wrap the app content in a Box that records its content into a GraphicsLayer (liveLayer) via drawWithContent. Expose three callbacks to children: triggerTransition(originInRoot: Offset), onPreCapture(), onCancelPreCapture(). On PointerDown of the toggle button call onPreCapture: record a downsampled snapshot (scale 0.6, pivot Zero) of liveLayer into a second GraphicsLayer and start an async toImageBitmap(); skip this if a transition is already locked by a Mutex, if size is zero, or if ReducedMotion is on; cancel it on pointer cancel. Track a theme epoch counter so a pre-captured bitmap is only used if it was taken in the current epoch. On trigger, guard with a Mutex (triggerMutex); use the finished pre-capture when available, otherwise wait one frame and capture synchronously. Then: lock the old frame as a snapshot, commit the new theme synchronously, and animate a radius Animatable from 0 to R = hypot(max(x, W - x), max(y, H - y)) using the pointer position as center (fallback to the screen center if the origin is unspecified or outside bounds), tween(duration 400ms default, clamped to 200..700, EaseOut), after one withFrameNanos. Draw an overlay: the old snapshot as the base layer, then for each active wave clip to a circle at its origin with its current radius and draw its frozen bitmap, or the live layer if it has none. Equivalently, you may draw the old image with ClipOp.Difference of the circle so the old theme is punched out. Support overlapping waves: a new wave freezes the previous top wave's content; when a wave finishes, cancel and drop older waves and promote its bitmap to the base. Never scale page content; both layers keep identical size and position. Reuse a single Path (rewind + addOval), draw bitmaps with FilterQuality.Low. Provide a LocalThemeTransitioning flag so children use snap() for color animations while a wave runs. ReducedMotion (animators disabled or duration scale 0, observed live): clear all waves and switch instantly. Release both GraphicsLayers on dispose. The toggle button must remain keyboard operable (Enter/Space) and expose Role.Switch semantics.
```
