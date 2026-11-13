package com.example.totalcheck.ui.main

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAX_STACK_DEPTH = 3f
private const val FADE_OUT_DEPTH = 4f
private const val SCALE_STEP_PER_LAYER = 0.04f
const val COMPRESSED_STACK_INTERACTION_THRESHOLD = 0.35f

@Immutable
data class PlacedStackedItemInfo(
    val index: Int,
    val key: Any,
    val placedTopY: Float,
    val heightPx: Float,
    val scale: Float,
    val depth: Float,
    val isTopStack: Boolean,
    val isBottomStack: Boolean
) {
    val visualTopY: Float
        get() = when {
            isBottomStack -> placedTopY + heightPx * (1f - scale)
            else -> placedTopY
        }

    val visualBottomY: Float
        get() = when {
            isBottomStack -> placedTopY + heightPx
            else -> placedTopY + heightPx * scale
        }

    val visualCenterY: Float
        get() = (visualTopY + visualBottomY) / 2f

    val isCompressed: Boolean
        get() = depth > COMPRESSED_STACK_INTERACTION_THRESHOLD
}

@Stable
class StackedCardSlotState {
    val depthState: MutableFloatState = mutableFloatStateOf(0f)
    val isCompressed: Boolean by derivedStateOf { depthState.floatValue > COMPRESSED_STACK_INTERACTION_THRESHOLD }
}

private class ItemReflowAnim(
    var currentOffsetPx: Float = 0f,
    var lastNaturalTopPx: Float? = null,
    var activeAnim: TargetBasedAnimation<Float, AnimationVector1D>? = null,
    var startNanos: Long = 0L
)

private data class MeasuredSlotItem(
    val index: Int,
    val key: Any,
    val naturalTop: Float,
    val height: Float,
    val placeable: Placeable
)

private data class ComputedStackPlacement(
    val placedTopY: Float,
    val scale: Float,
    val alpha: Float,
    val zIndex: Float,
    val depth: Float,
    val transformOrigin: TransformOrigin,
    val isTopStack: Boolean,
    val isBottomStack: Boolean
)

@Stable
class StackedCardListState {
    internal val scrollOffsetState: MutableFloatState = mutableFloatStateOf(0f)
    internal val maxScrollState: MutableFloatState = mutableFloatStateOf(0f)
    internal val viewportHeightState: MutableFloatState = mutableFloatStateOf(0f)
    internal val animFrameNanos: MutableLongState = mutableLongStateOf(0L)

    internal val reflowSignal = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val cachedHeightsPx = HashMap<Any, Int>()
    private val reflowAnims = HashMap<Any, ItemReflowAnim>()
    private val slotStates = HashMap<Any, StackedCardSlotState>()

    internal var currentItemTops: FloatArray = FloatArray(0)
    internal var currentItemHeights: FloatArray = FloatArray(0)
    internal var currentKeys: List<Any> = emptyList()
    internal var currentTotalLinearHeight: Float = 0f
    internal var currentTopMarginPx: Float = 0f
    internal var currentBottomMarginPx: Float = 0f
    internal var currentTopInsetPx: Float = 0f
    internal var currentBottomInsetPx: Float = 0f
    internal var currentActiveMaxDepth: Float = MAX_STACK_DEPTH
    internal var currentPeekStepPx: Float = 0f
    internal var currentGapPx: Float = 0f

    var placedItemsInfo: List<PlacedStackedItemInfo> = emptyList()
        internal set

    val scrollOffsetPx: Float
        get() = scrollOffsetState.floatValue

    val maxScrollPx: Float
        get() = maxScrollState.floatValue

    val canScrollForward: Boolean
        get() = scrollOffsetState.floatValue < maxScrollState.floatValue - 0.5f

    val canScrollBackward: Boolean
        get() = scrollOffsetState.floatValue > 0.5f

    val scrollableState: ScrollableState = ScrollableState { delta ->
        val current = scrollOffsetState.floatValue
        val max = maxScrollState.floatValue
        val target = (current + delta).coerceIn(0f, max)
        val consumed = target - current
        if (consumed != 0f) {
            scrollOffsetState.floatValue = target
        }
        consumed
    }

    fun dispatchRawDelta(delta: Float): Float = scrollableState.dispatchRawDelta(delta)

    suspend fun animateScrollDelta(
        targetDelta: Float,
        animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 340f)
    ) {
        if (abs(targetDelta) < 0.5f) return
        scrollableState.scroll(MutatePriority.Default) {
            var prev = 0f
            animate(
                initialValue = 0f,
                targetValue = targetDelta,
                animationSpec = animationSpec
            ) { value, _ ->
                prev += scrollBy(value - prev)
            }
        }
    }

    suspend fun scrollToTop(animated: Boolean = true) {
        if (scrollOffsetState.floatValue <= 0.5f) return
        if (animated) {
            animateScrollDelta(targetDelta = -scrollOffsetState.floatValue)
        } else {
            scrollableState.scroll(MutatePriority.Default) {
                scrollBy(-scrollOffsetState.floatValue)
            }
        }
    }

    suspend fun scrollToExpandItem(index: Int, animated: Boolean = true) {
        if (index !in currentKeys.indices) return
        val maxScroll = maxScrollState.floatValue
        if (maxScroll <= 0.5f) return

        val top = currentItemTops[index]
        val height = currentItemHeights[index]
        val bottom = top + height
        val count = currentKeys.size
        val h = viewportHeightState.floatValue
        val peek = currentPeekStepPx
        val activeMax = currentActiveMaxDepth
        val effectiveTopBound = currentTopInsetPx + currentTopMarginPx
        val effectiveBottomBound = h - currentBottomInsetPx - currentBottomMarginPx

        val visualTopAtCurrentScroll = currentTopInsetPx + top - scrollOffsetState.floatValue
        val targetScroll = if (visualTopAtCurrentScroll < effectiveTopBound + peek * activeMax) {
            val layerFactor = minOf(index.toFloat(), activeMax)
            ((currentTopInsetPx + top) - (effectiveTopBound + peek * layerFactor)).coerceIn(0f, maxScroll)
        } else {
            val remainingAfter = (count - 1 - index).coerceAtLeast(0).toFloat()
            val layerFactor = minOf(remainingAfter, activeMax)
            ((currentTopInsetPx + bottom) - (effectiveBottomBound - peek * layerFactor)).coerceIn(0f, maxScroll)
        }

        val delta = targetScroll - scrollOffsetState.floatValue
        if (abs(delta) < 1f) return

        if (animated) {
            animateScrollDelta(targetDelta = delta)
        } else {
            scrollableState.scroll(MutatePriority.Default) {
                scrollBy(delta)
            }
        }
    }

    suspend fun scrollToCenterItem(
        key: Any,
        targetTopInsetPx: Float = currentTopInsetPx,
        targetBottomInsetPx: Float = currentBottomInsetPx,
        animated: Boolean = true
    ) {
        val index = currentKeys.indexOf(key)
        if (index !in currentItemTops.indices) return
        val viewportH = viewportHeightState.floatValue
        if (viewportH <= 1f) return

        val top = currentItemTops[index]
        val height = currentItemHeights[index]
        val itemCenterContentY = top + height / 2f

        val visibleTop = targetTopInsetPx + currentTopMarginPx
        val visibleBottom = (viewportH - targetBottomInsetPx - currentBottomMarginPx)
            .coerceAtLeast(visibleTop + height)
        val desiredCenterInContainerY = (visibleTop + visibleBottom) / 2f

        val usableHeight = (viewportH - targetTopInsetPx - targetBottomInsetPx).coerceAtLeast(1f)
        val targetMaxScroll = (currentTotalLinearHeight - usableHeight).coerceAtLeast(0f)

        val rawTargetScroll = (targetTopInsetPx + itemCenterContentY) - desiredCenterInContainerY
        val targetScroll = rawTargetScroll.coerceIn(0f, targetMaxScroll)
        val delta = targetScroll - scrollOffsetState.floatValue
        if (abs(delta) < 1f) return

        if (animated) {
            animateScrollDelta(
                targetDelta = delta,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 320f)
            )
        } else {
            scrollableState.scroll(MutatePriority.Default) {
                scrollBy(delta)
            }
        }
    }

    internal fun getSlotState(key: Any): StackedCardSlotState {
        return slotStates.getOrPut(key) { StackedCardSlotState() }
    }

    internal fun getCachedHeight(key: Any): Int? = cachedHeightsPx[key]

    internal fun peekReflowOffset(key: Any): Float = reflowAnims[key]?.currentOffsetPx ?: 0f

    internal fun updateCachedHeight(key: Any, heightPx: Int): Boolean {
        val prev = cachedHeightsPx[key]
        if (prev != heightPx) {
            cachedHeightsPx[key] = heightPx
            return prev != null
        }
        return false
    }

    internal fun syncReflowOnMeasure(
        keys: List<Any>,
        naturalTops: FloatArray,
        isReducedMotion: Boolean
    ) {
        for (i in keys.indices) {
            val k = keys[i]
            val naturalTopPx = naturalTops[i]
            val entry = reflowAnims.getOrPut(k) { ItemReflowAnim(lastNaturalTopPx = naturalTopPx) }
            val prevTop = entry.lastNaturalTopPx
            entry.lastNaturalTopPx = naturalTopPx

            if (prevTop != null) {
                val delta = prevTop - naturalTopPx
                if (abs(delta) > 0.5f) {
                    if (isReducedMotion) {
                        entry.currentOffsetPx = 0f
                        entry.activeAnim = null
                        entry.startNanos = 0L
                    } else {
                        val startOffset = entry.currentOffsetPx + delta
                        entry.currentOffsetPx = startOffset
                        entry.activeAnim = TargetBasedAnimation(
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                            typeConverter = Float.VectorConverter,
                            initialValue = startOffset,
                            targetValue = 0f
                        )
                        entry.startNanos = 0L
                        reflowSignal.tryEmit(Unit)
                    }
                }
            }
        }
    }

    internal fun pruneRemovedKeys(activeKeySet: Set<Any>) {
        if (reflowAnims.size > activeKeySet.size + 8) {
            val it = reflowAnims.keys.iterator()
            while (it.hasNext()) {
                if (it.next() !in activeKeySet) it.remove()
            }
        }
        if (slotStates.size > activeKeySet.size + 8) {
            val it = slotStates.keys.iterator()
            while (it.hasNext()) {
                if (it.next() !in activeKeySet) it.remove()
            }
        }
        if (cachedHeightsPx.size > activeKeySet.size + 16) {
            val it = cachedHeightsPx.keys.iterator()
            while (it.hasNext()) {
                if (it.next() !in activeKeySet) it.remove()
            }
        }
    }

    internal fun hasActiveAnimations(): Boolean {
        for (entry in reflowAnims.values) {
            if (entry.activeAnim != null) return true
        }
        return false
    }

    internal fun advanceReflowAnimations(frameNanos: Long) {
        var anyChanged = false
        for (entry in reflowAnims.values) {
            val anim = entry.activeAnim ?: continue
            if (entry.startNanos == 0L) {
                entry.startNanos = frameNanos
            }
            val elapsedNanos = (frameNanos - entry.startNanos).coerceAtLeast(0L)
            if (anim.isFinishedFromNanos(elapsedNanos)) {
                entry.currentOffsetPx = 0f
                entry.activeAnim = null
                entry.startNanos = 0L
            } else {
                entry.currentOffsetPx = anim.getValueFromNanos(elapsedNanos)
            }
            anyChanged = true
        }
        if (anyChanged) {
            animFrameNanos.longValue = frameNanos
        }
    }
}

@Composable
fun rememberStackedCardListState(): StackedCardListState {
    return remember { StackedCardListState() }
}

@Composable
fun <T> BidirectionalStackedCardList(
    items: List<T>,
    key: (T) -> Any,
    state: StackedCardListState = rememberStackedCardListState(),
    topInsetPxProvider: () -> Float = { 0f },
    bottomInsetPxProvider: () -> Float = { 0f },
    pinnedExpandedKeyProvider: () -> Any? = { null },
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalSpacing: Dp = 10.dp,
    peekStep: Dp = 8.dp,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T, slotState: StackedCardSlotState) -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isReducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()

    val currentItems by rememberUpdatedState(items)
    val currentKeySelector by rememberUpdatedState(key)
    val currentReducedMotion by rememberUpdatedState(isReducedMotion)

    LaunchedEffect(state) {
        state.reflowSignal.collect {
            while (state.hasActiveAnimations()) {
                withFrameNanos { frameNanos ->
                    state.advanceReflowAnimations(frameNanos)
                }
            }
        }
    }

    val topPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    val bottomPaddingPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val startPaddingPx = with(density) { contentPadding.calculateStartPadding(layoutDirection).roundToPx() }
    val endPaddingPx = with(density) { contentPadding.calculateEndPadding(layoutDirection).roundToPx() }
    val gapPx = with(density) { verticalSpacing.toPx() }
    val peekStepPx = with(density) { peekStep.toPx() }
    val defaultCardHeightPx = with(density) { 62.dp.roundToPx() }
    val minExpandedHeightPx = with(density) { 78.dp.toPx() }
    val bucketSizePx = with(density) { 160.dp.toPx() }.coerceAtLeast(1f)
    val preMeasureBufferPx = with(density) { 360.dp.toPx() }

    // 僅以較粗粒度追蹤捲動區段，確保鍵盤升降與一般滑動期間不觸發逐幀子組合重測
    val scrollBucket by remember(state, bucketSizePx) {
        derivedStateOf { (state.scrollOffsetState.floatValue / bucketSizePx).toInt() }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .semantics {
                verticalScrollAxisRange = ScrollAxisRange(
                    value = { state.scrollOffsetState.floatValue },
                    maxValue = { state.maxScrollState.floatValue.coerceAtLeast(1f) }
                )
                scrollBy { _, y ->
                    scope.launch {
                        state.scrollableState.scroll(MutatePriority.Default) {
                            scrollBy(y)
                        }
                    }
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val h = state.viewportHeightState.floatValue
                    when (event.key) {
                        Key.PageDown -> {
                            if (state.canScrollForward && h > 0f) {
                                scope.launch { state.animateScrollDelta(h * 0.75f) }
                                true
                            } else false
                        }
                        Key.PageUp -> {
                            if (state.canScrollBackward && h > 0f) {
                                scope.launch { state.animateScrollDelta(-h * 0.75f) }
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (state.canScrollForward) {
                                scope.launch { state.animateScrollDelta(defaultCardHeightPx + gapPx) }
                                true
                            } else false
                        }
                        Key.DirectionUp -> {
                            if (state.canScrollBackward) {
                                scope.launch { state.animateScrollDelta(-(defaultCardHeightPx + gapPx)) }
                                true
                            } else false
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .scrollable(
                state = state.scrollableState,
                orientation = Orientation.Vertical,
                reverseDirection = true,
                flingBehavior = ScrollableDefaults.flingBehavior()
            )
    ) {
        SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
            // 【測量階段】：僅於清單資料改變、寬度改變或跨越 160.dp 捲動區段時執行子組合與測量
            val itemsList = currentItems
            val keySelector = currentKeySelector
            val reducedMotion = currentReducedMotion
            val bucket = scrollBucket

            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val viewportH = height.toFloat()
            state.viewportHeightState.floatValue = viewportH

            val count = itemsList.size
            if (count == 0 || width <= 0 || height <= 0) {
                state.maxScrollState.floatValue = 0f
                state.scrollOffsetState.floatValue = 0f
                state.placedItemsInfo = emptyList()
                state.currentKeys = emptyList()
                state.currentTotalLinearHeight = 0f
                return@SubcomposeLayout layout(width, height) {}
            }

            val keys = ArrayList<Any>(count)
            val keySet = HashSet<Any>(count)
            for (i in 0 until count) {
                val k = keySelector(itemsList[i])
                keys.add(k)
                keySet.add(k)
            }
            state.pruneRemovedKeys(keySet)

            val itemWidth = (width - startPaddingPx - endPaddingPx).coerceAtLeast(1)
            val itemConstraints = Constraints(
                minWidth = itemWidth,
                maxWidth = itemWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight
            )

            // 1) 估算各項目內容位置並測量目前區段可視與緩衝範圍的卡片
            val estTops = FloatArray(count)
            val estHeights = FloatArray(count)
            var cursorY = topPaddingPx
            for (i in 0 until count) {
                val h = (state.getCachedHeight(keys[i]) ?: defaultCardHeightPx).toFloat()
                estTops[i] = cursorY
                estHeights[i] = h
                cursorY += h + gapPx
            }

            val bucketStart = bucket * bucketSizePx
            val windowMinContentY = bucketStart - preMeasureBufferPx
            val windowMaxContentY = bucketStart + bucketSizePx + viewportH + preMeasureBufferPx
            val measuredPlaceables = HashMap<Any, Placeable>(24)

            for (i in 0 until count) {
                val k = keys[i]
                val reflowHint = state.peekReflowOffset(k)
                val itemTop = estTops[i] + reflowHint
                val itemBottom = itemTop + estHeights[i]
                if (itemBottom >= windowMinContentY && itemTop <= windowMaxContentY) {
                    val slotState = state.getSlotState(k)
                    val measurables = subcompose(k) {
                        itemContent(i, itemsList[i], slotState)
                    }
                    if (measurables.isNotEmpty()) {
                        val placeable = measurables[0].measure(itemConstraints)
                        measuredPlaceables[k] = placeable
                        state.updateCachedHeight(k, placeable.height)
                    }
                }
            }

            // 2) 以實測高度精算最終線性位置與同步補位彈簧狀態（不讀取動態 IME 或 Scroll 狀態）
            val finalTops = FloatArray(count)
            val finalHeights = FloatArray(count)
            cursorY = topPaddingPx
            val measuredSlotItems = ArrayList<MeasuredSlotItem>(measuredPlaceables.size)
            for (i in 0 until count) {
                val k = keys[i]
                val placeable = measuredPlaceables[k]
                val h = (placeable?.height ?: state.getCachedHeight(k) ?: defaultCardHeightPx).toFloat()
                finalTops[i] = cursorY
                finalHeights[i] = h
                if (placeable != null) {
                    measuredSlotItems.add(
                        MeasuredSlotItem(
                            index = i,
                            key = k,
                            naturalTop = cursorY,
                            height = h,
                            placeable = placeable
                        )
                    )
                }
                cursorY += h + gapPx
            }

            val totalLinearHeight = cursorY - gapPx + bottomPaddingPx
            state.syncReflowOnMeasure(
                keys = keys,
                naturalTops = finalTops,
                isReducedMotion = reducedMotion
            )

            state.currentItemTops = finalTops
            state.currentItemHeights = finalHeights
            state.currentKeys = keys
            state.currentTotalLinearHeight = totalLinearHeight
            state.currentTopMarginPx = topPaddingPx
            state.currentBottomMarginPx = bottomPaddingPx
            state.currentPeekStepPx = peekStepPx
            state.currentGapPx = gapPx

            val contentSpan = (totalLinearHeight - topPaddingPx - bottomPaddingPx + gapPx).coerceAtLeast(1f)
            val avgStride = (contentSpan / count).coerceAtLeast(1f)
            val effectiveStride = (avgStride - peekStepPx).coerceAtLeast(1f)

            layout(width, height) {
                // 【放置與圖層階段】：於此處讀取滾動偏移、鍵盤高度、進度卡收摺比例及編緝鎖定項，達成零重測 60fps
                @Suppress("UNUSED_VARIABLE")
                val frameTick = state.animFrameNanos.longValue

                val topInsetPx = topInsetPxProvider().coerceIn(0f, viewportH * 0.7f)
                val bottomInsetPx = bottomInsetPxProvider().coerceIn(0f, viewportH * 0.85f)
                val pinnedKey = pinnedExpandedKeyProvider()

                state.currentTopInsetPx = topInsetPx
                state.currentBottomInsetPx = bottomInsetPx

                val usableHeight = (viewportH - topInsetPx - bottomInsetPx).coerceAtLeast(1f)
                val maxScroll = (totalLinearHeight - usableHeight).coerceAtLeast(0f)
                state.maxScrollState.floatValue = maxScroll

                val clampedScroll = state.scrollOffsetState.floatValue.coerceIn(0f, maxScroll)
                if (clampedScroll != state.scrollOffsetState.floatValue) {
                    state.scrollOffsetState.floatValue = clampedScroll
                }

                val effectiveTopBound = topInsetPx + topPaddingPx
                val effectiveBottomBound = (viewportH - bottomInsetPx - bottomPaddingPx)
                    .coerceAtLeast(effectiveTopBound + 1f)
                val availableInnerH = (effectiveBottomBound - effectiveTopBound).coerceAtLeast(0f)

                val maxAllowedHeadroom = ((availableInnerH - minExpandedHeightPx) / 2f).coerceAtLeast(0f)
                val effectiveMaxStackPx = (peekStepPx * MAX_STACK_DEPTH).coerceAtMost(maxAllowedHeadroom)
                val activeMaxDepth = (effectiveMaxStackPx / peekStepPx.coerceAtLeast(1f)).coerceAtLeast(0f)
                state.currentActiveMaxDepth = activeMaxDepth

                val topHeadroomPx = if (maxScroll > 0.5f && activeMaxDepth > 0f) {
                    peekStepPx * (clampedScroll / effectiveStride).coerceIn(0f, activeMaxDepth)
                } else {
                    0f
                }
                val topPinY = effectiveTopBound + topHeadroomPx

                val remainingBottomScroll = (maxScroll - clampedScroll).coerceAtLeast(0f)
                val bottomHeadroomPx = if (maxScroll > 0.5f && activeMaxDepth > 0f) {
                    peekStepPx * (remainingBottomScroll / effectiveStride).coerceIn(0f, activeMaxDepth)
                } else {
                    0f
                }
                val bottomPinBottomY = effectiveBottomBound - bottomHeadroomPx

                val placements = ArrayList<Pair<Placeable, ComputedStackPlacement>>(measuredSlotItems.size)
                val newPlacedInfos = ArrayList<PlacedStackedItemInfo>(measuredSlotItems.size)

                for (slot in measuredSlotItems) {
                    val k = slot.key
                    val h = slot.height
                    val reflowOffsetPx = state.peekReflowOffset(k)
                    val rawTop = topInsetPx + (slot.naturalTop + reflowOffsetPx) - clampedScroll
                    val rawBottom = rawTop + h
                    val isPinnedExpanded = pinnedKey != null && k == pinnedKey

                    if (isPinnedExpanded) {
                        val minY = effectiveTopBound
                        val maxY = (effectiveBottomBound - h).coerceAtLeast(minY)
                        val clampedY = rawTop.coerceIn(minY, maxY)
                        state.getSlotState(k).depthState.floatValue = 0f

                        placements.add(
                            slot.placeable to ComputedStackPlacement(
                                placedTopY = clampedY,
                                scale = 1f,
                                alpha = 1f,
                                zIndex = 20f,
                                depth = 0f,
                                transformOrigin = TransformOrigin(0.5f, 0.5f),
                                isTopStack = false,
                                isBottomStack = false
                            )
                        )
                        newPlacedInfos.add(
                            PlacedStackedItemInfo(
                                index = slot.index,
                                key = k,
                                placedTopY = clampedY,
                                heightPx = h,
                                scale = 1f,
                                depth = 0f,
                                isTopStack = false,
                                isBottomStack = false
                            )
                        )
                        continue
                    }

                    val stride = (h + gapPx).coerceAtLeast(1f)
                    val rawTopDepth = if (maxScroll > 0.5f && activeMaxDepth > 0f && rawTop < topPinY) {
                        (topPinY - rawTop) / stride
                    } else {
                        0f
                    }
                    val rawBottomDepth = if (maxScroll > 0.5f && activeMaxDepth > 0f && rawBottom > bottomPinBottomY) {
                        (rawBottom - bottomPinBottomY) / stride
                    } else {
                        0f
                    }

                    val isTopStack = rawTopDepth > 0f && rawTopDepth >= rawBottomDepth
                    val isBottomStack = !isTopStack && rawBottomDepth > 0f
                    val depth = if (isTopStack) rawTopDepth else if (isBottomStack) rawBottomDepth else 0f

                    state.getSlotState(k).depthState.floatValue = depth

                    if (depth > FADE_OUT_DEPTH + 0.1f) {
                        continue
                    }

                    val clampedScaleDepth = depth.coerceIn(0f, FADE_OUT_DEPTH)
                    val scale = if (reducedMotion) {
                        (1f - (SCALE_STEP_PER_LAYER * 0.5f) * clampedScaleDepth.coerceAtMost(MAX_STACK_DEPTH))
                    } else {
                        (1f - SCALE_STEP_PER_LAYER * clampedScaleDepth).coerceAtLeast(0.75f)
                    }

                    val alpha = if (depth <= MAX_STACK_DEPTH) {
                        1f
                    } else {
                        (1f - (depth - MAX_STACK_DEPTH)).coerceIn(0f, 1f)
                    }

                    val placedTopY: Float
                    val transformOrigin: TransformOrigin

                    if (isTopStack) {
                        val stackVisualDepth = depth.coerceAtMost(activeMaxDepth)
                        val extraFade = (depth - activeMaxDepth).coerceAtLeast(0f)
                        val tuckInPx = extraFade * (peekStepPx * 0.5f)
                        placedTopY = topPinY - peekStepPx * stackVisualDepth + tuckInPx
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    } else if (isBottomStack) {
                        val stackVisualDepth = depth.coerceAtMost(activeMaxDepth)
                        val extraFade = (depth - activeMaxDepth).coerceAtLeast(0f)
                        val tuckInPx = extraFade * (peekStepPx * 0.5f)
                        val placedBottomY = bottomPinBottomY + peekStepPx * stackVisualDepth - tuckInPx
                        placedTopY = placedBottomY - h
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    } else {
                        placedTopY = rawTop
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }

                    val zIndex = if (depth <= 0f) {
                        10f
                    } else {
                        (10f - depth).coerceAtLeast(0f)
                    }

                    placements.add(
                        slot.placeable to ComputedStackPlacement(
                            placedTopY = placedTopY,
                            scale = scale,
                            alpha = alpha,
                            zIndex = zIndex,
                            depth = depth,
                            transformOrigin = transformOrigin,
                            isTopStack = isTopStack,
                            isBottomStack = isBottomStack
                        )
                    )
                    newPlacedInfos.add(
                        PlacedStackedItemInfo(
                            index = slot.index,
                            key = k,
                            placedTopY = placedTopY,
                            heightPx = h,
                            scale = scale,
                            depth = depth,
                            isTopStack = isTopStack,
                            isBottomStack = isBottomStack
                        )
                    )
                }

                state.placedItemsInfo = newPlacedInfos

                for (entry in placements) {
                    val placeable = entry.first
                    val p = entry.second
                    placeable.placeRelativeWithLayer(
                        x = startPaddingPx,
                        y = p.placedTopY.roundToInt(),
                        zIndex = p.zIndex
                    ) {
                        scaleX = p.scale
                        scaleY = p.scale
                        alpha = p.alpha
                        this.transformOrigin = p.transformOrigin
                    }
                }
            }
        }
    }
}
