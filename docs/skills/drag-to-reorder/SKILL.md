---
name: drag-to-reorder
description: 長按手把使項目脫離佈局流升起（1.03f/1.06f 縮放 + 陰影邊框），原位留下虛線槽位，每幀依中心線計算落點索引讓其他項目彈簧讓位，含邊緣自動捲動與無障礙上下/左右移動。
---

# Drag-to-Reorder | 懸浮手把拖拽排序與虛線槽位

> 來源：`ExpandingChecklistTabBar.kt`（水平頁籤拖曳完整實作）、`CapsuleComponents.kt`（列表項視覺：`isFloatingDragged`、`DashedCapsuleEmptySlot`、拖曳手把與無障礙）。
> 列表項的拖曳控制器不在讀取範圍內，下方以頁籤實作為準，列表項採同一狀態機（軸向改為垂直）。

## 核心作用與適用場景

- **變化起點**：長按頁籤 / 按下列表項的六點手把。
- **操作落點**：手指位置；浮層跟隨手指，槽位停在「目前落點索引」。
- **誰跟著回應**：其餘項目依新順序彈簧位移讓位；浮層放開後彈簧落入槽位。
- **周圍是否讓位**：是，位移補償 `TabReflowAnim` / `ItemReflowAnim`。
- 適用：頁籤、清單項目、卡片、圖示網格的手動排序。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 狀態 | `Idle → Dragging(draggedId, workingList) → Settling(isSettlingDrop) → Idle`；另有 `committedOrderIds` 樂觀提交，待上游資料同步後清除 |
| 觸發 | 頁籤：系統長按逾時（`longPressTimeoutMillis`）+ `LongPress` 觸覺；僅 `size > 1` 可排序 |
| 升起縮放 | 頁籤浮層 `FLOATING_TAG_LIFT_SCALE = 1.06f`（`spring(0.8f, 420f)`，放開時回 1f）× 選中縮放 `1.08f`；列表項浮層 `1.03f`（`spring(0.8f, 380f)`） |
| 浮層視覺 | 頁籤：`shadow(10.dp)` + `2.dp` 主色邊框；列表項：`shadowElevation = 14.dp` + `shadow(10.dp, RoundedCornerShape(24.dp))` + `2.dp` 主色邊框 |
| 虛線槽位 | 頁籤 `DashedTabEmptySlot`：填色 `surfaceContainerLow@0.45`、虛線 `primary@0.50`、`dashPathEffect([12f, 8f])`、線寬 `1.5.dp`、最小 `48×40.dp`；列表項 `DashedCapsuleEmptySlot`：虛線 `primary@0.42`、`[14f, 10f]`、圓角 `24.dp` |
| 浮層座標 | `dragOverlayX = (pointerX − grabOffsetX).coerceIn(4.dp, viewportW − dragW − 4.dp)`；`grabOffsetX = pointerX − itemLeftInViewport`（夾在 `0..width`） |
| 落點索引 | 浮層左緣 `L`、右緣 `R`（換算到 layout 座標：`+ scroll − startSpacer`）；對 `idx < cur`：`L < itemCenter` → `target = min(target, idx)`；對 `idx > cur`：`R > itemCenter` → `target = max(target, idx)`；每次變動 `TextHandleMove` 觸覺 |
| 讓位彈簧 | 頁籤 `spring(0.78f, 460f)`（偏移夾 `±420f`）；列表 `spring(0.8f, 380f)` |
| 放開落位 | `dropOffsetAnim` 起點 = `浮層X − 目標槽X`，`spring(dampingRatio = 0.8f, stiffness = 420f, visibilityThreshold = 0.5f)` → 0；完成後 `suppressNextReflowFor(id)` 避免二次補間 |
| 邊緣自動捲動 | 邊緣區 `min(40.dp, viewport × 0.22)`；最大速度 `280.dp/s`；`速度 = max × t² × dt`，`t = 滲入邊緣比例 (0..1)`；`dt` 夾 `0..0.05s`；逐幀 `withFrameNanos`；捲動後重算落點 |
| 順序凍結 | 拖曳/落位/樂觀提交期間 `isOrderFrozen`：選中分數改為 0/1 硬值，避免與 Pager 位置相互干擾 |
| 無障礙 | 頁籤自訂動作 `move left / move right`；列表手把 `DirectionUp/Down` 鍵 + 自訂動作 `move up / move down`；`contentDescription` 含項目名稱 |
| ReducedMotion | 不升起縮放、不補償、落位 `snapTo(0)` |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **拖曳項從佈局流抽離**：`Layout` 中該位置換成同尺寸 `DashedEmptySlot`，浮層另以疊加的 `Box.graphicsLayer { translationX = overlayX + dropOffset }` 繪製，**手指移動只改 GraphicsLayer 平移**，不觸發重量測。
2. **手勢走 `PointerEventPass.Initial`**：容器層攔截並 `consume()`，不與子項的點擊/捲動衝突；長按判定在子項，拖曳追蹤在容器。
3. **落點計算用預配置陣列**（`visualLefts/visualRights`），每次 pointer move 零分配；只有 `workingList` 真的換序才寫 state。
4. **讓位位移只在 placement 讀 `reflowFrameNanos`**。
5. **自動捲動在 `withFrameNanos` 協程**，不佔用 pointer 事件迴圈。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
/** 以浮層邊界對照其他項目中心線，求新的插入索引（零分配） */
fun targetIndex(cur: Int, lefts: FloatArray, rights: FloatArray, count: Int, floatL: Float, floatR: Float): Int {
    var target = cur
    for (idx in 0 until count) {
        if (idx == cur) continue
        val c = (lefts[idx] + rights[idx]) / 2f
        if (idx < cur && floatL < c) { if (idx < target) target = idx }
        else if (idx > cur && floatR > c) { if (idx > target) target = idx }
    }
    return target
}

@Composable
fun <T> ReorderableRow(items: List<T>, id: (T) -> Long, onCommit: (List<Long>) -> Unit,
                       itemContent: @Composable (T, lifted: Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current; val scope = rememberCoroutineScope()
    var dragId by remember { mutableStateOf<Long?>(null) }
    var working by remember { mutableStateOf(items) }
    var overlayX by remember { mutableFloatStateOf(0f) }; var grab by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val drop = remember { Animatable(0f) }
    val lift by animateFloatAsState(if (settling) 1f else 1.06f, spring(0.8f, 420f))

    fun finish(targetX: Float) { if (dragId == null || settling) return
        settling = true; onCommit(working.map(id))
        scope.launch {
            drop.snapTo(overlayX - targetX); overlayX = targetX
            drop.animateTo(0f, spring(0.8f, 420f, 0.5f))
            dragId = null; settling = false; drop.snapTo(0f)
        } }

    Box(Modifier.pointerInput(Unit) { awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (c == null || !c.pressed) { if (dragId != null) finish(/* 槽位 X */ overlayX); break }
            if (dragId != null && !settling) {
                c.consume(); overlayX = c.position.x - grab
                val cur = working.indexOfFirst { id(it) == dragId }
                // val t = targetIndex(cur, lefts, rights, n, overlayX, overlayX + dragW)
                // if (t != cur) { working = working.toMutableList().apply { add(t, removeAt(cur)) }; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }
        }
    } }) {
        Row { working.forEach { key(id(it)) {
            if (id(it) == dragId) DashedSlot() else itemContent(it, false) } } }
        working.firstOrNull { id(it) == dragId }?.let {
            Box(Modifier.graphicsLayer { translationX = overlayX + drop.value; scaleX = lift; scaleY = lift }
                .shadow(10.dp, RoundedCornerShape(50), clip = false)) { itemContent(it, true) }
        }
    }
}
```

## Copyable Implementation Prompt (English)

```text
Implement drag-to-reorder for a list/tab strip in Jetpack Compose with a floating item and a dashed placeholder. Start dragging on long press (tab) or on press of a six-dot handle (list item) with LongPress haptic feedback; only enable when there are at least 2 items. Maintain a state machine Idle -> Dragging -> Settling -> Idle with a working copy of the list and an optimistic committedOrder until the upstream data matches. When dragging begins, REMOVE the item from the layout flow: render a same-size dashed placeholder (fill surfaceContainerLow at 45%, dashed outline primary at 50%, dashPathEffect [12, 8] (list: [14, 10], alpha 42%), stroke 1.5dp, corner radius = pill/24dp) and draw the dragged item as a floating overlay using graphicsLayer translation only (scale 1.06 for tabs / 1.03 for list rows via spring(0.8, 420/380), shadow 10dp, 2dp primary border). Track pointers on the container with PointerEventPass.Initial and consume while dragging. Overlay position = (pointer - grabOffset) clamped to the viewport with 4dp padding. On every move compute the target index with allocation-free arrays of visual bounds: for items before the current index, if floating.left < itemCenter lower the target; for items after, if floating.right > itemCenter raise it; when the target changes, reorder the working list and give TextHandleMove haptic feedback. Other items animate to new positions with a compensation spring (dampingRatio 0.78-0.8, stiffness 380-460, initial offset = previousNaturalPosition - newNaturalPosition, clamped to +/-420px), driven by withFrameNanos and read only in the placement phase; skip compensation for the dragged item. Auto-scroll when the pointer is within min(40dp, 22% of viewport) of an edge at up to 280dp/s with speed = max * t^2 * dt (t = penetration 0..1, dt clamped to 50ms), recomputing the target after each scroll step. On release animate the overlay into the slot: offset starts at (overlay - slot) and springs to 0 with dampingRatio 0.8, stiffness 420, then clear drag state and suppress the next reflow for that item. Freeze the order/selection state while dragging. ReducedMotion: no lift scale, no springs, snap into place. Accessibility: custom accessibility actions "Move left/right" (tabs) or "Move up/down" (rows), Arrow keys on the focused handle move the item, contentDescription includes the item name, and a visible keyboard focus ring on the handle.
```
