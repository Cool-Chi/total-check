---
name: ime-anchored-scoped-modal
description: 輸入型彈窗跨會話記憶軟鍵盤高度，開啟首幀即定錨於鍵盤上方，以 0.92f 縮放 + 20dp 位移進場，消除鍵盤升起造成的二次跳動。
---

# IME-Anchored Scoped Modal | 鍵盤高度記憶首幀定錨彈窗

> 來源：`ScopedModalOverlay.kt`（`ScopedModalHost`）。

## 核心作用與適用場景

- **變化起點**：彈窗開啟瞬間（同時請求焦點與喚起鍵盤）。
- **操作落點**：局部遮罩層內的面板；點遮罩（邊緣 28dp 除外）先收鍵盤，再次點才關閉。
- **誰跟著回應**：遮罩淡入、面板縮放上浮、整個面板垂直抬升 `ime × 0.5`。
- **周圍是否讓位**：背景頁面被遮罩攔截觸控與翻頁；面板自己讓位給鍵盤。
- 適用：新增/重新命名對話框、任何「彈出即聚焦輸入」的 Dialog / BottomSheet。

## 狀態機與物理參數規範 (Mathematical & Physics Specs)

| 項目 | 數值 / 公式 |
| :--- | :--- |
| 鍵盤額外高度 | `imeExtra = ime.bottom − navigationBars.bottom`（≥0） |
| 預設記憶值 | `defaultImeExtraPx = 300.dp.toPx()` |
| 記憶更新 | `snapshotFlow{imeExtra}.collectLatest`：`imeExtra > 120.dp` 時 `delay(180ms)` 後寫入 `rememberedImeExtraPx`（防動畫途中半截值） |
| 首幀錨點 | `sessionAnchorImePx = if (文字輸入彈窗) max(rememberedImeExtraPx, currentImeExtraPx) else 0f`；`modalImeLiftAnim = Animatable(sessionAnchorImePx)`，**初始值即錨點，無需動畫** |
| 抬升位移 | `translationY = −modalImeLiftAnim.value × 0.5f`（置中面板上移鍵盤高度的一半，即剛好落在可視區中央） |
| 目標抬升 | `max(sessionAnchor, remembered, 當前 IME)`；若 `value < target − 1f` 才補動畫（`tween(220, FastOutSlowInEasing)`） |
| 鍵盤收起 | `expectImeForModal=false` → `modalImeLiftAnim → 0f`（`tween(220, FastOutSlowInEasing)`），面板平滑回到中央 |
| 手動收鍵盤偵測 | 超過 `40.dp` 視為已開；之後若 `!imeVisible \|\| imeExtra < peak − 12.dp` → 解除鎖定、清焦點 |
| 進場縮放/位移 | `MODAL_START_SCALE = 0.92f`、`MODAL_TRANSLATE_Y = 20.dp`（由下往上） |
| 進場時長 | 遮罩 `MODAL_SCRIM_ENTER_MS = 200`（`LinearEasing`）；面板 `MODAL_PANEL_ENTER_MS = 250`（`CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)`） |
| 退場 | `MODAL_EXIT_MS = 150`（遮罩 Linear、面板同上貝茲） |
| 遮罩 | 最大 alpha `MODAL_SCRIM_MAX_ALPHA = 0.48f`，`zIndex(50f)` |
| 關閉邏輯 | 返回鍵/點遮罩：`imeExtra > 24.dp \|\| isImeVisible \|\| expectIme \|\| 有輸入焦點 \|\| 280ms 內剛收過鍵盤` → 先收鍵盤；否則關閉 |
| 點遮罩判定 | `dist ≤ touchSlop` 才算點擊；起點距左右邊緣 `< 28.dp` 放行（不攔截系統返回手勢） |
| ReducedMotion | 遮罩/面板 `snapTo`；抬升 `snapTo` |

## 零重組與渲染管線設計 (Zero-Recomposition Pipeline)

1. **面板進場全走 `panelProgressProvider: () -> Float`**：縮放 `lerp(0.92f, 1f, progress)`、位移 `lerp(20.dp, 0, progress)`、alpha 皆在 `graphicsLayer` 內讀取，動畫期間 0 重組。
2. **抬升用 `graphicsLayer { translationY }`，不用 `imePadding()`**：避免鍵盤動畫每幀重新量測，並且「凍結背景 IME 傳遞」——置中面板不吃 IME inset，只吃自己的 `Animatable`。
3. **遮罩 alpha 走 `graphicsLayer { alpha = scrim.value }`**，色塊在 `drawBehind` 繪製，不隨 alpha 重組。
4. **IME 觀測在 `snapshotFlow`**：把 inset 變化轉為 Flow，僅在符合門檻時更新記憶值。
5. **session 隔離**：所有 state 用 `remember(sessionKey)`，每次開窗重置，記憶值則留在 Host 層跨會話保存。

## 核心實作骨架 (Jetpack Compose Reference Snippet)

```kotlin
private val ENTER_EASING = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImeAnchoredModalHost(
    visible: Boolean, sessionKey: Int, isTextInput: Boolean, reducedMotion: Boolean,
    onRequestClose: () -> Unit, panel: @Composable (progress: () -> Float) -> Unit,
) {
    val density = LocalDensity.current
    val ime = WindowInsets.ime; val nav = WindowInsets.navigationBars
    var remembered by remember { mutableFloatStateOf(with(density) { 300.dp.toPx() }) }   // 跨會話
    LaunchedEffect(density) {
        val min = with(density) { 120.dp.toPx() }
        snapshotFlow { (ime.getBottom(density) - nav.getBottom(density)).toFloat() }
            .collectLatest { extra -> if (extra > min) { delay(180L); remembered = extra } }
    }
    if (!visible) return

    val current = (ime.getBottom(density) - nav.getBottom(density)).coerceAtLeast(0).toFloat()
    val anchor = remember(sessionKey) { if (isTextInput) max(remembered, current) else 0f }
    val lift = remember(sessionKey) { Animatable(anchor) }          // 首幀即在最終位置
    val scrim = remember(sessionKey) { Animatable(0f) }
    val panelP = remember(sessionKey) { Animatable(0f) }
    var expectIme by remember(sessionKey) { mutableStateOf(isTextInput) }

    LaunchedEffect(sessionKey, reducedMotion) {
        if (reducedMotion) { scrim.snapTo(1f); panelP.snapTo(1f) } else coroutineScope {
            launch { scrim.animateTo(1f, tween(200, easing = LinearEasing)) }
            launch { panelP.animateTo(1f, tween(250, easing = ENTER_EASING)) }
        }
    }
    LaunchedEffect(expectIme, reducedMotion) {
        val target = if (expectIme) max(anchor, max(remembered, current)) else 0f
        if (expectIme && lift.value >= target - 1f) return@LaunchedEffect
        if (reducedMotion) lift.snapTo(target) else lift.animateTo(target, tween(220, easing = FastOutSlowInEasing))
    }

    Box(Modifier.fillMaxSize().zIndex(50f)) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim.value }
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.48f)) }
            .pointerInput(sessionKey) { detectTapGestures { onRequestClose() } })
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)
            .graphicsLayer { translationY = -lift.value * 0.5f }, Alignment.Center) {
            val dy = with(density) { 20.dp.toPx() }
            Box(Modifier.graphicsLayer {
                val p = panelP.value.coerceIn(0f, 1f)
                scaleX = lerp(0.92f, 1f, p); scaleY = scaleX
                translationY = (1f - p) * dy; alpha = p
            }) { panel { panelP.value } }
        }
    }
}
```

## Copyable Implementation Prompt (English)

```text
Build an IME-anchored modal host for Jetpack Compose. Keep a rememberedImeExtraPx in the host (outliving individual modal sessions), initialised to 300dp. Observe imeExtra = WindowInsets.ime.bottom - WindowInsets.navigationBars.bottom via snapshotFlow().collectLatest; when imeExtra > 120dp, delay 180ms and store it. For a text-input modal, compute sessionAnchorImePx = max(rememberedImeExtraPx, currentImeExtraPx) at session start and create modalImeLiftAnim = Animatable(sessionAnchorImePx) so the very first frame is already at its final position. Do NOT use imePadding() on the centered panel (freeze background IME propagation); instead apply graphicsLayer { translationY = -modalImeLiftAnim.value * 0.5f }. Request focus and show the keyboard in the first frame of the session. If the actual IME later becomes taller, animate the lift to the new target with tween(220, FastOutSlowInEasing); when the keyboard is dismissed (button, system gesture, or imeExtra drops 12dp below its peak after exceeding 40dp), animate the lift back to 0 with the same tween so the panel glides to the screen center. Entrance: scrim alpha 0 to 1 over 200ms LinearEasing (max black alpha 0.48); panel progress 0 to 1 over 250ms with CubicBezierEasing(0.2, 0.8, 0.2, 1); panel scale lerp(0.92, 1, p), translationY lerp(20dp, 0, p), alpha p. Exit is 150ms. Pass progress to the panel as a () -> Float lambda read only in graphicsLayer. Scrim consumes touches (ignore touches within 28dp of the left/right edges so system back gestures still work); a tap (movement <= touchSlop) first dismisses the keyboard when IME is visible, an input is focused, or the IME closed within the last 280ms, otherwise closes the modal. Back button follows the same rule. Scope all state with remember(sessionKey). ReducedMotion: snapTo every animation. Accessibility: focus the cancel button first for destructive confirms, traversal group semantics, visible keyboard focus rings, Escape/Back closes.
```
