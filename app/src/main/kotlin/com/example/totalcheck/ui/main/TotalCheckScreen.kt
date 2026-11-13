package com.example.totalcheck.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalCheckScreen(
    viewModel: MainViewModel,
    onToggleTheme: (originInRoot: Offset) -> Unit,
    isThemeTransitioning: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val checklistsWithItems by viewModel.checklistsWithItems.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val showUncheckedOnly by viewModel.showUncheckedOnly.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }

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
                onToggleDynamicColor = { viewModel.toggleDynamicColor() }
            )

            if (checklistsWithItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { checklistsWithItems.size })

                val submitNewItem: () -> Unit = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty() && pagerState.currentPage in checklistsWithItems.indices) {
                        val currentChecklistId = checklistsWithItems[pagerState.currentPage].checklist.id
                        viewModel.addItem(currentChecklistId, trimmed)
                        inputText = ""
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, checklistsWithItems.lastIndex),
                    containerColor = colorScheme.surface,
                    contentColor = colorScheme.primary,
                    edgePadding = 16.dp,
                    divider = {
                        HorizontalDivider(thickness = 0.5.dp, color = colorScheme.outlineVariant)
                    }
                ) {
                    checklistsWithItems.forEachIndexed { index, group ->
                        val unchecked = group.items.count { !it.isChecked }
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(
                                    text = if (unchecked > 0) "${group.checklist.name} ($unchecked)" else group.checklist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    val group = checklistsWithItems[pageIndex]
                    val totalCount = group.items.size
                    val uncheckedCount = group.items.count { !it.isChecked }
                    val targetProgress = if (totalCount == 0) 1f else (totalCount - uncheckedCount).toFloat() / totalCount
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
                        label = "checklistProgress"
                    )
                    val visibleItems = remember(group.items, showUncheckedOnly) {
                        if (showUncheckedOnly) {
                            group.items.filter { !it.isChecked }
                        } else {
                            group.items
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        CapsuleProgressModule(
                            uncheckedCount = uncheckedCount,
                            totalCount = totalCount,
                            animatedProgress = animatedProgress,
                            showUncheckedOnly = showUncheckedOnly,
                            onToggleUncheckedOnly = { viewModel.toggleShowUncheckedOnly() },
                            onReset = {
                                viewModel.resetChecklist(group.checklist.id)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = visibleItems, key = { it.id }) { item ->
                                CapsuleCheckItemRow(
                                    item = item,
                                    onToggle = {
                                        viewModel.toggleItemChecked(item)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    modifier = Modifier.animateItem(
                                        placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f)
                                    )
                                )
                            }
                        }
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
}

@Composable
private fun TopAppHeaderBar(
    isDarkTheme: Boolean,
    useDynamicColor: Boolean,
    isThemeTransitioning: Boolean,
    onToggleTheme: (originInRoot: Offset) -> Unit,
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
                                val coords = themeButtonCoords
                                if (coords != null && coords.isAttached) {
                                    lastTouchRootOffset = coords.localToRoot(down.position)
                                }
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                if (up != null && coords != null && coords.isAttached) {
                                    lastTouchRootOffset = coords.localToRoot(up.position)
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
