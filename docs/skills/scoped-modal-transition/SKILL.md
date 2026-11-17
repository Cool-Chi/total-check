---
name: scoped-modal-transition
description: 局部作用域模態彈窗的標準入退場動效，具備非對稱進退場時長、微位移縮放、焦點陷阱與無障礙語意管理。
---

## 核心作用與適用場景

* **變化起源**：在最近的定位父容器（Scoped Host Container）內部原位浮現，絕不覆蓋全螢幕（非全局 `Dialog` / `position: fixed`），保留外部導覽與背景上下文的可見性。
* **操作落點**：開啟首幀焦點立即鎖定於彈窗面板本身並啟動焦點陷阱（Focus Trap）；關閉後焦點精準歸還至觸發開啟前的原元件。
* **周圍回應**：僅遮罩層（Scrim）與面板本體執行純合成層動畫（`opacity` 與 `transform`），背景內容保持靜止不重排。
* **適用場景**：卡片內嵌確認視窗、局部設定彈窗、輕量表單對話框。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 屬性 / 階段 | 規格參數 | 備註與邊界條件 |
| :--- | :--- | :--- |
| **Scrim 遮罩進場** | `opacity: 0f → 1f`，時長 `180ms`，`LinearEasing` | 背景色 `rgba(7, 8, 10, 0.62)`；**嚴禁**位移與縮放。 |
| **Panel 面板進場** | `opacity: 0f → 1f`、`translateY: 8.dp → 0.dp`、`scale: 0.98f → 1.0f`，時長 `220ms`，`CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)` | 水平與垂直置中於 Scrim；視覺基準 `maxWidth = 300.dp`、`radius = 18.dp`、`padding = 20.dp`、`1.dp` 微亮邊框。 |
| **統一退場 (Closing)** | 精準反轉上述屬性，時長統一為 **`150ms`** | 退場時間永遠短於進場時間（`150ms < 180ms/220ms`），確保關閉乾脆俐落；退場動畫結束後才卸載元件。 |
| **作用域邊界 (Scoping)** | 限縮於宿主容器（`Box` 邊界內） | 攔截內部點擊不穿透，點擊 Scrim 空白處或按 `Back` / `Escape` 鍵觸發關閉。 |
| **無障礙與焦點 (A11y)** | `role = dialog`、`isTraversalGroup = true` | 首幀自動 `requestFocus()` 至面板；建立焦點循環陷阱；關閉時呼叫原觸發節點 `focusRequester.requestFocus()`。 |
| **Reduced Motion 降級** | 時長壓縮至 `≤ 1ms`（瞬時 `snapTo`） | 開啟同幀直接套用最終狀態；關閉時立即卸載（Unmount）不播放退場動畫。 |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

* **純合成層屬性動畫**：僅允許對 `alpha`、`translationY` 與 `scaleX / scaleY` 進行動畫插值，嚴禁動畫化 `width`、`height` 或 `padding` 觸發 Layout Pass。
* **單一進度驅動（Single Progress Driver）**：使用單一 `Animatable(0f)` 驅動進退場狀態，在 `Modifier.graphicsLayer { ... }` 區塊內透過 `lerp` 計算即時位移、縮放與透明度，確保整個過渡期間 **0 次 Composition 重組**。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
@Composable
fun ScopedModalContainer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var isRendered by remember { mutableStateOf(visible) }
    val panelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            isRendered = true
            if (reducedMotion) {
                progress.snapTo(1f)
            } else {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)
                    )
                )
            }
        } else if (isRendered) {
            if (reducedMotion) {
                progress.snapTo(0f)
            } else {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
                )
            }
            isRendered = false
        }
    }

    if (isRendered) {
        BackHandler(enabled = visible) { onDismissRequest() }
        LaunchedEffect(Unit) { runCatching { panelFocusRequester.requestFocus() } }

        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 1. Scrim 遮罩層（無位移、無縮放）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (progress.value * (220f / 180f)).coerceIn(0f, 1f) }
                    .background(Color(0xFF07080A).copy(alpha = 0.62f))
                    .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
            )
            // 2. Panel 面板層（8.dp 位移 + 0.98f 縮放 + 焦點容器）
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(20.dp)
                    .focusRequester(panelFocusRequester)
                    .focusable()
                    .semantics { isTraversalGroup = true }
                    .graphicsLayer {
                        val t = progress.value
                        alpha = t
                        translationY = (1f - t) * 8.dp.toPx()
                        scaleX = 0.98f + 0.02f * t
                        scaleY = 0.98f + 0.02f * t
                    }
                    .background(Color(0xFF12151A), RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFFEDEEF0).copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                content()
            }
        }
    }
}
```

## Copyable Implementation Prompt (English)

```text
Plaintext
Use the Scoped Modal Entry & Exit interaction pattern in my existing interface. Preserve the current visual style, layout language, and component structure. Do not rewrite unrelated components. Implement the trigger, state changes, start and end states, motion behavior, responsive behavior, keyboard accessibility, touch support, and reduced-motion fallback described below:

Create a scoped modal overlay component that renders inside its nearest positioned parent container rather than covering the full viewport. When triggered, animate a backdrop scrim (background rgba(7, 8, 10, 0.62)) from opacity 0 to 1 over 180ms using a linear easing curve with zero translation and zero scaling. Simultaneously, animate a centered modal panel (max-width 300px, background #12151a, 1px border rgba(237, 238, 240, 0.1), border-radius 18px, padding 20px, shadow 0 24px 60px rgba(0, 0, 0, 0.55)) from opacity 0 to 1, translateY(8px) to 0, and scale(0.98) to 1 over 220ms using cubic-bezier(0.2, 0.8, 0.2, 1). Animate only transform and opacity properties to maintain compositor-only rendering. When closing via scrim click or Escape key, reverse the exact properties over 150ms for both scrim and panel before unmounting. On the initial open frame, focus the panel container directly, establish a strict keyboard focus trap with dialog accessibility semantics, and restore focus to the previously active trigger element upon closing. For reduced-motion preferences, collapse durations to <=1ms, apply final states in the same frame, and unmount immediately on close.
```
