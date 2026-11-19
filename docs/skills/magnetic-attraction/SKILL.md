---
name: magnetic-attraction
description: 可移動元素靠近吸附目標時，依距離連續計算磁吸權重並以遲滯閾值防抖，在真實指標與目標之間平滑混合後透過阻尼彈簧追隨，鬆手僅於有效落點才提交。
---

# Magnetic Attraction | 磁吸效果

> 來源：互動動效設計規範（通用可複用規格，非提取自特定業務檔案）。

## 核心作用與適用場景

- **變化起點**：可移動元素（拖放物件、浮動控點、可吸附標記）的錨點進入目標錨點的磁吸半徑。
- **操作落點**：使用者持續拖動指標；視覺位置在「真實指標座標」與「目標錨點」間連續混合，而非於邊緣瞬間跳變。
- **誰跟著回應**：被拖動元素平滑偏向目標、目標槽位可同步顯示預覽高亮；關鍵按鈕本體在無明確需求時**不**追著滑鼠跑，避免命中區域難以預測。
- **周圍是否讓位**：不強制讓位（可依需求搭配槽位高亮或縮放提示）；移出磁吸範圍或取消手勢後立即恢復 1:1 正常控制。
- 適用：拖放目標、吸附槽位、畫布對齊輔助線、需要明確指示落點的可移動元素。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 狀態機 | `Free(跟隨真實指標) → Attracted(activeTargetId, weight ∈ (0,1]) → Committed(鬆手且有效)`；手勢取消或移出範圍 → `Free` |
| 距離與權重 | `d = hypot(pointerAnchor − targetAnchor)`；`raw = (1 − d / enterRadius).coerceIn(0f, 1f)`；`weight = raw.pow(1.6f)`（進入範圍邊緣時權重從 0 平滑起跳，越近越強） |
| 遲滯雙閾值（防抖） | `enterRadius`（如 `72.dp`）用於尚未鎖定目標時的進入判定；`exitRadius = enterRadius × 1.25f`（如 `90.dp`）用於已鎖定目標的脫離判定，避免交界處反覆切換 |
| 多目標競爭規則 | 1. 若已有 `activeTarget` 且其距離 `≤ exitRadius`，僅當另一目標距離 `< currentDist × 0.85f` 才切換；2. 否則選 `d ≤ enterRadius` 中距離最小者 |
| 混合目標座標 | `blendedTarget = lerp(pointerPos, targetAnchor, weight)`（欲表現「先有阻力再貼合」時，將 `weight` 改為非線性曲線映射視覺位移，絕不人為延遲輸入事件） |
| 阻尼追隨 | 以 `Animatable<Offset, AnimationVector2D>` 或雙軸 `Animatable<Float>` 追隨 `blendedTarget`：`spring(dampingRatio = 0.85f, stiffness = 520f, visibilityThreshold = 0.5f)`，避免每幀直接傳送座標 |
| 鬆手提交條件 | 僅當 `activeTarget != null && weight >= commitThreshold`（如 `0.35f`）且手勢正常結束（非 Cancel）時才執行放置提交；否則回彈或停留在原處/取消 |
| ReducedMotion | 不使用阻尼彈簧，直接 `snapTo(blendedTarget)`；仍保留清晰的目標高亮提示落點 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **座標與權重只在 `graphicsLayer` 讀取**：`offsetXAnim` / `offsetYAnim`（或 `Offset` `Animatable`）的 `.value` 只在 `Modifier.graphicsLayer { translationX = ...; translationY = ... }` 內讀取，拖動與磁吸追隨期間 0 重組。
2. **手勢追蹤與目標搜尋零分配**：目標錨點列表以預先量測的 `FloatArray` / `List<TargetAnchor>` 保存，於 `pointerInput` 內以純數值迴圈計算最近距離，不配置臨時物件。
3. **單一狀態驅動提交**：僅在「鎖定目標 ID 改變」或「鬆手提交」時才更新 Compose State（用於目標高亮與業務回呼），其餘每幀移動皆走協程 `animateTo` + `graphicsLayer`。
4. **輸入事件即時消費**：`awaitPointerEvent` 立即讀取最新指標位置並更新目標，不以 `delay` 延遲輸入，確保快速穿越時依然跟手。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
data class MagnetTarget(val id: Any, val anchor: Offset)

class MagneticAttractorState(
    private val enterRadiusPx: Float,
    private val exitRadiusPx: Float = enterRadiusPx * 1.25f,
    private val commitThreshold: Float = 0.35f,
) {
    var activeTargetId by mutableStateOf<Any?>(null)
        private set

    fun resolveBlendedTarget(pointer: Offset, targets: List<MagnetTarget>): Pair<Offset, Float> {
        val current = targets.firstOrNull { it.id == activeTargetId }
        val currentDist = current?.let { hypot(pointer.x - it.anchor.x, pointer.y - it.anchor.y) } ?: Float.MAX_VALUE

        var best: MagnetTarget? = if (current != null && currentDist <= exitRadiusPx) current else null
        var bestDist = if (best != null) currentDist else Float.MAX_VALUE

        for (t in targets) {
            val d = hypot(pointer.x - t.anchor.x, pointer.y - t.anchor.y)
            val threshold = if (best == null) enterRadiusPx else bestDist * 0.85f
            if (d <= threshold && d < bestDist) {
                best = t
                bestDist = d
            }
        }
        activeTargetId = best?.id
        if (best == null) return pointer to 0f
        val norm = (1f - bestDist / enterRadiusPx).coerceIn(0f, 1f)
        val weight = norm.pow(1.6f)
        return lerp(pointer, best.anchor, weight) to weight
    }

    fun canCommit(weight: Float): Boolean = activeTargetId != null && weight >= commitThreshold
}

// 使用端：於 graphicsLayer { translationX = animX.value; translationY = animY.value } 讀取
// 拖動中：val (blended, w) = state.resolveBlendedTarget(pointer, targets)
//         animX.animateTo(blended.x, spring(0.85f, 520f))；animY.animateTo(blended.y, spring(0.85f, 520f))
// 鬆手時：if (state.canCommit(lastWeight)) onCommit(state.activeTargetId!!) else onCancel()
```

## Copyable Implementation Prompt (English)

```text
Implement a magnetic attraction behavior for draggable elements and drop targets in Jetpack Compose. Compute the Euclidean distance d between the dragged element's anchor and each target anchor. Define two hysteresis radii: enterRadius (e.g., 72dp) to acquire a target and exitRadius = enterRadius * 1.25 (e.g., 90dp) to release an already-acquired target, preventing rapid switching at boundaries. When multiple targets compete, keep the current target while d <= exitRadius unless another target is strictly closer than currentDist * 0.85; otherwise pick the nearest target within enterRadius. Compute attraction weight smoothly from 0 at the edge to 1 at the center using weight = clamp(1 - d / enterRadius, 0, 1) ^ 1.6 (never snap abruptly at the boundary). Blend the target position: blended = lerp(pointerPos, targetAnchor, weight); if a "resistance then snap" feel is desired, shape the weight curve nonlinearly rather than delaying input events. Follow the blended target with a damped spring (dampingRatio 0.85, stiffness 520, visibilityThreshold 0.5) on Animatable X/Y values read ONLY inside Modifier.graphicsLayer (zero recomposition during drag). Distinguish attraction during drag from commit on release: only commit placement when the gesture ends normally (not cancelled) with an active target and weight >= 0.35; if dragged outside or cancelled, restore 1:1 pointer control or return cleanly without committing. Do not make stationary action buttons chase the pointer unless explicitly required. ReducedMotion: snapTo(blended) instead of spring animation while keeping target highlight feedback. Accessibility: provide keyboard/semantic move-to-target actions so users can place items without pointer dragging.
```
