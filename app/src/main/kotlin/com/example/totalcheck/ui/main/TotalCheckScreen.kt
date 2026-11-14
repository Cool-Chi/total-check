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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.totalcheck.R
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
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
    val context = LocalContext.current
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
    var modalContent by remember { mutableStateOf<ChecklistModalContent?>(null) }
    var isModalOpen by remember { mutableStateOf(false) }
    var modalSessionKey by remember { mutableIntStateOf(0) }
    var returnFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    val addChecklistFocusRequester = remember { FocusRequester() }
    val renameFocusRequesters = remember { HashMap<Long, FocusRequester>() }
    val deleteFocusRequesters = remember { HashMap<Long, FocusRequester>() }
    var editingItemId by remember { mutableStateOf<Long?>(null) }
    val editingDraftHolder = remember { EditingDraftHolder() }
    val optimisticItemLabels = remember { mutableStateMapOf<Long, String>() }
    var suppressMainImeUntilClosed by remember { mutableStateOf(false) }
    val keyboardFocusController = remember { KeyboardFocusVisibleController() }

    LaunchedEffect(itemsByChecklist) {
        if (optimisticItemLabels.isNotEmpty()) {
            val allDbItemsById = itemsByChecklist.values.flatten().associateBy { it.id }
            val resolvedIds = optimisticItemLabels.entries
                .filter { (id, optimisticLabel) ->
                    val dbItem = allDbItemsById[id]
                    dbItem == null || dbItem.label == optimisticLabel
                }
                .map { it.key }
            resolvedIds.forEach { optimisticItemLabels.remove(it) }
        }
    }

    val commitActiveInlineEdit: () -> Unit = {
        val currentEditId = editingItemId
        if (currentEditId != null) {
            val trimmed = editingDraftHolder.text.trim()
            if (trimmed.isNotEmpty()) {
                optimisticItemLabels[currentEditId] = trimmed
                viewModel.renameItem(currentEditId, trimmed)
            }
            editingItemId = null
            editingDraftHolder.text = ""
        }
    }

    fun openModal(content: ChecklistModalContent, triggerFocus: FocusRequester?) {
        commitActiveInlineEdit()
        suppressMainImeUntilClosed = true
        modalSessionKey += 1
        modalContent = content
        returnFocusRequester = triggerFocus
        isModalOpen = true
    }

    fun closeModal(restoreFocus: Boolean) {
        suppressMainImeUntilClosed = true
        isModalOpen = false
        if (restoreFocus && keyboardFocusController.isKeyboardFocusVisible) {
            runCatching { returnFocusRequester?.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }
    var previousEditingItemId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(editingItemId) {
        if (previousEditingItemId != null && editingItemId == null) {
            focusManager.clearFocus()
        } else if (editingItemId != null) {
            suppressMainImeUntilClosed = false
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
        commitActiveInlineEdit()
        focusManager.clearFocus()
    }

    val imeInsets = WindowInsets.ime
    val navBarInsets = WindowInsets.navigationBars
    val isImeVisible = WindowInsets.isImeVisible
    val isImeVisibleState = rememberUpdatedState(isImeVisible)
    val imeCollapseSpanPx = with(density) { 180.dp.toPx() }.coerceAtLeast(1f)
    val isModalPresent = isModalOpen || modalContent != null
    val shouldFreezeMainIme = isModalPresent || suppressMainImeUntilClosed
    val shouldFreezeMainImeState = rememberUpdatedState(shouldFreezeMainIme)
    val isModalPresentState = rememberUpdatedState(isModalPresent)

    // 彈窗完全關閉後：待軟鍵盤收合完成或最多 320ms 即自動解除主畫面 IME 凍結，確保背景零跳動且永不卡死
    LaunchedEffect(isModalPresent) {
        if (!isModalPresent && suppressMainImeUntilClosed) {
            val startMs = android.os.SystemClock.uptimeMillis()
            val closeThresholdPx = with(density) { 4.dp.roundToPx() }
            while (suppressMainImeUntilClosed && android.os.SystemClock.uptimeMillis() - startMs < 320L) {
                val imeExtra = (imeInsets.getBottom(density) - navBarInsets.getBottom(density)).coerceAtLeast(0)
                if (imeExtra <= closeThresholdPx && !isImeVisibleState.value) break
                withFrameNanos { }
            }
            suppressMainImeUntilClosed = false
        }
    }

    // 單項原地重命名專屬 IME 監聽：一旦系統返回手勢觸發軟鍵盤退場（isImeVisible 轉為 false 或高度自峰值回落），第 1 幀即刻完成重命名並恢復介面
    LaunchedEffect(editingItemId) {
        val currentEditId = editingItemId ?: return@LaunchedEffect
        var hasSeenImeOpen = false
        var peakImeExtraPx = 0f
        val openThresholdPx = with(density) { 40.dp.toPx() }
        val dropTriggerPx = with(density) { 12.dp.toPx() }

        snapshotFlow {
            val imeBottom = imeInsets.getBottom(density).toFloat()
            val navBottom = navBarInsets.getBottom(density).toFloat()
            val imeExtra = (imeBottom - navBottom).coerceAtLeast(0f)
            imeExtra to isImeVisibleState.value
        }.collect { (imeExtra, imeVisible) ->
            if (isModalPresentState.value) return@collect
            if (imeExtra > openThresholdPx && imeVisible) {
                hasSeenImeOpen = true
                if (imeExtra > peakImeExtraPx) {
                    peakImeExtraPx = imeExtra
                }
            } else if (hasSeenImeOpen && (!imeVisible || imeExtra < peakImeExtraPx - dropTriggerPx)) {
                hasSeenImeOpen = false
                if (editingItemId == currentEditId) {
                    commitActiveInlineEdit()
                    focusManager.clearFocus()
                }
            }
        }
    }

    // 底部新增項目導覽列專屬 IME 監聽：退場過程絕不中途打斷輸入會話，確保導覽列 1:1 跟隨鍵盤平滑下移到底部後再清除焦點
    LaunchedEffect(density) {
        var wasQuickInputImeOpen = false
        val openThresholdPx = with(density) { 40.dp.toPx() }
        snapshotFlow {
            val imeBottom = imeInsets.getBottom(density).toFloat()
            val navBottom = navBarInsets.getBottom(density).toFloat()
            val imeExtra = (imeBottom - navBottom).coerceAtLeast(0f)
            imeExtra to isImeVisibleState.value
        }.collect { (imeExtra, imeVisible) ->
            if (isModalPresentState.value || editingItemId != null) {
                wasQuickInputImeOpen = false
                return@collect
            }
            if (suppressMainImeUntilClosed) {
                if (imeExtra <= 4f && !imeVisible) {
                    suppressMainImeUntilClosed = false
                }
                wasQuickInputImeOpen = false
                return@collect
            }
            if (imeExtra > openThresholdPx && imeVisible) {
                wasQuickInputImeOpen = true
            } else if (wasQuickInputImeOpen && imeExtra <= 4f && !imeVisible) {
                wasQuickInputImeOpen = false
                focusManager.clearFocus()
            }
        }
    }

    // 僅於進入/離開單項編輯時以彈簧驅動進度卡收摺；當局部彈窗開啟或等待彈窗鍵盤收合時凍結背景 IME 響應，達成背景 0 跳動
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

    val progressCollapseFractionProvider: () -> Float = remember(
        imeInsets,
        navBarInsets,
        density,
        imeCollapseSpanPx,
        progressCollapseAnim,
        shouldFreezeMainImeState
    ) {
        {
            val imeFraction = if (shouldFreezeMainImeState.value) {
                0f
            } else {
                val imePx = imeInsets.getBottom(density).toFloat()
                val navPx = navBarInsets.getBottom(density).toFloat()
                ((imePx - navPx) / imeCollapseSpanPx).coerceIn(0f, 1f)
            }
            maxOf(progressCollapseAnim.value.coerceIn(0f, 1f), imeFraction)
        }
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

    val quickBarBodyHeightHolder = remember { FloatValueHolder(with(density) { 76.dp.toPx() }) }
    val bottomOverlayInsetPxProvider: () -> Float = remember(
        imeInsets,
        navBarInsets,
        density,
        quickBarBodyHeightHolder,
        quickInputHideAnim,
        shouldFreezeMainImeState
    ) {
        {
            val navPx = navBarInsets.getBottom(density).toFloat()
            val imePx = if (shouldFreezeMainImeState.value) {
                0f
            } else {
                imeInsets.getBottom(density).toFloat()
            }
            val visibleQuickBarPx = quickBarBodyHeightHolder.value * (1f - quickInputHideAnim.value.coerceIn(0f, 1f))
            maxOf(imePx, navPx) + visibleQuickBarPx
        }
    }

    val baseBottomOverlayInsetPxProvider: () -> Float = remember(
        navBarInsets,
        density,
        quickBarBodyHeightHolder
    ) {
        {
            navBarInsets.getBottom(density).toFloat() + quickBarBodyHeightHolder.value
        }
    }

    val onStartInlineEditStable = remember<(Long, String) -> Unit> {
        { itemId, label ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            editingDraftHolder.text = label
            editingItemId = itemId
        }
    }
    val onEditDraftChangedStable = remember<(Long, String) -> Unit> {
        { itemId, draft ->
            if (editingItemId == itemId) {
                editingDraftHolder.text = draft
            }
        }
    }
    val onConfirmInlineEditStable = remember<(Long, String) -> Unit> {
        { itemId, newLabel ->
            val trimmed = newLabel.trim()
            if (trimmed.isNotEmpty()) {
                optimisticItemLabels[itemId] = trimmed
                viewModel.renameItem(itemId, trimmed)
            }
            editingItemId = null
            editingDraftHolder.text = ""
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val onCancelInlineEditStable = remember<() -> Unit> {
        {
            editingItemId = null
            editingDraftHolder.text = ""
        }
    }
    val onClearEditingIfMatchesStable = remember<(Long) -> Unit> {
        { itemId ->
            if (editingItemId == itemId) {
                editingItemId = null
                editingDraftHolder.text = ""
            }
        }
    }
    val onOpenModalStable = remember<(ChecklistModalContent, FocusRequester?) -> Unit> {
        { content, trigger ->
            openModal(content, trigger)
        }
    }

    CompositionLocalProvider(LocalKeyboardFocusVisible provides keyboardFocusController) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .trackKeyboardFocusVisibility(keyboardFocusController),
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                val isReadyForInput = !isStartupLoading && checklists.isNotEmpty()
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isStartupLoading || checklists.isEmpty()) {
                        TotalCheckSkeletonShimmer(
                            isDarkTheme = isDarkMode,
                            checklists = checklists,
                            itemsByChecklist = itemsByChecklist,
                            showUncheckedOnly = showUncheckedOnly,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = with(density) { quickBarBodyHeightHolder.value.toDp() })
                        )
                    } else {
                            val pagerState = rememberPagerState(pageCount = { checklists.size })
                            val isReducedMotion = LocalReducedMotion.current
                            var distantTabTransition by remember { mutableStateOf<DistantTabTransition?>(null) }
                            var activeTabScrollJob by remember { mutableStateOf<Job?>(null) }
                            var activeChecklistId by remember { mutableStateOf(checklists.firstOrNull()?.id) }
                            val currentChecklistsInPager by rememberUpdatedState(checklists)

                            // 使用者手動拖曳 Pager 時立即取消遠距導航接管與編輯鎖定，保持 1:1 跟手控制
                            LaunchedEffect(pagerState) {
                                pagerState.interactionSource.interactions.collect { interaction ->
                                    if (interaction is DragInteraction.Start) {
                                        commitActiveInlineEdit()
                                        activeTabScrollJob?.cancel()
                                        activeTabScrollJob = null
                                        distantTabTransition = null
                                    }
                                }
                            }

                            val navigateToPage: (Int) -> Unit = { rawIndex ->
                                val listNow = currentChecklistsInPager
                                val targetIndex = rawIndex.coerceIn(0, (listNow.size - 1).coerceAtLeast(0))
                                val targetId = listNow.getOrNull(targetIndex)?.id
                                activeChecklistId = targetId
                                if (targetId != null) {
                                    viewModel.selectChecklist(targetId)
                                }
                                commitActiveInlineEdit()
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
                                        val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
                                        if (pageDistance <= 1) {
                                            distantTabTransition = null
                                            pagerState.animateScrollToPage(
                                                page = targetIndex,
                                                animationSpec = spec
                                            )
                                        } else {
                                            // 統一單一同步動效並取消中途預備跳頁 (stagingPage)，消除跨頁組合毛刺與動效重疊
                                            val transition = DistantTabTransition(
                                                fromPage = currentPage,
                                                toPage = targetIndex
                                            )
                                            distantTabTransition = transition
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

                            LaunchedEffect(pagerState) {
                                snapshotFlow {
                                    val overrideTarget = distantTabTransition?.toPage
                                    val target = overrideTarget ?: pagerState.targetPage
                                    Triple(target, pagerState.isScrollInProgress, distantTabTransition != null)
                                }
                                    .collect { (targetIndex, inProgress, hasDistant) ->
                                        val listNow = currentChecklistsInPager
                                        val clamped = targetIndex.coerceIn(0, (listNow.size - 1).coerceAtLeast(0))
                                        if (clamped in listNow.indices && (inProgress || hasDistant || activeChecklistId == null)) {
                                            val id = listNow[clamped].id
                                            if (activeChecklistId != id) {
                                                activeChecklistId = id
                                                viewModel.selectChecklist(id)
                                            }
                                        }
                                    }
                            }

                            val checklistIdsKey = checklists.map { it.id }
                            LaunchedEffect(checklistIdsKey) {
                                val currentActiveId = activeChecklistId
                                if (currentActiveId == null) {
                                    val firstId = checklistIdsKey.firstOrNull()
                                    activeChecklistId = firstId
                                    if (firstId != null) {
                                        viewModel.selectChecklist(firstId)
                                    }
                                    return@LaunchedEffect
                                }
                                val newIdx = checklistIdsKey.indexOf(currentActiveId)
                                if (newIdx >= 0) {
                                    if (newIdx != pagerState.currentPage && pendingScrollToChecklistId == null) {
                                        pagerState.scrollToPage(newIdx)
                                    }
                                } else {
                                    val fallbackIdx = pagerState.currentPage.coerceIn(0, (checklistIdsKey.size - 1).coerceAtLeast(0))
                                    val fallbackId = checklistIdsKey.getOrNull(fallbackIdx)
                                    activeChecklistId = fallbackId
                                    if (fallbackId != null) {
                                        viewModel.selectChecklist(fallbackId)
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
                                    activeChecklistId = activeChecklistId,
                                    onSelectPage = navigateToPage,
                                    onReorderChecklists = { orderedIds ->
                                        viewModel.reorderChecklists(orderedIds)
                                    },
                                    onMoveChecklistLeft = { id ->
                                        viewModel.moveChecklistByStep(id, -1)
                                    },
                                    onMoveChecklistRight = { id ->
                                        viewModel.moveChecklistByStep(id, 1)
                                    },
                                    onAddChecklist = {
                                        openModal(
                                            ChecklistModalContent.CreateChecklist,
                                            addChecklistFocusRequester
                                        )
                                    },
                                    addButtonFocusRequester = addChecklistFocusRequester
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
                                        val isActivePage = pageIndex == pagerState.currentPage || pageIndex == pagerState.targetPage
                                        val rawItems = itemsByChecklist[checklist.id].orEmpty()
                                        val items = if (optimisticItemLabels.isEmpty()) {
                                            rawItems
                                        } else {
                                            rawItems.map { item ->
                                                val optimistic = optimisticItemLabels[item.id]
                                                if (optimistic != null && optimistic != item.label) {
                                                    item.copy(label = optimistic)
                                                } else {
                                                    item
                                                }
                                            }
                                        }
                                        val pageEditingItemId = if (editingItemId != null && items.any { it.id == editingItemId }) {
                                            editingItemId
                                        } else {
                                            null
                                        }
                                        val renameBtnFocus = renameFocusRequesters.getOrPut(checklist.id) {
                                            FocusRequester()
                                        }
                                        val deleteBtnFocus = deleteFocusRequesters.getOrPut(checklist.id) {
                                            FocusRequester()
                                        }
                                        ChecklistPageContent(
                                            checklist = checklist,
                                            items = items,
                                            canDeleteChecklist = checklists.size > 1,
                                            showUncheckedOnly = showUncheckedOnly,
                                            editingItemId = pageEditingItemId,
                                            isActivePage = isActivePage,
                                            isReducedMotion = isReducedMotion,
                                            viewModel = viewModel,
                                            snackbarHostState = snackbarHostState,
                                            progressCollapseFractionProvider = progressCollapseFractionProvider,
                                            bottomOverlayInsetPxProvider = bottomOverlayInsetPxProvider,
                                            baseBottomOverlayInsetPxProvider = baseBottomOverlayInsetPxProvider,
                                            renameButtonFocusRequester = renameBtnFocus,
                                            deleteButtonFocusRequester = deleteBtnFocus,
                                            onStartEdit = onStartInlineEditStable,
                                            onEditDraftChanged = onEditDraftChangedStable,
                                            onConfirmEdit = onConfirmInlineEditStable,
                                            onCancelEdit = onCancelInlineEditStable,
                                            onClearEditingIfMatches = onClearEditingIfMatchesStable,
                                            onOpenModal = onOpenModalStable
                                        )
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

                    QuickInputBar(
                        value = inputText,
                        onValueChange = {
                            if (editingItemId == null) {
                                suppressMainImeUntilClosed = false
                                inputText = it
                            }
                        },
                        canSubmit = isReadyForInput,
                        onSubmit = {
                            if (isReadyForInput && editingItemId == null) {
                                submitHolder.onSubmit?.invoke()
                            }
                        },
                        imeInsets = imeInsets,
                        navBarInsets = navBarInsets,
                        shouldFreezeImeProvider = { shouldFreezeMainImeState.value },
                        hideProgressProvider = { quickInputHideAnim.value },
                        onBodyHeightChanged = { quickBarBodyHeightHolder.value = it },
                        onInputFocused = { suppressMainImeUntilClosed = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(30f)
                    )

                ScopedModalHost(
                    modalContent = modalContent,
                    isOpen = isModalOpen,
                    sessionKey = modalSessionKey,
                    onRequestClose = { restoreFocus ->
                        closeModal(restoreFocus)
                    },
                    onConfirmCreate = { name ->
                        closeModal(restoreFocus = true)
                        viewModel.createChecklist(name)
                    },
                    onConfirmImport = { title, items ->
                        closeModal(restoreFocus = false)
                        viewModel.importChecklist(title, items) { createdChecklist, count ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        R.string.snackbar_imported_checklist,
                                        createdChecklist.name,
                                        count
                                    ),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onConfirmRename = { target, newName ->
                        closeModal(restoreFocus = true)
                        viewModel.renameChecklist(target.id, newName)
                    },
                    onConfirmDelete = { target ->
                        closeModal(restoreFocus = false)
                        viewModel.deleteChecklist(target) { deletedChecklist, itemsSnapshot ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        R.string.snackbar_deleted_checklist,
                                        deletedChecklist.name
                                    ),
                                    actionLabel = context.getString(R.string.action_undo),
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreChecklist(deletedChecklist, itemsSnapshot)
                                }
                            }
                        }
                    },
                    onExited = {
                        if (!isModalOpen) {
                            modalContent = null
                            returnFocusRequester = null
                        }
                    }
                )
            }
        }
        }
    }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.header_brand_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileIconButton(
                onClick = onToggleDynamicColor,
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = if (useDynamicColor) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                contentColor = if (useDynamicColor) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (useDynamicColor) colorScheme.primary else colorScheme.outlineVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = stringResource(
                        if (useDynamicColor) R.string.cd_disable_dynamic_color else R.string.cd_enable_dynamic_color
                    ),
                    modifier = Modifier.size(20.dp)
                )
            }

            TactileIconButton(
                onClick = {
                    val origin = themeButtonCoords?.takeIf { it.isAttached }?.boundsInRoot()?.center
                        ?: Offset.Unspecified
                    onToggleTheme(origin)
                },
                onPressDown = onPreCaptureTheme,
                onPressCancelled = onCancelPreCaptureTheme,
                onClickWithLocalOffset = { localPos ->
                    val origin = themeButtonCoords?.takeIf { it.isAttached }?.localToRoot(localPos)
                        ?: themeButtonCoords?.takeIf { it.isAttached }?.boundsInRoot()?.center
                        ?: Offset.Unspecified
                    onToggleTheme(origin)
                },
                modifier = Modifier
                    .size(42.dp)
                    .onGloballyPositioned { themeButtonCoords = it },
                shape = RoundedCornerShape(16.dp),
                containerColor = colorScheme.surfaceContainerHigh,
                contentColor = colorScheme.primary,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = stringResource(
                        if (isDarkTheme) R.string.cd_switch_to_light_mode else R.string.cd_switch_to_dark_mode
                    ),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChecklistPageContent(
    checklist: ChecklistEntity,
    items: List<CheckItemEntity>,
    canDeleteChecklist: Boolean,
    showUncheckedOnly: Boolean,
    editingItemId: Long?,
    isActivePage: Boolean,
    isReducedMotion: Boolean,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    progressCollapseFractionProvider: () -> Float,
    bottomOverlayInsetPxProvider: () -> Float,
    baseBottomOverlayInsetPxProvider: () -> Float,
    renameButtonFocusRequester: FocusRequester,
    deleteButtonFocusRequester: FocusRequester,
    onStartEdit: (Long, String) -> Unit,
    onEditDraftChanged: (Long, String) -> Unit,
    onConfirmEdit: (Long, String) -> Unit,
    onCancelEdit: () -> Unit,
    onClearEditingIfMatches: (Long) -> Unit,
    onOpenModal: (ChecklistModalContent, FocusRequester?) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val imeInsets = WindowInsets.ime
    val navBarInsets = WindowInsets.navigationBars
    val isActivePageState = rememberUpdatedState(isActivePage)

    val pageCollapseProvider: () -> Float = remember(
        checklist.id,
        progressCollapseFractionProvider
    ) {
        {
            if (isActivePageState.value) progressCollapseFractionProvider() else 0f
        }
    }
    val pageBottomOverlayInsetPxProvider: () -> Float = remember(
        checklist.id,
        bottomOverlayInsetPxProvider,
        baseBottomOverlayInsetPxProvider
    ) {
        {
            if (isActivePageState.value) {
                bottomOverlayInsetPxProvider()
            } else {
                baseBottomOverlayInsetPxProvider()
            }
        }
    }

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
    val topOverlayInsetPxProvider: () -> Float = remember(
        checklist.id,
        progressCardHeightHolder,
        pageCollapseProvider
    ) {
        {
            progressCardHeightHolder.value * (1f - pageCollapseProvider())
        }
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
    val currentEditingItemIdState = rememberUpdatedState(editingItemId)

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
                        val bottomBound = (containerHeightPx - pageBottomOverlayInsetPxProvider())
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
                    onCancelEdit()
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
                                message = context.getString(
                                    R.string.snackbar_reset_checklist,
                                    checklist.name
                                ),
                                actionLabel = context.getString(R.string.action_undo),
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.restoreCheckedStates(checkedSnapshot)
                            }
                        }
                    }
                },
                canDeleteChecklist = canDeleteChecklist,
                onRenameChecklist = {
                    onOpenModal(
                        ChecklistModalContent.RenameChecklist(checklist),
                        renameButtonFocusRequester
                    )
                },
                onDeleteChecklist = {
                    onOpenModal(
                        ChecklistModalContent.DeleteChecklistConfirm(
                            checklist = checklist,
                            itemCount = items.size
                        ),
                        deleteButtonFocusRequester
                    )
                },
                renameButtonFocusRequester = renameButtonFocusRequester,
                deleteButtonFocusRequester = deleteButtonFocusRequester,
                modifier = Modifier
                    .zIndex(20f)
                    .graphicsLayer {
                        val collapse = pageCollapseProvider()
                        val h = size.height
                        translationY = if (collapse >= 0.99f) {
                            -h - 200f
                        } else {
                            -h * collapse
                        }
                        alpha = (1f - collapse * 1.4f).coerceIn(0f, 1f)
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
                                if (currentEditingItemIdState.value == itemId) continue
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
                            val maxOverlayY = (containerHeightPx - pageBottomOverlayInsetPxProvider() - rowHeightPx)
                                .coerceAtLeast(minOverlayY)

                            draggedItemHeightPx = rowHeightPx
                            dragOverlayY = rowTopInContainer.coerceIn(minOverlayY, maxOverlayY)
                            pointerYInContainerState.floatValue = down.position.y
                            workingItems = itemsNow.toList()
                            draggedItemId = hitItemId
                            onCancelEdit()
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
                                    val maxY = (containerHeightPx - pageBottomOverlayInsetPxProvider() - draggedItemHeightPx)
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
                    bottomInsetPxProvider = pageBottomOverlayInsetPxProvider,
                    pinnedExpandedKeyProvider = { currentEditingItemIdState.value },
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
                            onStartEdit = { onStartEdit(item.id, item.label) },
                            onEditDraftChanged = { draft -> onEditDraftChanged(item.id, draft) },
                            onConfirmEdit = { newLabel -> onConfirmEdit(item.id, newLabel) },
                            onCancelEdit = onCancelEdit,
                            onDelete = {
                                onClearEditingIfMatches(item.id)
                                viewModel.deleteItem(item) { deletedItem ->
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(
                                                R.string.snackbar_deleted_item,
                                                deletedItem.label
                                            ),
                                            actionLabel = context.getString(R.string.action_undo),
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
            progressPlaceable.placeRelative(0, 0)
        }
    }
}

@Composable
private fun QuickInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    imeInsets: WindowInsets,
    navBarInsets: WindowInsets,
    shouldFreezeImeProvider: () -> Boolean,
    hideProgressProvider: () -> Float,
    onBodyHeightChanged: (Float) -> Unit,
    onInputFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val bodyHeightHolder = remember { FloatValueHolder(0f) }

    // 將導覽列靜態底部留白維持於 Surface 內，並於 GPU graphicsLayer 階段讀取 IME 高度，確保 1:1 上移與返回下移歸位且 0 Layout Placement
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val imePx = if (shouldFreezeImeProvider()) {
                    0
                } else {
                    imeInsets.getBottom(density)
                }
                val navPx = navBarInsets.getBottom(density)
                val imeLiftPx = (imePx - navPx).coerceAtLeast(0).toFloat()
                val hideFraction = hideProgressProvider().coerceIn(0f, 1f)
                val hideDropPx = bodyHeightHolder.value * hideFraction +
                    if (hideFraction >= 0.99f) 2000f else 0f
                translationY = hideDropPx - imeLiftPx
                alpha = (1f - hideFraction * 1.4f).coerceIn(0f, 1f)
            },
        color = colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged {
                        val h = it.height.toFloat()
                        bodyHeightHolder.value = h
                        onBodyHeightChanged(h)
                    }
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
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { if (it.isFocused) onInputFocused() },
                        placeholder = { Text(stringResource(R.string.quick_add_placeholder)) },
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
                    TactileIconButton(
                        onClick = onSubmit,
                        enabled = canSubmit && value.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                        disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_add_item)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.windowInsetsBottomHeight(navBarInsets))
        }
    }
}

private data class ResetWaveState(
    val waveKey: Int = 0,
    val delaysByItemId: Map<Long, Int> = emptyMap(),
    val triggeredAtUptimeMs: Long = 0L
)

private class FloatValueHolder(var value: Float)

private class EditingDraftHolder(var text: String = "")

