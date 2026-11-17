---
name: staggered-bulk-selection
description: 批量重置時底層資料立即更新，視覺層由上而下以 20ms + idx×35ms（上限 560ms）錯峰取消勾選，並附 1.045f 彈性脈衝放大的波浪效果。
---

# Staggered Bulk Selection | 錯峰波浪批量狀態切換

> 來源：`CapsuleComponents.kt`（`CapsuleCheckItemRow` 的 `resetWaveKey` / `resetDelayMs` / `resetTriggeredAtMillis`）。
> 延遲公式 `20 + idx × 35`（上限 560）由**呼叫端**計算並以 `resetDelayMs` 傳入，不在讀取範圍檔案中，下方列為規格值。

## 核心作用與適用場景

- **變化起點**：「全部重置」按鈕。
- **操作落點**：整份列表；資料層一次性寫入，不等動畫。
- **誰跟著回應**：每一列依自身索引延遲後才播放「取消勾選」視覺 + 脈衝。
- **周圍是否讓位**：不讓位。
- 適用：全選/全不選、批量重置、批量刪除前的狀態回退。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 資料層 | `item.isChecked` 立即變 false（資料與持久化不延遲） |
| 視覺層 | 每列自有 `visualChecked`；波次期間保持 `true`，延遲後才設為 `false` |
| 延遲（規格） | `resetDelayMs = min(20 + idx × 35, 560)`，由上而下 |
| 時序對齊 | `resetTriggeredAtMillis`（觸發時刻 `uptimeMillis`）；`elapsed = now − triggeredAt`；`remainingDelay = (delay − elapsed).coerceAtLeast(0)`，避免列表重組/捲動後延遲被重新計算 |
| 波次有效窗 | `elapsed < delay + 450L` 才參與；滾進視窗較晚的列不會補播 |
| 去重 | `lastConsumedWaveKey` 與 `resetWaveKey` 比對，同一波只消耗一次 |
| 初始預判 | 重組時 `isInActiveWave` → `visualChecked` 初值 `true`，避免閃現未勾選 |
| 脈衝 | `wavePulseScale: Animatable(1f)`：先 `animateTo(1.045f, spring(0.7f, 900f))`，再 `animateTo(1f, spring(0.45f, 360f))`（過衝後欠阻尼回彈） |
| 脈衝外溢 | 卡片整體 `scale = pulse`；左側色條 `scaleY = 1 + (pulse−1)×2.2`、`scaleX = 1 + 0.5×extra`；狀態徽章 `scale = 1 + (pulse−1)×1.8` |
| 勾選轉場 | `checkedProgress: Animatable`，`spring(0.8f, 380f)` 驅動：卡片背景/邊框/色條/徽章色 `lerp`；整列 `alpha = lerp(1f, 0.45f, progress)` |
| 中斷 | 波次等待中若資料又變為已勾選 → `isWaveWaitingOrAnimating = false`，立即以資料為準 |
| ReducedMotion | 無波次、無脈衝，`checkedProgress.snapTo` |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **單一 `checkedProgress: Animatable<Float>` 驅動所有顏色**：背景、邊框、色條、徽章全部在 `drawBehind` 內 `lerpColor(unchecked, checked, progress)`，不為每個顏色建立 `animateColorAsState`。
2. **脈衝在 `graphicsLayer { }` 內讀 `wavePulseScale.value`**；卡片 / 色條 / 徽章三處用不同倍率共用同一個值。
3. **延遲用 `LaunchedEffect(resetWaveKey)` 內 `delay`**：每列各自一個協程，不需要中央排程器。
4. **只有 `visualChecked`（Boolean）觸發重組**，一次轉換一次；文字刪除線與字重依賴它。
5. **與時間戳對齊而非累加**：列的建立時機不影響總時序。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
/** 呼叫端：依列表索引產生錯峰延遲 */
fun waveDelayMs(index: Int): Int = (20 + index * 35).coerceAtMost(560)

@Composable
fun <T> StaggeredToggleRow(
    checked: Boolean,                 // 資料層真實狀態（已立即更新）
    waveKey: Int, waveDelayMs: Int?, waveTriggeredAt: Long,
    reducedMotion: Boolean, content: @Composable (progress: () -> Float, pulse: () -> Float) -> Unit,
) {
    val inWave = !reducedMotion && waveKey > 0 && waveDelayMs != null && waveTriggeredAt > 0L &&
        SystemClock.uptimeMillis() - waveTriggeredAt < waveDelayMs + 450L
    var visualChecked by remember { mutableStateOf(if (inWave) true else checked) }
    var consumedKey by remember { mutableIntStateOf(0) }
    var waiting by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(1f) }
    val progress = remember { Animatable(if (visualChecked) 1f else 0f) }

    if (waveKey > 0) LaunchedEffect(waveKey) {
        val delayMs = waveDelayMs ?: return@LaunchedEffect
        if (waveKey == consumedKey || reducedMotion) return@LaunchedEffect
        val elapsed = (SystemClock.uptimeMillis() - waveTriggeredAt).coerceAtLeast(0L)
        if (elapsed >= delayMs + 450L) return@LaunchedEffect
        consumedKey = waveKey; waiting = true; visualChecked = true; pulse.snapTo(1f)
        delay((delayMs - elapsed).coerceAtLeast(0L))
        if (!waiting) return@LaunchedEffect
        visualChecked = false; waiting = false
        pulse.animateTo(1.045f, spring(dampingRatio = 0.7f, stiffness = 900f))
        pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 360f))
    }
    LaunchedEffect(checked) {                                    // 資料層同步，但等待波次時不提前取消
        if (checked) { waiting = false; visualChecked = true; pulse.snapTo(1f) }
        else if (reducedMotion || !(waveKey > 0 && waveDelayMs != null && (waveKey != consumedKey || waiting)))
            visualChecked = false
    }
    LaunchedEffect(visualChecked, reducedMotion) {
        val t = if (visualChecked) 1f else 0f
        if (reducedMotion) progress.snapTo(t) else progress.animateTo(t, spring(0.8f, 380f))
    }
    content({ progress.value }, { pulse.value })
}
```

## Copyable Implementation Prompt (English)

```text
Implement a staggered bulk-reset wave for a list of toggle rows in Jetpack Compose. When the user taps "Reset all", update the underlying data immediately (all items become unchecked in the database/state), but keep each row's VISUAL state checked until its personal delay elapses. The caller computes per-row delay = min(20ms + index * 35ms, 560ms) from top to bottom and passes resetWaveKey (increments per wave), resetDelayMs and resetTriggeredAtMillis (uptimeMillis at tap) to every row. Each row keeps its own visualChecked flag and a lastConsumedWaveKey. In a LaunchedEffect(resetWaveKey): compute elapsed = now - triggeredAt; skip if elapsed >= delay + 450ms or the key was already consumed or ReducedMotion is on; otherwise set visualChecked = true, wait max(0, delay - elapsed) (so recomposition or late scrolling never restarts the timeline), then set visualChecked = false and run a pulse: Animatable scale to 1.045 with spring(dampingRatio 0.7, stiffness 900), then back to 1 with spring(dampingRatio 0.45, stiffness 360). On first composition, if the row is within an active wave, initialise visualChecked = true to avoid flashing unchecked. If the data becomes checked again while waiting, cancel the wait and follow the data. Drive all color changes (background, border, accent bar, badge) from ONE Animatable checkedProgress animated with spring(0.8, 380), lerping colors inside drawBehind, and fade row content alpha from 1 to 0.45. Apply the pulse in graphicsLayer: card scale = pulse, accent bar scaleY = 1 + (pulse - 1) * 2.2 and scaleX = 1 + 0.5 * that extra, badge scale = 1 + (pulse - 1) * 1.8. Only the Boolean visualChecked may cause recomposition (strikethrough, font weight). ReducedMotion: no wave, no pulse, snapTo final state. Accessibility: stateDescription follows visualChecked, custom click action label "Mark checked/unchecked", live announcement of the bulk result once rather than per row.
```
