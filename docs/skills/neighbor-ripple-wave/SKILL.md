---
name: neighbor-ripple-wave
description: 單一項目狀態變更時，向前後相鄰項目（最多 9 層）發射錯峰衝擊波（每層延遲 35ms、持續 320ms、衰減率 0.48f、最多 3 道同時活躍），讓鄰居產生微位移與縮放共鳴而不改變真實狀態。
---

# Neighbor Ripple Wave | 鄰近項目波紋連動回饋

> 來源：`StackedCardScrollLayout.kt`（`triggerNeighborRipple`、`NeighborRippleWave`、placement 內波形計算）。

## 核心作用與適用場景

- **變化起點**：被切換的那一項（`sourceKey`）。
- **操作落點**：該項本身，輕微放大；鄰居不被操作。
- **誰跟著回應**：上下相鄰項目按距離延遲、衰減地被「推開再回位」並輕微放大。
- **周圍是否讓位**：是，但只是視覺位移（`placeRelativeWithLayer`），不改 layout、不改狀態。
- 適用：勾選/開關列表、設定列、任何希望「這一下有連動感」的密集列表。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 層間延遲 | `RIPPLE_LAYER_DELAY_NANOS = 35_000_000`（每遠一層晚 35ms） |
| 單層持續 | `RIPPLE_TOGGLE_DURATION_NANOS = 320_000_000`（320ms） |
| 最大距離 | `RIPPLE_MAX_EFFECTIVE_DISTANCE = 9`，實際 `min(max(anchorIndex, count−1−anchorIndex), 9)` |
| 衰減 | `attenuation = exp(−0.48 × (distance − 1))`（`RIPPLE_ATTENUATION_RATE = 0.48f`）；`< 0.02f`（`RIPPLE_MIN_ATTENUATION`）略過 |
| 同時活躍波 | `RIPPLE_MAX_ACTIVE_WAVES = 3`，超過先移除最舊一道 |
| 強度 | `intensity.coerceIn(0.1f, 1.5f)`，預設 1f |
| 本地時間 | `localElapsed = elapsed − distance × 35ms`；僅在 `0 < local < 320ms` 作用；`u = local / 320ms` |
| 縮放包絡 | `scaleEnv = (sin(πu) × (1 − u) × 1.74).coerceIn(0,1)` |
| 推移包絡 | `pushWave = sin(1.45πu) × (1 − u)² × 2.01`（先推開、略回彈、衰減為 0） |
| 源項 (distance=0) | `scaleAdd += 0.012 × intensity × stackVisibility × scaleEnv`，不位移 |
| 鄰居 | `offsetY += direction × maxPush × weight × pushWave`；`scaleAdd += 0.014 × weight × scaleEnv`；`weight = intensity × attenuation × stackVisibility`；`direction = −1`（上方）/ `+1`（下方） |
| 最大推移 | `maxRipplePushPx = 6.dp`；總偏移夾 `±(6.dp × 1.35)`；`scaleAdd` 夾 `0..0.028` |
| 堆疊可見度 | `stackVisibility = (1 − depth / 2.5).coerceIn(0,1)`；被壓縮進堆疊的卡片不抖動（`< 0.01` 略過） |
| 源項消失 | 來源 key 已不在列表（例如被刪除）→ 以 `anchorIndex − 0.5` 為虛擬來源，`distance = ceil(\|diff\|)` |
| 生命週期 | `maxLife = maxEffectiveDistance × 35ms + 320ms`；超過即移除；`startNanos` 在第一次 frame tick 才賦值 |
| ReducedMotion | `triggerNeighborRipple` 直接 return；placement 內不計算 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **波形計算全在 `layout { }` placement 區塊**：以 `animFrameNanos`（`MutableLongState`）為 frame tick，只失效 placement，不重組不重測。
2. **波以 `ArrayList<NeighborRippleWave>`（容量 4）管理**，純物件、不進 Snapshot state；喚醒靠 `MutableSharedFlow(extraBufferCapacity = 1, DROP_OLDEST)`。
3. **`withFrameNanos` 迴圈只在 `hasActiveAnimations()` 為真時運行**，閒置時協程自動結束。
4. **位移與縮放走 `placeRelativeWithLayer`**，不改變真實排版位置，不影響手勢命中。
5. **真實狀態零干擾**：波紋只疊加視覺偏移，`item.isChecked` 等業務狀態獨立。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
const val LAYER_DELAY = 35_000_000L; const val DURATION = 320_000_000L
const val MAX_DIST = 9; const val ATTEN = 0.48f; const val MIN_ATTEN = 0.02f; const val MAX_WAVES = 3

class RippleWave(val sourceKey: Any, val anchorIndex: Int, val intensity: Float,
                 val maxDistance: Int, var startNanos: Long = 0L)

class RippleHost {
    val waves = ArrayList<RippleWave>(4); var frameNanos = 0L
    val tick = mutableLongStateOf(0L)                         // 只在 placement 讀取
    val signal = MutableSharedFlow<Unit>(0, 1, BufferOverflow.DROP_OLDEST)

    fun trigger(keys: List<Any>, source: Any, intensity: Float = 1f, reduced: Boolean = false) {
        if (reduced) return
        val a = keys.indexOf(source); if (a < 0) return
        if (waves.size >= MAX_WAVES) waves.removeAt(0)
        waves += RippleWave(source, a, intensity.coerceIn(0.1f, 1.5f), min(max(a, keys.size - 1 - a), MAX_DIST))
        signal.tryEmit(Unit)
    }
    fun advance(now: Long) {
        frameNanos = now
        waves.removeAll { w -> if (w.startNanos == 0L) w.startNanos = now
            now - w.startNanos >= w.maxDistance * LAYER_DELAY + DURATION }
        tick.longValue = now
    }
    /** 回傳 (offsetY, scaleAdd)；於 placement 對每個 index 呼叫 */
    fun sample(index: Int, keys: List<Any>, visibility: Float, maxPushPx: Float): Pair<Float, Float> {
        var dy = 0f; var ds = 0f
        if (visibility <= 0.01f) return 0f to 0f
        for (w in waves) {
            if (w.startNanos == 0L) continue
            val src = keys.indexOf(w.sourceKey)
            val diff = if (src >= 0) index - src else index - (w.anchorIndex - 0.5f).toInt()
            val dist = if (src >= 0) abs(diff) else abs(diff).coerceAtLeast(1)
            if (dist > MAX_DIST) continue
            val local = frameNanos - w.startNanos - dist * LAYER_DELAY
            if (local !in 1L until DURATION) continue
            val u = local.toFloat() / DURATION
            val env = (sin(u * PI.toFloat()) * (1f - u) * 1.74f).coerceIn(0f, 1f)
            if (dist == 0) { ds += 0.012f * w.intensity * visibility * env; continue }
            val att = exp(-ATTEN * (dist - 1)); if (att < MIN_ATTEN) continue
            val push = sin(u * PI.toFloat() * 1.45f) * (1f - u) * (1f - u) * 2.01f
            val weight = w.intensity * att * visibility
            dy += (if (diff < 0) -1f else 1f) * maxPushPx * weight * push; ds += 0.014f * weight * env
        }
        return dy.coerceIn(-maxPushPx * 1.35f, maxPushPx * 1.35f) to ds.coerceIn(0f, 0.028f)
    }
}
// LaunchedEffect: host.signal.collect { while (host.waves.isNotEmpty()) withFrameNanos { host.advance(it) } }
```

## Copyable Implementation Prompt (English)

```text
Add a neighbor ripple wave to a vertical list in Jetpack Compose. When one item toggles, call triggerNeighborRipple(sourceKey, intensity = 1f) (ignored under ReducedMotion). Keep at most 3 active waves (drop the oldest) as plain objects in an ArrayList, each with sourceKey, anchorIndex, intensity clamped to 0.1..1.5, durationNanos = 320ms and maxEffectiveDistance = min(max(anchorIndex, count - 1 - anchorIndex), 9). Drive time with a withFrameNanos loop that runs only while waves exist (woken by a MutableSharedFlow with extraBufferCapacity 1 and DROP_OLDEST) and publishes a frame tick (MutableLongState) that is read ONLY inside the layout placement block; remove a wave when elapsed >= maxDistance * 35ms + 320ms and assign startNanos on its first frame. In placement, for each visible item and each wave: distance = |index - sourceIndex| (if the source no longer exists, use a virtual source at anchorIndex - 0.5 and distance = ceil(|diff|) >= 1), skip if distance > 9, localElapsed = elapsed - distance * 35ms, act only when 0 < localElapsed < 320ms, u = localElapsed / 320ms. scaleEnv = clamp(sin(pi*u) * (1-u) * 1.74, 0, 1). The source item (distance 0) only gets scaleAdd += 0.012 * intensity * visibility * scaleEnv. Neighbors: attenuation = exp(-0.48 * (distance - 1)), skip if below 0.02; pushWave = sin(1.45*pi*u) * (1-u)^2 * 2.01; weight = intensity * attenuation * visibility; offsetY += direction * 6dp * weight * pushWave (direction -1 above the source, +1 below); scaleAdd += 0.014 * weight * scaleEnv. Clamp total offset to +/-(6dp * 1.35) and scaleAdd to 0..0.028. visibility = clamp(1 - depth/2.5, 0, 1) so cards compressed into a stack do not jiggle. Apply with placeRelativeWithLayer (translation via y, scaleX/scaleY = baseScale * (1 + scaleAdd)); never change measured layout, hit-testing positions, or the items' real state. ReducedMotion: no ripple at all. Accessibility: the ripple is purely decorative, add no semantics and do not announce it; real state changes keep their own stateDescription.
```
