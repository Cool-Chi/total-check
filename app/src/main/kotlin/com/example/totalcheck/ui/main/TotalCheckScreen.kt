package com.example.totalcheck.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.totalcheck.R
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.local.ReminderEntity
import com.example.totalcheck.data.reminder.ReminderFormatter
import com.example.totalcheck.data.repository.ReminderLongPressOutcome
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    val archivedItemsByChecklist by viewModel.archivedItemsByChecklist.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val pendingScrollToChecklistId by viewModel.pendingScrollToChecklistId.collectAsStateWithLifecycle()
    val pendingAutoFocusInput by viewModel.pendingAutoFocusInput.collectAsStateWithLifecycle()
    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
    val isDarkMode = themeConfig?.isDarkMode ?: true
    val useDynamicColor = themeConfig?.useDynamicColor ?: false
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val tagPool by viewModel.tagPool.collectAsStateWithLifecycle()
    val checklistTagPools by viewModel.checklistTagPools.collectAsStateWithLifecycle()
    val showUncheckedOnly = filterState.showUncheckedOnly
    val isStartupLoading by viewModel.isStartupLoading.collectAsStateWithLifecycle()

    val inputTextState = remember { mutableStateOf("") }
    var modalContent by remember { mutableStateOf<ChecklistModalContent?>(null) }
    var isModalOpen by remember { mutableStateOf(false) }
    var modalSessionKey by remember { mutableIntStateOf(0) }
    var returnFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    val quickInputFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val addChecklistFocusRequester = remember { FocusRequester() }
    val archiveFocusRequesters = remember { HashMap<Long, FocusRequester>() }
    val reminderFocusRequesters = remember { HashMap<Long, FocusRequester>() }
    val renameFocusRequesters = remember { HashMap<Long, FocusRequester>() }
    val deleteFocusRequesters = remember { HashMap<Long, FocusRequester>() }
    var editingItemId by remember { mutableStateOf<Long?>(null) }
    val editingDraftHolder = remember { EditingDraftHolder() }
    var suppressMainImeUntilClosed by remember { mutableStateOf(false) }
    val keyboardFocusController = remember { KeyboardFocusVisibleController() }

    val commitActiveInlineEdit: () -> Unit = {
        val currentEditId = editingItemId
        if (currentEditId != null) {
            val trimmed = editingDraftHolder.text.trim()
            if (trimmed.isNotEmpty()) {
                viewModel.renameItem(currentEditId, trimmed)
            }
            editingItemId = null
            editingDraftHolder.text = ""
            softwareKeyboardController?.hide()
            focusManager.clearFocus()
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
    var pendingQuickReminderAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val quickReminderPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val action = pendingQuickReminderAction
        pendingQuickReminderAction = null
        action?.invoke()
    }
    val triggerReminderLongPress: (Long, Long?, Boolean) -> Unit = { checklistId, itemId, hasActiveEnabledReminder ->
        commitActiveInlineEdit()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val executeLongPress: () -> Unit = {
            viewModel.handleReminderLongPress(
                checklistId = checklistId,
                itemId = itemId
            ) { result ->
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val message = when (result.outcome) {
                        ReminderLongPressOutcome.SET_QUICK_15M -> {
                            val saved = result.reminder
                            val formattedTime = if (saved != null) {
                                ReminderFormatter.formatTime(saved.hour, saved.minute)
                            } else {
                                ""
                            }
                            context.getString(
                                R.string.snackbar_quick_reminder_15m_set,
                                formattedTime
                            )
                        }
                        ReminderLongPressOutcome.SKIPPED_PERIODIC_ONCE -> {
                            val skipped = result.reminder
                            val nextText = if (skipped != null) {
                                ReminderFormatter.formatNextOccurrence(
                                    context = context,
                                    triggerEpochMillis = skipped.triggerEpochMillis
                                )
                            } else {
                                ""
                            }
                            context.getString(
                                R.string.snackbar_reminder_skipped_once,
                                nextText
                            )
                        }
                        ReminderLongPressOutcome.CANCELLED -> {
                            context.getString(R.string.snackbar_reminder_cancelled)
                        }
                    }
                    val actionResult = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short
                    )
                    if (actionResult == SnackbarResult.ActionPerformed) {
                        viewModel.undoReminderLongPress(result)
                    }
                }
            }
        }
        if (
            !hasActiveEnabledReminder &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingQuickReminderAction = executeLongPress
            quickReminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            executeLongPress()
        }
    }
    val submitHolder = remember {
        object {
            var onSubmit: (() -> Unit)? = null
        }
    }

    BackHandler(enabled = editingItemId != null) {
        commitActiveInlineEdit()
        softwareKeyboardController?.hide()
        focusManager.clearFocus()
    }

    var isTabInlineRenameActive by remember { mutableStateOf(false) }
    var openSettingsChecklistIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isHeaderOrCardInputActive = isTabInlineRenameActive || openSettingsChecklistIds.isNotEmpty()

    val imeInsets = WindowInsets.ime
    val navBarInsets = WindowInsets.navigationBars
    val isImeVisible = WindowInsets.isImeVisible
    val isImeVisibleState = rememberUpdatedState(isImeVisible)
    val isModalPresent = isModalOpen || modalContent != null
    val shouldFreezeMainIme = isModalPresent || suppressMainImeUntilClosed || isHeaderOrCardInputActive
    val shouldFreezeMainImeState = rememberUpdatedState(shouldFreezeMainIme)
    val isModalPresentState = rememberUpdatedState(isModalPresent)
    val isHeaderOrCardInputActiveState = rememberUpdatedState(isHeaderOrCardInputActive)

    // 彈窗完全關閉或編輯封存離場後：待軟鍵盤收合完成或最多 320ms 即自動解除主畫面 IME 凍結，確保背景零跳動且永不卡死
    LaunchedEffect(isModalPresent, suppressMainImeUntilClosed) {
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
            if (isModalPresentState.value || editingItemId != null || isHeaderOrCardInputActiveState.value) {
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

    // 動態校準實際軟鍵盤高度，確保資訊卡收合／展開速度與軟鍵盤上滑／下滑 1:1 逐幀同步
    val defaultImeHeightPx = with(density) { 260.dp.toPx() }
    val minImeCalibrateHeightPx = with(density) { 120.dp.toPx() }
    val minImeSpanPx = with(density) { 140.dp.toPx() }.coerceAtLeast(1f)
    val observedImeHeightHolder = remember(density) { FloatValueHolder(defaultImeHeightPx) }
    LaunchedEffect(density) {
        var lastStableCandidate = 0f
        var candidateSinceMs = 0L
        snapshotFlow {
            val imeBottom = imeInsets.getBottom(density).toFloat()
            val navBottom = navBarInsets.getBottom(density).toFloat()
            val imeExtra = (imeBottom - navBottom).coerceAtLeast(0f)
            imeExtra to isImeVisibleState.value
        }.collect { (imeExtra, imeVisible) ->
            if (imeVisible && imeExtra > minImeCalibrateHeightPx) {
                if (imeExtra > observedImeHeightHolder.value) {
                    observedImeHeightHolder.value = imeExtra
                    lastStableCandidate = imeExtra
                    candidateSinceMs = android.os.SystemClock.uptimeMillis()
                } else {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (abs(imeExtra - lastStableCandidate) > 2f) {
                        lastStableCandidate = imeExtra
                        candidateSinceMs = now
                    } else if (now - candidateSinceMs >= 120L && imeExtra < observedImeHeightHolder.value - 8f) {
                        observedImeHeightHolder.value = imeExtra
                    }
                }
            }
        }
    }

    // 外接實體鍵盤備援：僅於進入編輯後 220ms 內仍無軟鍵盤彈出時才收合資訊卡，一般軟鍵盤裝置完全交由 IME 幀同步驅動
    val isInlineEditingActive = editingItemId != null
    val hardwareKeyboardFallbackAnim = remember { Animatable(0f) }
    LaunchedEffect(isInlineEditingActive, isReducedMotionGlobal) {
        if (!isInlineEditingActive) {
            if (hardwareKeyboardFallbackAnim.value > 0.001f) {
                if (isReducedMotionGlobal) {
                    hardwareKeyboardFallbackAnim.snapTo(0f)
                } else {
                    hardwareKeyboardFallbackAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    )
                }
            }
            return@LaunchedEffect
        }
        val softImeActiveThresholdPx = with(density) { 24.dp.toPx() }
        delay(220L)
        val liveImeExtra = (imeInsets.getBottom(density) - navBarInsets.getBottom(density))
            .coerceAtLeast(0).toFloat()
        if (liveImeExtra < softImeActiveThresholdPx && !isImeVisibleState.value) {
            if (isReducedMotionGlobal) {
                hardwareKeyboardFallbackAnim.snapTo(1f)
            } else {
                hardwareKeyboardFallbackAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val progressCollapseFractionProvider: () -> Float = remember(
        imeInsets,
        navBarInsets,
        density,
        minImeSpanPx,
        observedImeHeightHolder,
        hardwareKeyboardFallbackAnim,
        shouldFreezeMainImeState
    ) {
        {
            val imeFraction = if (shouldFreezeMainImeState.value) {
                0f
            } else {
                val imePx = imeInsets.getBottom(density).toFloat()
                val navPx = navBarInsets.getBottom(density).toFloat()
                val imeExtra = (imePx - navPx).coerceAtLeast(0f)
                val spanPx = observedImeHeightHolder.value.coerceAtLeast(minImeSpanPx)
                (imeExtra / spanPx).coerceIn(0f, 1f)
            }
            val fallback = if (imeFraction > 0.05f) 0f else hardwareKeyboardFallbackAnim.value.coerceIn(0f, 1f)
            maxOf(fallback, imeFraction)
        }
    }

    val shouldHideQuickInput = editingItemId != null || isHeaderOrCardInputActive
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
                viewModel.renameItem(itemId, trimmed)
            }
            editingItemId = null
            editingDraftHolder.text = ""
            softwareKeyboardController?.hide()
            focusManager.clearFocus()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val onCancelInlineEditStable = remember<() -> Unit> {
        {
            editingItemId = null
            editingDraftHolder.text = ""
            softwareKeyboardController?.hide()
            focusManager.clearFocus()
        }
    }
    val onFinishEditForArchiveStable = remember<() -> Unit> {
        {
            suppressMainImeUntilClosed = true
            editingItemId = null
            editingDraftHolder.text = ""
            softwareKeyboardController?.hide()
            focusManager.clearFocus()
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
                val startupRevealAnim = remember { Animatable(if (isReadyForInput) 1f else 0f) }
                var showSkeletonOverlay by remember { mutableStateOf(!isReadyForInput) }
                LaunchedEffect(isReadyForInput, isReducedMotionGlobal) {
                    if (isReadyForInput) {
                        if (isReducedMotionGlobal) {
                            startupRevealAnim.snapTo(1f)
                        } else if (startupRevealAnim.value < 0.999f) {
                            startupRevealAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = 250,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                        showSkeletonOverlay = false
                    } else {
                        showSkeletonOverlay = true
                        startupRevealAnim.snapTo(0f)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (checklists.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = startupRevealAnim.value.coerceIn(0f, 1f)
                                    alpha = p
                                    val revealScale = if (isReducedMotionGlobal) 1f else lerp(0.988f, 1f, p)
                                    scaleX = revealScale
                                    scaleY = revealScale
                                }
                        ) {
                            val requestedInitialId = pendingScrollToChecklistId ?: viewModel.selectedChecklistId.value
                            val initialPageIndex = requestedInitialId
                                ?.let { id -> checklists.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                                ?: 0
                            val pagerState = rememberPagerState(
                                initialPage = initialPageIndex,
                                pageCount = { checklists.size }
                            )
                            val isReducedMotion = LocalReducedMotion.current
                            val distantTabTransitionState = remember { mutableStateOf<DistantTabTransition?>(null) }
                            val activeTabScrollJobHolder = remember { TabScrollJobHolder() }
                            val activeChecklistIdState = remember {
                                mutableStateOf(checklists.getOrNull(initialPageIndex)?.id)
                            }
                            val currentChecklistsInPager by rememberUpdatedState(checklists)
                            val pageViewModelHolder = remember(viewModel) { ChecklistPageViewModelHolder(viewModel) }

                            // 使用者手動拖曳 Pager 時立即取消遠距導航接管與編輯鎖定，保持 1:1 跟手控制
                            LaunchedEffect(pagerState) {
                                pagerState.interactionSource.interactions.collect { interaction ->
                                    if (interaction is DragInteraction.Start) {
                                        commitActiveInlineEdit()
                                        activeTabScrollJobHolder.job?.cancel()
                                        activeTabScrollJobHolder.job = null
                                        distantTabTransitionState.value = null
                                    }
                                }
                            }

                            val navigateToPage: (Int) -> Unit = remember(pagerState, scope, isReducedMotion, viewModel) {
                                { rawIndex ->
                                    val listNow = currentChecklistsInPager
                                    val targetIndex = rawIndex.coerceIn(0, (listNow.size - 1).coerceAtLeast(0))
                                    val targetId = listNow.getOrNull(targetIndex)?.id
                                    activeChecklistIdState.value = targetId
                                    if (targetId != null) {
                                        viewModel.selectChecklist(targetId)
                                    }
                                    commitActiveInlineEdit()
                                    activeTabScrollJobHolder.job?.cancel()
                                    val job = scope.launch {
                                        try {
                                            val currentPage = pagerState.currentPage
                                            if (targetIndex == currentPage && abs(pagerState.currentPageOffsetFraction) < 0.01f) {
                                                distantTabTransitionState.value = null
                                                return@launch
                                            }
                                            if (isReducedMotion) {
                                                distantTabTransitionState.value = null
                                                pagerState.scrollToPage(targetIndex)
                                                return@launch
                                            }
                                            val pageDistance = abs(targetIndex - currentPage)
                                            val spec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
                                            val preloadedRadius = (listNow.size - 1).coerceIn(1, 5)
                                            if (pageDistance <= 1) {
                                                distantTabTransitionState.value = null
                                                pagerState.animateScrollToPage(
                                                    page = targetIndex,
                                                    animationSpec = spec
                                                )
                                            } else if (pageDistance <= preloadedRadius) {
                                                val transition = DistantTabTransition(
                                                    fromPage = currentPage,
                                                    toPage = targetIndex
                                                )
                                                distantTabTransitionState.value = transition
                                                val tabAnimJob = launch {
                                                    transition.progress.animateTo(targetValue = 1f, animationSpec = spec)
                                                }
                                                pagerState.animateScrollToPage(
                                                    page = targetIndex,
                                                    animationSpec = spec
                                                )
                                                tabAnimJob.join()
                                            } else {
                                                // 超出常駐預載池之遠距頁籤：先於跳板頁錯峰預熱 Composition & Layout，避免首幀掉幀
                                                val stagingPage = if (targetIndex > currentPage) targetIndex - 1 else targetIndex + 1
                                                val transition = DistantTabTransition(
                                                    fromPage = currentPage,
                                                    toPage = targetIndex,
                                                    isPreparing = mutableStateOf(true)
                                                )
                                                distantTabTransitionState.value = transition
                                                withFrameNanos { }
                                                pagerState.scrollToPage(stagingPage)
                                                withFrameNanos { }
                                                transition.isPreparing.value = false
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
                                            if (activeTabScrollJobHolder.job == coroutineContext[Job]) {
                                                distantTabTransitionState.value = null
                                                activeTabScrollJobHolder.job = null
                                            }
                                        }
                                    }
                                    activeTabScrollJobHolder.job = job
                                }
                            }

                            LaunchedEffect(pagerState) {
                                snapshotFlow {
                                    val overrideTarget = distantTabTransitionState.value?.toPage
                                    val target = overrideTarget ?: pagerState.targetPage
                                    Triple(target, pagerState.isScrollInProgress, distantTabTransitionState.value != null)
                                }
                                    .collect { (targetIndex, inProgress, hasDistant) ->
                                        val listNow = currentChecklistsInPager
                                        val clamped = targetIndex.coerceIn(0, (listNow.size - 1).coerceAtLeast(0))
                                        if (clamped in listNow.indices && (inProgress || hasDistant || activeChecklistIdState.value == null)) {
                                            val id = listNow[clamped].id
                                            if (activeChecklistIdState.value != id) {
                                                activeChecklistIdState.value = id
                                                viewModel.selectChecklist(id)
                                            }
                                        }
                                    }
                            }

                            val checklistIdsKey = checklists.map { it.id }
                            LaunchedEffect(checklistIdsKey) {
                                val currentActiveId = activeChecklistIdState.value
                                if (currentActiveId == null) {
                                    val firstId = checklistIdsKey.firstOrNull()
                                    activeChecklistIdState.value = firstId
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
                                    activeChecklistIdState.value = fallbackId
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

                            LaunchedEffect(
                                pendingAutoFocusInput,
                                isReadyForInput,
                                pendingScrollToChecklistId,
                                isModalPresent
                            ) {
                                if (!pendingAutoFocusInput) return@LaunchedEffect
                                if (!isReadyForInput || pendingScrollToChecklistId != null || isModalPresent) {
                                    return@LaunchedEffect
                                }
                                editingItemId = null
                                editingDraftHolder.text = ""
                                suppressMainImeUntilClosed = false
                                quickInputHideAnim.snapTo(0f)
                                withFrameNanos { }
                                quickInputFocusRequester.requestFocus()
                                softwareKeyboardController?.show()
                                viewModel.consumeAutoFocusInput()
                            }

                            val submitNewItem: () -> Unit = {
                                val trimmed = inputTextState.value.trim()
                                if (trimmed.isNotEmpty() && pagerState.currentPage in checklists.indices) {
                                    val currentChecklistId = checklists[pagerState.currentPage].id
                                    viewModel.addItem(currentChecklistId, trimmed)
                                    inputTextState.value = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            submitHolder.onSubmit = submitNewItem

                            val stableChecklists = remember(checklists) { ImmutableList(checklists) }
                            val stableItemsByChecklist = remember(itemsByChecklist) { ImmutableMap(itemsByChecklist) }
                            val stableGlobalTagPool = remember(tagPool) { ImmutableList(tagPool) }
                            val distantTabTransitionProvider = remember(distantTabTransitionState) {
                                { distantTabTransitionState.value }
                            }
                            val activeChecklistIdProvider = remember(activeChecklistIdState) {
                                { activeChecklistIdState.value }
                            }
                            val onReorderChecklistsStable = remember(viewModel) {
                                { orderedIds: List<Long> -> viewModel.reorderChecklists(orderedIds) }
                            }
                            val onMoveChecklistLeftStable = remember(viewModel) {
                                { id: Long -> viewModel.moveChecklistByStep(id, -1) }
                            }
                            val onMoveChecklistRightStable = remember(viewModel) {
                                { id: Long -> viewModel.moveChecklistByStep(id, 1) }
                            }
                            val onAddChecklistStable = remember(addChecklistFocusRequester) {
                                {
                                    openModal(
                                        ChecklistModalContent.CreateChecklist,
                                        addChecklistFocusRequester
                                    )
                                }
                            }

                            val onRenameChecklistInlineStable = remember(viewModel) {
                                { id: Long, newName: String -> viewModel.renameChecklist(id, newName) }
                            }
                            val onInlineRenameActiveChangedStable = remember {
                                { active: Boolean -> isTabInlineRenameActive = active }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                ExpandingChecklistTabBar(
                                    checklists = stableChecklists,
                                    itemsByChecklist = stableItemsByChecklist,
                                    pagerState = pagerState,
                                    distantTabTransitionProvider = distantTabTransitionProvider,
                                    activeChecklistIdProvider = activeChecklistIdProvider,
                                    onSelectPage = navigateToPage,
                                    onReorderChecklists = onReorderChecklistsStable,
                                    onRenameChecklist = onRenameChecklistInlineStable,
                                    onInlineRenameActiveChanged = onInlineRenameActiveChangedStable,
                                    onMoveChecklistLeft = onMoveChecklistLeftStable,
                                    onMoveChecklistRight = onMoveChecklistRightStable,
                                    onAddChecklist = onAddChecklistStable,
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
                                        beyondViewportPageCount = (checklists.size - 1).coerceIn(1, 5),
                                        modifier = Modifier.fillMaxSize()
                                    ) { pageIndex ->
                                        val checklist = checklists.getOrNull(pageIndex) ?: return@HorizontalPager
                                        val rawItems = itemsByChecklist[checklist.id].orEmpty()
                                        val stableItems = remember(rawItems) { ImmutableList(rawItems) }
                                        val pageEditingItemId = if (editingItemId != null && rawItems.any { it.id == editingItemId }) {
                                            editingItemId
                                        } else {
                                            null
                                        }
                                        val isActivePageProvider = remember(pageIndex, pagerState, distantTabTransitionState) {
                                            {
                                                val distant = distantTabTransitionState.value
                                                if (distant != null) {
                                                    pageIndex == distant.fromPage || pageIndex == distant.toPage
                                                } else {
                                                    pageIndex == pagerState.currentPage || pageIndex == pagerState.targetPage
                                                }
                                            }
                                        }
                                        val archiveBtnFocus = remember(checklist.id) {
                                            archiveFocusRequesters.getOrPut(checklist.id) { FocusRequester() }
                                        }
                                        val reminderBtnFocus = remember(checklist.id) {
                                            reminderFocusRequesters.getOrPut(checklist.id) { FocusRequester() }
                                        }
                                        val renameBtnFocus = remember(checklist.id) {
                                            renameFocusRequesters.getOrPut(checklist.id) { FocusRequester() }
                                        }
                                        val deleteBtnFocus = remember(checklist.id) {
                                            deleteFocusRequesters.getOrPut(checklist.id) { FocusRequester() }
                                        }
                                        val rawArchivedItems = archivedItemsByChecklist[checklist.id].orEmpty()
                                        val stableArchivedItems = remember(rawArchivedItems) {
                                            ImmutableList(rawArchivedItems)
                                        }
                                        val archivedCount = rawArchivedItems.size
                                        val rawChecklistTagPool = checklistTagPools[checklist.id].orEmpty()
                                        val stableChecklistTagPool = remember(rawChecklistTagPool) {
                                            ImmutableList(rawChecklistTagPool)
                                        }
                                        val checklistReminders = remember(reminders, checklist.id) {
                                            reminders.filter { it.checklistId == checklist.id }
                                        }
                                        val activeChecklistReminder = remember(checklistReminders) {
                                            checklistReminders.firstOrNull {
                                                it.itemId == null && it.scheduleKind == ReminderEntity.KIND_REMINDER
                                            }
                                        }
                                        val activeItemIdSet = remember(rawItems) {
                                            rawItems.asSequence().map { it.id }.toHashSet()
                                        }
                                        val hasAnyActiveItemReminder = remember(checklistReminders, activeItemIdSet) {
                                            checklistReminders.any { r ->
                                                r.itemId != null &&
                                                    r.isEnabled &&
                                                    r.scheduleKind == ReminderEntity.KIND_REMINDER &&
                                                    r.itemId in activeItemIdSet
                                            }
                                        }
                                        val itemRemindersById = remember(checklistReminders) {
                                            ImmutableMap(
                                                checklistReminders.mapNotNull { r ->
                                                    if (r.scheduleKind == ReminderEntity.KIND_REMINDER) {
                                                        r.itemId?.let { id -> id to r }
                                                    } else {
                                                        null
                                                    }
                                                }.toMap()
                                            )
                                        }
                                        val itemAutoRestoresById = remember(checklistReminders) {
                                            ImmutableMap(
                                                checklistReminders.mapNotNull { r ->
                                                    if (r.scheduleKind == ReminderEntity.KIND_AUTO_RESTORE) {
                                                        r.itemId?.let { id -> id to r }
                                                    } else {
                                                        null
                                                    }
                                                }.toMap()
                                            )
                                        }
                                        val onSettingsPanelOpenChangedPage = remember(checklist.id) {
                                            { open: Boolean ->
                                                openSettingsChecklistIds = if (open) {
                                                    openSettingsChecklistIds + checklist.id
                                                } else {
                                                    openSettingsChecklistIds - checklist.id
                                                }
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    val distant = distantTabTransitionState.value
                                                    if (distant != null && distant.isPreparing.value) {
                                                        alpha = 0f
                                                    }
                                                }
                                        ) {
                                            ChecklistPageContent(
                                                checklist = checklist,
                                                stableItems = stableItems,
                                                stableArchivedItems = stableArchivedItems,
                                                archivedItemsCount = archivedCount,
                                                checklistReminder = activeChecklistReminder,
                                                hasAnyActiveItemReminder = hasAnyActiveItemReminder,
                                                stableItemRemindersById = itemRemindersById,
                                                stableItemAutoRestoresById = itemAutoRestoresById,
                                                canDeleteChecklist = checklists.size > 1,
                                                filterState = filterState,
                                                stableGlobalTagPool = stableGlobalTagPool,
                                                stableChecklistTagPool = stableChecklistTagPool,
                                                editingItemId = pageEditingItemId,
                                                isActivePageProvider = isActivePageProvider,
                                                isReducedMotion = isReducedMotion,
                                                viewModelHolder = pageViewModelHolder,
                                                snackbarHostState = snackbarHostState,
                                                progressCollapseFractionProvider = progressCollapseFractionProvider,
                                                observedImeHeightPxProvider = { observedImeHeightHolder.value },
                                                bottomOverlayInsetPxProvider = bottomOverlayInsetPxProvider,
                                                baseBottomOverlayInsetPxProvider = baseBottomOverlayInsetPxProvider,
                                                archiveButtonFocusRequester = archiveBtnFocus,
                                                reminderButtonFocusRequester = reminderBtnFocus,
                                                renameButtonFocusRequester = renameBtnFocus,
                                                deleteButtonFocusRequester = deleteBtnFocus,
                                                onStartEdit = onStartInlineEditStable,
                                                onEditDraftChanged = onEditDraftChangedStable,
                                                onConfirmEdit = onConfirmInlineEditStable,
                                                onCancelEdit = onCancelInlineEditStable,
                                                onFinishEditForArchive = onFinishEditForArchiveStable,
                                                onClearEditingIfMatches = onClearEditingIfMatchesStable,
                                                onOpenModal = onOpenModalStable,
                                                onReminderLongPress = triggerReminderLongPress,
                                                onSettingsPanelOpenChanged = onSettingsPanelOpenChangedPage
                                            )
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
                    if (showSkeletonOverlay) {
                        val skeletonBottomPadDp = with(density) {
                            (navBarInsets.getBottom(density).toFloat() + quickBarBodyHeightHolder.value).toDp()
                        }
                        TotalCheckSkeletonShimmer(
                            isDarkTheme = isDarkMode,
                            checklists = checklists,
                            itemsByChecklist = itemsByChecklist,
                            showUncheckedOnly = showUncheckedOnly,
                            activeChecklistId = pendingScrollToChecklistId ?: viewModel.selectedChecklistId.value,
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(10f)
                                .graphicsLayer {
                                    val p = startupRevealAnim.value.coerceIn(0f, 1f)
                                    alpha = 1f - p
                                    val exitScale = if (isReducedMotionGlobal) 1f else lerp(1f, 1.012f, p)
                                    scaleX = exitScale
                                    scaleY = exitScale
                                }
                                .then(
                                    if (!isReadyForInput) {
                                        Modifier.pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(bottom = skeletonBottomPadDp)
                        )
                    }
                }

                    QuickInputBarHost(
                        inputTextState = inputTextState,
                        onValueChange = {
                            if (editingItemId == null) {
                                suppressMainImeUntilClosed = false
                                inputTextState.value = it
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
                        inputFocusRequester = quickInputFocusRequester,
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
                    onSaveReminder = { checklistId, itemId, hour, minute, repeatDaysBitmask, onlyIfUnchecked, scheduleKind ->
                        closeModal(restoreFocus = true)
                        viewModel.saveReminder(
                            checklistId = checklistId,
                            itemId = itemId,
                            hour = hour,
                            minute = minute,
                            repeatDaysBitmask = repeatDaysBitmask,
                            onlyIfUnchecked = onlyIfUnchecked,
                            scheduleKind = scheduleKind,
                        ) { saved ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val summaryText = ReminderFormatter.formatSavedSummary(
                                    context = context,
                                    hour = saved.hour,
                                    minute = saved.minute,
                                    repeatDaysBitmask = saved.repeatDaysBitmask
                                )
                                val messageRes = if (saved.scheduleKind == ReminderEntity.KIND_AUTO_RESTORE) {
                                    R.string.snackbar_auto_restore_saved
                                } else {
                                    R.string.snackbar_reminder_saved
                                }
                                snackbarHostState.showSnackbar(
                                    message = context.getString(messageRes, summaryText),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onDeleteReminder = { reminderId, scheduleKind ->
                        closeModal(restoreFocus = false)
                        viewModel.deleteReminder(reminderId) {
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val messageRes = if (scheduleKind == ReminderEntity.KIND_AUTO_RESTORE) {
                                    R.string.snackbar_auto_restore_deleted
                                } else {
                                    R.string.snackbar_reminder_deleted
                                }
                                snackbarHostState.showSnackbar(
                                    message = context.getString(messageRes),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onSendTestReminder = { checklistId, itemId, hour, minute, repeatDaysBitmask, onlyIfUnchecked ->
                        viewModel.sendTestReminderNotification(
                            checklistId = checklistId,
                            itemId = itemId,
                            hour = hour,
                            minute = minute,
                            repeatDaysBitmask = repeatDaysBitmask,
                            onlyIfUnchecked = onlyIfUnchecked
                        ) { posted ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        if (posted) {
                                            R.string.snackbar_test_notification_sent
                                        } else {
                                            R.string.snackbar_notif_permission_denied
                                        }
                                    ),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onNotificationPermissionDenied = {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.snackbar_notif_permission_denied),
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    archivedItemsByChecklist = archivedItemsByChecklist,
                    onUnarchiveItem = { item ->
                        viewModel.unarchiveItem(item) { restored ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        R.string.snackbar_unarchived_item,
                                        restored.label
                                    ),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onDeleteArchivedItemForever = { item ->
                        viewModel.deleteItem(item) { deleted ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        R.string.snackbar_deleted_archived_item,
                                        deleted.label
                                    ),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    globalTagPool = tagPool,
                    checklistTagPools = checklistTagPools,
                    onSaveItemTags = { checklistId, itemId, previousTags, tags ->
                        closeModal(restoreFocus = true)
                        viewModel.updateItemTags(checklistId, itemId, previousTags, tags)
                    },
                    onSelectFilterTag = { selectedTagToken, checklistId ->
                        closeModal(restoreFocus = true)
                        viewModel.selectFilterTag(selectedTagToken, checklistId)
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

private class TabScrollJobHolder(var job: Job? = null)

private class VisibleListTopTracker(var size: Int, var firstId: Long?)

@Stable
private class ChecklistPageViewModelHolder(val viewModel: MainViewModel)

@Composable
private fun QuickInputBarHost(
    inputTextState: MutableState<String>,
    onValueChange: (String) -> Unit,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    imeInsets: WindowInsets,
    navBarInsets: WindowInsets,
    shouldFreezeImeProvider: () -> Boolean,
    hideProgressProvider: () -> Float,
    onBodyHeightChanged: (Float) -> Unit,
    onInputFocused: () -> Unit,
    inputFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    QuickInputBar(
        value = inputTextState.value,
        onValueChange = onValueChange,
        canSubmit = canSubmit,
        onSubmit = onSubmit,
        imeInsets = imeInsets,
        navBarInsets = navBarInsets,
        shouldFreezeImeProvider = shouldFreezeImeProvider,
        hideProgressProvider = hideProgressProvider,
        onBodyHeightChanged = onBodyHeightChanged,
        onInputFocused = onInputFocused,
        inputFocusRequester = inputFocusRequester,
        modifier = modifier
    )
}

@Composable
private fun ChecklistPageContent(
    checklist: ChecklistEntity,
    stableItems: ImmutableList<CheckItemEntity>,
    stableArchivedItems: ImmutableList<CheckItemEntity>,
    archivedItemsCount: Int,
    checklistReminder: ReminderEntity?,
    hasAnyActiveItemReminder: Boolean,
    stableItemRemindersById: ImmutableMap<Long, ReminderEntity>,
    stableItemAutoRestoresById: ImmutableMap<Long, ReminderEntity>,
    canDeleteChecklist: Boolean,
    filterState: ChecklistFilterState,
    stableGlobalTagPool: ImmutableList<TagDefinition>,
    stableChecklistTagPool: ImmutableList<TagDefinition>,
    editingItemId: Long?,
    isActivePageProvider: () -> Boolean,
    isReducedMotion: Boolean,
    viewModelHolder: ChecklistPageViewModelHolder,
    snackbarHostState: SnackbarHostState,
    progressCollapseFractionProvider: () -> Float,
    observedImeHeightPxProvider: () -> Float,
    bottomOverlayInsetPxProvider: () -> Float,
    baseBottomOverlayInsetPxProvider: () -> Float,
    archiveButtonFocusRequester: FocusRequester,
    reminderButtonFocusRequester: FocusRequester,
    renameButtonFocusRequester: FocusRequester,
    deleteButtonFocusRequester: FocusRequester,
    onStartEdit: (Long, String) -> Unit,
    onEditDraftChanged: (Long, String) -> Unit,
    onConfirmEdit: (Long, String) -> Unit,
    onCancelEdit: () -> Unit,
    onFinishEditForArchive: () -> Unit,
    onClearEditingIfMatches: (Long) -> Unit,
    onOpenModal: (ChecklistModalContent, FocusRequester?) -> Unit,
    onReminderLongPress: (Long, Long?, Boolean) -> Unit,
    onSettingsPanelOpenChanged: (Boolean) -> Unit
) {
    val items = stableItems.items
    val archivedItems = stableArchivedItems.items
    val globalTagPool = stableGlobalTagPool.items
    val checklistTagPool = stableChecklistTagPool.items
    val mergedAvailableTags = remember(globalTagPool, checklistTagPool) {
        globalTagPool + checklistTagPool
    }
    val showUncheckedOnly = filterState.showUncheckedOnly
    val itemRemindersById = stableItemRemindersById.map
    val itemAutoRestoresById = stableItemAutoRestoresById.map
    val viewModel = viewModelHolder.viewModel
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val imeInsets = WindowInsets.ime
    val navBarInsets = WindowInsets.navigationBars
    val currentIsActivePageProvider by rememberUpdatedState(isActivePageProvider)

    val pageCollapseProvider: () -> Float = remember(
        checklist.id,
        progressCollapseFractionProvider
    ) {
        {
            if (currentIsActivePageProvider()) progressCollapseFractionProvider() else 0f
        }
    }
    val pageBottomOverlayInsetPxProvider: () -> Float = remember(
        checklist.id,
        bottomOverlayInsetPxProvider,
        baseBottomOverlayInsetPxProvider
    ) {
        {
            if (currentIsActivePageProvider()) {
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

    val needsProgressAnim = abs(progressAnimatable.targetValue - targetProgress) > 0.0001f ||
        abs(progressAnimatable.value - targetProgress) > 0.0005f
    if (needsProgressAnim) {
        LaunchedEffect(targetProgress, isReducedMotion) {
            if (isReducedMotion) {
                progressAnimatable.snapTo(targetProgress)
            } else if (targetProgress > progressAnimatable.value) {
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

    var stagingCheckedItemIds by remember(checklist.id) { mutableStateOf<Set<Long>>(emptySet()) }
    val stagingJobs = remember(checklist.id) { HashMap<Long, Job>() }
    DisposableEffect(checklist.id) {
        onDispose {
            stagingJobs.values.forEach { it.cancel() }
            stagingJobs.clear()
        }
    }
    if ((!showUncheckedOnly || isReducedMotion) && (stagingCheckedItemIds.isNotEmpty() || stagingJobs.isNotEmpty())) {
        LaunchedEffect(showUncheckedOnly, isReducedMotion) {
            stagingJobs.values.forEach { it.cancel() }
            stagingJobs.clear()
            stagingCheckedItemIds = emptySet()
        }
    }

    val visibleItems = remember(items, filterState, checklist.id, stagingCheckedItemIds) {
        if (!filterState.isActiveForChecklist(checklist.id)) {
            items
        } else {
            items.filter { filterState.matches(it, stagingCheckedItemIds) }
        }
    }

    val currentFirstItemId = visibleItems.firstOrNull()?.id
    val visibleTopTracker = remember(checklist.id) {
        VisibleListTopTracker(size = visibleItems.size, firstId = currentFirstItemId)
    }

    if (visibleItems.size != visibleTopTracker.size || currentFirstItemId != visibleTopTracker.firstId) {
        LaunchedEffect(visibleItems.size, currentFirstItemId) {
            if (visibleItems.size > visibleTopTracker.size && currentFirstItemId != visibleTopTracker.firstId) {
                stackedListState.scrollToTop(animated = !isReducedMotion)
            }
            visibleTopTracker.size = visibleItems.size
            visibleTopTracker.firstId = currentFirstItemId
        }
    }

    // 進入單項原地編輯時記錄編輯前捲動位置，僅於項目會被軟鍵盤遮擋或壓縮於頂部堆疊時微幅捲動；
    // 結束編輯時恢復至編輯前捲動位置（若使用者編輯期間未主動手動捲動），確保中間項目隨資訊卡平順歸位、絕不位移至頂部
    val editBottomFloorAnim = remember(checklist.id) { Animatable(0f) }
    val preEditScrollOffsetHolder = remember(checklist.id) { FloatValueHolder(-1f) }
    LaunchedEffect(editingItemId, isReducedMotion) {
        val targetEditId = editingItemId
        if (targetEditId == null) {
            val savedPreEditScroll = preEditScrollOffsetHolder.value
            preEditScrollOffsetHolder.value = -1f
            val startFloor = stackedListState.editBottomInsetFloorState.floatValue
            val shouldRestoreScroll = savedPreEditScroll >= 0f &&
                !stackedListState.userScrolledManuallySinceReset &&
                !stackedListState.hasActiveExitAnchor() &&
                abs(stackedListState.scrollOffsetPx - savedPreEditScroll) > 1f

            if (isReducedMotion) {
                editBottomFloorAnim.snapTo(0f)
                stackedListState.editBottomInsetFloorState.floatValue = 0f
                if (shouldRestoreScroll) {
                    stackedListState.animateScrollToOffset(
                        targetScrollPx = savedPreEditScroll,
                        animated = false
                    )
                }
            } else if (shouldRestoreScroll || startFloor > 0.5f) {
                coroutineScope {
                    if (shouldRestoreScroll) {
                        launch {
                            stackedListState.animateScrollToOffset(
                                targetScrollPx = savedPreEditScroll,
                                durationMs = 240,
                                animated = true
                            )
                        }
                    }
                    if (startFloor > 0.5f) {
                        launch {
                            editBottomFloorAnim.snapTo(startFloor)
                            editBottomFloorAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                            ) {
                                stackedListState.editBottomInsetFloorState.floatValue = value
                            }
                            stackedListState.editBottomInsetFloorState.floatValue = 0f
                        }
                    }
                }
            }
            return@LaunchedEffect
        }
        if (visibleItems.none { it.id == targetEditId }) return@LaunchedEffect

        stackedListState.clearExitAnchorLock()
        stackedListState.userScrolledManuallySinceReset = false
        preEditScrollOffsetHolder.value = stackedListState.scrollOffsetPx

        val expectedImePx = observedImeHeightPxProvider()
            .coerceAtLeast(with(density) { 240.dp.toPx() })
        val targetBottomInset = maxOf(
            imeInsets.getBottom(density).toFloat(),
            navBarInsets.getBottom(density).toFloat() + expectedImePx
        )
        withFrameNanos { }
        val neededTargetScroll = stackedListState.computeMinimalScrollForEditVisibility(
            key = targetEditId,
            targetTopInsetPx = 0f,
            targetBottomInsetPx = targetBottomInset,
            topBreathingRoomPx = with(density) { 12.dp.toPx() },
            bottomBreathingRoomPx = with(density) { 24.dp.toPx() }
        )
        if (neededTargetScroll != null) {
            if (neededTargetScroll > stackedListState.maxScrollPx) {
                editBottomFloorAnim.snapTo(targetBottomInset)
                stackedListState.editBottomInsetFloorState.floatValue = targetBottomInset
                withFrameNanos { }
            }
            stackedListState.animateScrollToOffset(
                targetScrollPx = neededTargetScroll,
                durationMs = 240,
                animated = !isReducedMotion
            )
        }
    }

    val progressCardHeightState = remember(checklist.id) {
        mutableFloatStateOf(with(density) { 142.dp.toPx() })
    }
    val topOverlayInsetPxProvider: () -> Float = remember(
        checklist.id,
        progressCardHeightState,
        pageCollapseProvider
    ) {
        {
            progressCardHeightState.floatValue * (1f - pageCollapseProvider())
        }
    }

    var containerWidthPx by remember(checklist.id) { mutableFloatStateOf(0f) }
    var containerHeightPx by remember(checklist.id) { mutableFloatStateOf(0f) }
    var draggedItemId by remember(checklist.id) { mutableStateOf<Long?>(null) }
    var workingItems by remember(checklist.id) { mutableStateOf<List<CheckItemEntity>>(emptyList()) }
    var initialWorkingItems by remember(checklist.id) { mutableStateOf<List<CheckItemEntity>>(emptyList()) }
    var committedOrderIds by remember(checklist.id) { mutableStateOf<List<Long>?>(null) }
    var draggedItemHeightPx by remember(checklist.id) { mutableFloatStateOf(0f) }
    var rawDragOverlayY by remember(checklist.id) { mutableFloatStateOf(0f) }
    var isSettlingDrop by remember(checklist.id) { mutableStateOf(false) }
    val springTracker = remember(checklist.id) {
        MagneticSpringTracker(
            dampingRatio = 0.85f,
            stiffness = 520f,
            visibilityThreshold = 0.5f,
            velocityThreshold = 4f
        )
    }
    val pointerYInContainerState = remember(checklist.id) { mutableFloatStateOf(0f) }

    val enterRadiusPx = with(density) { 72.dp.toPx() }
    val magneticAttractor = remember(checklist.id, enterRadiusPx) {
        MagneticAttractorState(defaultEnterRadiusPx = enterRadiusPx, commitThreshold = 0.35f)
    }
    val consecutiveDeleteTracker = remember(checklist.id) { ConsecutiveDeleteTracker() }
    val moveToBottomVisualState = remember(checklist.id) { MoveToBottomVisualState() }
    val moveToBottomTransitionSpec: ItemMoveToBottomTransitionSpec = DefaultItemMoveToBottomTransitionSpec
    var slotTopsBuffer by remember(checklist.id) { mutableStateOf(FloatArray(64)) }
    var slotCentersBuffer by remember(checklist.id) { mutableStateOf(FloatArray(64)) }

    val currentVisibleItemsState = rememberUpdatedState(visibleItems)
    val currentShowUncheckedOnlyState = rememberUpdatedState(showUncheckedOnly)
    val isReducedMotionState = rememberUpdatedState(isReducedMotion)
    val currentEditingItemIdState = rememberUpdatedState(editingItemId)

    DisposableEffect(checklist.id) {
        onDispose {
            stackedListState.draggedItemKey = null
            draggedItemId = null
            isSettlingDrop = false
            workingItems = emptyList()
            initialWorkingItems = emptyList()
            committedOrderIds = null
            magneticAttractor.reset()
            moveToBottomVisualState.isArmed = false
            moveToBottomVisualState.isMovingToBottom = false
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

    val recalculateInsertionAndMagnet: (Float) -> Unit = { rawTopY ->
        val currentDraggedId = draggedItemId
        val currentList = workingItems
        if (currentDraggedId != null && currentList.isNotEmpty()) {
            val currentIndex = currentList.indexOfFirst { it.id == currentDraggedId }
            if (currentIndex >= 0) {
                val requiredSlots = currentList.size + 1
                if (slotTopsBuffer.size < requiredSlots) {
                    slotTopsBuffer = FloatArray(maxOf(requiredSlots, slotTopsBuffer.size * 2))
                }
                if (slotCentersBuffer.size < requiredSlots) {
                    slotCentersBuffer = FloatArray(maxOf(requiredSlots, slotCentersBuffer.size * 2))
                }
                val slotCount = stackedListState.fillCandidateSlotsForDraggedItem(
                    draggedKey = currentDraggedId,
                    draggedHeightPx = draggedItemHeightPx,
                    outSlotTops = slotTopsBuffer,
                    outSlotCenters = slotCentersBuffer
                )
                val count = minOf(currentList.size, slotCount)
                val blendedTopY = magneticAttractor.resolveBlendedTopY(
                    rawTopY = rawTopY,
                    draggedHeightPx = draggedItemHeightPx,
                    slotCount = count,
                    slotTopsY = slotTopsBuffer,
                    slotCentersY = slotCentersBuffer,
                    avgSlotPitchPx = stackedListState.currentAvgSlotPitchPx
                )
                val minY = topOverlayInsetPxProvider().coerceAtLeast(0f)
                val maxY = (containerHeightPx - pageBottomOverlayInsetPxProvider() - draggedItemHeightPx)
                    .coerceAtLeast(minY)
                val clampedTargetY = blendedTopY.coerceIn(minY, maxY)
                if (isReducedMotionState.value) {
                    springTracker.snapTo(clampedTargetY)
                } else {
                    springTracker.setTarget(clampedTargetY)
                }

                if (magneticAttractor.canCommit()) {
                    val targetSlotIdx = magneticAttractor.activeTargetSlotIndex
                    if (targetSlotIdx in 0 until currentList.size && targetSlotIdx != currentIndex) {
                        val mutable = currentList.toMutableList()
                        val moved = mutable.removeAt(currentIndex)
                        mutable.add(targetSlotIdx, moved)
                        workingItems = mutable
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                } else if (initialWorkingItems.isNotEmpty() && currentList != initialWorkingItems) {
                    workingItems = initialWorkingItems
                }
            }
        }
    }

    // 單一 VSYNC 迴圈同時步進連續欠阻尼彈簧與漸變邊緣自動捲動（0 Cancellation Starvation / 0 重組）
    LaunchedEffect(draggedItemId, isSettlingDrop) {
        if (draggedItemId != null && !isSettlingDrop) {
            springTracker.resetFrameClock()
            val maxEdgeZonePx = with(density) { 48.dp.toPx() }
            val maxSpeedPxPerSec = with(density) { 300.dp.toPx() }
            var lastFrameNanos = 0L
            while (isActive) {
                withFrameNanos { frameNanos ->
                    if (!isReducedMotionState.value) {
                        springTracker.stepFrame(frameNanos)
                    }
                    val dtSec = if (lastFrameNanos == 0L) {
                        0f
                    } else {
                        ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    }
                    lastFrameNanos = frameNanos
                    if (!moveToBottomVisualState.isArmed && dtSec > 0f && containerHeightPx > 0f) {
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
                                recalculateInsertionAndMagnet(rawDragOverlayY)
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
                checklistId = checklist.id,
                uncheckedCount = uncheckedCount,
                totalCount = totalCount,
                progressProvider = { progressAnimatable.value },
                filterState = filterState,
                globalTagPool = globalTagPool,
                checklistTagPool = checklistTagPool,
                onToggleFilter = { viewModel.toggleFilterUncheckedOnly() },
                onLongClickFilter = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenModal(
                        ChecklistModalContent.SelectFilterTag(
                            activeTagToken = filterState.effectiveSelectedTagsForChecklist(checklist.id).firstOrNull(),
                            checklistId = checklist.id
                        ),
                        null
                    )
                },
                onReset = {
                    onCancelEdit()
                    stagingJobs.values.forEach { it.cancel() }
                    stagingJobs.clear()
                    stagingCheckedItemIds = emptySet()
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
                archivedCount = archivedItemsCount,
                archivedItems = archivedItems,
                onOpenArchivedItems = {
                    onOpenModal(
                        ChecklistModalContent.ArchivedItems(checklist),
                        archiveButtonFocusRequester
                    )
                },
                onUnarchiveItem = { archivedItem ->
                    viewModel.unarchiveItem(archivedItem) {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = context.getString(
                                    R.string.snackbar_unarchived_item,
                                    archivedItem.label
                                ),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onDeleteArchivedItemForever = { archivedItem ->
                    viewModel.deleteItem(archivedItem) {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = context.getString(
                                    R.string.snackbar_deleted_archived_item,
                                    archivedItem.label
                                ),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onCreateTagInPool = { name, iconKey, tagScope ->
                    viewModel.createTagInPool(
                        name = name,
                        iconKey = iconKey,
                        scope = tagScope,
                        checklistId = if (tagScope == TagScope.CHECKLIST) checklist.id else null
                    ) { created ->
                        if (created != null) {
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        R.string.snackbar_created_pool_tag,
                                        created.name
                                    ),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                },
                onDeleteTagFromPool = { tagDef ->
                    viewModel.deleteTagFromPool(
                        tag = tagDef,
                        checklistId = if (tagDef.scope == TagScope.CHECKLIST) checklist.id else null
                    ) { snapshot ->
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(
                                    R.string.snackbar_deleted_pool_tag,
                                    snapshot.tag.name
                                ),
                                actionLabel = context.getString(R.string.action_undo),
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.restoreDeletedTag(snapshot)
                            }
                        }
                    }
                },
                activeChecklistReminder = checklistReminder,
                hasAnyActiveItemReminder = hasAnyActiveItemReminder,
                onOpenChecklistReminder = {
                    onOpenModal(
                        ChecklistModalContent.ConfigureReminder(
                            checklist = checklist,
                            item = null,
                            existingReminder = checklistReminder,
                        ),
                        reminderButtonFocusRequester
                    )
                },
                onQuickChecklistReminder15m = {
                    onReminderLongPress(
                        checklist.id,
                        null,
                        checklistReminder?.isEnabled == true
                    )
                },
                onRenameChecklist = {
                    onOpenModal(
                        ChecklistModalContent.RenameChecklist(checklist),
                        renameButtonFocusRequester
                    )
                },
                onLongClickRenameChecklist = {
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.snackbar_edit_card_layout_reserved),
                            duration = SnackbarDuration.Short
                        )
                    }
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
                onSettingsPanelOpenChanged = onSettingsPanelOpenChanged,
                archiveButtonFocusRequester = archiveButtonFocusRequester,
                reminderButtonFocusRequester = reminderButtonFocusRequester,
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
                        val moveBottomThresholdPx = with(density) { 56.dp.toPx() }
                        val moveBottomDisarmPx = with(density) { 44.dp.toPx() }
                        val horizontalPullSlopPx = with(density) { 12.dp.toPx() }
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

                            // 命中右側六點拖曳手把：立即消費事件並開啟零延遲拖曳與磁吸追蹤
                            down.consume()

                            val rowTopInContainer = matchedInfo.placedTopY
                            val rowHeightPx = matchedInfo.heightPx.coerceAtLeast(1f)
                            val grabOffsetY = down.position.y - rowTopInContainer
                            val startPointerX = down.position.x
                            val minOverlayY = topOverlayInsetPxProvider().coerceAtLeast(0f)
                            val maxOverlayY = (containerHeightPx - pageBottomOverlayInsetPxProvider() - rowHeightPx)
                                .coerceAtLeast(minOverlayY)

                            stackedListState.clearOverscrollImmediate()
                            stackedListState.clearExitAnchorLock()
                            val initialOrder = itemsNow.toList()
                            val startIdx = initialOrder.indexOfFirst { it.id == hitItemId }
                            initialWorkingItems = initialOrder
                            workingItems = initialOrder
                            magneticAttractor.reset(initialSlotIndex = startIdx)
                            moveToBottomVisualState.isArmed = false
                            moveToBottomVisualState.isMovingToBottom = false
                            draggedItemHeightPx = rowHeightPx
                            val startTopY = rowTopInContainer.coerceIn(minOverlayY, maxOverlayY)
                            rawDragOverlayY = startTopY
                            springTracker.snapTo(startTopY)
                            pointerYInContainerState.floatValue = down.position.y
                            stackedListState.draggedItemKey = hitItemId
                            draggedItemId = hitItemId
                            onCancelEdit()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            var gestureCancelled = false
                            var scopeDisposed = false
                            var isMoveToBottomArmed = false
                            var horizontalPullJob: Job? = null
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        gestureCancelled = true
                                        break
                                    }
                                    if (!change.pressed) {
                                        break
                                    }
                                    change.consume()

                                    val dx = change.position.x - startPointerX
                                    val nextArmed = if (isMoveToBottomArmed) {
                                        dx <= -moveBottomDisarmPx
                                    } else {
                                        dx <= -moveBottomThresholdPx
                                    }
                                    if (nextArmed != isMoveToBottomArmed) {
                                        isMoveToBottomArmed = nextArmed
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isMoveToBottomArmed) {
                                            if (initialWorkingItems.isNotEmpty() && workingItems != initialWorkingItems) {
                                                workingItems = initialWorkingItems
                                            }
                                            if (startIdx >= 0) {
                                                magneticAttractor.reset(initialSlotIndex = startIdx)
                                            }
                                        }
                                    }

                                    val effectivePullDx = if (dx < -horizontalPullSlopPx) {
                                        dx + horizontalPullSlopPx
                                    } else {
                                        0f
                                    }
                                    if (
                                        effectivePullDx < 0f ||
                                        moveToBottomVisualState.translationX.value != 0f ||
                                        moveToBottomVisualState.isArmed != isMoveToBottomArmed
                                    ) {
                                        horizontalPullJob?.cancel()
                                        val armedSnapshot = isMoveToBottomArmed
                                        horizontalPullJob = scope.launch {
                                            moveToBottomTransitionSpec.onHorizontalPull(
                                                visualState = moveToBottomVisualState,
                                                effectivePullDxPx = effectivePullDx,
                                                thresholdPx = moveBottomThresholdPx,
                                                isArmed = armedSnapshot
                                            )
                                        }
                                    }

                                    val newPointerY = change.position.y
                                    pointerYInContainerState.floatValue = newPointerY
                                    val minY = topOverlayInsetPxProvider().coerceAtLeast(0f)
                                    val maxY = (containerHeightPx - pageBottomOverlayInsetPxProvider() - draggedItemHeightPx)
                                        .coerceAtLeast(minY)
                                    val nextRawTopY = (newPointerY - grabOffsetY).coerceIn(minY, maxY)
                                    rawDragOverlayY = nextRawTopY
                                    if (isMoveToBottomArmed) {
                                        if (isReducedMotionState.value) {
                                            springTracker.snapTo(startTopY)
                                        } else {
                                            springTracker.setTarget(startTopY)
                                        }
                                    } else {
                                        recalculateInsertionAndMagnet(nextRawTopY)
                                    }
                                }
                            } catch (e: CancellationException) {
                                scopeDisposed = true
                                throw e
                            } finally {
                                horizontalPullJob?.cancel()
                                val activeId = draggedItemId
                                val canCommitMoveToBottom = !gestureCancelled &&
                                    isMoveToBottomArmed &&
                                    activeId != null &&
                                    initialWorkingItems.lastOrNull()?.id != activeId
                                val finalOrderIds = workingItems.map { it.id }
                                val canCommitOrder = !gestureCancelled &&
                                    !canCommitMoveToBottom &&
                                    !isMoveToBottomArmed &&
                                    magneticAttractor.canCommit() &&
                                    finalOrderIds.isNotEmpty()
                                if (scopeDisposed) {
                                    stackedListState.draggedItemKey = null
                                    draggedItemId = null
                                    isSettlingDrop = false
                                    workingItems = emptyList()
                                    initialWorkingItems = emptyList()
                                    magneticAttractor.reset()
                                    moveToBottomVisualState.isArmed = false
                                    moveToBottomVisualState.isMovingToBottom = false
                                } else if (activeId != null && canCommitMoveToBottom) {
                                    isSettlingDrop = true
                                    val itemToMove = initialWorkingItems.firstOrNull { it.id == activeId }
                                    val movedToBottomList = if (itemToMove != null) {
                                        initialWorkingItems.filter { it.id != activeId } + itemToMove
                                    } else {
                                        initialWorkingItems
                                    }
                                    workingItems = movedToBottomList
                                    committedOrderIds = movedToBottomList.map { it.id }
                                    scope.launch {
                                        try {
                                            moveToBottomTransitionSpec.onCommitMoveToBottom(
                                                visualState = moveToBottomVisualState,
                                                springTracker = springTracker,
                                                resolveTargetBottomTopY = {
                                                    stackedListState.resolveSlotTargetTopY(activeId)
                                                },
                                                isReducedMotion = isReducedMotionState.value,
                                                onCommitOrder = {
                                                    viewModel.moveItemToBottom(
                                                        checklistId = checklist.id,
                                                        itemId = activeId,
                                                        showUncheckedOnly = currentShowUncheckedOnlyState.value
                                                    )
                                                }
                                            )
                                        } finally {
                                            stackedListState.suppressNextReflowFor(activeId)
                                            stackedListState.draggedItemKey = null
                                            draggedItemId = null
                                            isSettlingDrop = false
                                            workingItems = emptyList()
                                            initialWorkingItems = emptyList()
                                            magneticAttractor.reset()
                                        }
                                    }
                                } else if (activeId != null) {
                                    isSettlingDrop = true
                                    if (canCommitOrder) {
                                        committedOrderIds = finalOrderIds
                                        viewModel.reorderChecklistItems(
                                            checklistId = checklist.id,
                                            reorderedVisibleIds = finalOrderIds,
                                            showUncheckedOnly = currentShowUncheckedOnlyState.value
                                        )
                                    } else if (initialWorkingItems.isNotEmpty()) {
                                        workingItems = initialWorkingItems
                                    }
                                    scope.launch {
                                        val cancelHorizJob = launch {
                                            moveToBottomTransitionSpec.onCancelPull(
                                                visualState = moveToBottomVisualState,
                                                isReducedMotion = isReducedMotionState.value
                                            )
                                        }
                                        try {
                                            val firstFrameNanos = withFrameNanos { it }
                                            val targetY = stackedListState.resolveSlotTargetTopY(activeId)
                                                ?: springTracker.currentValueState.floatValue
                                            if (isReducedMotionState.value) {
                                                springTracker.snapTo(targetY)
                                            } else {
                                                springTracker.setTarget(targetY)
                                                var settled = springTracker.stepFrame(firstFrameNanos)
                                                val settleStartNanos = firstFrameNanos
                                                while (isActive && !settled) {
                                                    withFrameNanos { frameNanos ->
                                                        val elapsedNanos = frameNanos - settleStartNanos
                                                        settled = springTracker.stepFrame(frameNanos) ||
                                                            elapsedNanos >= 500_000_000L
                                                    }
                                                }
                                                springTracker.snapTo(targetY)
                                            }
                                            cancelHorizJob.join()
                                        } finally {
                                            cancelHorizJob.cancel()
                                            moveToBottomVisualState.snapReset()
                                            stackedListState.suppressNextReflowFor(activeId)
                                            stackedListState.draggedItemKey = null
                                            draggedItemId = null
                                            isSettlingDrop = false
                                            workingItems = emptyList()
                                            initialWorkingItems = emptyList()
                                            magneticAttractor.reset()
                                        }
                                    }
                                } else {
                                    stackedListState.draggedItemKey = null
                                    draggedItemId = null
                                    isSettlingDrop = false
                                    workingItems = emptyList()
                                    initialWorkingItems = emptyList()
                                    magneticAttractor.reset()
                                    moveToBottomVisualState.isArmed = false
                                    moveToBottomVisualState.isMovingToBottom = false
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
                        DashedCapsuleEmptySlot(
                            heightDp = slotHeightDp,
                            attractionWeightProvider = { magneticAttractor.weightState.floatValue }
                        )
                    } else {
                        val itemReminder = itemRemindersById[item.id]
                        val itemAutoRestore = itemAutoRestoresById[item.id]
                        CapsuleCheckItemRow(
                            item = item,
                            onToggle = {
                                val wasChecked = item.isChecked
                                if (showUncheckedOnly && !isReducedMotion) {
                                    if (!wasChecked) {
                                        stagingJobs[item.id]?.cancel()
                                        stagingCheckedItemIds = stagingCheckedItemIds + item.id
                                        stagingJobs[item.id] = scope.launch {
                                            delay(220L)
                                            stagingCheckedItemIds = stagingCheckedItemIds - item.id
                                            stagingJobs.remove(item.id)
                                        }
                                    } else {
                                        stagingJobs.remove(item.id)?.cancel()
                                        stagingCheckedItemIds = stagingCheckedItemIds - item.id
                                    }
                                }
                                viewModel.toggleItemChecked(item)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                stackedListState.triggerNeighborRipple(
                                    sourceKey = item.id,
                                    intensity = 1f,
                                    isReducedMotion = isReducedMotion
                                )
                            },
                            isEditing = editingItemId == item.id,
                            hasAutoRestore = itemAutoRestore?.isEnabled == true,
                            tagPool = mergedAvailableTags,
                            activeItemReminder = itemReminder,
                            onOpenItemReminder = {
                                onOpenModal(
                                    ChecklistModalContent.ConfigureReminder(
                                        checklist = checklist,
                                        item = item,
                                        existingReminder = itemReminder,
                                        existingAutoRestore = itemAutoRestore,
                                    ),
                                    null
                                )
                            },
                            onQuickItemReminder15m = {
                                onReminderLongPress(
                                    checklist.id,
                                    item.id,
                                    itemReminder?.isEnabled == true
                                )
                            },
                            holdDeleteFillDurationMsProvider = {
                                consecutiveDeleteTracker.currentFillDurationMs()
                            },
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
                            onEditTags = {
                                onOpenModal(
                                    ChecklistModalContent.ItemTagPicker(
                                        item = item,
                                        checklistId = checklist.id
                                    ),
                                    null
                                )
                            },
                            onLongClickItemTags = {
                                viewModel.quickToggleLastAddedTag(
                                    checklistId = checklist.id,
                                    item = item
                                ) { result ->
                                    val message = when (result) {
                                        is QuickTagToggleResult.Added -> context.getString(
                                            R.string.snackbar_quick_tag_added,
                                            result.tag.name
                                        )
                                        is QuickTagToggleResult.Removed -> context.getString(
                                            R.string.snackbar_quick_tag_removed,
                                            result.tag.name
                                        )
                                        is QuickTagToggleResult.LimitReached -> context.getString(
                                            R.string.snackbar_quick_tag_limit_reached
                                        )
                                        QuickTagToggleResult.NoTagAvailable -> context.getString(
                                            R.string.snackbar_quick_tag_none_available
                                        )
                                    }
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            },
                            onEditDraftChanged = { draft -> onEditDraftChanged(item.id, draft) },
                            onConfirmEdit = { newLabel -> onConfirmEdit(item.id, newLabel) },
                            onCancelEdit = onCancelEdit,
                            onArchive = {
                                stagingJobs.remove(item.id)?.cancel()
                                stagingCheckedItemIds = stagingCheckedItemIds - item.id
                                preEditScrollOffsetHolder.value = -1f
                                stackedListState.handoffPinnedAnchorBeforeRemoval(item.id)
                                onFinishEditForArchive()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.archiveItem(item) { archivedItem ->
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(
                                                R.string.snackbar_archived_item,
                                                archivedItem.label
                                            ),
                                            actionLabel = context.getString(R.string.action_undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.unarchiveItem(archivedItem)
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                consecutiveDeleteTracker.recordDeleteCompleted()
                                stagingJobs.remove(item.id)?.cancel()
                                stagingCheckedItemIds = stagingCheckedItemIds - item.id
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
                                            consecutiveDeleteTracker.reset()
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
                        hasAutoRestore = itemAutoRestoresById[draggedItem.id]?.isEnabled == true,
                        tagPool = mergedAvailableTags,
                        activeItemReminder = itemRemindersById[draggedItem.id],
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                translationX = moveToBottomVisualState.translationX.value
                                translationY = springTracker.currentValueState.floatValue +
                                    moveToBottomVisualState.translationY.value
                                val dragScale = moveToBottomVisualState.scale.value
                                scaleX = dragScale
                                scaleY = dragScale
                                alpha = moveToBottomVisualState.alpha.value
                            }
                    )
                }
            }
        }
    ) { pageMeasurables, pageConstraints ->
        val progressPlaceable = pageMeasurables[0].measure(
            pageConstraints.copy(minHeight = 0)
        )
        val measuredProgressHeight = progressPlaceable.height.toFloat()
        if (abs(progressCardHeightState.floatValue - measuredProgressHeight) > 0.25f) {
            progressCardHeightState.floatValue = measuredProgressHeight
        }
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
    inputFocusRequester: FocusRequester,
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
                            .focusRequester(inputFocusRequester)
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

private class ConsecutiveDeleteTracker(
    private val baseDurationMs: Int = 800,
    private val stepDecrementMs: Int = 100,
    private val minDurationMs: Int = 450,
    private val chainWindowMs: Long = 4_000L
) {
    private var streakCount = 0
    private var lastDeleteUptimeMs = 0L

    fun currentFillDurationMs(): Int {
        if (streakCount <= 0) return baseDurationMs
        val elapsed = android.os.SystemClock.uptimeMillis() - lastDeleteUptimeMs
        if (elapsed > chainWindowMs) {
            streakCount = 0
            return baseDurationMs
        }
        return (baseDurationMs - streakCount * stepDecrementMs).coerceAtLeast(minDurationMs)
    }

    fun recordDeleteCompleted() {
        val now = android.os.SystemClock.uptimeMillis()
        streakCount = if (lastDeleteUptimeMs > 0L && (now - lastDeleteUptimeMs) <= chainWindowMs) {
            streakCount + 1
        } else {
            1
        }
        lastDeleteUptimeMs = now
    }

    fun reset() {
        streakCount = 0
        lastDeleteUptimeMs = 0L
    }
}

