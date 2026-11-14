package com.example.totalcheck.ui.main

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_STACK_DEPTH = 3f
private const val FADE_OUT_DEPTH = 4f
private const val SCALE_STEP_PER_LAYER = 0.04f
const val COMPRESSED_STACK_INTERACTION_THRESHOLD = 0.35f

private const val OVERSCROLL_INITIAL_SLOPE = 0.55f
private const val OVERSCROLL_BASE_TRANSLATION_RATIO = 0.76f
private const val OVERSCROLL_ACCORDION_STRETCH_RATIO = 0.24f
private const val OVERSCROLL_ACCORDION_SPAN_ITEMS = 5f
private const val OVERSCROLL_FLING_IMPACT_FACTOR = 0.55f

internal fun computeVisualOverscrollPx(rawPx: Float, maxOverscrollPx: Float): Float {
    if (abs(rawPx) < 0.01f || maxOverscrollPx <= 0f) return 0f
    val sign = if (rawPx >= 0f) 1f else -1f
    val absRaw = abs(rawPx)
    val scaled = OVERSCROLL_INITIAL_SLOPE * absRaw
    val damped = (maxOverscrollPx * scaled) / (maxOverscrollPx + scaled)
    return sign * damped
}

internal fun computeRawFromVisualOverscrollPx(visualPx: Float, maxOverscrollPx: Float): Float {
    if (abs(visualPx) < 0.01f || maxOverscrollPx <= 0f) return 0f
    val sign = if (visualPx >= 0f) 1f else -1f
    val maxAllowedVisual = maxOverscrollPx * 0.96f
    val absVisual = abs(visualPx).coerceAtMost(maxAllowedVisual)
    val denom = (OVERSCROLL_INITIAL_SLOPE * (maxOverscrollPx - absVisual)).coerceAtLeast(0.001f)
    val absRaw = (maxOverscrollPx * absVisual) / denom
    return sign * absRaw
}

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

    // 供手勢與回呼於非組裝階段惰性讀取，避免捲動時觸發 Composition 重組
    val isCompressed: Boolean
        get() = depthState.floatValue > COMPRESSED_STACK_INTERACTION_THRESHOLD
}

private const val RIPPLE_LAYER_DELAY_NANOS = 35_000_000L
private const val RIPPLE_TOGGLE_DURATION_NANOS = 320_000_000L
private const val RIPPLE_MAX_EFFECTIVE_DISTANCE = 9
private const val RIPPLE_ATTENUATION_RATE = 0.48f
private const val RIPPLE_MIN_ATTENUATION = 0.02f
private const val RIPPLE_MAX_ACTIVE_WAVES = 3

internal class NeighborRippleWave(
    val sourceKey: Any,
    val anchorIndex: Int,
    val intensity: Float,
    val durationNanos: Long,
    val maxEffectiveDistance: Int,
    var startNanos: Long = 0L
)

private class ItemReflowAnim(
    var currentOffsetPx: Float = 0f,
    var lastNaturalTopPx: Float? = null,
    var activeAnim: TargetBasedAnimation<Float, AnimationVector1D>? = null,
    var startNanos: Long = 0L
)

@Stable
class StackedCardListState {
    internal val scrollOffsetState: MutableFloatState = mutableFloatStateOf(0f)
    internal val maxScrollState: MutableFloatState = mutableFloatStateOf(0f)
    internal val viewportHeightState: MutableFloatState = mutableFloatStateOf(0f)
    internal val animFrameNanos: MutableLongState = mutableLongStateOf(0L)
    internal val rawOverscrollState: MutableFloatState = mutableFloatStateOf(0f)
    internal val visualOverscrollState: MutableFloatState = mutableFloatStateOf(0f)
    internal var maxOverscrollPx: Float = 200f
    internal var isReducedMotionEnabled: Boolean = false
    internal var isFlinging: Boolean = false
    internal var isProgrammaticScroll: Boolean = false

    internal val reflowSignal = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val reflowAnims = HashMap<Any, ItemReflowAnim>()
    private val slotStates = HashMap<Any, StackedCardSlotState>()
    internal val activeRippleWaves = ArrayList<NeighborRippleWave>(4)
    internal var currentRippleFrameNanos: Long = 0L

    internal var placeablesBuffer: Array<Placeable?> = arrayOfNulls(32)
    internal var placedTopYsBuffer: FloatArray = FloatArray(32)
    internal var scalesBuffer: FloatArray = FloatArray(32)
    internal var alphasBuffer: FloatArray = FloatArray(32)
    internal var zIndicesBuffer: FloatArray = FloatArray(32)
    internal var originYsBuffer: FloatArray = FloatArray(32)
    internal var depthsBuffer: FloatArray = FloatArray(32)
    internal var visibleFlagsBuffer: BooleanArray = BooleanArray(32)

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

    internal fun ensureBufferCapacity(size: Int) {
        if (placeablesBuffer.size < size) {
            val nextSize = maxOf(size, placeablesBuffer.size * 2)
            placeablesBuffer = arrayOfNulls(nextSize)
            placedTopYsBuffer = FloatArray(nextSize)
            scalesBuffer = FloatArray(nextSize)
            alphasBuffer = FloatArray(nextSize)
            zIndicesBuffer = FloatArray(nextSize)
            originYsBuffer = FloatArray(nextSize)
            depthsBuffer = FloatArray(nextSize)
            visibleFlagsBuffer = BooleanArray(nextSize)
        }
    }

    val scrollOffsetPx: Float
        get() = scrollOffsetState.floatValue

    val maxScrollPx: Float
        get() = maxScrollState.floatValue

    val canScrollForward: Boolean
        get() = scrollOffsetState.floatValue < maxScrollState.floatValue - 0.5f

    val canScrollBackward: Boolean
        get() = scrollOffsetState.floatValue > 0.5f

    val scrollableState: ScrollableState = ScrollableState { delta ->
        consumeScrollDelta(delta)
    }

    internal fun consumeScrollDelta(delta: Float): Float {
        if (delta == 0f) return 0f
        var remainingDelta = delta
        val currentVisual = visualOverscrollState.floatValue
        val maxVisual = maxOverscrollPx.coerceAtLeast(1f)

        // 1. 若目前處於邊界過界拉伸狀態，且手勢往反方向推回，優先以初段斜率平滑收回過界量
        if (abs(currentVisual) > 0.001f) {
            val isUnwindingTop = currentVisual > 0f && remainingDelta > 0f
            val isUnwindingBottom = currentVisual < 0f && remainingDelta < 0f
            if (isUnwindingTop || isUnwindingBottom) {
                val maxRawUnwindNeeded = abs(currentVisual) / OVERSCROLL_INITIAL_SLOPE
                val rawUnwindUsed = minOf(abs(remainingDelta), maxRawUnwindNeeded)
                val newVisual = if (currentVisual > 0f) {
                    (currentVisual - rawUnwindUsed * OVERSCROLL_INITIAL_SLOPE).coerceAtLeast(0f)
                } else {
                    (currentVisual + rawUnwindUsed * OVERSCROLL_INITIAL_SLOPE).coerceAtMost(0f)
                }
                if (abs(newVisual) < 0.05f) {
                    visualOverscrollState.floatValue = 0f
                    rawOverscrollState.floatValue = 0f
                } else {
                    visualOverscrollState.floatValue = newVisual
                    rawOverscrollState.floatValue = computeRawFromVisualOverscrollPx(newVisual, maxVisual)
                }
                remainingDelta = if (remainingDelta > 0f) {
                    remainingDelta - rawUnwindUsed
                } else {
                    remainingDelta + rawUnwindUsed
                }
            }
        }

        // 2. 在合法捲動區間 [0, maxScroll] 內正常消耗捲動位移
        val currentScroll = scrollOffsetState.floatValue
        val maxScroll = maxScrollState.floatValue
        val targetScroll = (currentScroll + remainingDelta).coerceIn(0f, maxScroll)
        val scrollConsumed = targetScroll - currentScroll
        if (scrollConsumed != 0f) {
            scrollOffsetState.floatValue = targetScroll
        }
        val leftoverDelta = remainingDelta - scrollConsumed

        // 3. 抵達邊界後若仍有剩餘手勢位移：
        //    - 手指直接拖曳時：進入漸近阻尼拉伸 (初段輕盈、越往外拉阻力越大)
        //    - 慣性滑動時：讓 FlingBehavior 捕捉剩餘動量並轉化為臨界阻尼緩衝回彈
        if (
            abs(leftoverDelta) > 0.001f &&
            !isReducedMotionEnabled &&
            !isFlinging &&
            !isProgrammaticScroll
        ) {
            val maxRaw = maxVisual * 14f
            val nextRaw = (rawOverscrollState.floatValue - leftoverDelta).coerceIn(-maxRaw, maxRaw)
            rawOverscrollState.floatValue = nextRaw
            visualOverscrollState.floatValue = computeVisualOverscrollPx(nextRaw, maxVisual)
            return delta
        }

        return delta - leftoverDelta
    }

    internal fun currentOverscrollDerivative(): Float {
        val maxVisual = maxOverscrollPx.coerceAtLeast(1f)
        val absRaw = abs(rawOverscrollState.floatValue)
        val denom = maxVisual + OVERSCROLL_INITIAL_SLOPE * absRaw
        if (denom <= 0.001f) return OVERSCROLL_INITIAL_SLOPE
        val ratio = maxVisual / denom
        return OVERSCROLL_INITIAL_SLOPE * ratio * ratio
    }

    internal fun clearOverscrollImmediate() {
        if (rawOverscrollState.floatValue != 0f) rawOverscrollState.floatValue = 0f
        if (visualOverscrollState.floatValue != 0f) visualOverscrollState.floatValue = 0f
    }

    internal suspend fun runOverscrollSpringToZero(initialVelocityPxPerSec: Float) {
        val startVisual = visualOverscrollState.floatValue
        if (abs(startVisual) < 0.5f && abs(initialVelocityPxPerSec) < 10f) {
            clearOverscrollImmediate()
            return
        }
        if (isReducedMotionEnabled) {
            clearOverscrollImmediate()
            return
        }
        val maxVisual = maxOverscrollPx.coerceAtLeast(1f)
        val isFromBoundaryImpact = abs(startVisual) < 0.5f
        animate(
            initialValue = startVisual,
            targetValue = 0f,
            initialVelocity = initialVelocityPxPerSec,
            animationSpec = spring(
                dampingRatio = 1.0f,
                stiffness = 420f,
                visibilityThreshold = 0.25f
            )
        ) { value, _ ->
            val clampedVisual = if (isFromBoundaryImpact) {
                computeVisualOverscrollPx(value / OVERSCROLL_INITIAL_SLOPE, maxVisual)
            } else {
                value.coerceIn(-maxVisual, maxVisual)
            }
            visualOverscrollState.floatValue = clampedVisual
            rawOverscrollState.floatValue = computeRawFromVisualOverscrollPx(clampedVisual, maxVisual)
        }
        clearOverscrollImmediate()
    }

    internal suspend fun settleOverscrollIfIdle() {
        if (abs(visualOverscrollState.floatValue) <= 0.5f) {
            clearOverscrollImmediate()
            return
        }
        scrollableState.scroll(MutatePriority.Default) {
            runOverscrollSpringToZero(initialVelocityPxPerSec = 0f)
        }
    }

    fun dispatchRawDelta(delta: Float): Float {
        clearOverscrollImmediate()
        val current = scrollOffsetState.floatValue
        val max = maxScrollState.floatValue
        val target = (current + delta).coerceIn(0f, max)
        val consumed = target - current
        if (consumed != 0f) {
            scrollOffsetState.floatValue = target
        }
        return consumed
    }

    suspend fun animateScrollDelta(
        targetDelta: Float,
        animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 340f)
    ) {
        if (abs(targetDelta) < 0.5f) return
        scrollableState.scroll(MutatePriority.Default) {
            isProgrammaticScroll = true
            try {
                clearOverscrollImmediate()
                var prev = 0f
                animate(
                    initialValue = 0f,
                    targetValue = targetDelta,
                    animationSpec = animationSpec
                ) { value, _ ->
                    prev += scrollBy(value - prev)
                }
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    suspend fun scrollToTop(animated: Boolean = true) {
        clearOverscrollImmediate()
        if (scrollOffsetState.floatValue <= 0.5f) return
        if (animated) {
            animateScrollDelta(targetDelta = -scrollOffsetState.floatValue)
        } else {
            scrollableState.scroll(MutatePriority.Default) {
                isProgrammaticScroll = true
                try {
                    scrollBy(-scrollOffsetState.floatValue)
                } finally {
                    isProgrammaticScroll = false
                }
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

    fun triggerNeighborRipple(
        sourceKey: Any,
        intensity: Float = 1f,
        isReducedMotion: Boolean = false
    ) {
        if (isReducedMotion) return
        val anchorIndex = currentKeys.indexOf(sourceKey)
        if (anchorIndex < 0) return
        val maxSpan = maxOf(anchorIndex, (currentKeys.size - 1 - anchorIndex)).coerceAtLeast(0)
        val maxEffectiveDistance = minOf(maxSpan, RIPPLE_MAX_EFFECTIVE_DISTANCE)
        if (activeRippleWaves.size >= RIPPLE_MAX_ACTIVE_WAVES) {
            activeRippleWaves.removeAt(0)
        }
        activeRippleWaves.add(
            NeighborRippleWave(
                sourceKey = sourceKey,
                anchorIndex = anchorIndex,
                intensity = intensity.coerceIn(0.1f, 1.5f),
                durationNanos = RIPPLE_TOGGLE_DURATION_NANOS,
                maxEffectiveDistance = maxEffectiveDistance
            )
        )
        reflowSignal.tryEmit(Unit)
    }

    internal fun getSlotState(key: Any): StackedCardSlotState {
        return slotStates.getOrPut(key) { StackedCardSlotState() }
    }

    internal fun peekReflowOffset(key: Any): Float = reflowAnims[key]?.currentOffsetPx ?: 0f

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
    }

    internal fun hasActiveAnimations(): Boolean {
        if (activeRippleWaves.isNotEmpty()) return true
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
        if (activeRippleWaves.isNotEmpty()) {
            currentRippleFrameNanos = frameNanos
            var idx = activeRippleWaves.lastIndex
            while (idx >= 0) {
                val wave = activeRippleWaves[idx]
                if (wave.startNanos == 0L) {
                    wave.startNanos = frameNanos
                }
                val elapsedNanos = (frameNanos - wave.startNanos).coerceAtLeast(0L)
                val maxLifeNanos = wave.maxEffectiveDistance * RIPPLE_LAYER_DELAY_NANOS + wave.durationNanos
                if (elapsedNanos >= maxLifeNanos) {
                    activeRippleWaves.removeAt(idx)
                }
                idx--
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
    val maxRipplePushPx = with(density) { 6.dp.toPx() }
    val maxOverscrollPx = with(density) { 72.dp.toPx() }
    state.maxOverscrollPx = maxOverscrollPx
    state.isReducedMotionEnabled = isReducedMotion

    LaunchedEffect(state, isReducedMotion) {
        if (isReducedMotion) {
            state.clearOverscrollImmediate()
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.scrollableState.isScrollInProgress }
            .collect { inProgress ->
                if (!inProgress && abs(state.visualOverscrollState.floatValue) > 0.5f) {
                    state.settleOverscrollIfIdle()
                }
            }
    }

    val baseFlingBehavior = ScrollableDefaults.flingBehavior()
    val overscrollFlingBehavior = remember(state, baseFlingBehavior) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (state.isReducedMotionEnabled) {
                    state.clearOverscrollImmediate()
                    return with(baseFlingBehavior) { performFling(initialVelocity) }
                }

                val startVisual = state.visualOverscrollState.floatValue
                if (abs(startVisual) > 0.5f) {
                    val isFlingingBackIntoList =
                        (startVisual > 0f && initialVelocity > 0f) ||
                            (startVisual < 0f && initialVelocity < 0f)
                    if (!isFlingingBackIntoList) {
                        val slope = state.currentOverscrollDerivative()
                        val visualVel = (-initialVelocity * slope).coerceIn(-1400f, 1400f)
                        state.runOverscrollSpringToZero(initialVelocityPxPerSec = visualVel)
                        return 0f
                    }
                }

                state.isFlinging = true
                val remainingVelocity = try {
                    with(baseFlingBehavior) { performFling(initialVelocity) }
                } finally {
                    state.isFlinging = false
                }

                val postFlingVisual = state.visualOverscrollState.floatValue
                if (abs(postFlingVisual) > 0.5f) {
                    state.runOverscrollSpringToZero(initialVelocityPxPerSec = 0f)
                    return 0f
                }

                val atTopBoundary =
                    state.scrollOffsetState.floatValue <= 0.5f && remainingVelocity < -80f
                val atBottomBoundary =
                    state.scrollOffsetState.floatValue >= state.maxScrollState.floatValue - 0.5f &&
                        remainingVelocity > 80f
                if (atTopBoundary || atBottomBoundary) {
                    val maxImpactVel = state.maxOverscrollPx * 22f
                    val impactVisualVel = (-remainingVelocity * OVERSCROLL_FLING_IMPACT_FACTOR)
                        .coerceIn(-maxImpactVel, maxImpactVel)
                    state.runOverscrollSpringToZero(initialVelocityPxPerSec = impactVisualVel)
                    return 0f
                }

                return remainingVelocity
            }
        }
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
                flingBehavior = overscrollFlingBehavior
            )
    ) {
        // 直接預組裝 Layout：徹底移除 SubcomposeLayout 在滑動與拖曳期間的子組合開銷
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                items.forEachIndexed { index, item ->
                    val itemKey = key(item)
                    key(itemKey) {
                        val slotState = remember(itemKey) { state.getSlotState(itemKey) }
                        itemContent(index, item, slotState)
                    }
                }
            }
        ) { measurables, constraints ->
            val itemsList = currentItems
            val keySelector = currentKeySelector
            val reducedMotion = currentReducedMotion

            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val viewportH = height.toFloat()
            state.viewportHeightState.floatValue = viewportH

            val count = minOf(measurables.size, itemsList.size)
            if (count == 0 || width <= 0 || height <= 0) {
                state.maxScrollState.floatValue = 0f
                state.scrollOffsetState.floatValue = 0f
                state.clearOverscrollImmediate()
                state.placedItemsInfo = emptyList()
                state.currentKeys = emptyList()
                state.currentTotalLinearHeight = 0f
                return@Layout layout(width, height) {}
            }

            state.ensureBufferCapacity(count)
            val placeables = state.placeablesBuffer
            val keys = ArrayList<Any>(count)
            val keySet = HashSet<Any>(count * 2)

            val itemWidth = (width - startPaddingPx - endPaddingPx).coerceAtLeast(1)
            val itemConstraints = Constraints(
                minWidth = itemWidth,
                maxWidth = itemWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight
            )

            val finalTops = FloatArray(count)
            val finalHeights = FloatArray(count)
            var cursorY = topPaddingPx

            for (i in 0 until count) {
                val k = keySelector(itemsList[i])
                keys.add(k)
                keySet.add(k)
                val p = measurables[i].measure(itemConstraints)
                placeables[i] = p
                val h = p.height.toFloat()
                finalTops[i] = cursorY
                finalHeights[i] = h
                cursorY += h + gapPx
            }
            state.pruneRemovedKeys(keySet)

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
                // 【Placement 與圖層階段】：零配置陣列計算堆疊座標，捲動與鍵盤升降期間 0 重組、0 重測
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
                val visualOverscrollPx = if (reducedMotion || pinnedKey != null) {
                    0f
                } else {
                    state.visualOverscrollState.floatValue
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

                val placedTopYs = state.placedTopYsBuffer
                val scales = state.scalesBuffer
                val alphas = state.alphasBuffer
                val zIndices = state.zIndicesBuffer
                val originYs = state.originYsBuffer
                val depths = state.depthsBuffer
                val visibleFlags = state.visibleFlagsBuffer
                val newPlacedInfos = ArrayList<PlacedStackedItemInfo>(count)

                for (i in 0 until count) {
                    val k = keys[i]
                    val h = finalHeights[i]
                    val reflowOffsetPx = state.peekReflowOffset(k)
                    val rawTop = topInsetPx + (finalTops[i] + reflowOffsetPx) - clampedScroll
                    val rawBottom = rawTop + h
                    val isPinnedExpanded = pinnedKey != null && k == pinnedKey

                    if (isPinnedExpanded) {
                        val minY = effectiveTopBound
                        val maxY = (effectiveBottomBound - h).coerceAtLeast(minY)
                        val clampedY = rawTop.coerceIn(minY, maxY)
                        state.getSlotState(k).depthState.floatValue = 0f

                        placedTopYs[i] = clampedY
                        scales[i] = 1f
                        alphas[i] = 1f
                        zIndices[i] = 20f
                        originYs[i] = 0.5f
                        depths[i] = 0f
                        visibleFlags[i] = true

                        newPlacedInfos.add(
                            PlacedStackedItemInfo(
                                index = i,
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
                        visibleFlags[i] = false
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

                    val basePlacedTopY: Float
                    val originY: Float

                    if (isTopStack) {
                        val stackVisualDepth = depth.coerceAtMost(activeMaxDepth)
                        val extraFade = (depth - activeMaxDepth).coerceAtLeast(0f)
                        val tuckInPx = extraFade * (peekStepPx * 0.5f)
                        basePlacedTopY = topPinY - peekStepPx * stackVisualDepth + tuckInPx
                        originY = 0f
                    } else if (isBottomStack) {
                        val stackVisualDepth = depth.coerceAtMost(activeMaxDepth)
                        val extraFade = (depth - activeMaxDepth).coerceAtLeast(0f)
                        val tuckInPx = extraFade * (peekStepPx * 0.5f)
                        val placedBottomY = bottomPinBottomY + peekStepPx * stackVisualDepth - tuckInPx
                        basePlacedTopY = placedBottomY - h
                        originY = 1f
                    } else {
                        basePlacedTopY = rawTop
                        originY = 0.5f
                    }

                    val itemOverscrollOffsetPx = if (abs(visualOverscrollPx) <= 0.05f) {
                        0f
                    } else if (visualOverscrollPx > 0f) {
                        val t = (i.toFloat() / OVERSCROLL_ACCORDION_SPAN_ITEMS).coerceIn(0f, 1f)
                        val oneMinusT = 1f - t
                        val easeOut = 1f - oneMinusT * oneMinusT
                        visualOverscrollPx * (
                            OVERSCROLL_BASE_TRANSLATION_RATIO +
                                OVERSCROLL_ACCORDION_STRETCH_RATIO * easeOut
                            )
                    } else {
                        val distFromBottom = (count - 1 - i).coerceAtLeast(0)
                        val t = (distFromBottom.toFloat() / OVERSCROLL_ACCORDION_SPAN_ITEMS).coerceIn(0f, 1f)
                        val oneMinusT = 1f - t
                        val easeOut = 1f - oneMinusT * oneMinusT
                        visualOverscrollPx * (
                            OVERSCROLL_BASE_TRANSLATION_RATIO +
                                OVERSCROLL_ACCORDION_STRETCH_RATIO * easeOut
                            )
                    }

                    val placedTopY = basePlacedTopY + itemOverscrollOffsetPx

                    val zIndex = if (depth <= 0f) {
                        10f
                    } else {
                        (10f - depth).coerceAtLeast(0f)
                    }

                    placedTopYs[i] = placedTopY
                    scales[i] = scale
                    alphas[i] = alpha
                    zIndices[i] = zIndex
                    originYs[i] = originY
                    depths[i] = depth
                    visibleFlags[i] = true

                    newPlacedInfos.add(
                        PlacedStackedItemInfo(
                            index = i,
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

                val rippleWaves = state.activeRippleWaves
                val hasActiveRipple = !reducedMotion && rippleWaves.isNotEmpty()
                val rippleFrameNanos = state.currentRippleFrameNanos
                val piF = PI.toFloat()

                for (i in 0 until count) {
                    if (!visibleFlags[i]) continue
                    val placeable = placeables[i] ?: continue
                    var rippleOffsetY = 0f
                    var rippleScaleAdd = 0f
                    if (hasActiveRipple) {
                        val itemDepth = depths[i]
                        val stackVisibility = (1f - (itemDepth / 2.5f)).coerceIn(0f, 1f)
                        if (stackVisibility > 0.01f) {
                            for (w in 0 until rippleWaves.size) {
                                val wave = rippleWaves[w]
                                val waveStart = wave.startNanos
                                if (waveStart == 0L) continue
                                val elapsedNanos = (rippleFrameNanos - waveStart).coerceAtLeast(0L)
                                val currentSourceIdx = keys.indexOf(wave.sourceKey)
                                val distance: Int
                                val direction: Float
                                if (currentSourceIdx >= 0) {
                                    val diff = i - currentSourceIdx
                                    if (diff == 0) {
                                        distance = 0
                                        direction = 0f
                                    } else {
                                        distance = abs(diff)
                                        direction = if (diff < 0) -1f else 1f
                                    }
                                } else {
                                    val virtualSourceIdx = (wave.anchorIndex - 0.5f)
                                        .coerceIn(-0.5f, (count - 0.5f))
                                    val diff = i.toFloat() - virtualSourceIdx
                                    distance = ceil(abs(diff)).roundToInt().coerceAtLeast(1)
                                    direction = if (diff < 0f) -1f else 1f
                                }
                                if (distance > RIPPLE_MAX_EFFECTIVE_DISTANCE) continue
                                val localElapsedNanos = elapsedNanos - distance * RIPPLE_LAYER_DELAY_NANOS
                                if (localElapsedNanos in 1L until wave.durationNanos) {
                                    val u = (localElapsedNanos.toFloat() / wave.durationNanos.toFloat())
                                        .coerceIn(0f, 1f)
                                    val scaleEnv = (sin(u * piF) * (1f - u) * 1.74f).coerceIn(0f, 1f)
                                    if (distance == 0) {
                                        rippleScaleAdd += 0.012f * wave.intensity * stackVisibility * scaleEnv
                                    } else {
                                        val attenuation = exp(-RIPPLE_ATTENUATION_RATE * (distance - 1))
                                        if (attenuation >= RIPPLE_MIN_ATTENUATION) {
                                            val oneMinusU = 1f - u
                                            val pushWave = sin(u * piF * 1.45f) * oneMinusU * oneMinusU * 2.01f
                                            val weight = wave.intensity * attenuation * stackVisibility
                                            rippleOffsetY += direction * maxRipplePushPx * weight * pushWave
                                            rippleScaleAdd += 0.014f * weight * scaleEnv
                                        }
                                    }
                                }
                            }
                            rippleOffsetY = rippleOffsetY.coerceIn(
                                -maxRipplePushPx * 1.35f,
                                maxRipplePushPx * 1.35f
                            )
                            rippleScaleAdd = rippleScaleAdd.coerceIn(0f, 0.028f)
                        }
                    }

                    val topY = (placedTopYs[i] + rippleOffsetY).roundToInt()
                    val sc = scales[i] * (1f + rippleScaleAdd)
                    val al = alphas[i]
                    val z = zIndices[i]
                    val oy = originYs[i]
                    placeable.placeRelativeWithLayer(
                        x = startPaddingPx,
                        y = topY,
                        zIndex = z
                    ) {
                        scaleX = sc
                        scaleY = sc
                        alpha = al
                        transformOrigin = TransformOrigin(0.5f, oy)
                    }
                }
            }
        }
    }
}
