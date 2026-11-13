package com.example.totalcheck.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellPrototypeScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var selectedStyle by remember { mutableStateOf(VisualStyle.StyleA) }
    val checklists = remember { createInitialMockChecklists() }
    var nextId by remember { mutableLongStateOf(1000L) }
    var inputText by remember { mutableStateOf("") }

    val pagerState = rememberPagerState(pageCount = { checklists.size })

    val addItemToCurrentList: () -> Unit = {
        val trimmed = inputText.trim()
        if (trimmed.isNotEmpty()) {
            val currentList = checklists[pagerState.currentPage]
            currentList.items.add(
                index = 0,
                element = MockCheckItem(id = nextId++, label = trimmed, isChecked = false)
            )
            inputText = ""
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. 頂部臨時控制列（風格切換 + 亮/暗色切換）
            TopPrototypeControlBar(
                selectedStyle = selectedStyle,
                onSelectStyle = { selectedStyle = it },
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )

            // 2. 多清單跟手分頁 TabRow
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = colorScheme.background,
                contentColor = colorScheme.primary,
                edgePadding = 16.dp,
                divider = {
                    HorizontalDivider(thickness = 0.5.dp, color = colorScheme.outlineVariant)
                }
            ) {
                checklists.forEachIndexed { index, checklist ->
                    val unchecked = checklist.items.count { !it.isChecked }
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                text = if (unchecked > 0) "${checklist.name} ($unchecked)" else checklist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // 3. HorizontalPager 內容區
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val checklist = checklists[pageIndex]
                val totalCount = checklist.items.size
                val uncheckedCount = checklist.items.count { !it.isChecked }
                val targetProgress = if (totalCount == 0) 1f else (totalCount - uncheckedCount).toFloat() / totalCount
                val animatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
                    label = "checklistProgress"
                )
                val sortedItems = remember(checklist.items.toList()) {
                    checklist.items.sortedBy { it.isChecked }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    ProgressSection(
                        style = selectedStyle,
                        uncheckedCount = uncheckedCount,
                        totalCount = totalCount,
                        animatedProgress = animatedProgress,
                        onReset = {
                            for (i in checklist.items.indices) {
                                if (checklist.items[i].isChecked) {
                                    checklist.items[i] = checklist.items[i].copy(isChecked = false)
                                }
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = when (selectedStyle) {
                            VisualStyle.StyleA -> PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            VisualStyle.StyleB -> PaddingValues(vertical = 4.dp)
                            VisualStyle.StyleC -> PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        },
                        verticalArrangement = when (selectedStyle) {
                            VisualStyle.StyleA -> Arrangement.spacedBy(8.dp)
                            VisualStyle.StyleB -> Arrangement.spacedBy(0.dp)
                            VisualStyle.StyleC -> Arrangement.spacedBy(10.dp)
                        }
                    ) {
                        items(items = sortedItems, key = { it.id }) { item ->
                            CheckItemRow(
                                style = selectedStyle,
                                item = item,
                                onToggle = {
                                    val idx = checklist.items.indexOfFirst { it.id == item.id }
                                    if (idx >= 0) {
                                        checklist.items[idx] = checklist.items[idx].copy(isChecked = !item.isChecked)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

            // 4. 底部常駐極速輸入列（含 navigationBars + imeInsets 防遮擋）
            QuickInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSubmit = addItemToCurrentList
            )
        }
    }
}

@Composable
private fun TopPrototypeControlBar(
    selectedStyle: VisualStyle,
    onSelectStyle: (VisualStyle) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.weight(1f)
        ) {
            VisualStyle.entries.forEachIndexed { index, style ->
                SegmentedButton(
                    selected = selectedStyle == style,
                    onClick = { onSelectStyle(style) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = VisualStyle.entries.size
                    ),
                    icon = {}
                ) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
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
