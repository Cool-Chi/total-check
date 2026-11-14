package com.example.totalcheck.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.totalcheck.R
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ACTIVE_TAG_SCALE = 1.08f
private const val FLOATING_TAG_LIFT_SCALE = 1.06f

internal class DistantTabTransition(
    val fromPage: Int,
    val toPage: Int,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)
)

private class TabReflowAnim(
    var currentOffsetPx: Float = 0f,
    var lastNaturalLeftPx: Float? = null,
    var activeAnim: TargetBasedAnimation<Float, AnimationVector1D>? = null,
    var startNanos: Long = 0L
)

private class TagRowBoundsHolder {
    var placeablesBuffer: Array<Placeable?> = arrayOfNulls(16)
    var idsBuffer: LongArray = LongArray(16)
    var fractionsBuffer: FloatArray = FloatArray(16)
    var scalesBuffer: FloatArray = FloatArray(16)
    var unscaledLeftsBuffer: FloatArray = FloatArray(16)
    val visualLefts = FloatArray(64)
    val visualRights = FloatArray(64)
    val visualHeights = FloatArray(64)
    val unscaledHeights = FloatArray(64)
    val layoutUnscaledLefts = FloatArray(64)
    var count: Int = 0
    var maxTagHeightPx: Int = 0

    val reflowFrameNanos = mutableLongStateOf(0L)
    val reflowSignal = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val reflowAnims = HashMap<Long, TabReflowAnim>()
    private var suppressedReflowId: Long? = null
    private var lastOrderedIds: LongArray = LongArray(0)

    fun suppressNextReflowFor(id: Long) {
        suppressedReflowId = id
    }

    fun ensureCapacity(size: Int) {
        if (placeablesBuffer.size < size) {
            val nextSize = maxOf(size, placeablesBuffer.size * 2)
            placeablesBuffer = arrayOfNulls(nextSize)
            idsBuffer = LongArray(nextSize)
            fractionsBuffer = FloatArray(nextSize)
            scalesBuffer = FloatArray(nextSize)
            unscaledLeftsBuffer = FloatArray(nextSize)
        }
    }

    fun syncReflowOnMeasure(
        ids: LongArray,
        naturalLefts: FloatArray,
        itemCount: Int,
        draggedId: Long?,
        isReducedMotion: Boolean
    ) {
        var orderChanged = lastOrderedIds.size != itemCount
        if (!orderChanged) {
            for (i in 0 until itemCount) {
                if (lastOrderedIds[i] != ids[i]) {
                    orderChanged = true
                    break
                }
            }
        }
        val hadPreviousOrder = lastOrderedIds.isNotEmpty()
        if (orderChanged) {
            lastOrderedIds = ids.copyOf(itemCount)
        }

        val currentIdSet = HashSet<Long>(itemCount)
        for (i in 0 until itemCount) {
            currentIdSet.add(ids[i])
        }
        val iter = reflowAnims.keys.iterator()
        while (iter.hasNext()) {
            if (iter.next() !in currentIdSet) {
                iter.remove()
            }
        }

        // 僅於清單排列順序改變（拖曳換位、無障礙左移/右移或增刪清單）時啟動重排補償彈簧；
        // 一般分頁滑動或點擊切換造成的膠囊寬度平滑伸縮直接 1:1 跟隨，徹底消除每幀新建動畫與雙重動效重疊
        val allowNewReflowSpring = orderChanged && hadPreviousOrder
        var startedAny = false
        for (i in 0 until itemCount) {
            val id = ids[i]
            val naturalLeft = naturalLefts[i]
            val animState = reflowAnims.getOrPut(id) {
                TabReflowAnim(lastNaturalLeftPx = naturalLeft)
            }
            val prevNatural = animState.lastNaturalLeftPx
            if (id == draggedId || id == suppressedReflowId || isReducedMotion) {
                animState.activeAnim = null
                animState.currentOffsetPx = 0f
                animState.startNanos = 0L
                if (id == suppressedReflowId) {
                    suppressedReflowId = null
                }
            } else if (allowNewReflowSpring && prevNatural != null && abs(naturalLeft - prevNatural) > 0.5f) {
                val startOffset = (animState.currentOffsetPx + (prevNatural - naturalLeft))
                    .coerceIn(-420f, 420f)
                if (abs(startOffset) > 0.5f) {
                    animState.currentOffsetPx = startOffset
                    animState.activeAnim = TargetBasedAnimation(
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
                        typeConverter = Float.VectorConverter,
                        initialValue = startOffset,
                        targetValue = 0f
                    )
                    animState.startNanos = 0L
                    startedAny = true
                } else {
                    animState.activeAnim = null
                    animState.currentOffsetPx = 0f
                }
            }
            animState.lastNaturalLeftPx = naturalLeft
        }
        if (startedAny) {
            reflowSignal.tryEmit(Unit)
        }
    }

    fun hasActiveReflow(): Boolean {
        return reflowAnims.values.any { it.activeAnim != null }
    }

    fun stepReflowAnimations(frameNanos: Long): Boolean {
        var anyRunning = false
        for (animState in reflowAnims.values) {
            val anim = animState.activeAnim ?: continue
            if (animState.startNanos == 0L) {
                animState.startNanos = frameNanos
            }
            val elapsedNanos = (frameNanos - animState.startNanos).coerceAtLeast(0L)
            if (anim.isFinishedFromNanos(elapsedNanos)) {
                animState.currentOffsetPx = 0f
                animState.activeAnim = null
                animState.startNanos = 0L
            } else {
                animState.currentOffsetPx = anim.getValueFromNanos(elapsedNanos)
                anyRunning = true
            }
        }
        return anyRunning
    }

    fun getReflowOffset(id: Long): Float {
        return reflowAnims[id]?.currentOffsetPx ?: 0f
    }
}

@Composable
internal fun ExpandingChecklistTabBar(
    checklists: List<ChecklistEntity>,
    itemsByChecklist: Map<Long, List<CheckItemEntity>>,
    pagerState: PagerState,
    distantTabTransition: DistantTabTransition?,
    activeChecklistId: Long? = null,
    onSelectPage: (Int) -> Unit,
    onReorderChecklists: (List<Long>) -> Unit,
    onMoveChecklistLeft: ((Long) -> Unit)? = null,
    onMoveChecklistRight: ((Long) -> Unit)? = null,
    onAddChecklist: () -> Unit,
    addButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isReducedMotion = LocalReducedMotion.current
    val currentDistant by rememberUpdatedState(distantTabTransition)
    val currentOnReorderChecklists by rememberUpdatedState(onReorderChecklists)

    val scrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val boundsHolder = remember { TagRowBoundsHolder() }
    val focusRequesters = remember { HashMap<Long, FocusRequester>() }

    var draggedChecklistId by remember { mutableStateOf<Long?>(null) }
    var workingChecklists by remember { mutableStateOf<List<ChecklistEntity>>(emptyList()) }
    var committedOrderIds by remember { mutableStateOf<List<Long>?>(null) }
    var draggedVisualWidthPx by remember { mutableFloatStateOf(0f) }
    var draggedUnscaledHeightPx by remember { mutableFloatStateOf(0f) }
    var dragOverlayX by remember { mutableFloatStateOf(0f) }
    var dragGrabOffsetX by remember { mutableFloatStateOf(0f) }
    var isSettlingDrop by remember { mutableStateOf(false) }
    val dropOffsetAnim = remember { Animatable(0f) }
    val pointerXInViewportState = remember { mutableFloatStateOf(0f) }
    val latestPointerXRef = remember { mutableFloatStateOf(0f) }

    val currentChecklistsState = rememberUpdatedState(checklists)
    val currentIsReducedMotionState = rememberUpdatedState(isReducedMotion)

    LaunchedEffect(boundsHolder) {
        boundsHolder.reflowSignal.collect {
            while (boundsHolder.hasActiveReflow()) {
                withFrameNanos { frameNanos ->
                    boundsHolder.stepReflowAnimations(frameNanos)
                    boundsHolder.reflowFrameNanos.longValue = frameNanos
                }
            }
        }
    }

    LaunchedEffect(checklists, committedOrderIds) {
        val committed = committedOrderIds ?: return@LaunchedEffect
        val incomingIds = checklists.map { it.id }
        if (incomingIds == committed || incomingIds.toSet() != committed.toSet()) {
            committedOrderIds = null
        }
    }

    val displayedChecklists = remember(checklists, draggedChecklistId, workingChecklists, committedOrderIds) {
        when {
            draggedChecklistId != null && workingChecklists.isNotEmpty() -> workingChecklists
            committedOrderIds != null -> {
                val order = committedOrderIds!!
                val byId = checklists.associateBy { it.id }
                val ordered = order.mapNotNull { byId[it] }
                val orderSet = order.toSet()
                val extras = checklists.filter { it.id !in orderSet }
                ordered + extras
            }
            else -> checklists
        }
    }
    val currentDisplayedChecklistsState = rememberUpdatedState(displayedChecklists)

    val fallbackActiveIndex by remember(pagerState, checklists.size) {
        derivedStateOf {
            (currentDistant?.toPage ?: pagerState.targetPage)
                .coerceIn(0, (checklists.size - 1).coerceAtLeast(0))
        }
    }
    val resolvedActiveChecklistId = activeChecklistId ?: checklists.getOrNull(fallbackActiveIndex)?.id
    val isPagerSyncedWithOrder by remember(checklists, resolvedActiveChecklistId, pagerState) {
        derivedStateOf {
            val expectedIdx = checklists.indexOfFirst { it.id == resolvedActiveChecklistId }
            expectedIdx < 0 ||
                expectedIdx == pagerState.currentPage ||
                expectedIdx == pagerState.targetPage ||
                currentDistant != null
        }
    }
    val isOrderFrozen = draggedChecklistId != null ||
        isSettlingDrop ||
        committedOrderIds != null ||
        !isPagerSyncedWithOrder

    val selectionFractionFor: (Int, Long) -> Float = { index, checklistId ->
        if (isOrderFrozen) {
            if (checklistId == resolvedActiveChecklistId) 1f else 0f
        } else {
            val distant = currentDistant
            if (distant != null) {
                val p = distant.progress.value.coerceIn(0f, 1f)
                when (index) {
                    distant.toPage -> p
                    distant.fromPage -> 1f - p
                    else -> 0f
                }
            } else {
                val pagePos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                (1f - abs(pagePos - index)).coerceIn(0f, 1f)
            }
        }
    }

    val startRowSpacerPx = with(density) { 12.dp.toPx() }
    val baseGapPx = with(density) { 8.dp.toPx() }
    val extraNeighborMarginPx = with(density) { 4.dp.toPx() }
    val rowHorizontalEdgePadPx = with(density) { 4.dp.toPx() }
    val verticalScalePadPx = with(density) { 4.dp.roundToPx() }
    val overlayHorizontalPadPx = with(density) { 4.dp.toPx() }

    val recalculateInsertionSlot = {
        val currentDraggedId = draggedChecklistId
        val currentList = workingChecklists
        if (currentDraggedId != null && currentList.isNotEmpty()) {
            val currentIndex = currentList.indexOfFirst { it.id == currentDraggedId }
            if (currentIndex >= 0) {
                val floatingLeftInLayout =
                    dragOverlayX + scrollState.value.toFloat() - startRowSpacerPx
                val floatingRightInLayout = floatingLeftInLayout + draggedVisualWidthPx
                var targetIndex = currentIndex
                val trackedCount = minOf(currentList.size, boundsHolder.count)
                for (idx in 0 until trackedCount) {
                    val item = currentList[idx]
                    if (item.id == currentDraggedId) continue
                    val itemCenterX = (boundsHolder.visualLefts[idx] + boundsHolder.visualRights[idx]) / 2f
                    if (idx < currentIndex && floatingLeftInLayout < itemCenterX) {
                        if (idx < targetIndex) targetIndex = idx
                    } else if (idx > currentIndex && floatingRightInLayout > itemCenterX) {
                        if (idx > targetIndex) targetIndex = idx
                    }
                }
                if (targetIndex != currentIndex) {
                    val mutable = currentList.toMutableList()
                    val moved = mutable.removeAt(currentIndex)
                    mutable.add(targetIndex, moved)
                    workingChecklists = mutable
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    val finishDragSettle = {
        val activeId = draggedChecklistId
        val finalOrderIds = workingChecklists.map { it.id }
        if (activeId != null && !isSettlingDrop) {
            isSettlingDrop = true
            val currentIds = currentChecklistsState.value.map { it.id }
            if (finalOrderIds.isNotEmpty() && finalOrderIds != currentIds) {
                committedOrderIds = finalOrderIds
                currentOnReorderChecklists(finalOrderIds)
            }
            val targetIdx = workingChecklists.indexOfFirst { it.id == activeId }
            val targetViewportX = if (targetIdx in 0 until boundsHolder.count) {
                startRowSpacerPx + boundsHolder.visualLefts[targetIdx] - scrollState.value.toFloat()
            } else {
                dragOverlayX
            }
            scope.launch {
                try {
                    if (!currentIsReducedMotionState.value) {
                        val startDelta = dragOverlayX - targetViewportX
                        dropOffsetAnim.snapTo(startDelta)
                        dragOverlayX = targetViewportX
                        dropOffsetAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = 420f,
                                visibilityThreshold = 0.5f
                            )
                        )
                    } else {
                        dragOverlayX = targetViewportX
                        dropOffsetAnim.snapTo(0f)
                    }
                } finally {
                    boundsHolder.suppressNextReflowFor(activeId)
                    draggedChecklistId = null
                    isSettlingDrop = false
                    workingChecklists = emptyList()
                    dropOffsetAnim.snapTo(0f)
                }
            }
        }
    }

    LaunchedEffect(draggedChecklistId, isSettlingDrop) {
        if (draggedChecklistId != null && !isSettlingDrop) {
            val maxEdgeZonePx = with(density) { 40.dp.toPx() }
            val maxSpeedPxPerSec = with(density) { 280.dp.toPx() }
            var lastFrameNanos = 0L
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val dtSec = if (lastFrameNanos == 0L) {
                        0f
                    } else {
                        ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    }
                    lastFrameNanos = frameNanos
                    if (dtSec > 0f && viewportWidthPx > 0) {
                        val vw = viewportWidthPx.toFloat()
                        val edgeZonePx = maxEdgeZonePx
                            .coerceAtMost(vw * 0.22f)
                            .coerceAtLeast(1f)
                        val px = pointerXInViewportState.floatValue
                        val scrollDeltaPx = when {
                            px < edgeZonePx && scrollState.value > 0 -> {
                                val t = ((edgeZonePx - px) / edgeZonePx).coerceIn(0f, 1f)
                                -maxSpeedPxPerSec * (t * t) * dtSec
                            }
                            px > (vw - edgeZonePx) && scrollState.value < scrollState.maxValue -> {
                                val t = ((px - (vw - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                                maxSpeedPxPerSec * (t * t) * dtSec
                            }
                            else -> 0f
                        }
                        if (scrollDeltaPx != 0f) {
                            val consumed = scrollState.dispatchRawDelta(scrollDeltaPx)
                            if (consumed != 0f) {
                                recalculateInsertionSlot()
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(resolvedActiveChecklistId, isReducedMotion) {
        if (draggedChecklistId != null || isSettlingDrop) return@LaunchedEffect
        val targetId = resolvedActiveChecklistId ?: return@LaunchedEffect

        withFrameNanos { }
        if (draggedChecklistId != null || isSettlingDrop) return@LaunchedEffect
        val targetIdx = currentDisplayedChecklistsState.value.indexOfFirst { it.id == targetId }
        if (viewportWidthPx <= 0 || targetIdx !in 0 until boundsHolder.count) return@LaunchedEffect
        val edgePaddingPx = with(density) { 28.dp.toPx() }
        val currentWidth = (boundsHolder.visualRights[targetIdx] - boundsHolder.visualLefts[targetIdx])
            .coerceAtLeast(1f)
        val estimatedExpandedPadExtra = with(density) { 52.dp.toPx() }
        val estimatedMaxRight = boundsHolder.visualLefts[targetIdx] + maxOf(currentWidth, currentWidth + estimatedExpandedPadExtra * (1f - boundsHolder.fractionsBuffer.getOrElse(targetIdx) { 1f }))
        val desiredLeft = (startRowSpacerPx + boundsHolder.visualLefts[targetIdx] - edgePaddingPx)
            .coerceAtLeast(0f)
        val desiredRight = startRowSpacerPx + maxOf(boundsHolder.visualRights[targetIdx], estimatedMaxRight) + edgePaddingPx
        val currentStart = scrollState.value.toFloat()
        val currentEnd = currentStart + viewportWidthPx
        val targetScroll = when {
            desiredLeft < currentStart -> desiredLeft.roundToInt()
            desiredRight > currentEnd -> (desiredRight - viewportWidthPx).roundToInt()
            else -> null
        }?.coerceIn(0, scrollState.maxValue)

        if (targetScroll != null && targetScroll != scrollState.value) {
            if (isReducedMotion) {
                scrollState.scrollTo(targetScroll)
            } else {
                scrollState.animateScrollTo(
                    value = targetScroll,
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp)
            .onSizeChanged {
                viewportWidthPx = it.width
                viewportHeightPx = it.height
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    latestPointerXRef.floatValue = down.position.x
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            if (draggedChecklistId != null && !isSettlingDrop) {
                                finishDragSettle()
                            }
                            break
                        }
                        latestPointerXRef.floatValue = change.position.x
                        if (draggedChecklistId != null && !isSettlingDrop) {
                            change.consume()
                            pointerXInViewportState.floatValue = change.position.x
                            val minX = overlayHorizontalPadPx
                            val maxX = (viewportWidthPx.toFloat() - draggedVisualWidthPx - overlayHorizontalPadPx)
                                .coerceAtLeast(minX)
                            dragOverlayX = (change.position.x - dragGrabOffsetX).coerceIn(minX, maxX)
                            recalculateInsertionSlot()
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(12.dp))

            Layout(
                content = {
                    displayedChecklists.forEachIndexed { index, checklist ->
                        key(checklist.id) {
                            val items = itemsByChecklist[checklist.id].orEmpty()
                            val totalCount = items.size
                            val uncheckedCount = items.count { !it.isChecked }
                            val isDragged = draggedChecklistId == checklist.id

                            if (isDragged) {
                                val slotWidthDp = with(density) { draggedVisualWidthPx.toDp() }
                                    .coerceAtLeast(48.dp)
                                val slotHeightDp = with(density) { draggedUnscaledHeightPx.toDp() }
                                    .coerceAtLeast(40.dp)
                                val slotScaleY = if (checklist.id == resolvedActiveChecklistId && !isReducedMotion) {
                                    ACTIVE_TAG_SCALE
                                } else {
                                    1f
                                }
                                DashedTabEmptySlot(
                                    widthDp = slotWidthDp,
                                    heightDp = slotHeightDp,
                                    visualScaleY = slotScaleY
                                )
                            } else {
                                val requester = focusRequesters.getOrPut(checklist.id) { FocusRequester() }
                                val currentIndex by rememberUpdatedState(index)
                                val totalListSize by rememberUpdatedState(displayedChecklists.size)
                                val currentChecklists by rememberUpdatedState(displayedChecklists)
                                val canReorder = currentChecklistsState.value.size > 1

                                ExpandingChecklistTag(
                                    checklist = checklist,
                                    totalCount = totalCount,
                                    uncheckedCount = uncheckedCount,
                                    isSelected = checklist.id == resolvedActiveChecklistId,
                                    canReorder = canReorder,
                                    canMoveLeft = currentIndex > 0,
                                    canMoveRight = currentIndex < totalListSize - 1,
                                    selectionFractionProvider = {
                                        selectionFractionFor(currentIndex, checklist.id)
                                    },
                                    focusRequester = requester,
                                    onSelect = {
                                        val realIdx = currentChecklistsState.value.indexOfFirst {
                                            it.id == checklist.id
                                        }
                                        if (realIdx >= 0) {
                                            onSelectPage(realIdx)
                                        }
                                    },
                                    onLongPress = {
                                        if (
                                            currentChecklistsState.value.size > 1 &&
                                            draggedChecklistId == null &&
                                            !isSettlingDrop
                                        ) {
                                            val slotIdx = displayedChecklists.indexOfFirst {
                                                it.id == checklist.id
                                            }
                                            if (slotIdx in 0 until boundsHolder.count) {
                                                val vLeft = boundsHolder.visualLefts[slotIdx]
                                                val vRight = boundsHolder.visualRights[slotIdx]
                                                val vWidth = (vRight - vLeft).coerceAtLeast(1f)
                                                val unscaledH = boundsHolder.unscaledHeights[slotIdx]
                                                    .coerceAtLeast(1f)
                                                val startViewportX = startRowSpacerPx + vLeft - scrollState.value.toFloat()
                                                dragGrabOffsetX = (latestPointerXRef.floatValue - startViewportX)
                                                    .coerceIn(0f, vWidth)
                                                draggedVisualWidthPx = vWidth
                                                draggedUnscaledHeightPx = unscaledH
                                                val minX = overlayHorizontalPadPx
                                                val maxX = (viewportWidthPx.toFloat() - vWidth - overlayHorizontalPadPx)
                                                    .coerceAtLeast(minX)
                                                dragOverlayX = startViewportX.coerceIn(minX, maxX)
                                                pointerXInViewportState.floatValue = latestPointerXRef.floatValue
                                                workingChecklists = currentChecklistsState.value.toList()
                                                draggedChecklistId = checklist.id
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    },
                                    onMoveLeftAction = if (onMoveChecklistLeft != null) {
                                        { onMoveChecklistLeft(checklist.id) }
                                    } else {
                                        null
                                    },
                                    onMoveRightAction = if (onMoveChecklistRight != null) {
                                        { onMoveChecklistRight(checklist.id) }
                                    } else {
                                        null
                                    },
                                    onMoveFocusLeft = {
                                        val prevIdx = currentIndex - 1
                                        if (prevIdx >= 0) {
                                            onSelectPage(prevIdx)
                                            currentChecklists.getOrNull(prevIdx)?.let { prevList ->
                                                focusRequesters[prevList.id]?.requestFocus()
                                            }
                                        }
                                    },
                                    onMoveFocusRight = {
                                        val nextIdx = currentIndex + 1
                                        if (nextIdx < totalListSize) {
                                            onSelectPage(nextIdx)
                                            currentChecklists.getOrNull(nextIdx)?.let { nextList ->
                                                focusRequesters[nextList.id]?.requestFocus()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            ) { measurables, _ ->
                val count = measurables.size
                if (count == 0) {
                    boundsHolder.count = 0
                    boundsHolder.maxTagHeightPx = 0
                    return@Layout layout(0, 0) {}
                }

                boundsHolder.ensureCapacity(count)
                val placeables = boundsHolder.placeablesBuffer
                val ids = boundsHolder.idsBuffer
                val fractions = boundsHolder.fractionsBuffer
                val scales = boundsHolder.scalesBuffer
                val unscaledLefts = boundsHolder.unscaledLeftsBuffer
                val unboundedConstraints = Constraints()

                var maxTagHeight = 0
                for (i in 0 until count) {
                    val p = measurables[i].measure(unboundedConstraints)
                    placeables[i] = p
                    val item = displayedChecklists.getOrNull(i)
                    ids[i] = item?.id ?: -1L
                    if (p.height > maxTagHeight) {
                        maxTagHeight = p.height
                    }
                }
                boundsHolder.maxTagHeightPx = maxTagHeight

                var xCursor = rowHorizontalEdgePadPx
                val trackedCount = minOf(count, boundsHolder.visualLefts.size)
                boundsHolder.count = trackedCount

                for (i in 0 until count) {
                    val p = placeables[i]!!
                    val id = ids[i]
                    val t = selectionFractionFor(i, id).coerceIn(0f, 1f)
                    fractions[i] = t
                    val isSlot = id == draggedChecklistId
                    val scale = when {
                        isSlot || isReducedMotion -> 1f
                        else -> lerp(1f, ACTIVE_TAG_SCALE, t)
                    }
                    scales[i] = scale

                    val placeableW = p.width.toFloat()
                    val visualW = placeableW * scale
                    val extraMargin = extraNeighborMarginPx * t

                    val visualLeft = xCursor + extraMargin
                    val visualRight = visualLeft + visualW
                    if (i < trackedCount) {
                        boundsHolder.visualLefts[i] = visualLeft
                        boundsHolder.visualRights[i] = visualRight
                        boundsHolder.visualHeights[i] = p.height * scale
                        boundsHolder.unscaledHeights[i] = p.height.toFloat()
                    }

                    unscaledLefts[i] = visualLeft + (visualW - placeableW) * 0.5f
                    if (i < trackedCount) {
                        boundsHolder.layoutUnscaledLefts[i] = unscaledLefts[i]
                    }

                    xCursor = visualRight + extraMargin
                    if (i < count - 1) {
                        xCursor += baseGapPx
                    }
                }

                boundsHolder.syncReflowOnMeasure(
                    ids = ids,
                    naturalLefts = unscaledLefts,
                    itemCount = count,
                    draggedId = draggedChecklistId,
                    isReducedMotion = isReducedMotion
                )

                val totalWidth = (xCursor + rowHorizontalEdgePadPx).roundToInt().coerceAtLeast(0)
                val totalHeight = (maxTagHeight + verticalScalePadPx * 2).coerceAtLeast(0)

                layout(totalWidth, totalHeight) {
                    // 僅於 Placement 階段讀取 Frame 刻度，避免拖曳換位時觸發文字重組
                    boundsHolder.reflowFrameNanos.longValue
                    for (i in 0 until count) {
                        val p = placeables[i] ?: continue
                        val id = ids[i]
                        val scale = scales[i]
                        val t = fractions[i]
                        val reflowOffsetX = boundsHolder.getReflowOffset(id)
                        val yPos = verticalScalePadPx + (maxTagHeight - p.height) / 2
                        p.placeRelativeWithLayer(
                            x = (unscaledLefts[i] + reflowOffsetX).roundToInt(),
                            y = yPos,
                            zIndex = if (id == draggedChecklistId) 0f else 1f + t
                        ) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            TactileIconButton(
                onClick = onAddChecklist,
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(addButtonFocusRequester)
                    .testTag("add_checklist_button"),
                shape = RoundedCornerShape(50),
                containerColor = colorScheme.surfaceContainerHigh,
                contentColor = colorScheme.primary,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.cd_add_checklist),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        val floatingChecklist = draggedChecklistId?.let { id ->
            displayedChecklists.firstOrNull { it.id == id }
        }
        if (floatingChecklist != null) {
            val floatingItems = itemsByChecklist[floatingChecklist.id].orEmpty()
            val floatingSelected = floatingChecklist.id == resolvedActiveChecklistId
            val liftScale by animateFloatAsState(
                targetValue = if (isSettlingDrop || isReducedMotion) 1f else FLOATING_TAG_LIFT_SCALE,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
                label = "floatingTabLiftScale"
            )
            val selectionScale = if (floatingSelected && !isReducedMotion) ACTIVE_TAG_SCALE else 1f
            val totalOverlayScale = selectionScale * liftScale

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        val rawOffset = dragOverlayX + dropOffsetAnim.value
                        val centerShiftFix = (draggedVisualWidthPx - draggedVisualWidthPx / selectionScale) * 0.5f
                        translationX = rawOffset + centerShiftFix
                        scaleX = totalOverlayScale
                        scaleY = totalOverlayScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                FloatingChecklistTagOverlay(
                    checklist = floatingChecklist,
                    totalCount = floatingItems.size,
                    uncheckedCount = floatingItems.count { !it.isChecked },
                    isSelected = floatingSelected
                )
            }
        }
    }
}

@Composable
private fun DashedTabEmptySlot(
    widthDp: Dp,
    heightDp: Dp,
    visualScaleY: Float = 1f,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val fillColor = colorScheme.surfaceContainerLow.copy(alpha = 0.45f)
    val dashColor = colorScheme.primary.copy(alpha = 0.50f)
    Box(
        modifier = modifier
            .width(widthDp)
            .height(heightDp)
            .graphicsLayer {
                scaleY = visualScaleY
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clip(RoundedCornerShape(50))
            .drawBehind {
                val r = size.height / 2f
                val corner = CornerRadius(r, r)
                drawRoundRect(
                    color = fillColor,
                    cornerRadius = corner
                )
                val strokePx = 1.5.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = corner,
                    style = Stroke(
                        width = strokePx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }
    )
}

@Composable
private fun FloatingChecklistTagOverlay(
    checklist: ChecklistEntity,
    totalCount: Int,
    uncheckedCount: Int,
    isSelected: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val containerColor = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest
    val borderColor = colorScheme.primary
    val labelColor = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val titleStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val countStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
    val countText = if (totalCount == 0) "(0)" else "($uncheckedCount/$totalCount)"

    val padHPx = with(density) { if (isSelected) 18.dp.toPx() else 14.dp.toPx() }
    val padVPx = with(density) { 9.dp.toPx() }
    val minPillHeightPx = with(density) { 40.dp.roundToPx() }
    val countGapPx = with(density) { 6.dp.toPx() }
    val maxTitleWidthPx = with(density) { 168.dp.roundToPx() }
    val titleConstraints = remember(maxTitleWidthPx) { Constraints(maxWidth = maxTitleWidthPx) }
    val countConstraints = remember { Constraints() }

    Layout(
        modifier = Modifier
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(50), clip = false)
            .drawBehind {
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                drawRoundRect(
                    color = containerColor,
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = corner,
                    style = Stroke(width = 2.dp.toPx())
                )
            },
        content = {
            BasicText(
                text = checklist.name,
                style = titleStyle,
                color = { labelColor },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = countText,
                style = countStyle,
                color = { labelColor },
                maxLines = 1
            )
        }
    ) { tagMeasurables, _ ->
        val titlePlaceable = tagMeasurables[0].measure(titleConstraints)
        val countPlaceable = tagMeasurables[1].measure(countConstraints)
        val countRevealWidth = if (isSelected) countGapPx + countPlaceable.width else 0f
        val contentWidth = padHPx * 2f + titlePlaceable.width + countRevealWidth
        val contentHeight = padVPx * 2f + maxOf(titlePlaceable.height, countPlaceable.height)
        val pillWidth = contentWidth.roundToInt().coerceAtLeast(0)
        val pillHeight = contentHeight.roundToInt().coerceAtLeast(minPillHeightPx)

        layout(pillWidth, pillHeight) {
            val titleY = (pillHeight - titlePlaceable.height) / 2
            titlePlaceable.placeRelative(padHPx.roundToInt(), titleY)
            if (isSelected && countPlaceable.width > 0) {
                val countX = (padHPx + titlePlaceable.width + countGapPx).roundToInt()
                val countY = (pillHeight - countPlaceable.height) / 2
                countPlaceable.placeRelative(countX, countY)
            }
        }
    }
}

@Composable
private fun ExpandingChecklistTag(
    checklist: ChecklistEntity,
    totalCount: Int,
    uncheckedCount: Int,
    isSelected: Boolean,
    canReorder: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    selectionFractionProvider: () -> Float,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onMoveLeftAction: (() -> Unit)?,
    onMoveRightAction: (() -> Unit)?,
    onMoveFocusLeft: () -> Unit,
    onMoveFocusRight: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val keyboardFocusController = LocalKeyboardFocusVisible.current
    val pressFractionState = remember(checklist.id) { mutableFloatStateOf(0f) }
    val isHoveredState = remember(checklist.id) { mutableStateOf(false) }
    var isFocused by remember(checklist.id) { mutableStateOf(false) }

    val unselectedContainerColor = colorScheme.surfaceContainerHigh
    val hoveredContainerColor = colorScheme.surfaceContainerHighest
    val selectedContainerColor = colorScheme.primaryContainer
    val unselectedBorderColor = colorScheme.outlineVariant
    val selectedBorderColor = colorScheme.primary
    val unselectedLabelColor = colorScheme.onSurfaceVariant
    val selectedLabelColor = colorScheme.onPrimaryContainer
    val focusRingColor = colorScheme.primary
    val isDarkSurface = colorScheme.surface.luminance() < 0.5f
    val maxDeepenAlpha = if (isDarkSurface) 0.24f else 0.14f

    val titleStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val countStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
    val countText = if (totalCount == 0) "(0)" else "($uncheckedCount/$totalCount)"
    val a11yDescription = if (totalCount == 0) {
        stringResource(R.string.a11y_tab_empty, checklist.name)
    } else {
        stringResource(R.string.a11y_tab_counts, checklist.name, uncheckedCount, totalCount)
    }
    val moveLeftLabel = stringResource(R.string.a11y_move_left)
    val moveRightLabel = stringResource(R.string.a11y_move_right)

    val basePadHPx = with(density) { 14.dp.toPx() }
    val expandedPadHPx = with(density) { 18.dp.toPx() }
    val padVPx = with(density) { 9.dp.toPx() }
    val minPillHeightPx = with(density) { 40.dp.roundToPx() }
    val countGapFullPx = with(density) { 6.dp.toPx() }
    val maxTitleWidthPx = with(density) { 168.dp.roundToPx() }
    val titleConstraints = remember(maxTitleWidthPx) { Constraints(maxWidth = maxTitleWidthPx) }
    val countConstraints = remember { Constraints() }

    Layout(
        modifier = Modifier
            .testTag("checklist_tab_${checklist.id}")
            .expandingTabSlideOutCancel(
                onSelect = onSelect,
                onLongPress = onLongPress,
                pressFractionState = pressFractionState,
                isHoveredState = isHoveredState,
                longPressEnabled = canReorder
            )
            .drawBehind {
                val t = selectionFractionProvider().coerceIn(0f, 1f)
                val p = pressFractionState.floatValue.coerceIn(0f, 1f)
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                val baseBg = if (isHoveredState.value) hoveredContainerColor else unselectedContainerColor
                drawRoundRect(
                    color = lerpColor(baseBg, selectedContainerColor, t),
                    cornerRadius = corner
                )
                if (p > 0.001f) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = maxDeepenAlpha * p),
                        cornerRadius = corner
                    )
                }
                val borderStrokePx = lerp(1.dp.toPx(), 1.5.dp.toPx(), t)
                drawRoundRect(
                    color = lerpColor(unselectedBorderColor, selectedBorderColor, t),
                    cornerRadius = corner,
                    style = Stroke(width = borderStrokePx)
                )
                if (isFocused && keyboardFocusController.isKeyboardFocusVisible) {
                    val focusInset = 2.dp.toPx()
                    drawRoundRect(
                        color = focusRingColor.copy(alpha = 0.85f),
                        topLeft = Offset(focusInset, focusInset),
                        size = Size(
                            width = (size.width - focusInset * 2f).coerceAtLeast(1f),
                            height = (size.height - focusInset * 2f).coerceAtLeast(1f)
                        ),
                        cornerRadius = CornerRadius(
                            (radius - focusInset).coerceAtLeast(0f),
                            (radius - focusInset).coerceAtLeast(0f)
                        ),
                        style = Stroke(width = 1.75.dp.toPx())
                    )
                }
            }
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onMoveFocusLeft()
                            true
                        }
                        Key.DirectionRight -> {
                            onMoveFocusRight()
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                onSelect()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = a11yDescription
                onClick {
                    onSelect()
                    true
                }
                if (canReorder) {
                    val actions = buildList {
                        if (canMoveLeft && onMoveLeftAction != null) {
                            add(CustomAccessibilityAction(moveLeftLabel) { onMoveLeftAction(); true })
                        }
                        if (canMoveRight && onMoveRightAction != null) {
                            add(CustomAccessibilityAction(moveRightLabel) { onMoveRightAction(); true })
                        }
                    }
                    if (actions.isNotEmpty()) {
                        customActions = actions
                    }
                }
            },
        content = {
            BasicText(
                text = checklist.name,
                style = titleStyle,
                color = {
                    val t = selectionFractionProvider().coerceIn(0f, 1f)
                    lerpColor(unselectedLabelColor, selectedLabelColor, t)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            BasicText(
                text = countText,
                style = countStyle,
                color = { selectedLabelColor },
                maxLines = 1
            )
        }
    ) { tagMeasurables, _ ->
        // 兩個 BasicText 皆使用固定 Constraints，Compose LayoutNode 會直接命中測量快取，0 文字重排開銷
        val titlePlaceable = tagMeasurables[0].measure(titleConstraints)
        val countPlaceable = tagMeasurables[1].measure(countConstraints)
        val t = selectionFractionProvider().coerceIn(0f, 1f)

        val padHPx = lerp(basePadHPx, expandedPadHPx, t)
        val countRevealWidth = (countGapFullPx + countPlaceable.width) * t
        val contentWidth = padHPx * 2f + titlePlaceable.width + countRevealWidth
        val contentHeight = padVPx * 2f + maxOf(titlePlaceable.height, countPlaceable.height)

        val pillWidth = contentWidth.roundToInt().coerceAtLeast(0)
        val pillHeight = contentHeight.roundToInt().coerceAtLeast(minPillHeightPx)

        layout(pillWidth, pillHeight) {
            val titleY = (pillHeight - titlePlaceable.height) / 2
            val titleX = padHPx.roundToInt()
            titlePlaceable.placeRelative(titleX, titleY)

            if (t > 0.05f && countPlaceable.width > 0) {
                val countX = (padHPx + titlePlaceable.width + countGapFullPx * t).roundToInt()
                val countY = (pillHeight - countPlaceable.height) / 2
                val revealAlpha = ((t - 0.16f) / 0.72f).coerceIn(0f, 1f)
                val revealScale = lerp(0.84f, 1f, t)
                countPlaceable.placeRelativeWithLayer(countX, countY) {
                    alpha = revealAlpha
                    scaleX = revealScale
                    scaleY = revealScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            }
        }
    }
}
