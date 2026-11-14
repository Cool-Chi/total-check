package com.example.totalcheck.data.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.example.totalcheck.MainActivity
import com.example.totalcheck.R
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.preferences.UserPreferencesRepository
import com.example.totalcheck.data.preferences.WidgetPreferences
import com.example.totalcheck.data.repository.ChecklistRepository
import kotlinx.coroutines.flow.first

private enum class WidgetSizeTier {
    MICRO,
    COMPACT,
    STANDARD,
    EXPANDED
}

class TotalCheckAppWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 50.dp),   // Tier 1: Micro (2x1)
            DpSize(130.dp, 115.dp),  // Tier 2: Compact (2x2)
            DpSize(200.dp, 60.dp),   // Tier 2: Compact (3x1)
            DpSize(240.dp, 140.dp),  // Tier 3: Standard (3x2 / 4x2)
            DpSize(320.dp, 220.dp),  // Tier 4: Expanded 2-Col (4x3)
            DpSize(340.dp, 240.dp)   // Tier 4: Expanded 3-Col (4x4+)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val database = AppDatabase.getInstance(appContext)
        val repository = ChecklistRepository(appContext, database.checklistDao())
        val preferencesRepository = UserPreferencesRepository(appContext)

        repository.seedInitialDataIfNeeded()
        val initialChecklists = repository.getAllChecklists()
        val initialItems = repository.getAllItems()
        val initialWidgetPrefs = preferencesRepository.widgetPreferences.first()

        provideContent {
            val checklists by repository.observeAllChecklists()
                .collectAsState(initial = initialChecklists)
            val allItems by repository.observeAllItems()
                .collectAsState(initial = initialItems)
            val widgetPrefs by preferencesRepository.widgetPreferences
                .collectAsState(initial = initialWidgetPrefs)

            GlanceTheme {
                TotalCheckWidgetContent(
                    checklists = checklists,
                    allItems = allItems,
                    widgetPrefs = widgetPrefs
                )
            }
        }
    }
}

@Composable
private fun TotalCheckWidgetContent(
    checklists: List<ChecklistEntity>,
    allItems: List<CheckItemEntity>,
    widgetPrefs: WidgetPreferences
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val tier = resolveWidgetSizeTier(size)

    val activeChecklist = checklists.firstOrNull { it.id == widgetPrefs.selectedChecklistId }
        ?: checklists.firstOrNull()
    val checklistItems = if (activeChecklist != null) {
        allItems.filter { it.checklistId == activeChecklist.id }
    } else {
        emptyList()
    }
    val totalCount = checklistItems.size
    val uncheckedCount = checklistItems.count { !it.isChecked }
    val checkedCount = totalCount - uncheckedCount
    val visibleItems = if (widgetPrefs.showUncheckedOnly) {
        checklistItems.filter { !it.isChecked }
    } else {
        checklistItems
    }
    val isResetConfirmActive = widgetPrefs.resetConfirmUntilMillis > System.currentTimeMillis()
    val hasMultipleChecklists = checklists.size > 1
    val openAppAction = buildOpenAppAction(context, activeChecklist?.id)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
    ) {
        if (activeChecklist == null) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(openAppAction)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_empty_checklist_hint),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                )
            }
            return@Box
        }

        when (tier) {
            WidgetSizeTier.MICRO -> {
                MicroWidgetLayout(
                    context = context,
                    checklist = activeChecklist,
                    totalCount = totalCount,
                    uncheckedCount = uncheckedCount,
                    hasMultipleChecklists = hasMultipleChecklists,
                    openAppAction = openAppAction
                )
            }

            WidgetSizeTier.COMPACT -> {
                FullTierWidgetLayout(
                    context = context,
                    checklist = activeChecklist,
                    totalCount = totalCount,
                    checkedCount = checkedCount,
                    uncheckedCount = uncheckedCount,
                    visibleItems = visibleItems,
                    showUncheckedOnly = widgetPrefs.showUncheckedOnly,
                    isResetConfirmActive = isResetConfirmActive,
                    hasMultipleChecklists = hasMultipleChecklists,
                    showResetButton = false,
                    showExpandedProgressRow = false,
                    columnCount = 1,
                    openAppAction = openAppAction
                )
            }

            WidgetSizeTier.STANDARD -> {
                FullTierWidgetLayout(
                    context = context,
                    checklist = activeChecklist,
                    totalCount = totalCount,
                    checkedCount = checkedCount,
                    uncheckedCount = uncheckedCount,
                    visibleItems = visibleItems,
                    showUncheckedOnly = widgetPrefs.showUncheckedOnly,
                    isResetConfirmActive = isResetConfirmActive,
                    hasMultipleChecklists = hasMultipleChecklists,
                    showResetButton = true,
                    showExpandedProgressRow = false,
                    columnCount = 2,
                    openAppAction = openAppAction
                )
            }

            WidgetSizeTier.EXPANDED -> {
                val columnCount = if (size.width >= 340.dp) 3 else 2
                FullTierWidgetLayout(
                    context = context,
                    checklist = activeChecklist,
                    totalCount = totalCount,
                    checkedCount = checkedCount,
                    uncheckedCount = uncheckedCount,
                    visibleItems = visibleItems,
                    showUncheckedOnly = widgetPrefs.showUncheckedOnly,
                    isResetConfirmActive = isResetConfirmActive,
                    hasMultipleChecklists = hasMultipleChecklists,
                    showResetButton = true,
                    showExpandedProgressRow = true,
                    columnCount = columnCount,
                    openAppAction = openAppAction
                )
            }
        }
    }
}

@Composable
private fun MicroWidgetLayout(
    context: Context,
    checklist: ChecklistEntity,
    totalCount: Int,
    uncheckedCount: Int,
    hasMultipleChecklists: Boolean,
    openAppAction: Action
) {
    val isAllDone = totalCount > 0 && uncheckedCount == 0
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(openAppAction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = checklist.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Box(
                modifier = GlanceModifier
                    .background(
                        if (isAllDone) {
                            GlanceTheme.colors.primaryContainer
                        } else {
                            GlanceTheme.colors.tertiaryContainer
                        }
                    )
                    .cornerRadius(99.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isAllDone) {
                        context.getString(R.string.widget_all_set_short)
                    } else {
                        context.getString(R.string.widget_left_count, uncheckedCount)
                    },
                    style = TextStyle(
                        color = if (isAllDone) {
                            GlanceTheme.colors.onPrimaryContainer
                        } else {
                            GlanceTheme.colors.onTertiaryContainer
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        ChecklistSwitchPair(
            context = context,
            checklistId = checklist.id,
            enabled = hasMultipleChecklists,
            compactSize = true
        )
    }
}

@Composable
private fun FullTierWidgetLayout(
    context: Context,
    checklist: ChecklistEntity,
    totalCount: Int,
    checkedCount: Int,
    uncheckedCount: Int,
    visibleItems: List<CheckItemEntity>,
    showUncheckedOnly: Boolean,
    isResetConfirmActive: Boolean,
    hasMultipleChecklists: Boolean,
    showResetButton: Boolean,
    showExpandedProgressRow: Boolean,
    columnCount: Int,
    openAppAction: Action
) {
    val isAllDone = totalCount > 0 && uncheckedCount == 0
    val filterBackground = when {
        showUncheckedOnly -> GlanceTheme.colors.tertiary
        isAllDone -> GlanceTheme.colors.primaryContainer
        else -> GlanceTheme.colors.secondaryContainer
    }
    val filterContentColor = when {
        showUncheckedOnly -> GlanceTheme.colors.onTertiary
        isAllDone -> GlanceTheme.colors.onPrimaryContainer
        else -> GlanceTheme.colors.onSecondaryContainer
    }
    val filterLabel = when {
        showUncheckedOnly -> context.getString(R.string.widget_left_filtered, uncheckedCount)
        isAllDone -> context.getString(R.string.widget_all_set_short)
        else -> context.getString(R.string.widget_left_count, uncheckedCount)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Tier 2 / 3 / 4 Primary Header Row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(openAppAction)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = checklist.name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            Box(
                modifier = GlanceModifier
                    .background(filterBackground)
                    .cornerRadius(99.dp)
                    .clickable(actionRunCallback<TotalCheckWidgetActionCallback>(
                        actionParametersOf(PARAM_ACTION to ACTION_TOGGLE_FILTER)
                    ))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filterLabel,
                    style = TextStyle(
                        color = filterContentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            if (showResetButton && totalCount > 0) {
                Spacer(modifier = GlanceModifier.width(5.dp))
                Box(
                    modifier = GlanceModifier
                        .background(
                            if (isResetConfirmActive) {
                                GlanceTheme.colors.errorContainer
                            } else {
                                GlanceTheme.colors.surfaceVariant
                            }
                        )
                        .cornerRadius(99.dp)
                        .clickable(actionRunCallback<TotalCheckWidgetActionCallback>(
                            actionParametersOf(
                                PARAM_ACTION to ACTION_RESET_STAGE,
                                PARAM_CHECKLIST_ID to checklist.id
                            )
                        ))
                        .padding(
                            horizontal = if (isResetConfirmActive) 9.dp else 8.dp,
                            vertical = 4.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isResetConfirmActive) {
                            context.getString(R.string.widget_reset_confirm)
                        } else {
                            context.getString(R.string.widget_reset_icon)
                        },
                        style = TextStyle(
                            color = if (isResetConfirmActive) {
                                GlanceTheme.colors.onErrorContainer
                            } else {
                                GlanceTheme.colors.onSurfaceVariant
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(5.dp))

            ChecklistSwitchPair(
                context = context,
                checklistId = checklist.id,
                enabled = hasMultipleChecklists,
                compactSize = false
            )
        }

        // Tier 4 Secondary Progress & Quick Add Bar
        if (showExpandedProgressRow) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            val progressFraction = if (totalCount > 0) {
                (checkedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(99.dp)
                        .clickable(openAppAction)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = GlanceModifier.defaultWeight()) {
                        LinearProgressIndicator(
                            progress = progressFraction,
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .cornerRadius(99.dp),
                            color = if (isAllDone) {
                                GlanceTheme.colors.primary
                            } else {
                                GlanceTheme.colors.tertiary
                            },
                            backgroundColor = GlanceTheme.colors.surfaceVariant
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.widget_progress_ratio, checkedCount, totalCount),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(99.dp)
                        .clickable(openAppAction)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.widget_action_add_short),
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Content Region: Empty / All-Set / Bento Tile Matrix
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxWidth()
        ) {
            when {
                totalCount == 0 -> {
                    EmptyChecklistBadge(
                        context = context,
                        openAppAction = openAppAction
                    )
                }

                visibleItems.isEmpty() -> {
                    AllSetCompletionState(
                        context = context,
                        checklistId = checklist.id,
                        totalCount = totalCount
                    )
                }

                columnCount <= 1 -> {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(
                            items = visibleItems,
                            itemId = { item -> item.id }
                        ) { item ->
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                ItemBentoTile(
                                    item = item,
                                    isDense = true,
                                    modifier = GlanceModifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                else -> {
                    val rows = visibleItems.chunked(columnCount)
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(
                            items = rows,
                            itemId = { rowItems -> rowItems.firstOrNull()?.id ?: 0L }
                        ) { rowItems ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (col in 0 until columnCount) {
                                    if (col > 0) {
                                        Spacer(modifier = GlanceModifier.width(6.dp))
                                    }
                                    val cellItem = rowItems.getOrNull(col)
                                    if (cellItem != null) {
                                        ItemBentoTile(
                                            item = cellItem,
                                            isDense = columnCount >= 3,
                                            modifier = GlanceModifier.defaultWeight()
                                        )
                                    } else {
                                        Spacer(modifier = GlanceModifier.defaultWeight())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemBentoTile(
    item: CheckItemEntity,
    isDense: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    val containerColor = if (item.isChecked) {
        GlanceTheme.colors.surface
    } else {
        GlanceTheme.colors.surfaceVariant
    }
    val indicatorColor = if (item.isChecked) {
        GlanceTheme.colors.onSurfaceVariant
    } else {
        GlanceTheme.colors.tertiary
    }
    val textColor = if (item.isChecked) {
        GlanceTheme.colors.onSurfaceVariant
    } else {
        GlanceTheme.colors.onSurface
    }
    val textDecoration = if (item.isChecked) {
        TextDecoration.LineThrough
    } else {
        TextDecoration.None
    }

    Row(
        modifier = modifier
            .background(containerColor)
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<TotalCheckWidgetActionCallback>(
                actionParametersOf(
                    PARAM_ACTION to ACTION_TOGGLE_ITEM,
                    PARAM_ITEM_ID to item.id
                )
            ))
            .padding(
                horizontal = if (isDense) 8.dp else 10.dp,
                vertical = if (isDense) 7.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (item.isChecked) "✓" else "●",
            style = TextStyle(
                color = indicatorColor,
                fontSize = if (isDense) 11.sp else 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = item.label,
            style = TextStyle(
                color = textColor,
                fontSize = if (isDense) 12.sp else 13.sp,
                fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.Medium,
                textDecoration = textDecoration
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

@Composable
private fun EmptyChecklistBadge(
    context: Context,
    openAppAction: Action
) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(99.dp)
                .clickable(openAppAction)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.widget_empty_checklist_hint),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun AllSetCompletionState(
    context: Context,
    checklistId: Long,
    totalCount: Int
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(99.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.widget_all_set_with_count, totalCount),
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(99.dp)
                    .clickable(actionRunCallback<TotalCheckWidgetActionCallback>(
                        actionParametersOf(
                            PARAM_ACTION to ACTION_FORCE_RESET,
                            PARAM_CHECKLIST_ID to checklistId
                        )
                    ))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_action_recheck),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(99.dp)
                    .clickable(actionRunCallback<TotalCheckWidgetActionCallback>(
                        actionParametersOf(PARAM_ACTION to ACTION_SHOW_ALL)
                    ))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_action_show_all),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ChecklistSwitchPair(
    context: Context,
    checklistId: Long,
    enabled: Boolean,
    compactSize: Boolean
) {
    val btnHeight = if (compactSize) 26.dp else 24.dp
    val btnWidth = if (compactSize) 24.dp else 24.dp
    val textColor = if (enabled) {
        GlanceTheme.colors.onSurface
    } else {
        GlanceTheme.colors.onSurfaceVariant
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier
                .width(btnWidth)
                .height(btnHeight)
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(99.dp)
                .clickable(actionRunCallback<TotalCheckWidgetActionCallback>(
                    actionParametersOf(
                        PARAM_ACTION to ACTION_SWITCH_CHECKLIST,
                        PARAM_CHECKLIST_ID to checklistId,
                        PARAM_DIRECTION to -1
                    )
                )),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.widget_prev_checklist),
                style = TextStyle(
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Box(
            modifier = GlanceModifier
                .width(btnWidth)
                .height(btnHeight)
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(99.dp)
                .clickable(actionRunCallback<TotalCheckWidgetActionCallback>(
                    actionParametersOf(
                        PARAM_ACTION to ACTION_SWITCH_CHECKLIST,
                        PARAM_CHECKLIST_ID to checklistId,
                        PARAM_DIRECTION to 1
                    )
                )),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.widget_next_checklist),
                style = TextStyle(
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

private fun resolveWidgetSizeTier(size: DpSize): WidgetSizeTier {
    return when {
        size.width < 180.dp && size.height < 110.dp -> WidgetSizeTier.MICRO
        size.width < 240.dp || size.height < 140.dp -> WidgetSizeTier.COMPACT
        size.width >= 320.dp && size.height >= 220.dp -> WidgetSizeTier.EXPANDED
        else -> WidgetSizeTier.STANDARD
    }
}

private fun buildOpenAppAction(context: Context, checklistId: Long?): Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_NEW_TASK
        if (checklistId != null && checklistId > 0L) {
            putExtra(MainActivity.EXTRA_WIDGET_CHECKLIST_ID, checklistId)
        }
        data = Uri.parse("totalcheck://widget/checklist/${checklistId ?: 0L}")
    }
    return actionStartActivity(intent)
}

class TotalCheckWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appContext = context.applicationContext
        val actionType = parameters[PARAM_ACTION] ?: return
        val database = AppDatabase.getInstance(appContext)
        val repository = ChecklistRepository(appContext, database.checklistDao())
        val preferencesRepository = UserPreferencesRepository(appContext)

        when (actionType) {
            ACTION_TOGGLE_ITEM -> {
                val itemId = parameters[PARAM_ITEM_ID] ?: return
                repository.toggleItemChecked(itemId)
                TotalCheckAppWidget().updateAll(appContext)
            }

            ACTION_TOGGLE_FILTER -> {
                preferencesRepository.toggleWidgetShowUncheckedOnly()
                TotalCheckAppWidget().updateAll(appContext)
            }

            ACTION_SHOW_ALL -> {
                preferencesRepository.setWidgetShowUncheckedOnly(false)
                TotalCheckAppWidget().updateAll(appContext)
            }

            ACTION_SWITCH_CHECKLIST -> {
                val checklists = repository.getAllChecklists()
                if (checklists.size <= 1) return
                val currentId = parameters[PARAM_CHECKLIST_ID]
                    ?: preferencesRepository.widgetSelectedChecklistId.first()
                val direction = parameters[PARAM_DIRECTION] ?: 1
                val currentIdx = checklists.indexOfFirst { it.id == currentId }
                    .let { if (it < 0) 0 else it }
                val nextIdx = Math.floorMod(currentIdx + direction, checklists.size)
                val targetId = checklists[nextIdx].id
                WidgetSyncManager.cancelResetConfirmRevert()
                preferencesRepository.setWidgetSelectedChecklistId(targetId)
                TotalCheckAppWidget().updateAll(appContext)
            }

            ACTION_RESET_STAGE -> {
                val checklistId = parameters[PARAM_CHECKLIST_ID] ?: return
                val now = System.currentTimeMillis()
                val confirmUntil = preferencesRepository.widgetResetConfirmUntilMillis.first()
                if (now < confirmUntil) {
                    WidgetSyncManager.cancelResetConfirmRevert()
                    preferencesRepository.setWidgetResetConfirmUntilMillis(0L)
                    repository.resetChecklistItems(checklistId)
                    TotalCheckAppWidget().updateAll(appContext)
                } else {
                    preferencesRepository.setWidgetResetConfirmUntilMillis(now + RESET_CONFIRM_WINDOW_MS)
                    TotalCheckAppWidget().updateAll(appContext)
                    WidgetSyncManager.scheduleResetConfirmRevert(appContext, RESET_CONFIRM_WINDOW_MS + 100L)
                }
            }

            ACTION_FORCE_RESET -> {
                val checklistId = parameters[PARAM_CHECKLIST_ID] ?: return
                WidgetSyncManager.cancelResetConfirmRevert()
                preferencesRepository.setWidgetResetConfirmUntilMillis(0L)
                repository.resetChecklistItems(checklistId)
                TotalCheckAppWidget().updateAll(appContext)
            }
        }
    }
}

private const val RESET_CONFIRM_WINDOW_MS = 3000L

private val PARAM_ACTION = ActionParameters.Key<String>("widget_action")
private val PARAM_ITEM_ID = ActionParameters.Key<Long>("widget_item_id")
private val PARAM_CHECKLIST_ID = ActionParameters.Key<Long>("widget_checklist_id")
private val PARAM_DIRECTION = ActionParameters.Key<Int>("widget_direction")

private const val ACTION_TOGGLE_ITEM = "toggle_item"
private const val ACTION_TOGGLE_FILTER = "toggle_filter"
private const val ACTION_SHOW_ALL = "show_all"
private const val ACTION_SWITCH_CHECKLIST = "switch_checklist"
private const val ACTION_RESET_STAGE = "reset_stage"
private const val ACTION_FORCE_RESET = "force_reset"
