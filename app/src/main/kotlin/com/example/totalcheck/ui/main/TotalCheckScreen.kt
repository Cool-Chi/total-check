package com.example.totalcheck.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TotalCheckScreen(
    viewModel: MainViewModel,
    onToggleTheme: (originInRoot: Offset) -> Unit,
    onPreCaptureTheme: () -> Unit,
    onCancelPreCaptureTheme: () -> Unit,
    isThemeTransitioning: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val isReducedMotionGlobal = LocalReducedMotion.current

    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    val itemsByChecklist by viewModel.itemsByChecklist.collectAsStateWithLifecycle()
    val pendingScrollToChecklistId by viewModel.pendingScrollToChecklistId.collectAsStateWithLifecycle()
    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
    val isDarkMode = themeConfig?.isDarkMode ?: true
    val useDynamicColor = themeConfig?.useDynamicColor ?: false
    val showUncheckedOnly by viewModel.showUncheckedOnly.collectAsStateWithLifecycle()
    val isStartupLoading by viewModel.isStartupLoading.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var actionTargetChecklist by remember { mutableStateOf<ChecklistEntity?>(null) }
    var editingItemId by remember { mutableStateOf<Long?>(null) }
    var previousEditingItemId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(editingItemId) {
        if (previousEditingItemId != null && editingItemId == null) {
            focusManager.clearFocus()
        }
        previousEditingItemId = editingItemId
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val submitHolder = remember {
        object {
            var onSubmit: (() -> Unit)? = null
        }
    }

    BackHandler(enabled = editingItemId != null) {
        editingItemId = null
        focusManager.clearFocus()
    }

    val imeInsets = WindowInsets.ime
    val navBarInsets = WindowInsets.navigationBars
    val imeCollapseSpanPx = with(density) { 180.dp.toPx() }.coerceAtLeast(1f)

    // 僅於進入/離開單項編輯時以彈簧驅動進度卡收摺；一般鍵盤升降直接於 Placement 階段 1:1 讀取 imeInsets，達成 0 重組
    val shouldForceCollapseProgress = editingItemId != null
    val progressCollapseAnim = remember { Animatable(if (shouldForceCollapseProgress) 1f else 0f) }
    LaunchedEffect(shouldForceCollapseProgress, isReducedMotionGlobal) {
        val target = if (shouldForceCollapseProgress) 1f else 0f
        if (isReducedMotionGlobal) {
            progressCollapseAnim.snapTo(target)
        } else {
            progressCollapseAnim.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f, visibilityThreshold = 0.002f)
            )
        }
    }

    val progressCollapseFractionProvider: () -> Float = {
        val imePx = imeInsets.getBottom(density).toFloat()
        val navPx = navBarInsets.getBottom(density).toFloat()
        val imeFraction = ((imePx - navPx) / imeCollapseSpanPx).coerceIn(0f, 1f)
        maxOf(progressCollapseAnim.value.coerceIn(0f, 1f), imeFraction)
    }

    val shouldHideQuickInput = editingItemId != null
    val quickInputHideAnim = remember { Animatable(if (shouldHideQuickInput) 1f else 0f) }
    LaunchedEffect(shouldHideQuickInput, isReducedMotionGlobal) {
        val target = if (shouldHideQuickInput) 1f else 0f
        if (isReducedMotionGlobal) {
            quickInputHideAnim.snapTo(target)
        } else {
            quickInputHideAnim.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f, visibilityThreshold = 0.002f)
            )
        }
    }

    val quickBarHeightHolder = remember { FloatValueHolder(0f) }
    val bottomOverlayInsetPxProvider: () -> Float = {
        val imePx = imeInsets.getBottom(density).toFloat()
        val navPx = navBarInsets.getBottom(density).toFloat()
        val bodyHeightPx = (quickBarHeightHolder.value - navPx).coerceAtLeast(0f)
        val visibleQuickBarPx = bodyHeightPx * (1f - quickInputHideAnim.value.coerceIn(0f, 1f))
        maxOf(imePx, navPx) + visibleQuickBarPx
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        )
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TopAppHeaderBar(
                isDarkTheme = isDarkMode,
                useDynamicColor = useDynamicColor,
                isThemeTransitioning = isThemeTransitioning,
                onToggleTheme = onToggleTheme,
                onPreCaptureTheme = onPreCaptureTheme,
                onCancelPreCaptureTheme = onCancelPreCaptureTheme,
                onToggleDynamicColor = { viewModel.toggleDynamicColor() }
            )

            Layout(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
                content = {
                    val isReadyForInput = !isStartupLoading && checklists.isNotEmpty()
                    QuickInputBar(
                        value = inputText,
                        onValueChange = { if (editingItemId == null) inputText = it },
                        canSubmit = isReadyForInput,
                        onSubmit = {
                            if (isReadyForInput && editingItemId == null) {
                                submitHolder.onSubmit?.invoke()
                            }
                        }
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isStartupLoading || checklists.isEmpty()) {
                            TotalCheckSkeletonShimmer(
                                isDarkTheme = isDarkMode,
                                checklists = checklists,
                                itemsByChecklist = itemsByChecklist,
                                showUncheckedOnly = showUncheckedOnly,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val pagerState = rememberPagerState(pageCount = { checklists.size })
                            val isReducedMotion = LocalReducedMotion.current
                            var distantTabTransition by remember { mutableStateOf<DistantTabTransition?>(null) }
                            var activeTabScrollJob by remember { mutableStateOf<Job?>(null) }

                            // 使用者手動拖曳 Pager 時立即取消遠距導航接管與編輯鎖定，保持 1:1 跟手控制
                            LaunchedEffect(pagerState) {
                                pagerState.interactionSource.interactions.collect { interaction ->
                                    if (interaction is DragInteraction.Start) {
                                        editingItemId = null
                                        activeTabScrollJob?.cancel()
                                        activeTabScrollJob = null
                                        distantTabTransition = null
                                    }
                                }
                            }

                            val navigateToPage: (Int) -> Unit = { rawIndex ->
                                val targetIndex = rawIndex.coerceIn(0, (checklists.size - 1).coerceAtLeast(0))
                                editingItemId = null
                                activeTabScrollJob?.cancel()
                                val job = scope.launch {
                                    try {
                                        val currentPage = pagerState.currentPage
                                        if (targetIndex == currentPage && abs(pagerState.currentPageOffsetFraction) < 0.01f) {
                                            distantTabTransition = null
                                            return@launch
                                        }
                                        if (isReducedMotion) {
                                            distantTabTransition = null
                                            pagerState.scrollToPage(targetIndex)
                                            return@launch
                                        }
                                        val pageDistance = abs(targetIndex - currentPage)
                                        val spec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
                                        if (pageDistance <= 1) {
                                            distantTabTransition = null
                                            pagerState.animateScrollToPage(
                                                page = targetIndex,
                                                animationSpec = spec
                                            )
                                        } else {
                                            val transition = DistantTabTransition(
                                                fromPage = currentPage,
                                                toPage = targetIndex
                                            )
                                            distantTabTransition = transition
                                            val stagingPage = if (targetIndex > currentPage) targetIndex - 1 else targetIndex + 1
                                            pagerState.scrollToPage(stagingPage)
                                            withFrameNanos { }
                                            val tabAnimJob = launch {
                                                transition.progress.animateTo(targetValue = 1f, animationSpec = spec)
                                            }
                                            pagerState.animateScrollToPage(
                                                page = targetIndex,
                                                animationSpec = spec
                                            )
                                            tabAnimJob.join()
                                        }
                                    } finally {
                                        if (activeTabScrollJob == coroutineContext[Job]) {
                                            distantTabTransition = null
                                            activeTabScrollJob = null
                                        }
                                    }
                                }
                                activeTabScrollJob = job
                            }

                            LaunchedEffect(pagerState, checklists) {
                                snapshotFlow {
                                    val overrideTarget = distantTabTransition?.toPage
                                    (overrideTarget ?: pagerState.targetPage)
                                        .coerceIn(0, (checklists.size - 1).coerceAtLeast(0))
                                }
                                    .distinctUntilChanged()
                                    .collect { targetIndex ->
                                        if (targetIndex in checklists.indices) {
                                            viewModel.selectChecklist(checklists[targetIndex].id)
                                        }
                                    }
                            }

                            LaunchedEffect(checklists, pendingScrollToChecklistId) {
                                val targetId = pendingScrollToChecklistId ?: return@LaunchedEffect
                                val targetIndex = checklists.indexOfFirst { it.id == targetId }
                                if (targetIndex >= 0) {
                                    viewModel.clearPendingScroll()
                                    navigateToPage(targetIndex)
                                }
                            }

                            val submitNewItem: () -> Unit = {
                                val trimmed = inputText.trim()
                                if (trimmed.isNotEmpty() && pagerState.currentPage in checklists.indices) {
                                    val currentChecklistId = checklists[pagerState.currentPage].id
                                    viewModel.addItem(currentChecklistId, trimmed)
                                    inputText = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            submitHolder.onSubmit = submitNewItem

                            Column(modifier = Modifier.fillMaxSize()) {
                                ExpandingChecklistTabBar(
                                    checklists = checklists,
                                    itemsByChecklist = itemsByChecklist,
                                    pagerState = pagerState,
                                    distantTabTransition = distantTabTransition,
                                    onSelectPage = navigateToPage,
                                    onLongPressTab = { checklist ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        actionTargetChecklist = checklist
                                    },
                                    onAddChecklist = { showCreateDialog = true }
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clipToBounds()
                                ) {
                                    HorizontalPager(
                                        state = pagerState,
                                        key = { page -> checklists.getOrNull(page)?.id ?: page },
                                        beyondViewportPageCount = 1,
                                        modifier = Modifier.fillMaxSize()
                                    ) { pageIndex ->
                                        val checklist = checklists.getOrNull(pageIndex) ?: return@HorizontalPager
                                        val items = itemsByChecklist[checklist.id].orEmpty()
                                        val stackedListState = remember(checklist.id) { StackedCardListState() }
                                        val totalCount = items.size
                                        val uncheckedCount = items.count { !it.isChecked }
                                        val targetProgress = if (totalCount == 0) 0f else (totalCount - uncheckedCount).toFloat() / totalCount
                                        val progressAnimatable = remember(checklist.id) { Animatable(targetProgress) }
                                        var resetWave by remember(checklist.id) { mutableStateOf(ResetWaveState()) }

                                        LaunchedEffect(targetProgress, isReducedMotion) {
                                            if (isReducedMotion) {
                                                progressAnimatable.snapTo(targetProgress)
                                            } else if (
                                                abs(progressAnimatable.targetValue - targetProgress) > 0.0001f ||
                                                abs(progressAnimatable.value - targetProgress) > 0.0005f
                                            ) {
                                                if (targetProgress > progressAnimatable.value) {
                                                    progressAnimatable.animateTo(
                                                        targetValue = targetProgress,
                                                        initialVelocity = 1.6f,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.38f,
                                                            stiffness = 240f,
                                                            visibilityThreshold = 0.0005f
                                                        )
                                                    )
                                                } else {
                                                    progressAnimatable.animateTo(
                                                        targetValue = targetProgress,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.45f,
                                                            stiffness = 260f,
                                                            visibilityThreshold = 0.0005f
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        val visibleItems = remember(items, showUncheckedOnly) {
                                            if (showUncheckedOnly) {
                                                items.filter { !it.isChecked }
                                            } else {
                                                items
                                            }
                                        }

                                        var previousSize by remember(checklist.id) { mutableIntStateOf(visibleItems.size) }
                                        var previousFirstId by remember(checklist.id) { mutableStateOf(visibleItems.firstOrNull()?.id) }

                                        LaunchedEffect(visibleItems.size, visibleItems.firstOrNull()?.id) {
                                            val currentFirstId = visibleItems.firstOrNull()?.id
                                            if (visibleItems.size > previousSize && currentFirstId != previousFirstId) {
                                                stackedListState.scrollToTop(animated = !isReducedMotion)
                                            }
                                            previousSize = visibleItems.size
                                            previousFirstId = currentFirstId
                                        }

                                        // 進入單項原地編輯時，單次平滑置中於鍵盤上方可視區（搭配 Placement 階段 pinnedExpandedKey 即時邊界保護）
                                        LaunchedEffect(editingItemId) {
                                            val targetEditId = editingItemId ?: return@LaunchedEffect
                                            if (visibleItems.none { it.id == targetEditId }) return@LaunchedEffect

                                            val minExpectedImePx = with(density) { 280.dp.toPx() }
                                            val targetBottomInset = maxOf(
                                                imeInsets.getBottom(density).toFloat(),
                                                navBarInsets.getBottom(density).toFloat() + minExpectedImePx
                                            )
                                            stackedListState.scrollToCenterItem(
                                                key = targetEditId,
                                                targetTopInsetPx = 0f,
                                                targetBottomInsetPx = targetBottomInset,
                                                animated = !isReducedMotion
                                            )
                                        }

                                        val progressCardHeightHolder = remember(checklist.id) {
                                            FloatValueHolder(with(density) { 142.dp.toPx() })
                                        }
                                        val topOverlayInsetPxProvider: () -> Float = {
                                            progressCardHeightHolder.value * (1f - progressCollapseFractionProvider())
                                        }

                                        var containerWidthPx by remember(checklist.id) { mutableFloatStateOf(0f) }
                                        var containerHeightPx by remember(checklist.id) { mutableFloatStateOf(0f) }
                                        var draggedItemId by remember(checklist.id) { mutableStateOf<Long?>(null) }
                                        var workingItems by remember(checklist.id) { mutableStateOf<List<CheckItemEntity>>(emptyList()) }
                                        var committedOrderIds by remember(checklist.id) { mutableStateOf<List<Long>?>(null) }
                                        var draggedItemHeightPx by remember(checklist.id) { mutableFloatStateOf(0f) }
                                        var dragOverlayY by remember(checklist.id) { mutableFloatStateOf(0f) }
                                        var isSettlingDrop by remember(checklist.id) { mutableStateOf(false) }
                                        val overlayOffsetY = remember(checklist.id) { Animatable(0f) }
                                        val pointerYInContainerState = remember(checklist.id) { mutableFloatStateOf(0f) }

                                        val currentVisibleItemsState = rememberUpdatedState(visibleItems)
                                        val currentShowUncheckedOnlyState = rememberUpdatedState(showUncheckedOnly)
                                        val isReducedMotionState = rememberUpdatedState(isReducedMotion)

                                        DisposableEffect(checklist.id) {
                                            onDispose {
                                                draggedItemId = null
                                                isSettlingDrop = false
                                                workingItems = emptyList()
                                                committedOrderIds = null
                                            }
                                        }

                                        LaunchedEffect(visibleItems, committedOrderIds) {
                                            val committed = committedOrderIds ?: return@LaunchedEffect
                                            if (visibleItems.map { it.id } == committed) {
                                                committedOrderIds = null
                                            } else {
                                                delay(600L)
                                                committedOrderIds = null
                                            }
                                        }

                                        val displayedItems = remember(visibleItems, draggedItemId, workingItems, committedOrderIds) {
                                            when {
                                                draggedItemId != null && workingItems.isNotEmpty() -> workingItems
                                                committedOrderIds != null -> {
                                                    val order = committedOrderIds!!
                                                    val byId = visibleItems.associateBy { it.id }
                                                    val ordered = order.mapNotNull { byId[it] }
                                                    val orderSet = order.toSet()
                                                    val extras = visibleItems.filter { it.id !in orderSet }
                                                    ordered + extras
                                                }
                                                else -> visibleItems
                                            }
                                        }

                                        val recalculateInsertionSlot = {
                                            val currentDraggedId = draggedItemId
                                            val currentList = workingItems
                                            if (currentDraggedId != null && currentList.isNotEmpty()) {
                                                val currentIndex = currentList.indexOfFirst { it.id == currentDraggedId }
                                                if (currentIndex >= 0) {
                                                    val floatingCenterY = dragOverlayY + draggedItemHeightPx / 2f
                                                    val indexById = HashMap<Long, Int>(currentList.size)
                                                    for (i in currentList.indices) {
                                                        indexById[currentList[i].id] = i
                                                    }
                                                    var targetIndex = currentIndex
                                                    for (info in stackedListState.placedItemsInfo) {
                                                        val itemId = info.key as? Long ?: continue
                                                        if (itemId == currentDraggedId) continue
                                                        val idx = indexById[itemId] ?: continue
                                                        val itemCenterY = info.visualCenterY
                                                        if (idx < currentIndex && floatingCenterY < itemCenterY) {
                                                            if (idx < targetIndex) targetIndex = idx
                                                        } else if (idx > currentIndex && floatingCenterY > itemCenterY) {
                                                            if (idx > targetIndex) targetIndex = idx
                                                        }
                                                    }
                                                    if (targetIndex != currentIndex) {
                                                        val mutable = currentList.toMutableList()
                                                        val moved = mutable.removeAt(currentIndex)
                                                        mutable.add(targetIndex, moved)
                                                        workingItems = mutable
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }
                                        }

                                        // 漸變觸發區域 (48.dp) 與漸變速度 (t²) 邊緣自動捲動（動態對齊頂部進度卡底界與底部鍵盤/輸入列頂界）
                                        LaunchedEffect(draggedItemId, isSettlingDrop) {
                                            if (draggedItemId != null && !isSettlingDrop) {
                                                val maxEdgeZonePx = with(density) { 48.dp.toPx() }
                                                val maxSpeedPxPerSec = with(density) { 300.dp.toPx() }
                                                var lastFrameNanos = 0L
                                                while (isActive) {
                                                    withFrameNanos { frameNanos ->
                                                        val dtSec = if (lastFrameNanos == 0L) {
                                                            0f
                                                        } else {
                                                            ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                                        }
                                                        lastFrameNanos = frameNanos
                                                        if (dtSec > 0f && containerHeightPx > 0f) {
                                                            val topBound = topOverlayInsetPxProvider().coerceAtLeast(0f)
                                                            val bottomBound = (containerHeightPx - bottomOverlayInsetPxProvider())
                                                                .coerceAtLeast(topBound + 1f)
                                                            val activeSpan = (bottomBound - topBound).coerceAtLeast(1f)
                                                            val edgeZonePx = maxEdgeZonePx
                                                                .coerceAtMost(activeSpan * 0.22f)
                                                                .coerceAtLeast(1f)
                                                            val py = pointerYInContainerState.floatValue
                                                            val scrollDeltaPx = when {
                                                                py < (topBound + edgeZonePx) && stackedListState.canScrollBackward -> {
                                                                    val t = (((topBound + edgeZonePx) - py) / edgeZonePx).coerceIn(0f, 1f)
                                                                    -maxSpeedPxPerSec * (t * t) * dtSec
                                                                }
                                                                py > (bottomBound - edgeZonePx) && stackedListState.canScrollForward -> {
                                                                    val t = ((py - (bottomBound - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                                                                    maxSpeedPxPerSec * (t * t) * dtSec
                                                                }
                                                                else -> 0f
                                                            }
                                                            if (scrollDeltaPx != 0f) {
                                                                val consumed = stackedListState.dispatchRawDelta(scrollDeltaPx)
                                                                if (consumed != 0f) {
                                                                    recalculateInsertionSlot()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Layout(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clipToBounds(),
                                            content = {
                                                CapsuleProgressModule(
                                                    uncheckedCount = uncheckedCount,
                                                    totalCount = totalCount,
                                                    progressProvider = { progressAnimatable.value },
                                                    showUncheckedOnly = showUncheckedOnly,
                                                    onToggleUncheckedOnly = { viewModel.toggleShowUncheckedOnly() },
                                                    onReset = {
                                                        editingItemId = null
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        val checkedInOrder = items.filter { it.isChecked }.map { it.id }
                                                        if (!isReducedMotion && checkedInOrder.isNotEmpty()) {
                                                            val delays = checkedInOrder.mapIndexed { idx, id ->
                                                                id to (20 + idx * 35).coerceAtMost(560)
                                                            }.toMap()
                                                            resetWave = ResetWaveState(
                                                                waveKey = resetWave.waveKey + 1,
                                                                delaysByItemId = delays,
                                                                triggeredAtUptimeMs = android.os.SystemClock.uptimeMillis()
                                                            )
                                                        } else {
                                                            resetWave = ResetWaveState(waveKey = resetWave.waveKey + 1)
                                                        }
                                                        viewModel.resetChecklist(checklist.id) { checkedSnapshot ->
                                                            scope.launch {
                                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                                val result = snackbarHostState.showSnackbar(
                                                                    message = "已重置「${checklist.name}」所有項目",
                                                                    actionLabel = "復原",
                                                                    duration = SnackbarDuration.Short
                                                                )
                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                    viewModel.restoreCheckedStates(checkedSnapshot)
                                                                }
                                                            }
                                                        }
                                                    }
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .onSizeChanged { size ->
                                                            containerWidthPx = size.width.toFloat()
                                                            containerHeightPx = size.height.toFloat()
                                                        }
                                                        .pointerInput(checklist.id) {
                                                            val handleRightInsetPx = with(density) { 14.dp.toPx() }
                                                            val handleLeftInsetPx = with(density) { 64.dp.toPx() }
                                                            awaitEachGesture {
                                                                val down = awaitFirstDown(
                                                                    requireUnconsumed = true,
                                                                    pass = PointerEventPass.Initial
                                                                )
                                                                if (draggedItemId != null || isSettlingDrop) return@awaitEachGesture
                                                                val widthPx = containerWidthPx
                                                                if (widthPx <= 0f) return@awaitEachGesture

                                                                // O(1) X 軸快速篩除：非右側六點拖曳手把區域直接略過，完全不影響一般點擊或捲動
                                                                val handleLeftX = widthPx - handleLeftInsetPx
                                                                val handleRightX = widthPx - handleRightInsetPx
                                                                if (down.position.x !in handleLeftX..handleRightX) return@awaitEachGesture

                                                                val itemsNow = currentVisibleItemsState.value
                                                                if (itemsNow.isEmpty()) return@awaitEachGesture

                                                                var hitInfo: PlacedStackedItemInfo? = null
                                                                for (info in stackedListState.placedItemsInfo) {
                                                                    if (info.isCompressed) continue
                                                                    val itemId = info.key as? Long ?: continue
                                                                    if (editingItemId == itemId) continue
                                                                    if (down.position.y in info.visualTopY..info.visualBottomY) {
                                                                        hitInfo = info
                                                                        break
                                                                    }
                                                                }

                                                                val matchedInfo = hitInfo ?: return@awaitEachGesture
                                                                val hitItemId = matchedInfo.key as? Long ?: return@awaitEachGesture

                                                                // 命中右側六點拖曳手把：立即消費事件並開啟零延遲拖曳
                                                                down.consume()

                                                                val rowTopInContainer = matchedInfo.placedTopY
                                                                val rowHeightPx = matchedInfo.heightPx.coerceAtLeast(1f)
                                                                val grabOffsetY = down.position.y - rowTopInContainer
                                                                val minOverlayY = topOverlayInsetPxProvider().coerceAtLeast(0f)
                                                                val maxOverlayY = (containerHeightPx - bottomOverlayInsetPxProvider() - rowHeightPx)
                                                                    .coerceAtLeast(minOverlayY)

                                                                draggedItemHeightPx = rowHeightPx
                                                                dragOverlayY = rowTopInContainer.coerceIn(minOverlayY, maxOverlayY)
                                                                pointerYInContainerState.floatValue = down.position.y
                                                                workingItems = itemsNow.toList()
                                                                draggedItemId = hitItemId
                                                                editingItemId = null
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                                try {
                                                                    while (true) {
                                                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                                        val change = event.changes.firstOrNull { it.id == down.id }
                                                                        if (change == null || !change.pressed) {
                                                                            break
                                                                        }
                                                                        change.consume()
                                                                        val newPointerY = change.position.y
                                                                        pointerYInContainerState.floatValue = newPointerY
                                                                        val minY = topOverlayInsetPxProvider().coerceAtLeast(0f)
                                                                        val maxY = (containerHeightPx - bottomOverlayInsetPxProvider() - draggedItemHeightPx)
                                                                            .coerceAtLeast(minY)
                                                                        dragOverlayY = (newPointerY - grabOffsetY).coerceIn(minY, maxY)
                                                                        recalculateInsertionSlot()
                                                                    }
                                                                } finally {
                                                                    val finalOrderIds = workingItems.map { it.id }
                                                                    val activeId = draggedItemId
                                                                    if (activeId != null && finalOrderIds.isNotEmpty()) {
                                                                        isSettlingDrop = true
                                                                        committedOrderIds = finalOrderIds
                                                                        viewModel.reorderChecklistItems(
                                                                            checklistId = checklist.id,
                                                                            reorderedVisibleIds = finalOrderIds,
                                                                            showUncheckedOnly = currentShowUncheckedOnlyState.value
                                                                        )
                                                                        scope.launch {
                                                                            try {
                                                                                overlayOffsetY.snapTo(dragOverlayY)
                                                                                val targetInfo = stackedListState.placedItemsInfo
                                                                                    .firstOrNull { (it.key as? Long) == activeId }
                                                                                val targetY = targetInfo?.placedTopY ?: dragOverlayY
                                                                                if (!isReducedMotionState.value) {
                                                                                    overlayOffsetY.animateTo(
                                                                                        targetValue = targetY,
                                                                                        animationSpec = spring(
                                                                                            dampingRatio = 0.8f,
                                                                                            stiffness = 420f,
                                                                                            visibilityThreshold = 0.5f
                                                                                        )
                                                                                    )
                                                                                }
                                                                            } finally {
                                                                                draggedItemId = null
                                                                                isSettlingDrop = false
                                                                                workingItems = emptyList()
                                                                            }
                                                                        }
                                                                    } else {
                                                                        draggedItemId = null
                                                                        isSettlingDrop = false
                                                                        workingItems = emptyList()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                ) {
                                                    BidirectionalStackedCardList(
                                                        items = displayedItems,
                                                        key = { it.id },
                                                        state = stackedListState,
                                                        topInsetPxProvider = topOverlayInsetPxProvider,
                                                        bottomInsetPxProvider = bottomOverlayInsetPxProvider,
                                                        pinnedExpandedKeyProvider = { editingItemId },
                                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                        verticalSpacing = 10.dp,
                                                        peekStep = 8.dp,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) { index, item, slotState ->
                                                        if (draggedItemId == item.id) {
                                                            val slotHeightDp = with(density) { draggedItemHeightPx.toDp() }
                                                                .coerceAtLeast(52.dp)
                                                            DashedCapsuleEmptySlot(heightDp = slotHeightDp)
                                                        } else {
                                                            CapsuleCheckItemRow(
                                                                item = item,
                                                                onToggle = {
                                                                    viewModel.toggleItemChecked(item)
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    stackedListState.triggerNeighborRipple(
                                                                        sourceKey = item.id,
                                                                        intensity = 1f,
                                                                        isReducedMotion = isReducedMotion
                                                                    )
                                                                },
                                                                isEditing = editingItemId == item.id,
                                                                stackDepthProvider = { slotState.depthState.floatValue },
                                                                isCompressedProvider = { slotState.isCompressed },
                                                                onCompressedClick = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    scope.launch {
                                                                        stackedListState.scrollToExpandItem(
                                                                            index = index,
                                                                            animated = !isReducedMotion
                                                                        )
                                                                    }
                                                                },
                                                                resetWaveKey = resetWave.waveKey,
                                                                resetDelayMs = resetWave.delaysByItemId[item.id],
                                                                resetTriggeredAtMillis = resetWave.triggeredAtUptimeMs,
                                                                onStartEdit = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    editingItemId = item.id
                                                                },
                                                                onConfirmEdit = { newLabel ->
                                                                    viewModel.renameItem(item.id, newLabel)
                                                                    editingItemId = null
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    stackedListState.triggerNeighborRipple(
                                                                        sourceKey = item.id,
                                                                        intensity = 0.65f,
                                                                        isReducedMotion = isReducedMotion
                                                                    )
                                                                },
                                                                onCancelEdit = {
                                                                    editingItemId = null
                                                                },
                                                                onDelete = {
                                                                    if (editingItemId == item.id) {
                                                                        editingItemId = null
                                                                    }
                                                                    viewModel.deleteItem(item) { deletedItem ->
                                                                        scope.launch {
                                                                            snackbarHostState.currentSnackbarData?.dismiss()
                                                                            val result = snackbarHostState.showSnackbar(
                                                                                message = "已刪除「${deletedItem.label}」",
                                                                                actionLabel = "復原",
                                                                                duration = SnackbarDuration.Short
                                                                            )
                                                                            if (result == SnackbarResult.ActionPerformed) {
                                                                                viewModel.restoreItem(deletedItem)
                                                                            }
                                                                        }
                                                                    }
                                                                },
                                                                canMoveUp = index > 0 && draggedItemId == null,
                                                                canMoveDown = index < displayedItems.lastIndex && draggedItemId == null,
                                                                onMoveUp = {
                                                                    if (!slotState.isCompressed) {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        viewModel.moveItemByStep(
                                                                            checklistId = checklist.id,
                                                                            itemId = item.id,
                                                                            delta = -1,
                                                                            showUncheckedOnly = showUncheckedOnly
                                                                        )
                                                                    }
                                                                },
                                                                onMoveDown = {
                                                                    if (!slotState.isCompressed) {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        viewModel.moveItemByStep(
                                                                            checklistId = checklist.id,
                                                                            itemId = item.id,
                                                                            delta = 1,
                                                                            showUncheckedOnly = showUncheckedOnly
                                                                        )
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }

                                                    val draggedItem = remember(draggedItemId, workingItems) {
                                                        val id = draggedItemId ?: return@remember null
                                                        workingItems.firstOrNull { it.id == id }
                                                    }
                                                    if (draggedItem != null) {
                                                        CapsuleCheckItemRow(
                                                            item = draggedItem,
                                                            onToggle = {},
                                                            isEditing = false,
                                                            isFloatingDragged = !isSettlingDrop,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 16.dp)
                                                                .graphicsLayer {
                                                                    translationY = if (isSettlingDrop) {
                                                                        overlayOffsetY.value
                                                                    } else {
                                                                        dragOverlayY
                                                                    }
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        ) { pageMeasurables, pageConstraints ->
                                            val progressPlaceable = pageMeasurables[0].measure(
                                                pageConstraints.copy(minHeight = 0)
                                            )
                                            progressCardHeightHolder.value = progressPlaceable.height.toFloat()
                                            val listBoxPlaceable = pageMeasurables[1].measure(pageConstraints)

                                            layout(pageConstraints.maxWidth, pageConstraints.maxHeight) {
                                                listBoxPlaceable.placeRelative(0, 0)
                                                val collapse = progressCollapseFractionProvider()
                                                val yOffset = if (collapse >= 0.99f) {
                                                    -progressPlaceable.height - 200
                                                } else {
                                                    -(progressPlaceable.height * collapse).roundToInt()
                                                }
                                                progressPlaceable.placeRelativeWithLayer(
                                                    x = 0,
                                                    y = yOffset,
                                                    zIndex = 20f
                                                ) {
                                                    alpha = (1f - collapse * 1.4f).coerceIn(0f, 1f)
                                                }
                                            }
                                        }
                                    }

                                    SnackbarHost(
                                        hostState = snackbarHostState,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .graphicsLayer {
                                                translationY = -bottomOverlayInsetPxProvider()
                                            }
                                    ) { data ->
                                        Snackbar(
                                            snackbarData = data,
                                            shape = RoundedCornerShape(20.dp),
                                            containerColor = colorScheme.inverseSurface,
                                            contentColor = colorScheme.inverseOnSurface,
                                            actionColor = colorScheme.inversePrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { outerMeasurables, outerConstraints ->
                val quickBarPlaceable = outerMeasurables[0].measure(
                    outerConstraints.copy(minHeight = 0)
                )
                quickBarHeightHolder.value = quickBarPlaceable.height.toFloat()

                val isLoadingState = isStartupLoading || checklists.isEmpty()
                val contentConstraints = if (isLoadingState) {
                    val h = (outerConstraints.maxHeight - quickBarPlaceable.height).coerceAtLeast(0)
                    outerConstraints.copy(minHeight = h, maxHeight = h)
                } else {
                    outerConstraints
                }
                val contentPlaceable = outerMeasurables[1].measure(contentConstraints)

                layout(outerConstraints.maxWidth, outerConstraints.maxHeight) {
                    contentPlaceable.placeRelative(0, 0)
                    quickBarPlaceable.placeRelativeWithLayer(
                        x = 0,
                        y = outerConstraints.maxHeight - quickBarPlaceable.height,
                        zIndex = 30f
                    ) {
                        val imePx = imeInsets.getBottom(density).toFloat()
                        val navPx = navBarInsets.getBottom(density).toFloat()
                        val imeLiftPx = (imePx - navPx).coerceAtLeast(0f)
                        val hideFraction = quickInputHideAnim.value.coerceIn(0f, 1f)
                        val hideDropPx = quickBarPlaceable.height.toFloat() * hideFraction +
                            if (hideFraction >= 0.99f) 2000f else 0f
                        translationY = -imeLiftPx + hideDropPx
                        alpha = (1f - hideFraction * 1.4f).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateChecklistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.createChecklist(name)
            }
        )
    }

    actionTargetChecklist?.let { target ->
        ChecklistActionsDialog(
            checklist = target,
            canDelete = checklists.size > 1,
            onDismiss = { actionTargetChecklist = null },
            onRename = { newName ->
                viewModel.renameChecklist(target.id, newName)
                actionTargetChecklist = null
            },
            onDelete = {
                viewModel.deleteChecklist(target.id)
                actionTargetChecklist = null
            }
        )
    }
}

@Composable
private fun CreateChecklistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(text = "新增清點清單", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("例如：露營裝備") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (name.isNotBlank()) onConfirm(name)
                    }
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(50)
            ) {
                Text("建立")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ChecklistActionsDialog(
    checklist: ChecklistEntity,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var nameInput by remember(checklist.id) { mutableStateOf(checklist.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(text = "管理清單", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("清單名稱") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (nameInput.isNotBlank()) onRename(nameInput)
                        }
                    )
                )
                TextButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colorScheme.error,
                        disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (canDelete) "刪除此清單" else "至少須保留一個清單",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(nameInput) },
                enabled = nameInput.isNotBlank(),
                shape = RoundedCornerShape(50)
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun TopAppHeaderBar(
    isDarkTheme: Boolean,
    useDynamicColor: Boolean,
    isThemeTransitioning: Boolean,
    onToggleTheme: (originInRoot: Offset) -> Unit,
    onPreCaptureTheme: () -> Unit,
    onCancelPreCaptureTheme: () -> Unit,
    onToggleDynamicColor: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var themeButtonCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastTouchRootOffset by remember { mutableStateOf<Offset?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "TotalCheck",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (useDynamicColor) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (useDynamicColor) colorScheme.primary else colorScheme.outlineVariant
                )
            ) {
                FilledTonalIconButton(
                    onClick = onToggleDynamicColor,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (useDynamicColor) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                        contentColor = if (useDynamicColor) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = if (useDynamicColor) "關閉動態取色" else "開啟動態取色",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val origin = lastTouchRootOffset
                            ?: themeButtonCoords?.takeIf { it.isAttached }?.boundsInRoot()?.center
                            ?: Offset.Unspecified
                        lastTouchRootOffset = null
                        onToggleTheme(origin)
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .onGloballyPositioned { themeButtonCoords = it }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                onPreCaptureTheme()
                                val coords = themeButtonCoords
                                if (coords != null && coords.isAttached) {
                                    lastTouchRootOffset = coords.localToRoot(down.position)
                                }
                                var lastPos = down.position
                                var releasedInside = false
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        releasedInside = true
                                        break
                                    }
                                    lastPos = change.position
                                    if (!change.pressed) {
                                        val touchSlop = viewConfiguration.touchSlop
                                        releasedInside = change.position.x in -touchSlop..(size.width + touchSlop) &&
                                            change.position.y in -touchSlop..(size.height + touchSlop)
                                        break
                                    }
                                }
                                if (releasedInside && coords != null && coords.isAttached) {
                                    lastTouchRootOffset = coords.localToRoot(lastPos)
                                } else {
                                    lastTouchRootOffset = null
                                    onCancelPreCaptureTheme()
                                }
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.primary,
                        disabledContainerColor = colorScheme.surfaceContainerHigh,
                        disabledContentColor = colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = if (isDarkTheme) "切換為亮色模式" else "切換為暗色模式",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    canSubmit: Boolean,
    onSubmit: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                )
        ) {
            HorizontalDivider(thickness = 0.5.dp, color = colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("快速新增清點項目...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = colorScheme.surfaceContainerHigh,
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outlineVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (canSubmit) onSubmit()
                    })
                )
                Spacer(modifier = Modifier.width(10.dp))
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = canSubmit && value.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "新增項目"
                    )
                }
            }
        }
    }
}

private data class ResetWaveState(
    val waveKey: Int = 0,
    val delaysByItemId: Map<Long, Int> = emptyMap(),
    val triggeredAtUptimeMs: Long = 0L
)

private class FloatValueHolder(var value: Float)

