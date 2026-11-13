package com.example.totalcheck.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import com.example.totalcheck.ui.theme.LocalThemeTransitioning
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    val itemsByChecklist by viewModel.itemsByChecklist.collectAsStateWithLifecycle()
    val pendingScrollToChecklistId by viewModel.pendingScrollToChecklistId.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val showUncheckedOnly by viewModel.showUncheckedOnly.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var actionTargetChecklist by remember { mutableStateOf<ChecklistEntity?>(null) }
    var editingItemId by remember { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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

            if (checklists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { checklists.size })
                val tabListState = rememberLazyListState()
                val activeTabPage by remember(checklists.size) {
                    derivedStateOf {
                        pagerState.targetPage.coerceIn(0, (checklists.size - 1).coerceAtLeast(0))
                    }
                }

                // 雙向同步：僅在跨過目標分頁閾值時更新選中 ID 並捲動頂部膠囊 Tab 列，避免滑動幀全樹重組
                LaunchedEffect(activeTabPage, checklists) {
                    viewModel.selectChecklist(checklists[activeTabPage].id)
                    tabListState.animateScrollToItem(activeTabPage)
                }

                // 新建清單後自動切換至該分頁（等待 Room 發射新列表且 pageCount 同步後再滾動）
                LaunchedEffect(checklists, pendingScrollToChecklistId) {
                    val targetId = pendingScrollToChecklistId ?: return@LaunchedEffect
                    val targetIndex = checklists.indexOfFirst { it.id == targetId }
                    if (targetIndex >= 0) {
                        pagerState.animateScrollToPage(targetIndex)
                        viewModel.clearPendingScroll()
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

                CapsuleChecklistTabBar(
                    checklists = checklists,
                    itemsByChecklist = itemsByChecklist,
                    currentPage = activeTabPage,
                    tabListState = tabListState,
                    onSelectPage = { index ->
                        editingItemId = null
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
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
                ) {
                    HorizontalPager(
                        state = pagerState,
                        key = { page -> checklists.getOrNull(page)?.id ?: page },
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val checklist = checklists[pageIndex]
                        val items = itemsByChecklist[checklist.id].orEmpty()
                        val listState = rememberLazyListState()
                        val totalCount = items.size
                        val uncheckedCount = items.count { !it.isChecked }
                        val isReducedMotion = LocalReducedMotion.current
                        val targetProgress = if (totalCount == 0) 1f else (totalCount - uncheckedCount).toFloat() / totalCount
                        val progressAnimatable = remember(checklist.id) { Animatable(targetProgress) }
                        var previousTargetProgress by remember(checklist.id) { mutableFloatStateOf(targetProgress) }
                        var staggeredResetDelays by remember(checklist.id) { mutableStateOf<Map<Long, Int>>(emptyMap()) }
                        var resetWaveKey by remember(checklist.id) { mutableIntStateOf(0) }

                        LaunchedEffect(targetProgress, isReducedMotion) {
                            if (isReducedMotion) {
                                progressAnimatable.snapTo(targetProgress)
                            } else if (targetProgress > previousTargetProgress) {
                                progressAnimatable.animateTo(
                                    targetValue = targetProgress,
                                    initialVelocity = 1.6f,
                                    animationSpec = spring(
                                        dampingRatio = 0.38f,
                                        stiffness = 240f,
                                        visibilityThreshold = 0.0005f
                                    )
                                )
                            } else if (targetProgress < previousTargetProgress) {
                                progressAnimatable.animateTo(
                                    targetValue = targetProgress,
                                    animationSpec = spring(
                                        dampingRatio = 0.45f,
                                        stiffness = 260f,
                                        visibilityThreshold = 0.0005f
                                    )
                                )
                            }
                            previousTargetProgress = targetProgress
                        }
                        val animatedProgress = progressAnimatable.value
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
                                if (isReducedMotion) {
                                    listState.scrollToItem(0)
                                } else {
                                    listState.animateScrollToItem(0)
                                }
                            }
                            previousSize = visibleItems.size
                            previousFirstId = currentFirstId
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            CapsuleProgressModule(
                                uncheckedCount = uncheckedCount,
                                totalCount = totalCount,
                                animatedProgress = animatedProgress,
                                showUncheckedOnly = showUncheckedOnly,
                                onToggleUncheckedOnly = { viewModel.toggleShowUncheckedOnly() },
                                onReset = {
                                    editingItemId = null
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (!isReducedMotion) {
                                        val checkedInOrder = visibleItems.filter { it.isChecked }.map { it.id }
                                        staggeredResetDelays = checkedInOrder.mapIndexed { idx, id ->
                                            id to (idx * 30).coerceAtMost(360)
                                        }.toMap()
                                        resetWaveKey++
                                    } else {
                                        staggeredResetDelays = emptyMap()
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

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = visibleItems,
                                    key = { it.id },
                                    contentType = { "check_item" }
                                ) { item ->
                                    CapsuleCheckItemRow(
                                        item = item,
                                        onToggle = {
                                            viewModel.toggleItemChecked(item)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        isEditing = editingItemId == item.id,
                                        resetWaveKey = resetWaveKey,
                                        resetDelayMs = staggeredResetDelays[item.id],
                                        onStartEdit = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            editingItemId = item.id
                                        },
                                        onConfirmEdit = { newLabel ->
                                            viewModel.renameItem(item.id, newLabel)
                                            editingItemId = null
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
                                        modifier = Modifier.animateItem(
                                            placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
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

                QuickInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSubmit = submitNewItem
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CapsuleChecklistTabBar(
    checklists: List<ChecklistEntity>,
    itemsByChecklist: Map<Long, List<CheckItemEntity>>,
    currentPage: Int,
    tabListState: androidx.compose.foundation.lazy.LazyListState,
    onSelectPage: (Int) -> Unit,
    onLongPressTab: (ChecklistEntity) -> Unit,
    onAddChecklist: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = tabListState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(items = checklists, key = { _, item -> item.id }) { index, checklist ->
                val isSelected = currentPage == index
                val unchecked = itemsByChecklist[checklist.id].orEmpty().count { !it.isChecked }
                val freezeColorTransition = LocalThemeTransitioning.current || LocalReducedMotion.current
                val colorSpec: AnimationSpec<Color> = if (freezeColorTransition) {
                    snap()
                } else {
                    spring(dampingRatio = 0.8f, stiffness = 380f)
                }
                val containerColor = animateColorAsState(
                    targetValue = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                    animationSpec = colorSpec,
                    label = "tabContainer"
                )
                val borderColor = animateColorAsState(
                    targetValue = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                    animationSpec = colorSpec,
                    label = "tabBorder"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                    animationSpec = colorSpec,
                    label = "tabText"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .drawBehind {
                            val radius = size.height / 2f
                            val corner = CornerRadius(radius, radius)
                            drawRoundRect(
                                color = containerColor.value,
                                cornerRadius = corner
                            )
                            drawRoundRect(
                                color = borderColor.value,
                                cornerRadius = corner,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        .combinedClickable(
                            onClick = { onSelectPage(index) },
                            onLongClick = { onLongPressTab(checklist) }
                        )
                ) {
                    Text(
                        text = if (unchecked > 0) "${checklist.name} ($unchecked)" else checklist.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            FilledTonalIconButton(
                onClick = onAddChecklist,
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(50),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.surfaceContainerHigh,
                    contentColor = colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新增清單",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
                        if (!isThemeTransitioning) {
                            val origin = lastTouchRootOffset
                                ?: themeButtonCoords?.takeIf { it.isAttached }?.boundsInRoot()?.center
                                ?: Offset.Zero
                            lastTouchRootOffset = null
                            onToggleTheme(origin)
                        }
                    },
                    enabled = !isThemeTransitioning,
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
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                if (up != null && coords != null && coords.isAttached) {
                                    lastTouchRootOffset = coords.localToRoot(up.position)
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
                    WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
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
                    keyboardActions = KeyboardActions(onDone = { onSubmit() })
                )
                Spacer(modifier = Modifier.width(10.dp))
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = value.isNotBlank(),
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
