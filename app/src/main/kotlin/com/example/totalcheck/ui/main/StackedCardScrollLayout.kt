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
import androidx.compose.ui.util.lerp
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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

/**
 * 連續時間二階欠阻尼彈簧解析解步進器（Zero-Allocation / Frame-0 零延遲響應）。
 * 當目標座標於每幀觸控事件持續更新時完整保留瞬時速度，避免 Animatable.animateTo 被反覆取消重啟而凍結卡頓。
 */
internal class MagneticSpringTracker(
    private val dampingRatio: Float = 0.85f,
    private val stiffness: Float = 520f,
    private val visibilityThreshold: Float = 0.5f,
    private val velocityThreshold: Float = 4f,
) {
    val currentValueState: MutableFloatState = mutableFloatStateOf(0f)
    var targetValue: Float = 0f
        private set
    var velocity: Float = 0f
        private set
    private var lastFrameNanos: Long = 0L

    private val omega0: Float = sqrt(stiffness.coerceAtLeast(1f))
    private val clampedZeta: Float = dampingRatio.coerceIn(0.05f, 0.999f)
    private val alpha: Float = clampedZeta * omega0
    private val omegaD: Float = omega0 * sqrt((1f - clampedZeta * clampedZeta).coerceAtLeast(1e-4f))

    fun snapTo(value: Float) {
        currentValueState.floatValue = value
        targetValue = value
        velocity = 0f
        lastFrameNanos = 0L
    }

    fun setTarget(value: Float) {
        targetValue = value
    }

    fun resetFrameClock() {
        lastFrameNanos = 0L
    }

    /**
     * 推進彈簧至 [frameNanos]，回傳是否已收斂於 [targetValue]。
     */
    fun stepFrame(frameNanos: Long): Boolean {
        val prevNanos = lastFrameNanos
        lastFrameNanos = frameNanos
        if (prevNanos == 0L) {
            return isSettled()
        }
        val dtSec = ((frameNanos - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        if (dtSec <= 0f) {
            return isSettled()
        }

        val target = targetValue
        val x0 = currentValueState.floatValue - target
        val v0 = velocity
        if (abs(x0) <= visibilityThreshold && abs(v0) <= velocityThreshold) {
            currentValueState.floatValue = target
            velocity = 0f
            return true
        }

        val decay = exp(-alpha * dtSec)
        val angle = omegaD * dtSec
        val c = cos(angle)
        val s = sin(angle)
        val aCoeff = x0
        val bCoeff = (v0 + alpha * x0) / omegaD

        val nextOffset = decay * (aCoeff * c + bCoeff * s)
        val nextVelocity = decay * ((bCoeff * omegaD - alpha * aCoeff) * c - (aCoeff * omegaD + alpha * bCoeff) * s)
        val nextValue = target + nextOffset

        if (abs(nextOffset) <= visibilityThreshold && abs(nextVelocity) <= velocityThreshold) {
            currentValueState.floatValue = target
            velocity = 0f
            return true
        }

        currentValueState.floatValue = nextValue
        velocity = nextVelocity
        return false
    }

    fun isSettled(): Boolean {
        return abs(currentValueState.floatValue - targetValue) <= visibilityThreshold &&
            abs(velocity) <= velocityThreshold
    }
}

/**
 * 1D 垂直磁吸狀態機（Hysteresis 雙閾值防抖 + 冪次曲線權重 + 非線性吸附混合，零配置計算）。
 */
internal class MagneticAttractorState(
    private val defaultEnterRadiusPx: Float,
    private val commitThreshold: Float = 0.35f,
) {
    var activeTargetSlotIndex: Int = -1
        private set
    var lastWeight: Float = 0f
        private set
    val weightState: MutableFloatState = mutableFloatStateOf(0f)

    fun reset(initialSlotIndex: Int = -1) {
        activeTargetSlotIndex = initialSlotIndex
        val w = if (initialSlotIndex >= 0) 1f else 0f
        lastWeight = w
        weightState.floatValue = w
    }

    fun resolveBlendedTopY(
        rawTopY: Float,
        draggedHeightPx: Float,
        slotCount: Int,
        slotTopsY: FloatArray,
        slotCentersY: FloatArray,
        avgSlotPitchPx: Float,
    ): Float {
        if (slotCount <= 0) {
            activeTargetSlotIndex = -1
            lastWeight = 0f
            weightState.floatValue = 0f
            return rawTopY
        }
        val rawCenterY = rawTopY + draggedHeightPx * 0.5f
        val enterRadius = maxOf(defaultEnterRadiusPx, avgSlotPitchPx * 1.15f)
        val exitRadius = enterRadius * 1.25f

        val currentIdx = activeTargetSlotIndex
        val currentDist = if (currentIdx in 0 until slotCount) {
            abs(rawCenterY - slotCentersY[currentIdx])
        } else {
            Float.MAX_VALUE
        }

        var bestIdx = if (currentIdx in 0 until slotCount && currentDist <= exitRadius) {
            currentIdx
        } else {
            -1
        }
        var bestDist = if (bestIdx >= 0) currentDist else Float.MAX_VALUE

        for (i in 0 until slotCount) {
            val d = abs(rawCenterY - slotCentersY[i])
            val threshold = if (bestIdx < 0) enterRadius else bestDist * 0.85f
            if (d <= threshold && d < bestDist) {
                bestIdx = i
                bestDist = d
            }
        }

        if (bestIdx < 0) {
            activeTargetSlotIndex = -1
            lastWeight = 0f
            weightState.floatValue = 0f
            return rawTopY
        }

        activeTargetSlotIndex = bestIdx
        val norm = (1f - bestDist / enterRadius).coerceIn(0f, 1f)
        val weight = norm.pow(1.6f)
        lastWeight = weight
        weightState.floatValue = if (weight >= commitThreshold) weight else 0f

        // 非線性視覺吸附映射（維持高跟手度同時於接近槽位時產生明確的磁吸貼合拉力）
        val visualPull = (weight * 0.54f).coerceIn(0f, 0.54f)
        return lerp(rawTopY, slotTopsY[bestIdx], visualPull)
    }

    fun canCommit(): Boolean = activeTargetSlotIndex >= 0 && lastWeight >= commitThreshold
}

@Stable
class StackedCardListState {
    internal val scrollOffsetState: MutableFloatState = mutableFloatStateOf(0f)
    internal val maxScrollState: MutableFloatState = mutableFloatStateOf(0f)
    internal val viewportHeightState: MutableFloatState = mutableFloatStateOf(0f)
    internal val animFrameNanos: MutableLongState = mutableLongStateOf(0L)
    internal val rawOverscrollState: MutableFloatState = mutableFloatStateOf(0f)
    internal val visualOverscrollState: MutableFloatState = mutableFloatStateOf(0f)
    internal val editBottomInsetFloorState: MutableFloatState = mutableFloatStateOf(0f)
    internal var maxOverscrollPx: Float = 200f
    internal var isReducedMotionEnabled: Boolean = false
    internal var isFlinging: Boolean = false
    internal var isProgrammaticScroll: Boolean = false
    internal var draggedItemKey: Any? = null
    private var suppressedReflowKey: Any? = null

    internal var lastPinnedKey: Any? = null
    internal var lastPinnedVisualTopY: Float = 0f
    private var exitAnchorKey: Any? = null
    internal var exitAnchorVisualTopY: Float = 0f
    private var exitAnchorExpireNanos: Long = 0L

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
    internal var isTopStackBuffer: BooleanArray = BooleanArray(32)
    internal var isBottomStackBuffer: BooleanArray = BooleanArray(32)
    internal var currentPlacedCount: Int = 0
    private var lastReflowKeys: List<Any> = emptyList()

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

    internal val currentAvgSlotPitchPx: Float
        get() {
            val count = currentKeys.size
            if (count <= 0) return 1f
            val span = (currentTotalLinearHeight - currentTopMarginPx - currentBottomMarginPx + currentGapPx)
                .coerceAtLeast(1f)
            return (span / count).coerceAtLeast(1f)
        }

    fun suppressNextReflowFor(key: Any) {
        suppressedReflowKey = key
    }

    internal fun recordPinnedFrame(key: Any, visualTopY: Float) {
        lastPinnedKey = key
        lastPinnedVisualTopY = visualTopY
    }

    internal fun beginExitAnchorLock(
        key: Any,
        visualTopY: Float,
        nowNanos: Long,
        durationNanos: Long = 550_000_000L
    ) {
        exitAnchorKey = key
        exitAnchorVisualTopY = visualTopY
        exitAnchorExpireNanos = nowNanos + durationNanos
    }

    internal fun activeExitAnchorKey(nowNanos: Long): Any? {
        val k = exitAnchorKey ?: return null
        if (nowNanos >= exitAnchorExpireNanos) {
            exitAnchorKey = null
            return null
        }
        return k
    }

    fun hasActiveExitAnchor(): Boolean = exitAnchorKey != null

    fun clearExitAnchorLock() {
        exitAnchorKey = null
        exitAnchorExpireNanos = 0L
    }

    fun resolveSlotTargetTopY(key: Any): Float? {
        val idx = currentKeys.indexOf(key)
        if (idx < 0 || idx >= currentItemTops.size) return null
        if (idx < currentPlacedCount && visibleFlagsBuffer[idx]) {
            return placedTopYsBuffer[idx]
        }
        return currentTopInsetPx + currentItemTops[idx] - scrollOffsetState.floatValue
    }

    fun fillCandidateSlotsForDraggedItem(
        draggedKey: Any,
        draggedHeightPx: Float,
        outSlotTops: FloatArray,
        outSlotCenters: FloatArray
    ): Int {
        val keys = currentKeys
        val heights = currentItemHeights
        val n = minOf(keys.size, heights.size)
        if (n <= 0 || outSlotTops.size < n || outSlotCenters.size < n) return 0

        val topInset = currentTopInsetPx
        val scroll = scrollOffsetState.floatValue
        val gap = currentGapPx
        val halfDragged = draggedHeightPx * 0.5f

        var cursorContentY = currentTopMarginPx
        var slotIdx = 0
        for (i in 0 until n) {
            if (keys[i] == draggedKey) continue
            val slotTopY = topInset + cursorContentY - scroll
            outSlotTops[slotIdx] = slotTopY
            outSlotCenters[slotIdx] = slotTopY + halfDragged
            slotIdx++
            cursorContentY += heights[i] + gap
        }
        val lastSlotTopY = topInset + cursorContentY - scroll
        outSlotTops[slotIdx] = lastSlotTopY
        outSlotCenters[slotIdx] = lastSlotTopY + halfDragged
        return slotIdx + 1
    }

    internal fun clearWhenEmpty() {
        maxScrollState.floatValue = 0f
        scrollOffsetState.floatValue = 0f
        clearOverscrollImmediate()
        currentPlacedCount = 0
        currentKeys = emptyList()
        currentItemTops = FloatArray(0)
        currentItemHeights = FloatArray(0)
        currentTotalLinearHeight = 0f
        lastReflowKeys = emptyList()
        reflowAnims.clear()
        slotStates.clear()
        lastPinnedKey = null
        exitAnchorKey = null
    }

    val placedItemsInfo: List<PlacedStackedItemInfo>
        get() {
            val n = currentPlacedCount
            val keys = currentKeys
            val heights = currentItemHeights
            val count = minOf(n, minOf(keys.size, heights.size))
            if (count <= 0) return emptyList()
            val list = ArrayList<PlacedStackedItemInfo>(count)
            for (i in 0 until count) {
                if (!visibleFlagsBuffer[i]) continue
                list.add(
                    PlacedStackedItemInfo(
                        index = i,
                        key = keys[i],
                        placedTopY = placedTopYsBuffer[i],
                        heightPx = heights[i],
                        scale = scalesBuffer[i],
                        depth = depthsBuffer[i],
                        isTopStack = isTopStackBuffer[i],
                        isBottomStack = isBottomStackBuffer[i]
                    )
                )
            }
            return list
        }

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
            isTopStackBuffer = BooleanArray(nextSize)
            isBottomStackBuffer = BooleanArray(nextSize)
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
        if (!isProgrammaticScroll && abs(delta) > 0.5f) {
            clearExitAnchorLock()
        }
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
        val hadPreviousKeys = lastReflowKeys.isNotEmpty()
        val keysChanged = lastReflowKeys != keys
        if (keysChanged) {
            lastReflowKeys = keys.toList()
        }
        val allowNewReflowSpring = hadPreviousKeys && keysChanged && !isReducedMotion

        for (i in keys.indices) {
            val k = keys[i]
            val naturalTopPx = naturalTops[i]
            val entry = reflowAnims.getOrPut(k) { ItemReflowAnim(lastNaturalTopPx = naturalTopPx) }
            val prevTop = entry.lastNaturalTopPx
            entry.lastNaturalTopPx = naturalTopPx

            if (k == draggedItemKey || k == suppressedReflowKey || isReducedMotion) {
                entry.currentOffsetPx = 0f
                entry.activeAnim = null
                entry.startNanos = 0L
                if (k == suppressedReflowKey) {
                    suppressedReflowKey = null
                }
            } else if (prevTop != null && allowNewReflowSpring) {
                val delta = prevTop - naturalTopPx
                if (abs(delta) > 0.5f) {
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

    internal fun pruneRemovedKeys(activeKeySet: Set<Any>) {
        if (reflowAnims.size > activeKeySet.size) {
            val it = reflowAnims.keys.iterator()
            while (it.hasNext()) {
                if (it.next() !in activeKeySet) it.remove()
            }
        }
        if (slotStates.size > activeKeySet.size) {
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
            if (state.viewportHeightState.floatValue != viewportH) {
                state.viewportHeightState.floatValue = viewportH
            }

            val count = minOf(measurables.size, itemsList.size)
            if (count == 0 || width <= 0 || height <= 0) {
                state.clearWhenEmpty()
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

                val effectiveBottomForMaxScroll = maxOf(
                    bottomInsetPx,
                    state.editBottomInsetFloorState.floatValue
                )
                val usableHeight = (viewportH - topInsetPx - effectiveBottomForMaxScroll).coerceAtLeast(1f)
                val maxScroll = (totalLinearHeight - usableHeight).coerceAtLeast(0f)
                if (abs(state.maxScrollState.floatValue - maxScroll) > 0.05f ||
                    (maxScroll == 0f && state.maxScrollState.floatValue != 0f)
                ) {
                    state.maxScrollState.floatValue = maxScroll
                }

                val effectiveTopBound = topInsetPx + topPaddingPx
                val effectiveBottomBound = (viewportH - bottomInsetPx - bottomPaddingPx)
                    .coerceAtLeast(effectiveTopBound + 1f)
                val availableInnerH = (effectiveBottomBound - effectiveTopBound).coerceAtLeast(0f)

                val maxAllowedHeadroom = ((availableInnerH - minExpandedHeightPx) / 2f).coerceAtLeast(0f)
                val effectiveMaxStackPx = (peekStepPx * MAX_STACK_DEPTH).coerceAtMost(maxAllowedHeadroom)
                val activeMaxDepth = (effectiveMaxStackPx / peekStepPx.coerceAtLeast(1f)).coerceAtLeast(0f)
                state.currentActiveMaxDepth = activeMaxDepth

                val needsExitAnchorCheck = pinnedKey == null &&
                    (state.lastPinnedKey != null || state.hasActiveExitAnchor())
                val nowNanos = if (needsExitAnchorCheck) System.nanoTime() else 0L
                if (pinnedKey == null && state.lastPinnedKey != null) {
                    state.beginExitAnchorLock(
                        key = state.lastPinnedKey!!,
                        visualTopY = state.lastPinnedVisualTopY,
                        nowNanos = nowNanos
                    )
                    state.lastPinnedKey = null
                }

                if (pinnedKey == null && state.hasActiveExitAnchor()) {
                    val activeAnchorKey = state.activeExitAnchorKey(nowNanos)
                    if (activeAnchorKey != null) {
                        val anchorIdx = keys.indexOf(activeAnchorKey)
                        if (anchorIdx in 0 until count) {
                            val anchorH = finalHeights[anchorIdx]
                            val topStackReserve = if (maxScroll > 0.5f && activeMaxDepth > 0f) {
                                peekStepPx * minOf(anchorIdx.toFloat(), activeMaxDepth)
                            } else {
                                0f
                            }
                            val bottomStackReserve = if (maxScroll > 0.5f && activeMaxDepth > 0f) {
                                peekStepPx * minOf((count - 1 - anchorIdx).toFloat(), activeMaxDepth)
                            } else {
                                0f
                            }
                            val safeMinY = effectiveTopBound + topStackReserve
                            val safeMaxY = (effectiveBottomBound - anchorH - bottomStackReserve)
                                .coerceAtLeast(safeMinY)
                            val desiredY = state.exitAnchorVisualTopY.coerceIn(safeMinY, safeMaxY)
                            val idealScroll = (topInsetPx + finalTops[anchorIdx] - desiredY)
                                .coerceIn(0f, maxScroll)
                            if (abs(idealScroll - state.scrollOffsetState.floatValue) > 0.25f) {
                                state.scrollOffsetState.floatValue = idealScroll
                            }
                            if (reducedMotion) {
                                state.clearExitAnchorLock()
                            }
                        } else {
                            state.clearExitAnchorLock()
                        }
                    }
                }

                val clampedScroll = state.scrollOffsetState.floatValue.coerceIn(0f, maxScroll)
                if (clampedScroll != state.scrollOffsetState.floatValue) {
                    state.scrollOffsetState.floatValue = clampedScroll
                }
                val visualOverscrollPx = if (reducedMotion || pinnedKey != null || state.draggedItemKey != null) {
                    0f
                } else {
                    state.visualOverscrollState.floatValue
                }

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
                val isTopFlags = state.isTopStackBuffer
                val isBottomFlags = state.isBottomStackBuffer

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
                        val slotDepthState = state.getSlotState(k).depthState
                        if (slotDepthState.floatValue != 0f) {
                            slotDepthState.floatValue = 0f
                        }
                        state.recordPinnedFrame(k, clampedY)

                        placedTopYs[i] = clampedY
                        scales[i] = 1f
                        alphas[i] = 1f
                        zIndices[i] = 20f
                        originYs[i] = 0.5f
                        depths[i] = 0f
                        visibleFlags[i] = true
                        isTopFlags[i] = false
                        isBottomFlags[i] = false
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

                    val clampedSlotDepth = depth.coerceIn(0f, FADE_OUT_DEPTH + 0.15f)
                    val slotDepthState = state.getSlotState(k).depthState
                    val prevSlotDepth = slotDepthState.floatValue
                    if ((clampedSlotDepth == 0f && prevSlotDepth != 0f) ||
                        abs(prevSlotDepth - clampedSlotDepth) > 0.004f
                    ) {
                        slotDepthState.floatValue = clampedSlotDepth
                    }

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

                    val zIndex = when {
                        k == state.draggedItemKey -> 0f
                        depth <= 0f -> 10f
                        else -> (10f - depth).coerceAtLeast(0f)
                    }

                    placedTopYs[i] = placedTopY
                    scales[i] = scale
                    alphas[i] = alpha
                    zIndices[i] = zIndex
                    originYs[i] = originY
                    depths[i] = depth
                    visibleFlags[i] = true
                    isTopFlags[i] = isTopStack
                    isBottomFlags[i] = isBottomStack
                }

                state.currentPlacedCount = count

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
