package com.example.totalcheck.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import com.example.totalcheck.MainActivity
import com.example.totalcheck.R
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity

private enum class WidgetSizeTier {
    COMPACT,
    STANDARD,
    EXPANDED
}

private data class CounterFlipSlotViewIds(
    val openAppId: Int,
    val titleId: Int,
    val wideInfoId: Int,
    val progressActiveId: Int,
    val progressDoneId: Int,
    val ratioId: Int,
    val badgeId: Int,
)

private data class FlipSlotViewIds(
    val listViewId: Int,
    val emptyStateId: Int,
    val emptyStatePillId: Int,
    val allSetStateId: Int,
    val allSetBadgeId: Int,
    val btnRecheckId: Int,
    val btnShowAllId: Int,
)

object DirectWidgetRenderer {

    private const val REQ_LIST_TEMPLATE = 1001
    private const val REQ_TOGGLE_FILTER = 2001
    private const val REQ_RESET_STAGE = 2002
    private const val REQ_PREV_CHECKLIST = 2003
    private const val REQ_NEXT_CHECKLIST = 2004
    private const val REQ_FORCE_RESET = 2005
    private const val REQ_SHOW_ALL = 2006
    private const val REQ_OPEN_APP = 3001

    private val SLOT_0_IDS = FlipSlotViewIds(
        listViewId = R.id.widget_list_s0,
        emptyStateId = R.id.widget_empty_state_0,
        emptyStatePillId = R.id.widget_empty_state_pill_0,
        allSetStateId = R.id.widget_all_set_state_0,
        allSetBadgeId = R.id.widget_all_set_badge_0,
        btnRecheckId = R.id.widget_btn_recheck_0,
        btnShowAllId = R.id.widget_btn_show_all_0,
    )

    private val SLOT_1_IDS = FlipSlotViewIds(
        listViewId = R.id.widget_list_s1,
        emptyStateId = R.id.widget_empty_state_1,
        emptyStatePillId = R.id.widget_empty_state_pill_1,
        allSetStateId = R.id.widget_all_set_state_1,
        allSetBadgeId = R.id.widget_all_set_badge_1,
        btnRecheckId = R.id.widget_btn_recheck_1,
        btnShowAllId = R.id.widget_btn_show_all_1,
    )

    private val COUNTER_SLOT_0_IDS = CounterFlipSlotViewIds(
        openAppId = R.id.widget_counter_open_app_0,
        titleId = R.id.widget_counter_title_0,
        wideInfoId = R.id.widget_counter_wide_info_0,
        progressActiveId = R.id.widget_counter_progress_active_0,
        progressDoneId = R.id.widget_counter_progress_done_0,
        ratioId = R.id.widget_counter_ratio_0,
        badgeId = R.id.widget_counter_badge_0,
    )

    private val COUNTER_SLOT_1_IDS = CounterFlipSlotViewIds(
        openAppId = R.id.widget_counter_open_app_1,
        titleId = R.id.widget_counter_title_1,
        wideInfoId = R.id.widget_counter_wide_info_1,
        progressActiveId = R.id.widget_counter_progress_active_1,
        progressDoneId = R.id.widget_counter_progress_done_1,
        ratioId = R.id.widget_counter_ratio_1,
        badgeId = R.id.widget_counter_badge_1,
    )

    fun renderAll(context: Context) {
        val snapshot = WidgetSyncManager.snapshotFlow.value ?: return
        renderAll(context, snapshot, isContentFlip = false, isFilterFlip = false)
    }

    fun renderAll(
        context: Context,
        snapshot: WidgetUiSnapshot,
        isContentFlip: Boolean = false,
        isFilterFlip: Boolean = false,
    ) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)

        val gridProvider = ComponentName(appContext, TotalCheckWidgetReceiver::class.java)
        val gridWidgetIds = manager.getAppWidgetIds(gridProvider)
        for (appWidgetId in gridWidgetIds) {
            renderSingleGridWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                isContentFlip = isContentFlip,
                isFilterFlip = isFilterFlip,
            )
        }

        val counterProvider = ComponentName(appContext, TotalCheckCounterWidgetReceiver::class.java)
        val counterWidgetIds = manager.getAppWidgetIds(counterProvider)
        for (appWidgetId in counterWidgetIds) {
            renderSingleCounterWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                isContentFlip = isContentFlip,
            )
        }
    }

    fun renderWidgetsByIds(
        context: Context,
        appWidgetIds: IntArray,
        snapshot: WidgetUiSnapshot
    ) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        for (appWidgetId in appWidgetIds) {
            renderSingleGridWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                isContentFlip = false,
                isFilterFlip = snapshot.showUncheckedOnly,
            )
        }
    }

    fun renderCounterWidgetsByIds(
        context: Context,
        appWidgetIds: IntArray,
        snapshot: WidgetUiSnapshot
    ) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        for (appWidgetId in appWidgetIds) {
            renderSingleCounterWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                isContentFlip = false,
            )
        }
    }

    fun resolveColumnCountForWidget(context: Context, appWidgetId: Int): Int {
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val (widthDp, heightDp) = resolveDimensionsDp(context, options)
        val tier = resolveSizeTier(widthDp, heightDp)
        return resolveColumnCount(tier)
    }

    internal fun buildRowRemoteViews(
        context: Context,
        rowItems: List<CheckItemEntity>,
        columnCount: Int,
    ): RemoteViews {
        val rowLayoutRes = when {
            columnCount <= 1 -> R.layout.widget_row_1col
            columnCount >= 3 -> R.layout.widget_row_3col
            else -> R.layout.widget_row_2col
        }
        val rowRv = RemoteViews(context.packageName, rowLayoutRes)
        bindTileSlot(
            context = context,
            rowRv = rowRv,
            tileId = R.id.tile_0,
            item = rowItems.getOrNull(0),
        )
        if (columnCount >= 2) {
            bindTileSlot(
                context = context,
                rowRv = rowRv,
                tileId = R.id.tile_1,
                item = rowItems.getOrNull(1),
            )
        }
        if (columnCount >= 3) {
            bindTileSlot(
                context = context,
                rowRv = rowRv,
                tileId = R.id.tile_2,
                item = rowItems.getOrNull(2),
            )
        }
        return rowRv
    }

    private fun resolveColumnCount(tier: WidgetSizeTier): Int = when (tier) {
        WidgetSizeTier.COMPACT -> 1
        WidgetSizeTier.STANDARD -> 2
        WidgetSizeTier.EXPANDED -> 3
    }

    private fun renderSingleGridWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: WidgetUiSnapshot,
        isContentFlip: Boolean,
        isFilterFlip: Boolean,
    ) {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val (widthDp, heightDp) = resolveDimensionsDp(context, options)
        val tier = resolveSizeTier(widthDp, heightDp)

        val activeChecklist = snapshot.activeChecklist
        if (activeChecklist == null) {
            val emptyRv = RemoteViews(context.packageName, R.layout.widget_empty).apply {
                setOnClickPendingIntent(
                    R.id.widget_empty_root,
                    buildOpenAppPendingIntent(context, null)
                )
            }
            manager.updateAppWidget(appWidgetId, emptyRv)
            return
        }

        val checklistItems = snapshot.activeChecklistItems
        val totalCount = checklistItems.size
        val uncheckedCount = checklistItems.count { !it.isChecked }
        val checkedCount = totalCount - uncheckedCount
        val hasMultipleChecklists = snapshot.checklists.size > 1

        val visibleItems = if (snapshot.showUncheckedOnly) {
            checklistItems.filter { !it.isChecked }
        } else {
            checklistItems
        }
        val showResetButton = tier == WidgetSizeTier.STANDARD || tier == WidgetSizeTier.EXPANDED
        val showExpandedBar = tier == WidgetSizeTier.EXPANDED
        val columnCount = resolveColumnCount(tier)
        val flipSlot = snapshot.checklistFlipSlot and 1
        val activeSlotIds = if (flipSlot == 0) SLOT_0_IDS else SLOT_1_IDS
        val inactiveSlotIds = if (flipSlot == 0) SLOT_1_IDS else SLOT_0_IDS

        val fullRv = buildFullRemoteViews(
            context = context,
            appWidgetId = appWidgetId,
            checklist = activeChecklist,
            totalCount = totalCount,
            checkedCount = checkedCount,
            uncheckedCount = uncheckedCount,
            visibleItems = visibleItems,
            showUncheckedOnly = snapshot.showUncheckedOnly,
            isResetConfirmActive = snapshot.isResetConfirmActive,
            hasMultipleChecklists = hasMultipleChecklists,
            showResetButton = showResetButton,
            showExpandedBar = showExpandedBar,
            columnCount = columnCount,
            flipSlot = flipSlot,
            activeSlotIds = activeSlotIds,
            inactiveSlotIds = inactiveSlotIds,
            isContentFlip = isContentFlip,
            isFilterFlip = isFilterFlip,
        )

        manager.updateAppWidget(appWidgetId, fullRv)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && visibleItems.isNotEmpty()) {
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(appWidgetId, activeSlotIds.listViewId)
            if (!isContentFlip) {
                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(appWidgetId, inactiveSlotIds.listViewId)
            }
        }
    }

    private fun renderSingleCounterWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: WidgetUiSnapshot,
        isContentFlip: Boolean,
    ) {
        val activeChecklist = snapshot.activeChecklist
        if (activeChecklist == null) {
            val emptyRv = RemoteViews(context.packageName, R.layout.widget_empty).apply {
                setOnClickPendingIntent(
                    R.id.widget_empty_root,
                    buildOpenAppPendingIntent(context, null)
                )
            }
            manager.updateAppWidget(appWidgetId, emptyRv)
            return
        }

        val options = manager.getAppWidgetOptions(appWidgetId)
        val (widthDp, _) = resolveDimensionsDp(context, options)
        val isWide = widthDp >= 220f

        val checklistItems = snapshot.activeChecklistItems
        val totalCount = checklistItems.size
        val uncheckedCount = checklistItems.count { !it.isChecked }
        val checkedCount = totalCount - uncheckedCount
        val hasMultipleChecklists = snapshot.checklists.size > 1
        val flipSlot = snapshot.checklistFlipSlot and 1
        val activeSlotIds = if (flipSlot == 0) COUNTER_SLOT_0_IDS else COUNTER_SLOT_1_IDS
        val inactiveSlotIds = if (flipSlot == 0) COUNTER_SLOT_1_IDS else COUNTER_SLOT_0_IDS

        val counterRv = buildCounterBarRemoteViews(
            context = context,
            checklist = activeChecklist,
            totalCount = totalCount,
            checkedCount = checkedCount,
            uncheckedCount = uncheckedCount,
            isWide = isWide,
            isResetConfirmActive = snapshot.isResetConfirmActive,
            hasMultipleChecklists = hasMultipleChecklists,
            flipSlot = flipSlot,
            activeSlotIds = activeSlotIds,
            inactiveSlotIds = inactiveSlotIds,
            isContentFlip = isContentFlip,
        )
        manager.updateAppWidget(appWidgetId, counterRv)
    }

    private fun buildCounterBarRemoteViews(
        context: Context,
        checklist: ChecklistEntity,
        totalCount: Int,
        checkedCount: Int,
        uncheckedCount: Int,
        isWide: Boolean,
        isResetConfirmActive: Boolean,
        hasMultipleChecklists: Boolean,
        flipSlot: Int,
        activeSlotIds: CounterFlipSlotViewIds,
        inactiveSlotIds: CounterFlipSlotViewIds,
        isContentFlip: Boolean,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_counter_bar).also { rv ->
            bindCounterSlot(
                context = context,
                rv = rv,
                slotIds = activeSlotIds,
                checklist = checklist,
                totalCount = totalCount,
                checkedCount = checkedCount,
                uncheckedCount = uncheckedCount,
                isWide = isWide,
            )

            if (isContentFlip) {
                rv.setDisplayedChild(R.id.widget_counter_flipper, flipSlot)
            } else {
                bindCounterSlot(
                    context = context,
                    rv = rv,
                    slotIds = inactiveSlotIds,
                    checklist = checklist,
                    totalCount = totalCount,
                    checkedCount = checkedCount,
                    uncheckedCount = uncheckedCount,
                    isWide = isWide,
                )
            }

            bindResetButtons(
                context = context,
                rv = rv,
                resetButtonId = R.id.widget_counter_btn_reset,
                confirmButtonId = R.id.widget_counter_btn_reset_confirm,
                checklistId = checklist.id,
                visible = isWide && totalCount > 0,
                isResetConfirmActive = isResetConfirmActive,
            )

            bindSwitchButtons(
                context = context,
                rv = rv,
                checklistId = checklist.id,
                enabled = hasMultipleChecklists,
                prevButtonId = R.id.widget_counter_btn_prev,
                nextButtonId = R.id.widget_counter_btn_next,
            )
        }
    }

    private fun bindCounterSlot(
        context: Context,
        rv: RemoteViews,
        slotIds: CounterFlipSlotViewIds,
        checklist: ChecklistEntity,
        totalCount: Int,
        checkedCount: Int,
        uncheckedCount: Int,
        isWide: Boolean,
    ) {
        val isAllDone = totalCount > 0 && uncheckedCount == 0
        val openAppIntent = buildOpenAppPendingIntent(context, checklist.id)

        rv.setTextViewText(slotIds.titleId, checklist.name)
        rv.setOnClickPendingIntent(slotIds.openAppId, openAppIntent)

        val badgeText = if (isAllDone) {
            context.getString(R.string.widget_all_set_short)
        } else {
            context.getString(R.string.widget_left_count, uncheckedCount)
        }
        rv.setTextViewText(slotIds.badgeId, badgeText)
        rv.setInt(
            slotIds.badgeId,
            "setBackgroundResource",
            if (isAllDone) R.drawable.widget_pill_primary_container else R.drawable.widget_pill_tertiary_container
        )
        applyTextColor(
            context = context,
            rv = rv,
            viewId = slotIds.badgeId,
            colorRes = if (isAllDone) R.color.widget_on_primary_container else R.color.widget_on_tertiary_container
        )

        if (isWide && totalCount > 0) {
            rv.setViewVisibility(slotIds.wideInfoId, View.VISIBLE)
            bindProgressViews(
                context = context,
                rv = rv,
                activeProgressId = slotIds.progressActiveId,
                doneProgressId = slotIds.progressDoneId,
                ratioViewId = slotIds.ratioId,
                checkedCount = checkedCount,
                totalCount = totalCount,
                isAllDone = isAllDone,
            )
        } else {
            rv.setViewVisibility(slotIds.wideInfoId, View.GONE)
        }
    }

    private fun bindFullHeaderViews(
        context: Context,
        rv: RemoteViews,
        checklist: ChecklistEntity,
        totalCount: Int,
        checkedCount: Int,
        uncheckedCount: Int,
        showUncheckedOnly: Boolean,
        isResetConfirmActive: Boolean,
        hasMultipleChecklists: Boolean,
        showResetButton: Boolean,
        showExpandedBar: Boolean,
        isFilterFlip: Boolean,
    ) {
        val isAllDone = totalCount > 0 && uncheckedCount == 0
        val openAppIntent = buildOpenAppPendingIntent(context, checklist.id)

        // Primary Header
        rv.setTextViewText(R.id.widget_header_title, checklist.name)
        rv.setOnClickPendingIntent(R.id.widget_header_title, openAppIntent)

        // Lock pill label to concise count so toggling filter never squeezes the checklist title.
        val filterLabel = if (isAllDone && !showUncheckedOnly) {
            context.getString(R.string.widget_all_set_short)
        } else {
            context.getString(R.string.widget_left_count, uncheckedCount)
        }
        val filterIntent = buildBroadcastPendingIntent(
            context = context,
            requestCode = REQ_TOGGLE_FILTER,
            command = TotalCheckWidgetReceiver.CMD_TOGGLE_FILTER
        )
        rv.setTextViewText(R.id.widget_btn_filter_normal, filterLabel)
        rv.setInt(
            R.id.widget_btn_filter_normal,
            "setBackgroundResource",
            if (isAllDone) R.drawable.widget_pill_primary_container else R.drawable.widget_pill_secondary_container
        )
        applyTextColor(
            context,
            rv,
            R.id.widget_btn_filter_normal,
            if (isAllDone) R.color.widget_on_primary_container else R.color.widget_on_secondary_container
        )
        rv.setOnClickPendingIntent(R.id.widget_btn_filter_normal, filterIntent)

        rv.setTextViewText(R.id.widget_btn_filter_active, filterLabel)
        rv.setOnClickPendingIntent(R.id.widget_btn_filter_active, filterIntent)

        if (isFilterFlip) {
            rv.setDisplayedChild(R.id.widget_filter_pill_flipper, if (showUncheckedOnly) 1 else 0)
        }

        bindResetButtons(
            context = context,
            rv = rv,
            resetButtonId = R.id.widget_btn_reset,
            confirmButtonId = R.id.widget_btn_reset_confirm,
            checklistId = checklist.id,
            visible = showResetButton && totalCount > 0,
            isResetConfirmActive = isResetConfirmActive,
        )

        bindSwitchButtons(
            context = context,
            rv = rv,
            checklistId = checklist.id,
            enabled = hasMultipleChecklists
        )

        // Tier 4 Secondary Progress & Quick Add Bar
        if (showExpandedBar) {
            rv.setViewVisibility(R.id.widget_expanded_bar, View.VISIBLE)
            bindProgressViews(
                context = context,
                rv = rv,
                activeProgressId = R.id.widget_progress_active,
                doneProgressId = R.id.widget_progress_done,
                ratioViewId = R.id.widget_progress_ratio,
                checkedCount = checkedCount,
                totalCount = totalCount,
                isAllDone = isAllDone,
            )
            rv.setOnClickPendingIntent(R.id.widget_progress_pill, openAppIntent)
            rv.setOnClickPendingIntent(R.id.widget_btn_add_short, openAppIntent)
        } else {
            rv.setViewVisibility(R.id.widget_expanded_bar, View.GONE)
        }
    }

    private fun buildFullRemoteViews(
        context: Context,
        appWidgetId: Int,
        checklist: ChecklistEntity,
        totalCount: Int,
        checkedCount: Int,
        uncheckedCount: Int,
        visibleItems: List<CheckItemEntity>,
        showUncheckedOnly: Boolean,
        isResetConfirmActive: Boolean,
        hasMultipleChecklists: Boolean,
        showResetButton: Boolean,
        showExpandedBar: Boolean,
        columnCount: Int,
        flipSlot: Int,
        activeSlotIds: FlipSlotViewIds,
        inactiveSlotIds: FlipSlotViewIds,
        isContentFlip: Boolean,
        isFilterFlip: Boolean,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_full)
        val openAppIntent = buildOpenAppPendingIntent(context, checklist.id)

        bindFullHeaderViews(
            context = context,
            rv = rv,
            checklist = checklist,
            totalCount = totalCount,
            checkedCount = checkedCount,
            uncheckedCount = uncheckedCount,
            showUncheckedOnly = showUncheckedOnly,
            isResetConfirmActive = isResetConfirmActive,
            hasMultipleChecklists = hasMultipleChecklists,
            showResetButton = showResetButton,
            showExpandedBar = showExpandedBar,
            isFilterFlip = isFilterFlip,
        )

        bindFlipSlotContent(
            context = context,
            rv = rv,
            appWidgetId = appWidgetId,
            slotIds = activeSlotIds,
            checklist = checklist,
            totalCount = totalCount,
            visibleItems = visibleItems,
            columnCount = columnCount,
            openAppIntent = openAppIntent,
        )

        if (isContentFlip) {
            // Preserve the outgoing slot's existing ListView content during the 140ms fade-out.
            rv.setDisplayedChild(R.id.widget_content_flipper, flipSlot)
        } else {
            // Keep the hidden slot synced for Launcher cold restarts (which default ViewFlipper to child 0).
            bindFlipSlotContent(
                context = context,
                rv = rv,
                appWidgetId = appWidgetId,
                slotIds = inactiveSlotIds,
                checklist = checklist,
                totalCount = totalCount,
                visibleItems = visibleItems,
                columnCount = columnCount,
                openAppIntent = openAppIntent,
            )
        }

        return rv
    }

    private fun bindFlipSlotContent(
        context: Context,
        rv: RemoteViews,
        appWidgetId: Int,
        slotIds: FlipSlotViewIds,
        checklist: ChecklistEntity,
        totalCount: Int,
        visibleItems: List<CheckItemEntity>,
        columnCount: Int,
        openAppIntent: PendingIntent,
    ) {
        when {
            totalCount == 0 -> {
                rv.setViewVisibility(slotIds.listViewId, View.GONE)
                rv.setViewVisibility(slotIds.allSetStateId, View.GONE)
                rv.setViewVisibility(slotIds.emptyStateId, View.VISIBLE)
                rv.setOnClickPendingIntent(slotIds.emptyStatePillId, openAppIntent)
            }

            visibleItems.isEmpty() -> {
                rv.setViewVisibility(slotIds.listViewId, View.GONE)
                rv.setViewVisibility(slotIds.emptyStateId, View.GONE)
                rv.setViewVisibility(slotIds.allSetStateId, View.VISIBLE)
                rv.setTextViewText(
                    slotIds.allSetBadgeId,
                    context.getString(R.string.widget_all_set_with_count, totalCount)
                )
                rv.setOnClickPendingIntent(
                    slotIds.btnRecheckId,
                    buildBroadcastPendingIntent(
                        context = context,
                        requestCode = REQ_FORCE_RESET,
                        command = TotalCheckWidgetReceiver.CMD_FORCE_RESET,
                        checklistId = checklist.id
                    )
                )
                rv.setOnClickPendingIntent(
                    slotIds.btnShowAllId,
                    buildBroadcastPendingIntent(
                        context = context,
                        requestCode = REQ_SHOW_ALL,
                        command = TotalCheckWidgetReceiver.CMD_SHOW_ALL
                    )
                )
            }

            else -> {
                rv.setViewVisibility(slotIds.emptyStateId, View.GONE)
                rv.setViewVisibility(slotIds.allSetStateId, View.GONE)
                rv.setViewVisibility(slotIds.listViewId, View.VISIBLE)

                val templateIntent = Intent(context, TotalCheckWidgetReceiver::class.java).apply {
                    action = TotalCheckWidgetReceiver.ACTION_WIDGET_COMMAND
                }
                val templateFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val templatePendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQ_LIST_TEMPLATE,
                    templateIntent,
                    templateFlags
                )
                rv.setPendingIntentTemplate(slotIds.listViewId, templatePendingIntent)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bindCollectionItemsApi31(
                        context = context,
                        rv = rv,
                        listViewId = slotIds.listViewId,
                        visibleItems = visibleItems,
                        columnCount = columnCount,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val serviceIntent = Intent(context, TotalCheckWidgetViewsService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = Uri.parse("${toUri(Intent.URI_INTENT_SCHEME)}#slot_${slotIds.listViewId}")
                    }
                    @Suppress("DEPRECATION")
                    rv.setRemoteAdapter(slotIds.listViewId, serviceIntent)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun bindCollectionItemsApi31(
        context: Context,
        rv: RemoteViews,
        listViewId: Int,
        visibleItems: List<CheckItemEntity>,
        columnCount: Int,
    ) {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(3)

        if (columnCount <= 1) {
            for (item in visibleItems) {
                builder.addItem(
                    item.id,
                    buildRowRemoteViews(context, listOf(item), 1)
                )
            }
        } else {
            val size = visibleItems.size
            var idx = 0
            while (idx < size) {
                val end = (idx + columnCount).coerceAtMost(size)
                val rowItems = visibleItems.subList(idx, end)
                builder.addItem(
                    rowItems[0].id,
                    buildRowRemoteViews(context, rowItems, columnCount)
                )
                idx = end
            }
        }

        rv.setRemoteAdapter(listViewId, builder.build())
    }

    private fun bindTileSlot(
        context: Context,
        rowRv: RemoteViews,
        tileId: Int,
        item: CheckItemEntity?,
    ) {
        if (item == null) {
            rowRv.setViewVisibility(tileId, View.INVISIBLE)
            rowRv.setOnClickFillInIntent(tileId, Intent())
            return
        }
        rowRv.setViewVisibility(tileId, View.VISIBLE)
        rowRv.setInt(
            tileId,
            "setBackgroundResource",
            if (item.isChecked) {
                R.drawable.widget_pill_surface_low
            } else {
                R.drawable.widget_pill_surface_high
            }
        )
        rowRv.setTextViewCompoundDrawablesRelative(
            tileId,
            if (item.isChecked) {
                R.drawable.widget_ic_check_checked
            } else {
                R.drawable.widget_ic_dot_unchecked
            },
            0,
            0,
            0
        )
        applyTextColor(
            context = context,
            rv = rowRv,
            viewId = tileId,
            colorRes = if (item.isChecked) {
                R.color.widget_on_surface_variant
            } else {
                R.color.widget_on_surface
            }
        )
        rowRv.setTextViewText(tileId, buildStyledLabel(item.label, item.isChecked))
        val fillInIntent = Intent().apply {
            putExtra(TotalCheckWidgetReceiver.EXTRA_COMMAND, TotalCheckWidgetReceiver.CMD_TOGGLE_ITEM)
            putExtra(TotalCheckWidgetReceiver.EXTRA_ITEM_ID, item.id)
        }
        rowRv.setOnClickFillInIntent(tileId, fillInIntent)
    }

    private fun buildStyledLabel(label: String, isChecked: Boolean): CharSequence {
        return if (isChecked) {
            SpannableString(label).apply {
                setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            label
        }
    }

    private fun bindProgressViews(
        context: Context,
        rv: RemoteViews,
        activeProgressId: Int,
        doneProgressId: Int,
        ratioViewId: Int,
        checkedCount: Int,
        totalCount: Int,
        isAllDone: Boolean,
    ) {
        val progressPct = if (totalCount > 0) {
            ((checkedCount * 100f) / totalCount.toFloat()).toInt().coerceIn(0, 100)
        } else {
            0
        }
        if (isAllDone) {
            rv.setViewVisibility(activeProgressId, View.GONE)
            rv.setViewVisibility(doneProgressId, View.VISIBLE)
            rv.setProgressBar(doneProgressId, 100, progressPct, false)
        } else {
            rv.setViewVisibility(doneProgressId, View.GONE)
            rv.setViewVisibility(activeProgressId, View.VISIBLE)
            rv.setProgressBar(activeProgressId, 100, progressPct, false)
        }
        rv.setTextViewText(
            ratioViewId,
            context.getString(R.string.widget_progress_ratio, checkedCount, totalCount)
        )
    }

    private fun bindResetButtons(
        context: Context,
        rv: RemoteViews,
        resetButtonId: Int,
        confirmButtonId: Int,
        checklistId: Long,
        visible: Boolean,
        isResetConfirmActive: Boolean,
    ) {
        if (!visible) {
            rv.setViewVisibility(resetButtonId, View.GONE)
            rv.setViewVisibility(confirmButtonId, View.GONE)
            return
        }
        val resetIntent = buildBroadcastPendingIntent(
            context = context,
            requestCode = REQ_RESET_STAGE,
            command = TotalCheckWidgetReceiver.CMD_RESET_STAGE,
            checklistId = checklistId
        )
        if (isResetConfirmActive) {
            rv.setViewVisibility(resetButtonId, View.GONE)
            rv.setViewVisibility(confirmButtonId, View.VISIBLE)
            rv.setOnClickPendingIntent(confirmButtonId, resetIntent)
        } else {
            rv.setViewVisibility(confirmButtonId, View.GONE)
            rv.setViewVisibility(resetButtonId, View.VISIBLE)
            rv.setOnClickPendingIntent(resetButtonId, resetIntent)
        }
    }

    private fun bindSwitchButtons(
        context: Context,
        rv: RemoteViews,
        checklistId: Long,
        enabled: Boolean,
        prevButtonId: Int = R.id.widget_btn_prev,
        nextButtonId: Int = R.id.widget_btn_next,
    ) {
        val textColorRes = if (enabled) {
            R.color.widget_on_surface
        } else {
            R.color.widget_on_surface_variant
        }
        applyTextColor(context, rv, prevButtonId, textColorRes)
        applyTextColor(context, rv, nextButtonId, textColorRes)

        rv.setOnClickPendingIntent(
            prevButtonId,
            buildBroadcastPendingIntent(
                context = context,
                requestCode = REQ_PREV_CHECKLIST,
                command = TotalCheckWidgetReceiver.CMD_SWITCH_CHECKLIST,
                checklistId = checklistId,
                direction = -1
            )
        )
        rv.setOnClickPendingIntent(
            nextButtonId,
            buildBroadcastPendingIntent(
                context = context,
                requestCode = REQ_NEXT_CHECKLIST,
                command = TotalCheckWidgetReceiver.CMD_SWITCH_CHECKLIST,
                checklistId = checklistId,
                direction = 1
            )
        )
    }

    private fun applyTextColor(
        context: Context,
        rv: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setColor(viewId, "setTextColor", colorRes)
        } else {
            rv.setTextColor(viewId, context.getColor(colorRes))
        }
    }

    private fun buildBroadcastPendingIntent(
        context: Context,
        requestCode: Int,
        command: String,
        checklistId: Long? = null,
        direction: Int? = null
    ): PendingIntent {
        val intent = Intent(context, TotalCheckWidgetReceiver::class.java).apply {
            action = TotalCheckWidgetReceiver.ACTION_WIDGET_COMMAND
            putExtra(TotalCheckWidgetReceiver.EXTRA_COMMAND, command)
            if (checklistId != null) {
                putExtra(TotalCheckWidgetReceiver.EXTRA_CHECKLIST_ID, checklistId)
            }
            if (direction != null) {
                putExtra(TotalCheckWidgetReceiver.EXTRA_DIRECTION, direction)
            }
            data = Uri.parse("totalcheck://widget/cmd/$command/${direction ?: 0}")
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildOpenAppPendingIntent(
        context: Context,
        checklistId: Long?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            if (checklistId != null && checklistId > 0L) {
                putExtra(MainActivity.EXTRA_WIDGET_CHECKLIST_ID, checklistId)
            }
            data = Uri.parse("totalcheck://widget/checklist/${checklistId ?: 0L}")
        }
        return PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun resolveDimensionsDp(context: Context, options: Bundle): Pair<Float, Float> {
        val isLandscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        var widthDp = if (isLandscape) {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0).toFloat()
        } else {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toFloat()
        }
        var heightDp = if (isLandscape) {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).toFloat()
        } else {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).toFloat()
        }

        if ((widthDp <= 0f || heightDp <= 0f) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes: ArrayList<SizeF>? =
                options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                val chosen = if (isLandscape) {
                    sizes.maxByOrNull { it.width }
                } else {
                    sizes.minByOrNull { it.width }
                }
                if (chosen != null && chosen.width > 0f && chosen.height > 0f) {
                    widthDp = chosen.width
                    heightDp = chosen.height
                }
            }
        }

        if (widthDp <= 0f) widthDp = 250f
        if (heightDp <= 0f) heightDp = 180f
        return widthDp to heightDp
    }

    private fun resolveSizeTier(widthDp: Float, heightDp: Float): WidgetSizeTier {
        return when {
            widthDp < 240f -> WidgetSizeTier.COMPACT
            widthDp >= 320f && heightDp >= 220f -> WidgetSizeTier.EXPANDED
            else -> WidgetSizeTier.STANDARD
        }
    }
}
