package com.example.totalcheck.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val checklistsWithItems by viewModel.checklistsWithItems.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val showUncheckedOnly by viewModel.showUncheckedOnly.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppHeaderBar(
                isDarkTheme = isDarkMode,
                onToggleTheme = { viewModel.toggleDarkMode() }
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
                    containerColor = colorScheme.background,
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
    onToggleTheme: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "TotalCheck",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onBackground
        )
        FilledTonalIconButton(
            onClick = onToggleTheme,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colorScheme.surfaceContainerHigh,
                contentColor = colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = if (isDarkTheme) "切換為亮色模式" else "切換為暗色模式"
            )
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
