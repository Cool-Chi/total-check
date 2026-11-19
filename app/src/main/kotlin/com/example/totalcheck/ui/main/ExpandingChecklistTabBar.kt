package com.example.totalcheck.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.totalcheck.R
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ACTIVE_TAG_SCALE = 1.08f
private const val FLOATING_TAG_LIFT_SCALE = 1.06f

@Immutable
internal class ImmutableList<T>(val items: List<T>) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ImmutableList<*> && items == other.items)
    override fun hashCode(): Int = items.hashCode()
}

@Immutable
internal class ImmutableMap<K, V>(val map: Map<K, V>) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ImmutableMap<*, *> && map == other.map)
    override fun hashCode(): Int = map.hashCode()
}

@Stable
internal class DistantTabTransition(
    val fromPage: Int,
    val toPage: Int,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f),
    val isPreparing: MutableState<Boolean> = mutableStateOf(false)
)

@Stable
private class TabPressState(
    val pressAnim: Animatable<Float, AnimationVector1D> = Animatable(0f),
    val pressFractionState: MutableFloatState = mutableFloatStateOf(0f),
    var animJob: Job? = null
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
    var baseWidthsBuffer: FloatArray = FloatArray(16)
    var expandedWidthsBuffer: FloatArray = FloatArray(16)
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
            baseWidthsBuffer = FloatArray(nextSize)
            expandedWidthsBuffer = FloatArray(nextSize)
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
        if (!orderChanged && hadPreviousOrder && draggedId == null && suppressedReflowId == null) {
            if (reflowAnims.isNotEmpty()) {
                for (i in 0 until itemCount) {
                    reflowAnims[ids[i]]?.lastNaturalLeftPx = naturalLefts[i]
                }
            }
            return
        }
        if (orderChanged) {
            lastOrderedIds = ids.copyOf(itemCount)
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
    checklists: ImmutableList<ChecklistEntity>,
    itemsByChecklist: ImmutableMap<Long, List<CheckItemEntity>>,
    pagerState: PagerState,
    distantTabTransitionProvider: () -> DistantTabTransition?,
    activeChecklistIdProvider: () -> Long?,
    onSelectPage: (Int) -> Unit,
    onReorderChecklists: (List<Long>) -> Unit,
    onRenameChecklist: (Long, String) -> Unit = { _, _ -> },
    onInlineRenameActiveChanged: (Boolean) -> Unit = {},
    onMoveChecklistLeft: ((Long) -> Unit)? = null,
    onMoveChecklistRight: ((Long) -> Unit)? = null,
    onAddChecklist: () -> Unit,
    addButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val rawChecklists = checklists.items
    val rawItemsByChecklist = itemsByChecklist.map
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val isReducedMotion = LocalReducedMotion.current
    val currentDistantProvider by rememberUpdatedState(distantTabTransitionProvider)
    val currentActiveChecklistIdProvider by rememberUpdatedState(activeChecklistIdProvider)
    val currentOnSelectPage by rememberUpdatedState(onSelectPage)
    val currentOnReorderChecklists by rememberUpdatedState(onReorderChecklists)
    val currentOnRenameChecklist by rememberUpdatedState(onRenameChecklist)
    val currentOnInlineRenameActiveChanged by rememberUpdatedState(onInlineRenameActiveChanged)
    val currentOnMoveChecklistLeft by rememberUpdatedState(onMoveChecklistLeft)
    val currentOnMoveChecklistRight by rememberUpdatedState(onMoveChecklistRight)

    var editingChecklistId by remember { mutableStateOf<Long?>(null) }
    var editingTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val currentEditingChecklistIdState = rememberUpdatedState(editingChecklistId)
    val currentEditingTextValueState = rememberUpdatedState(editingTextFieldValue)

    LaunchedEffect(editingChecklistId) {
        currentOnInlineRenameActiveChanged(editingChecklistId != null)
    }

    val scrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val boundsHolder = remember { TagRowBoundsHolder() }
    val focusRequesters = remember { HashMap<Long, FocusRequester>() }
    val metricsHoldersById = remember { HashMap<Long, ExpandingTagMetricsHolder>() }
    val tabPressStates = remember { HashMap<Long, TabPressState>() }
    DisposableEffect(Unit) {
        onDispose {
            currentOnInlineRenameActiveChanged(false)
            tabPressStates.values.forEach { it.animJob?.cancel() }
            tabPressStates.clear()
        }
    }

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

    val currentChecklistsState = rememberUpdatedState(rawChecklists)
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

    LaunchedEffect(rawChecklists, committedOrderIds) {
        val committed = committedOrderIds ?: return@LaunchedEffect
        val incomingIds = rawChecklists.map { it.id }
        if (incomingIds == committed || incomingIds.toSet() != committed.toSet()) {
            committedOrderIds = null
        }
    }

    val displayedChecklists = remember(rawChecklists, draggedChecklistId, workingChecklists, committedOrderIds) {
        when {
            draggedChecklistId != null && workingChecklists.isNotEmpty() -> workingChecklists
            committedOrderIds != null -> {
                val order = committedOrderIds!!
                val byId = rawChecklists.associateBy { it.id }
                val ordered = order.mapNotNull { byId[it] }
                val orderSet = order.toSet()
                val extras = rawChecklists.filter { it.id !in orderSet }
                ordered + extras
            }
            else -> rawChecklists
        }
    }
    val currentDisplayedChecklistsState = rememberUpdatedState(displayedChecklists)

    val resolvedActiveChecklistIdProvider: () -> Long? = remember(pagerState) {
        {
            currentActiveChecklistIdProvider() ?: run {
                val list = currentChecklistsState.value
                val fallbackIdx = (currentDistantProvider()?.toPage ?: pagerState.targetPage)
                    .coerceIn(0, (list.size - 1).coerceAtLeast(0))
                list.getOrNull(fallbackIdx)?.id
            }
        }
    }

    val commitTabInlineRename: () -> Unit = {
        val targetId = currentEditingChecklistIdState.value
        if (targetId != null) {
            val trimmed = currentEditingTextValueState.value.text.trim()
            if (trimmed.isNotEmpty()) {
                currentOnRenameChecklist(targetId, trimmed)
            }
            editingChecklistId = null
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    val startTabInlineRename: (Long) -> Unit = { targetId ->
        val currentTarget = currentChecklistsState.value.firstOrNull { it.id == targetId }
        if (currentTarget != null) {
            val prevId = currentEditingChecklistIdState.value
            if (prevId != null && prevId != targetId) {
                val prevTrimmed = currentEditingTextValueState.value.text.trim()
                if (prevTrimmed.isNotEmpty()) {
                    currentOnRenameChecklist(prevId, prevTrimmed)
                }
            }
            editingTextFieldValue = TextFieldValue(
                text = currentTarget.name,
                selection = TextRange(0, currentTarget.name.length)
            )
            editingChecklistId = targetId
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    BackHandler(enabled = editingChecklistId != null) {
        commitTabInlineRename()
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress && currentEditingChecklistIdState.value != null) {
            commitTabInlineRename()
        }
    }
    val isOrderFrozenState = remember(pagerState) {
        derivedStateOf {
            if (draggedChecklistId != null || isSettlingDrop || committedOrderIds != null) {
                true
            } else {
                val list = currentChecklistsState.value
                val activeId = resolvedActiveChecklistIdProvider()
                val expectedIdx = list.indexOfFirst { it.id == activeId }
                val synced = expectedIdx < 0 ||
                    expectedIdx == pagerState.currentPage ||
                    expectedIdx == pagerState.targetPage ||
                    currentDistantProvider() != null
                !synced
            }
        }
    }

    val selectionFractionFor: (Int, Long) -> Float = remember(pagerState) {
        { index, checklistId ->
            if (isOrderFrozenState.value) {
                if (checklistId == resolvedActiveChecklistIdProvider()) 1f else 0f
            } else {
                val distant = currentDistantProvider()
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

    LaunchedEffect(isReducedMotion) {
        snapshotFlow {
            if (draggedChecklistId != null || isSettlingDrop) {
                null
            } else {
                resolvedActiveChecklistIdProvider()
            }
        }
            .distinctUntilChanged()
            .collectLatest { targetId ->
                if (targetId == null) return@collectLatest

                withFrameNanos { }
                if (draggedChecklistId != null || isSettlingDrop) return@collectLatest
                val targetIdx = currentDisplayedChecklistsState.value.indexOfFirst { it.id == targetId }
                if (viewportWidthPx <= 0 || targetIdx !in 0 until boundsHolder.count) return@collectLatest
                val edgePaddingPx = with(density) { 28.dp.toPx() }
                val currentWidth = (boundsHolder.visualRights[targetIdx] - boundsHolder.visualLefts[targetIdx])
                    .coerceAtLeast(1f)
                val targetScale = if (isReducedMotion) 1f else ACTIVE_TAG_SCALE
                val exactExpandedW = boundsHolder.expandedWidthsBuffer.getOrElse(targetIdx) { currentWidth } * targetScale
                val estimatedMaxRight = boundsHolder.visualLefts[targetIdx] + maxOf(currentWidth, exactExpandedW)
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
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
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
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        if (scrollState.value < scrollState.maxValue) {
                            val fadeWidthPx = 32.dp.toPx().coerceAtMost(size.width)
                            if (fadeWidthPx > 0f) {
                                val fadeStartX = size.width - fadeWidthPx
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startX = fadeStartX,
                                        endX = size.width
                                    ),
                                    topLeft = Offset(fadeStartX, 0f),
                                    size = Size(fadeWidthPx, size.height),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                    }
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Spacer(modifier = Modifier.width(12.dp))

            val startTabDrag: (Long) -> Unit = { checklistId ->
                if (
                    currentChecklistsState.value.size > 1 &&
                    draggedChecklistId == null &&
                    !isSettlingDrop
                ) {
                    val slotIdx = currentDisplayedChecklistsState.value.indexOfFirst {
                        it.id == checklistId
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
                        draggedChecklistId = checklistId
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
            val cancelRadiusPx = with(density) { SLIDE_OUT_CANCEL_DISTANCE.toPx() }

            Layout(
                modifier = Modifier.pointerInput(isReducedMotion) {
                    var lastSelectedTapId = -1L
                    var lastSelectedTapUpTimeMs = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        if (draggedChecklistId != null || isSettlingDrop) return@awaitEachGesture
                        val trackedCount = boundsHolder.count
                        var hitId = -1L
                        var hitLeft = 0f
                        var hitRight = 0f
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        if (down.position.y in 0f..h) {
                            for (i in 0 until trackedCount) {
                                val id = boundsHolder.idsBuffer[i]
                                if (id <= 0L) continue
                                val reflowX = boundsHolder.getReflowOffset(id)
                                val vLeft = boundsHolder.visualLefts[i] + reflowX
                                val vRight = boundsHolder.visualRights[i] + reflowX
                                if (down.position.x in vLeft..vRight) {
                                    hitId = id
                                    hitLeft = vLeft
                                    hitRight = vRight
                                    break
                                }
                            }
                        }
                        val activeEditId = currentEditingChecklistIdState.value
                        if (activeEditId != null) {
                            if (hitId == activeEditId) {
                                // 讓行內編輯器之 BasicTextField 與確認按鈕直接接收觸控
                                return@awaitEachGesture
                            } else {
                                commitTabInlineRename()
                            }
                        }
                        if (hitId <= 0L || hitId == draggedChecklistId) return@awaitEachGesture

                        val wasAlreadySelectedOnDown = hitId == resolvedActiveChecklistIdProvider()
                        val pressState = tabPressStates.getOrPut(hitId) { TabPressState() }
                        val downTimeMs = down.uptimeMillis
                        var longPressFired = false
                        var isInsideBounds = true
                        var gestureCancelled = false
                        var isGestureLocked = false
                        var exceededHorizontalSlopEarly = false

                        val animateToRest: () -> Unit = {
                            pressState.animJob?.cancel()
                            if (isReducedMotion) {
                                pressState.pressFractionState.floatValue = 0f
                                pressState.animJob = scope.launch { pressState.pressAnim.snapTo(0f) }
                            } else {
                                pressState.animJob = scope.launch {
                                    pressState.pressAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.82f,
                                            stiffness = 620f,
                                            visibilityThreshold = 0.002f
                                        )
                                    ) {
                                        pressState.pressFractionState.floatValue = value
                                    }
                                }
                            }
                        }

                        pressState.animJob?.cancel()
                        if (isReducedMotion) {
                            pressState.pressFractionState.floatValue = 1f
                            pressState.animJob = scope.launch { pressState.pressAnim.snapTo(1f) }
                        } else {
                            pressState.animJob = scope.launch {
                                pressState.pressAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = 920f,
                                        visibilityThreshold = 0.002f
                                    )
                                ) {
                                    pressState.pressFractionState.floatValue = value
                                }
                            }
                        }

                        var longPressJob: Job? = null
                        if (currentChecklistsState.value.size > 1) {
                            longPressJob = scope.launch {
                                delay(viewConfig.longPressTimeoutMillis)
                                if (isInsideBounds && !gestureCancelled) {
                                    longPressFired = true
                                    animateToRest()
                                    startTabDrag(hitId)
                                }
                            }
                        }

                        try {
                            while (true) {
                                val mainEvent = awaitPointerEvent(pass = PointerEventPass.Main)
                                val mainChange = mainEvent.changes.firstOrNull { it.id == down.id }
                                if (mainChange == null || mainChange.isConsumed) {
                                    gestureCancelled = true
                                    longPressJob?.cancel()
                                    animateToRest()
                                    break
                                }

                                var consumedBySelf = false
                                if (!isGestureLocked && !exceededHorizontalSlopEarly) {
                                    val heldMs = mainChange.uptimeMillis - downTimeMs
                                    val movedX = abs(mainChange.position.x - down.position.x)
                                    if (heldMs >= HOLD_LOCK_DELAY_MS) {
                                        isGestureLocked = true
                                    } else if (movedX > viewConfig.touchSlop) {
                                        exceededHorizontalSlopEarly = true
                                    }
                                }
                                if (isGestureLocked) {
                                    mainChange.consume()
                                    consumedBySelf = true
                                }

                                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || (!consumedBySelf && change.isConsumed)) {
                                    gestureCancelled = true
                                    longPressJob?.cancel()
                                    animateToRest()
                                    break
                                }

                                val currentCountNow = boundsHolder.count
                                var curLeft = hitLeft
                                var curRight = hitRight
                                for (i in 0 until currentCountNow) {
                                    if (boundsHolder.idsBuffer[i] == hitId) {
                                        val reflowX = boundsHolder.getReflowOffset(hitId)
                                        curLeft = boundsHolder.visualLefts[i] + reflowX
                                        curRight = boundsHolder.visualRights[i] + reflowX
                                        break
                                    }
                                }
                                val w = (curRight - curLeft).coerceAtLeast(1f)
                                val localPosX = change.position.x - curLeft
                                val localPosY = change.position.y
                                isInsideBounds = localPosX in 0f..w && localPosY in 0f..h

                                if (!change.pressed) {
                                    longPressJob?.cancel()
                                    animateToRest()
                                    if (!longPressFired && isInsideBounds) {
                                        val nowMs = change.uptimeMillis
                                        if (
                                            wasAlreadySelectedOnDown &&
                                            lastSelectedTapId == hitId &&
                                            (nowMs - lastSelectedTapUpTimeMs) in 1L..360L
                                        ) {
                                            lastSelectedTapId = -1L
                                            lastSelectedTapUpTimeMs = 0L
                                            startTabInlineRename(hitId)
                                        } else {
                                            if (wasAlreadySelectedOnDown) {
                                                lastSelectedTapId = hitId
                                                lastSelectedTapUpTimeMs = nowMs
                                            } else {
                                                lastSelectedTapId = -1L
                                                lastSelectedTapUpTimeMs = 0L
                                            }
                                            val realIdx = currentChecklistsState.value.indexOfFirst { it.id == hitId }
                                            if (realIdx >= 0) {
                                                currentOnSelectPage(realIdx)
                                            }
                                        }
                                    }
                                    break
                                }

                                val targetStrength = computeSlideOutPressStrength(
                                    posX = localPosX,
                                    posY = localPosY,
                                    widthPx = w,
                                    heightPx = h,
                                    cancelRadiusPx = cancelRadiusPx
                                )
                                if (isReducedMotion) {
                                    val snapTarget = if (isInsideBounds) 1f else 0f
                                    if (pressState.pressFractionState.floatValue != snapTarget) {
                                        pressState.pressFractionState.floatValue = snapTarget
                                        pressState.animJob?.cancel()
                                        pressState.animJob = scope.launch { pressState.pressAnim.snapTo(snapTarget) }
                                    }
                                } else if (!isInsideBounds) {
                                    pressState.animJob?.cancel()
                                    pressState.pressFractionState.floatValue = targetStrength
                                    pressState.animJob = scope.launch { pressState.pressAnim.snapTo(targetStrength) }
                                } else if (pressState.pressAnim.targetValue < 0.999f) {
                                    pressState.animJob?.cancel()
                                    pressState.animJob = scope.launch {
                                        pressState.pressAnim.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = 0.85f,
                                                stiffness = 880f,
                                                visibilityThreshold = 0.002f
                                            )
                                        ) {
                                            pressState.pressFractionState.floatValue = value
                                        }
                                    }
                                }
                            }
                        } finally {
                            longPressJob?.cancel()
                        }
                    }
                },
                content = {
                    displayedChecklists.forEachIndexed { index, checklist ->
                        key(checklist.id) {
                            val items = rawItemsByChecklist[checklist.id].orEmpty()
                            val totalCount = items.size
                            val uncheckedCount = items.count { !it.isChecked }
                            val isDragged = draggedChecklistId == checklist.id

                            if (isDragged) {
                                val slotWidthDp = with(density) { draggedVisualWidthPx.toDp() }
                                    .coerceAtLeast(48.dp)
                                val slotHeightDp = with(density) { draggedUnscaledHeightPx.toDp() }
                                    .coerceAtLeast(40.dp)
                                val slotScaleY = if (
                                    checklist.id == resolvedActiveChecklistIdProvider() &&
                                    !isReducedMotion
                                ) {
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
                                val requester = remember(checklist.id) {
                                    focusRequesters.getOrPut(checklist.id) { FocusRequester() }
                                }
                                val tagMetricsHolder = remember(checklist.id) {
                                    metricsHoldersById.getOrPut(checklist.id) { ExpandingTagMetricsHolder() }
                                }
                                if (editingChecklistId == checklist.id) {
                                    InlineEditingChecklistTag(
                                        checklist = checklist,
                                        value = editingTextFieldValue,
                                        onValueChange = { nextVal ->
                                            if (nextVal.text.length <= 40) {
                                                editingTextFieldValue = nextVal
                                            }
                                        },
                                        metricsHolder = tagMetricsHolder,
                                        onConfirm = commitTabInlineRename
                                    )
                                } else {
                                    val tagPressState = remember(checklist.id) {
                                        tabPressStates.getOrPut(checklist.id) { TabPressState() }
                                    }
                                    val currentIndexState = rememberUpdatedState(index)
                                    val totalListSizeState = rememberUpdatedState(displayedChecklists.size)
                                    val canReorder = displayedChecklists.size > 1
                                    val tabSelectionFractionProvider = remember(checklist.id, selectionFractionFor) {
                                        { selectionFractionFor(currentIndexState.value, checklist.id) }
                                    }
                                    val tabIsSelectedProvider = remember(checklist.id, resolvedActiveChecklistIdProvider) {
                                        { checklist.id == resolvedActiveChecklistIdProvider() }
                                    }
                                    val onSelectTab = remember(checklist.id) {
                                        {
                                            val realIdx = currentChecklistsState.value.indexOfFirst {
                                                it.id == checklist.id
                                            }
                                            if (realIdx >= 0) {
                                                currentOnSelectPage(realIdx)
                                            }
                                        }
                                    }
                                    val hasMoveLeft = onMoveChecklistLeft != null
                                    val hasMoveRight = onMoveChecklistRight != null
                                    val onMoveLeftTab = remember(checklist.id, hasMoveLeft) {
                                        if (hasMoveLeft) {
                                            { currentOnMoveChecklistLeft?.invoke(checklist.id); Unit }
                                        } else {
                                            null
                                        }
                                    }
                                    val onMoveRightTab = remember(checklist.id, hasMoveRight) {
                                        if (hasMoveRight) {
                                            { currentOnMoveChecklistRight?.invoke(checklist.id); Unit }
                                        } else {
                                            null
                                        }
                                    }
                                    val onMoveFocusLeftTab = remember(checklist.id) {
                                        {
                                            val prevIdx = currentIndexState.value - 1
                                            if (prevIdx >= 0) {
                                                currentOnSelectPage(prevIdx)
                                                currentDisplayedChecklistsState.value.getOrNull(prevIdx)?.let { prevList ->
                                                    focusRequesters[prevList.id]?.requestFocus()
                                                }
                                            }
                                        }
                                    }
                                    val onMoveFocusRightTab = remember(checklist.id) {
                                        {
                                            val nextIdx = currentIndexState.value + 1
                                            if (nextIdx < totalListSizeState.value) {
                                                currentOnSelectPage(nextIdx)
                                                currentDisplayedChecklistsState.value.getOrNull(nextIdx)?.let { nextList ->
                                                    focusRequesters[nextList.id]?.requestFocus()
                                                }
                                            }
                                        }
                                    }

                                    ExpandingChecklistTag(
                                        checklist = checklist,
                                        totalCount = totalCount,
                                        uncheckedCount = uncheckedCount,
                                        metricsHolder = tagMetricsHolder,
                                        pressState = tagPressState,
                                        isSelectedProvider = tabIsSelectedProvider,
                                        canReorder = canReorder,
                                        canMoveLeft = index > 0,
                                        canMoveRight = index < displayedChecklists.size - 1,
                                        selectionFractionProvider = tabSelectionFractionProvider,
                                        focusRequester = requester,
                                        onSelect = onSelectTab,
                                        onStartInlineRename = { startTabInlineRename(checklist.id) },
                                        onMoveLeftAction = onMoveLeftTab,
                                        onMoveRightAction = onMoveRightTab,
                                        onMoveFocusLeft = onMoveFocusLeftTab,
                                        onMoveFocusRight = onMoveFocusRightTab
                                    )
                                }
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
                val baseWidths = boundsHolder.baseWidthsBuffer
                val expandedWidths = boundsHolder.expandedWidthsBuffer
                val fractions = boundsHolder.fractionsBuffer
                val scales = boundsHolder.scalesBuffer
                val unscaledLefts = boundsHolder.unscaledLeftsBuffer
                val unboundedConstraints = Constraints()

                var maxTagHeight = 0
                var baseSumWidth = 0f
                var maxExpandExtra = 0f
                val maxScaleForTotal = if (isReducedMotion) 1f else ACTIVE_TAG_SCALE

                for (i in 0 until count) {
                    val p = measurables[i].measure(unboundedConstraints)
                    placeables[i] = p
                    val item = displayedChecklists.getOrNull(i)
                    val id = item?.id ?: -1L
                    ids[i] = id
                    if (p.height > maxTagHeight) {
                        maxTagHeight = p.height
                    }
                    val isSlot = id == draggedChecklistId
                    val m = if (isSlot) null else metricsHoldersById[id]
                    val baseW = m?.baseWidthPx?.takeIf { it > 0f } ?: p.width.toFloat()
                    val expW = m?.expandedWidthPx?.takeIf { it > 0f } ?: p.width.toFloat()
                    baseWidths[i] = baseW
                    expandedWidths[i] = expW
                    baseSumWidth += baseW
                    val extra = if (isSlot) {
                        0f
                    } else {
                        (expW * maxScaleForTotal - baseW) + extraNeighborMarginPx * 2f
                    }
                    if (extra > maxExpandExtra) {
                        maxExpandExtra = extra
                    }
                }
                boundsHolder.maxTagHeightPx = maxTagHeight
                val trackedCount = minOf(count, boundsHolder.visualLefts.size)
                boundsHolder.count = trackedCount

                // 靜態測量總寬：不讀取 selectionFractionFor，故滑動/320ms 遠距過渡期間父層與子標籤均為 0 Measure
                val staticTotalWidthFloat = rowHorizontalEdgePadPx * 2f +
                    baseSumWidth +
                    maxExpandExtra +
                    baseGapPx * (count - 1).coerceAtLeast(0)
                val totalWidth = staticTotalWidthFloat.roundToInt().coerceAtLeast(0)
                val totalHeight = (maxTagHeight + verticalScalePadPx * 2).coerceAtLeast(0)

                layout(totalWidth, totalHeight) {
                    boundsHolder.reflowFrameNanos.longValue
                    var xCursor = rowHorizontalEdgePadPx

                    for (i in 0 until count) {
                        val p = placeables[i] ?: continue
                        val id = ids[i]
                        val t = selectionFractionFor(i, id).coerceIn(0f, 1f)
                        fractions[i] = t
                        val isSlot = id == draggedChecklistId
                        val scale = when {
                            isSlot || isReducedMotion -> 1f
                            else -> lerp(1f, ACTIVE_TAG_SCALE, t)
                        }
                        scales[i] = scale

                        val currentPillW = if (isSlot) {
                            p.width.toFloat()
                        } else {
                            lerp(baseWidths[i], expandedWidths[i], t)
                        }
                        val visualW = currentPillW * scale
                        val extraMargin = extraNeighborMarginPx * t

                        val visualLeft = xCursor + extraMargin
                        val visualRight = visualLeft + visualW
                        if (i < trackedCount) {
                            boundsHolder.visualLefts[i] = visualLeft
                            boundsHolder.visualRights[i] = visualRight
                            boundsHolder.visualHeights[i] = p.height * scale
                            boundsHolder.unscaledHeights[i] = p.height.toFloat()
                        }

                        val placeLeft = visualLeft + (visualW - currentPillW) * 0.5f
                        unscaledLefts[i] = placeLeft
                        if (i < trackedCount) {
                            boundsHolder.layoutUnscaledLefts[i] = placeLeft
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

                    for (i in 0 until count) {
                        val p = placeables[i] ?: continue
                        val id = ids[i]
                        val scale = scales[i]
                        val t = fractions[i]
                        val isSlot = id == draggedChecklistId
                        val currentPillW = if (isSlot) {
                            p.width.toFloat()
                        } else {
                            lerp(baseWidths[i], expandedWidths[i], t)
                        }
                        val pw = p.width.toFloat().coerceAtLeast(1f)
                        val pivotX = ((currentPillW * 0.5f) / pw).coerceIn(0f, 1f)
                        val reflowOffsetX = boundsHolder.getReflowOffset(id)
                        val yPos = verticalScalePadPx + (maxTagHeight - p.height) / 2
                        p.placeRelativeWithLayer(
                            x = (unscaledLefts[i] + reflowOffsetX).roundToInt(),
                            y = yPos,
                            zIndex = if (isSlot) 0f else 1f + t
                        ) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(pivotX, 0.5f)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
        }

        val floatingChecklist = draggedChecklistId?.let { id ->
            displayedChecklists.firstOrNull { it.id == id }
        }
        if (floatingChecklist != null) {
            val floatingItems = rawItemsByChecklist[floatingChecklist.id].orEmpty()
            val floatingSelected = floatingChecklist.id == resolvedActiveChecklistIdProvider()
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

        Spacer(modifier = Modifier.width(4.dp))

        TactileIconButton(
            onClick = onAddChecklist,
            modifier = Modifier
                .padding(end = 12.dp)
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

private fun buildStableCountTemplate(totalCount: Int): String {
    if (totalCount <= 0) return "(0)"
    val digits = totalCount.toString().length.coerceAtLeast(1)
    val maxDigits = "8".repeat(digits)
    return "($maxDigits/$maxDigits)"
}

private class StableCountWidthHolder(
    var bucketDigits: Int = -1,
    var lockedWidthPx: Float = 0f
)

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
    val countStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum"
    )
    val countText = if (totalCount == 0) "(0)" else "($uncheckedCount/$totalCount)"
    val referenceCountText = buildStableCountTemplate(totalCount)
    val countBucketDigits = if (totalCount <= 0) 0 else totalCount.toString().length
    val stableCountWidthHolder = remember(checklist.id) { StableCountWidthHolder() }

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
            BasicText(
                text = referenceCountText,
                style = countStyle,
                color = { Color.Transparent },
                maxLines = 1
            )
        }
    ) { tagMeasurables, _ ->
        val titlePlaceable = tagMeasurables[0].measure(titleConstraints)
        val countPlaceable = tagMeasurables[1].measure(countConstraints)
        val templatePlaceable = tagMeasurables[2].measure(countConstraints)
        val rawStableWidth = maxOf(countPlaceable.width, templatePlaceable.width).toFloat()
        val stableCountWidth = if (
            stableCountWidthHolder.bucketDigits == countBucketDigits &&
            abs(rawStableWidth - stableCountWidthHolder.lockedWidthPx) <= 2f
        ) {
            stableCountWidthHolder.lockedWidthPx
        } else {
            stableCountWidthHolder.bucketDigits = countBucketDigits
            stableCountWidthHolder.lockedWidthPx = rawStableWidth
            rawStableWidth
        }

        val countRevealWidth = if (isSelected) countGapPx + stableCountWidth else 0f
        val contentWidth = padHPx * 2f + titlePlaceable.width + countRevealWidth
        val contentHeight = padVPx * 2f + maxOf(titlePlaceable.height, countPlaceable.height)
        val pillWidth = contentWidth.roundToInt().coerceAtLeast(0)
        val pillHeight = contentHeight.roundToInt().coerceAtLeast(minPillHeightPx)

        layout(pillWidth, pillHeight) {
            val titleY = (pillHeight - titlePlaceable.height) / 2
            titlePlaceable.placeRelative(padHPx.roundToInt(), titleY)
            if (isSelected && countPlaceable.width > 0) {
                val centerOffset = ((stableCountWidth - countPlaceable.width) * 0.5f).coerceAtLeast(0f)
                val countX = (padHPx + titlePlaceable.width + countGapPx + centerOffset).roundToInt()
                val countY = (pillHeight - countPlaceable.height) / 2
                countPlaceable.placeRelative(countX, countY)
            }
        }
    }
}

@Stable
private class ExpandingTagMetricsHolder(
    var baseWidthPx: Float = 0f,
    var expandedWidthPx: Float = 0f,
    var pillHeightPx: Int = 0
)

@Composable
private fun StaticExpandingTagTexts(
    title: String,
    countText: String,
    referenceCountText: String,
    titleStyle: androidx.compose.ui.text.TextStyle,
    countStyle: androidx.compose.ui.text.TextStyle,
    unselectedLabelColor: Color,
    selectedLabelColor: Color,
    selectionFractionProvider: () -> Float,
    stableCountWidthHolder: StableCountWidthHolder,
    countBucketDigits: Int,
    basePadHPx: Float,
    expandedPadHPx: Float,
    padVPx: Float,
    minPillHeightPx: Int,
    countGapFullPx: Float,
    titleConstraints: Constraints,
    countConstraints: Constraints,
    metricsHolder: ExpandingTagMetricsHolder
) {
    Layout(
        content = {
            BasicText(
                text = title,
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

            BasicText(
                text = referenceCountText,
                style = countStyle,
                color = { Color.Transparent },
                maxLines = 1
            )
        }
    ) { tagMeasurables, _ ->
        // 三個 BasicText 僅於文字/計數變更時測量一次並快取，滑動/切換頁籤期間 0 文字重測
        val titlePlaceable = tagMeasurables[0].measure(titleConstraints)
        val countPlaceable = tagMeasurables[1].measure(countConstraints)
        val templatePlaceable = tagMeasurables[2].measure(countConstraints)
        val rawStableWidth = maxOf(countPlaceable.width, templatePlaceable.width).toFloat()
        val stableCountWidth = if (
            stableCountWidthHolder.bucketDigits == countBucketDigits &&
            abs(rawStableWidth - stableCountWidthHolder.lockedWidthPx) <= 2f
        ) {
            stableCountWidthHolder.lockedWidthPx
        } else {
            stableCountWidthHolder.bucketDigits = countBucketDigits
            stableCountWidthHolder.lockedWidthPx = rawStableWidth
            rawStableWidth
        }

        val baseContentWidth = basePadHPx * 2f + titlePlaceable.width
        val expandedContentWidth = expandedPadHPx * 2f + titlePlaceable.width + countGapFullPx + stableCountWidth
        val contentHeight = padVPx * 2f + maxOf(titlePlaceable.height, countPlaceable.height)
        val pillHeight = contentHeight.roundToInt().coerceAtLeast(minPillHeightPx)

        metricsHolder.baseWidthPx = baseContentWidth
        metricsHolder.expandedWidthPx = expandedContentWidth
        metricsHolder.pillHeightPx = pillHeight

        layout(expandedContentWidth.roundToInt().coerceAtLeast(0), pillHeight) {
            val t = selectionFractionProvider().coerceIn(0f, 1f)
            val padHPx = lerp(basePadHPx, expandedPadHPx, t)
            val titleY = (pillHeight - titlePlaceable.height) / 2
            val titleX = padHPx.roundToInt()
            titlePlaceable.placeRelative(titleX, titleY)

            if (t > 0.05f && countPlaceable.width > 0) {
                val slotLeft = padHPx + titlePlaceable.width + countGapFullPx * t
                val centerOffset = ((stableCountWidth - countPlaceable.width) * 0.5f).coerceAtLeast(0f)
                val countX = (slotLeft + centerOffset).roundToInt()
                val countY = (pillHeight - countPlaceable.height) / 2
                val revealAlpha = ((t - 0.16f) / 0.72f).coerceIn(0f, 1f)
                val revealScale = lerp(0.84f, 1f, t)
                countPlaceable.placeRelativeWithLayer(countX, countY) {
                    alpha = revealAlpha
                    scaleX = revealScale
                    scaleY = revealScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
            }
        }
    }
}

@Composable
private fun InlineEditingChecklistTag(
    checklist: ChecklistEntity,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    metricsHolder: ExpandingTagMetricsHolder,
    onConfirm: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val inputFocusRequester = remember(checklist.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Bold,
        color = colorScheme.onPrimaryContainer
    )

    LaunchedEffect(checklist.id) {
        withFrameNanos { }
        inputFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Layout(
        modifier = Modifier.testTag("checklist_tab_editing_${checklist.id}"),
        content = {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colorScheme.primaryContainer)
                    .border(2.dp, colorScheme.primary, RoundedCornerShape(50))
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = titleStyle,
                    cursorBrush = SolidColor(colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    modifier = Modifier
                        .widthIn(min = 56.dp, max = 160.dp)
                        .focusRequester(inputFocusRequester)
                        .testTag("checklist_tab_inline_input_${checklist.id}")
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colorScheme.primary)
                        .clickable(onClick = onConfirm)
                        .testTag("checklist_tab_inline_confirm_${checklist.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.cd_save_name),
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val p = measurables[0].measure(constraints)
        metricsHolder.baseWidthPx = p.width.toFloat()
        metricsHolder.expandedWidthPx = p.width.toFloat()
        metricsHolder.pillHeightPx = p.height
        layout(p.width, p.height) {
            p.placeRelative(0, 0)
        }
    }
}

@Composable
private fun ExpandingChecklistTag(
    checklist: ChecklistEntity,
    totalCount: Int,
    uncheckedCount: Int,
    metricsHolder: ExpandingTagMetricsHolder,
    pressState: TabPressState,
    isSelectedProvider: () -> Boolean,
    canReorder: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    selectionFractionProvider: () -> Float,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
    onStartInlineRename: () -> Unit,
    onMoveLeftAction: (() -> Unit)?,
    onMoveRightAction: (() -> Unit)?,
    onMoveFocusLeft: () -> Unit,
    onMoveFocusRight: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val isReducedMotion = LocalReducedMotion.current
    val keyboardFocusController = LocalKeyboardFocusVisible.current
    val hoverInteractionSource = remember(checklist.id) {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val isHovered by hoverInteractionSource.collectIsHoveredAsState()
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
    val countStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum"
    )
    val countText = if (totalCount == 0) "(0)" else "($uncheckedCount/$totalCount)"
    val referenceCountText = buildStableCountTemplate(totalCount)
    val countBucketDigits = if (totalCount <= 0) 0 else totalCount.toString().length
    val stableCountWidthHolder = remember(checklist.id) { StableCountWidthHolder() }

    val tabSelectionState = remember(checklist.id, selectionFractionProvider) {
        derivedStateOf { selectionFractionProvider().coerceIn(0f, 1f) }
    }
    val fastSelectionProvider: () -> Float = remember(tabSelectionState) {
        { tabSelectionState.value }
    }

    val a11yDescription = if (totalCount == 0) {
        stringResource(R.string.a11y_tab_empty, checklist.name)
    } else {
        stringResource(R.string.a11y_tab_counts, checklist.name, uncheckedCount, totalCount)
    }
    val moveLeftLabel = stringResource(R.string.a11y_move_left)
    val moveRightLabel = stringResource(R.string.a11y_move_right)
    val inlineRenameLabel = stringResource(R.string.a11y_double_tap_rename_checklist)

    val basePadHPx = with(density) { 14.dp.toPx() }
    val expandedPadHPx = with(density) { 18.dp.toPx() }
    val padVPx = with(density) { 9.dp.toPx() }
    val minPillHeightPx = with(density) { 40.dp.roundToPx() }
    val countGapFullPx = with(density) { 6.dp.toPx() }
    val maxTitleWidthPx = with(density) { 168.dp.roundToPx() }
    val sinkTranslationPx = with(density) { PRESS_SINK_TRANSLATION_Y.toPx() }
    val titleConstraints = remember(maxTitleWidthPx) { Constraints(maxWidth = maxTitleWidthPx) }
    val countConstraints = remember { Constraints() }
    val unboundedConstraints = remember { Constraints() }

    Layout(
        modifier = Modifier
            .testTag("checklist_tab_${checklist.id}")
            .graphicsLayer {
                val p = pressState.pressAnim.value.coerceIn(0f, 1f)
                if (!isReducedMotion && p > 0.001f) {
                    val t = fastSelectionProvider()
                    val currentPillW = lerp(metricsHolder.baseWidthPx, metricsHolder.expandedWidthPx, t)
                    val w = size.width.coerceAtLeast(1f)
                    val sc = lerp(1f, PRESS_SINK_SCALE, p)
                    scaleX = sc
                    scaleY = sc
                    translationY = lerp(0f, sinkTranslationPx, p)
                    transformOrigin = TransformOrigin(((currentPillW * 0.5f) / w).coerceIn(0f, 1f), 0.5f)
                }
            }
            .hoverable(interactionSource = hoverInteractionSource)
            .drawBehind {
                val t = fastSelectionProvider()
                val p = pressState.pressFractionState.floatValue.coerceIn(0f, 1f)
                val currentPillW = lerp(metricsHolder.baseWidthPx, metricsHolder.expandedWidthPx, t)
                    .coerceIn(1f, size.width.coerceAtLeast(1f))
                val pillSize = Size(currentPillW, size.height)
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                val baseBg = if (isHovered) hoveredContainerColor else unselectedContainerColor
                drawRoundRect(
                    color = lerpColor(baseBg, selectedContainerColor, t),
                    size = pillSize,
                    cornerRadius = corner
                )
                if (p > 0.001f) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = maxDeepenAlpha * p),
                        size = pillSize,
                        cornerRadius = corner
                    )
                }
                val borderStrokePx = lerp(1.dp.toPx(), 1.5.dp.toPx(), t)
                drawRoundRect(
                    color = lerpColor(unselectedBorderColor, selectedBorderColor, t),
                    size = pillSize,
                    cornerRadius = corner,
                    style = Stroke(width = borderStrokePx)
                )
                if (isFocused && keyboardFocusController.isKeyboardFocusVisible) {
                    val focusInset = 2.dp.toPx()
                    drawRoundRect(
                        color = focusRingColor.copy(alpha = 0.85f),
                        topLeft = Offset(focusInset, focusInset),
                        size = Size(
                            width = (currentPillW - focusInset * 2f).coerceAtLeast(1f),
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
                val isSelectedNow = isSelectedProvider()
                role = Role.Tab
                selected = isSelectedNow
                contentDescription = a11yDescription
                onClick {
                    onSelect()
                    true
                }
                val actions = buildList {
                    if (isSelectedNow) {
                        add(CustomAccessibilityAction(inlineRenameLabel) { onStartInlineRename(); true })
                    }
                    if (canReorder) {
                        if (canMoveLeft && onMoveLeftAction != null) {
                            add(CustomAccessibilityAction(moveLeftLabel) { onMoveLeftAction(); true })
                        }
                        if (canMoveRight && onMoveRightAction != null) {
                            add(CustomAccessibilityAction(moveRightLabel) { onMoveRightAction(); true })
                        }
                    }
                }
                if (actions.isNotEmpty()) {
                    customActions = actions
                }
            },
        content = {
            StaticExpandingTagTexts(
                title = checklist.name,
                countText = countText,
                referenceCountText = referenceCountText,
                titleStyle = titleStyle,
                countStyle = countStyle,
                unselectedLabelColor = unselectedLabelColor,
                selectedLabelColor = selectedLabelColor,
                selectionFractionProvider = fastSelectionProvider,
                stableCountWidthHolder = stableCountWidthHolder,
                countBucketDigits = countBucketDigits,
                basePadHPx = basePadHPx,
                expandedPadHPx = expandedPadHPx,
                padVPx = padVPx,
                minPillHeightPx = minPillHeightPx,
                countGapFullPx = countGapFullPx,
                titleConstraints = titleConstraints,
                countConstraints = countConstraints,
                metricsHolder = metricsHolder
            )
        }
    ) { measurables, _ ->
        val contentPlaceable = measurables[0].measure(unboundedConstraints)
        val pillWidth = metricsHolder.expandedWidthPx
            .roundToInt()
            .coerceAtLeast(0)
        val pillHeight = metricsHolder.pillHeightPx.coerceAtLeast(contentPlaceable.height)

        layout(pillWidth, pillHeight) {
            contentPlaceable.placeRelative(0, 0)
        }
    }
}
