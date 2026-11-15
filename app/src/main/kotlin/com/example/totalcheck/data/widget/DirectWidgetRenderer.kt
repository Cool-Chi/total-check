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
    MICRO,
    COMPACT,
    STANDARD,
    EXPANDED
}

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
        listViewId = R.id.widget_list_view_0,
        emptyStateId = R.id.widget_empty_state_0,
        emptyStatePillId = R.id.widget_empty_state_pill_0,
        allSetStateId = R.id.widget_all_set_state_0,
        allSetBadgeId = R.id.widget_all_set_badge_0,
        btnRecheckId = R.id.widget_btn_recheck_0,
        btnShowAllId = R.id.widget_btn_show_all_0,
    )

    private val SLOT_1_IDS = FlipSlotViewIds(
        listViewId = R.id.widget_list_view_1,
        emptyStateId = R.id.widget_empty_state_1,
        emptyStatePillId = R.id.widget_empty_state_pill_1,
        allSetStateId = R.id.widget_all_set_state_1,
        allSetBadgeId = R.id.widget_all_set_badge_1,
        btnRecheckId = R.id.widget_btn_recheck_1,
        btnShowAllId = R.id.widget_btn_show_all_1,
    )

    fun renderAllWidgets(
        context: Context,
        snapshot: WidgetUiSnapshot
    ) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = ComponentName(appContext, TotalCheckWidgetReceiver::class.java)
        val appWidgetIds = manager.getAppWidgetIds(provider)
        if (appWidgetIds.isEmpty()) return

        for (appWidgetId in appWidgetIds) {
            renderSingleWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot
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
            renderSingleWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot
            )
        }
    }

    /**
     * Partially updates only the widget header (title, remaining badge, reset button, switch buttons,
     * and Tier 4 progress bar) without touching ViewFlipper or ListView collection items.
     */
    fun updateHeaderOnly(context: Context) {
        val snapshot = WidgetSyncManager.snapshotFlow.value ?: return
        updateHeaderOnly(context, snapshot)
    }

    internal fun updateHeaderOnly(
        context: Context,
        snapshot: WidgetUiSnapshot
    ) {
        val activeChecklist = snapshot.activeChecklist ?: return
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = ComponentName(appContext, TotalCheckWidgetReceiver::class.java)
        val appWidgetIds = manager.getAppWidgetIds(provider)
        if (appWidgetIds.isEmpty()) return

        val checklistItems = snapshot.activeChecklistItems
        val totalCount = checklistItems.size
        val uncheckedCount = checklistItems.count { !it.isChecked }
        val checkedCount = totalCount - uncheckedCount
        val hasMultipleChecklists = snapshot.checklists.size > 1

        for (appWidgetId in appWidgetIds) {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val (widthDp, heightDp) = resolveDimensionsDp(appContext, options)
            val tier = resolveSizeTier(widthDp, heightDp)

            val headerRv = if (tier == WidgetSizeTier.MICRO) {
                RemoteViews(appContext.packageName, R.layout.widget_micro).also { rv ->
                    bindMicroHeaderViews(
                        context = appContext,
                        rv = rv,
                        checklist = activeChecklist,
                        totalCount = totalCount,
                        uncheckedCount = uncheckedCount,
                        hasMultipleChecklists = hasMultipleChecklists,
                    )
                }
            } else {
                val showResetButton = tier == WidgetSizeTier.STANDARD || tier == WidgetSizeTier.EXPANDED
                val showExpandedBar = tier == WidgetSizeTier.EXPANDED
                RemoteViews(appContext.packageName, R.layout.widget_full).also { rv ->
                    bindFullHeaderViews(
                        context = appContext,
                        rv = rv,
                        checklist = activeChecklist,
                        totalCount = totalCount,
                        checkedCount = checkedCount,
                        uncheckedCount = uncheckedCount,
                        showUncheckedOnly = snapshot.showUncheckedOnly,
                        isResetConfirmActive = snapshot.isResetConfirmActive,
                        hasMultipleChecklists = hasMultipleChecklists,
                        showResetButton = showResetButton,
                        showExpandedBar = showExpandedBar,
                    )
                }
            }
            manager.partiallyUpdateAppWidget(appWidgetId, headerRv)
        }
    }

    fun resolveColumnCountForWidget(context: Context, appWidgetId: Int): Int {
        val manager = AppWidgetManager.getInstance(context)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val (widthDp, heightDp) = resolveDimensionsDp(context, options)
        return when (resolveSizeTier(widthDp, heightDp)) {
            WidgetSizeTier.COMPACT -> 1
            WidgetSizeTier.EXPANDED -> if (widthDp >= 340f) 3 else 2
            else -> 2
        }
    }

    internal fun buildRowRemoteViews(
        context: Context,
        rowItems: List<CheckItemEntity>,
        columnCount: Int
    ): RemoteViews {
        val rowLayoutRes = when {
            columnCount <= 1 -> R.layout.widget_row_1col
            columnCount >= 3 -> R.layout.widget_row_3col
            else -> R.layout.widget_row_2col
        }
        val rowRv = RemoteViews(context.packageName, rowLayoutRes)
        bindTileSlot(
            rowRv = rowRv,
            tileId = R.id.tile_0,
            item = rowItems.getOrNull(0)
        )
        if (columnCount >= 2) {
            bindTileSlot(
                rowRv = rowRv,
                tileId = R.id.tile_1,
                item = rowItems.getOrNull(1)
            )
        }
        if (columnCount >= 3) {
            bindTileSlot(
                rowRv = rowRv,
                tileId = R.id.tile_2,
                item = rowItems.getOrNull(2)
            )
        }
        return rowRv
    }

    private fun renderSingleWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: WidgetUiSnapshot
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

        if (tier == WidgetSizeTier.MICRO) {
            val microRv = buildMicroRemoteViews(
                context = context,
                checklist = activeChecklist,
                totalCount = totalCount,
                uncheckedCount = uncheckedCount,
                hasMultipleChecklists = hasMultipleChecklists
            )
            manager.updateAppWidget(appWidgetId, microRv)
            return
        }

        val visibleItems = if (snapshot.showUncheckedOnly) {
            checklistItems.filter { !it.isChecked }
        } else {
            checklistItems
        }
        val showResetButton = tier == WidgetSizeTier.STANDARD || tier == WidgetSizeTier.EXPANDED
        val showExpandedBar = tier == WidgetSizeTier.EXPANDED
        val columnCount = when (tier) {
            WidgetSizeTier.COMPACT -> 1
            WidgetSizeTier.EXPANDED -> if (widthDp >= 340f) 3 else 2
            else -> 2
        }
        val flipSlot = snapshot.checklistFlipSlot and 1
        val slotIds = if (flipSlot == 0) SLOT_0_IDS else SLOT_1_IDS

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
            slotIds = slotIds,
        )

        manager.updateAppWidget(appWidgetId, fullRv)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            totalCount > 0 &&
            visibleItems.isNotEmpty()
        ) {
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(appWidgetId, slotIds.listViewId)
        }
    }

    private fun buildMicroRemoteViews(
        context: Context,
        checklist: ChecklistEntity,
        totalCount: Int,
        uncheckedCount: Int,
        hasMultipleChecklists: Boolean
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_micro).also { rv ->
            bindMicroHeaderViews(
                context = context,
                rv = rv,
                checklist = checklist,
                totalCount = totalCount,
                uncheckedCount = uncheckedCount,
                hasMultipleChecklists = hasMultipleChecklists,
            )
        }
    }

    private fun bindMicroHeaderViews(
        context: Context,
        rv: RemoteViews,
        checklist: ChecklistEntity,
        totalCount: Int,
        uncheckedCount: Int,
        hasMultipleChecklists: Boolean
    ) {
        val isAllDone = totalCount > 0 && uncheckedCount == 0
        val openAppIntent = buildOpenAppPendingIntent(context, checklist.id)

        rv.setTextViewText(R.id.widget_micro_title, checklist.name)
        rv.setOnClickPendingIntent(R.id.widget_micro_open_app, openAppIntent)

        val badgeText = if (isAllDone) {
            context.getString(R.string.widget_all_set_short)
        } else {
            context.getString(R.string.widget_left_count, uncheckedCount)
        }
        rv.setTextViewText(R.id.widget_micro_badge, badgeText)
        rv.setInt(
            R.id.widget_micro_badge,
            "setBackgroundResource",
            if (isAllDone) R.drawable.widget_pill_primary_container else R.drawable.widget_pill_tertiary_container
        )
        applyTextColor(
            context = context,
            rv = rv,
            viewId = R.id.widget_micro_badge,
            colorRes = if (isAllDone) R.color.widget_on_primary_container else R.color.widget_on_tertiary_container
        )

        bindSwitchButtons(
            context = context,
            rv = rv,
            checklistId = checklist.id,
            enabled = hasMultipleChecklists
        )
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
    ) {
        val isAllDone = totalCount > 0 && uncheckedCount == 0
        val openAppIntent = buildOpenAppPendingIntent(context, checklist.id)

        // Primary Header
        rv.setTextViewText(R.id.widget_header_title, checklist.name)
        rv.setOnClickPendingIntent(R.id.widget_header_title, openAppIntent)

        val filterLabel = when {
            showUncheckedOnly -> context.getString(R.string.widget_left_filtered, uncheckedCount)
            isAllDone -> context.getString(R.string.widget_all_set_short)
            else -> context.getString(R.string.widget_left_count, uncheckedCount)
        }
        val filterBg = when {
            showUncheckedOnly -> R.drawable.widget_pill_tertiary
            isAllDone -> R.drawable.widget_pill_primary_container
            else -> R.drawable.widget_pill_secondary_container
        }
        val filterTextColor = when {
            showUncheckedOnly -> R.color.widget_on_tertiary
            isAllDone -> R.color.widget_on_primary_container
            else -> R.color.widget_on_secondary_container
        }
        rv.setTextViewText(R.id.widget_btn_filter, filterLabel)
        rv.setInt(R.id.widget_btn_filter, "setBackgroundResource", filterBg)
        applyTextColor(context, rv, R.id.widget_btn_filter, filterTextColor)
        rv.setOnClickPendingIntent(
            R.id.widget_btn_filter,
            buildBroadcastPendingIntent(
                context = context,
                requestCode = REQ_TOGGLE_FILTER,
                command = TotalCheckWidgetReceiver.CMD_TOGGLE_FILTER
            )
        )

        if (showResetButton && totalCount > 0) {
            rv.setViewVisibility(R.id.widget_btn_reset, View.VISIBLE)
            rv.setTextViewText(
                R.id.widget_btn_reset,
                if (isResetConfirmActive) {
                    context.getString(R.string.widget_reset_confirm)
                } else {
                    context.getString(R.string.widget_reset_icon)
                }
            )
            rv.setInt(
                R.id.widget_btn_reset,
                "setBackgroundResource",
                if (isResetConfirmActive) {
                    R.drawable.widget_pill_error_container
                } else {
                    R.drawable.widget_pill_surface_variant
                }
            )
            applyTextColor(
                context = context,
                rv = rv,
                viewId = R.id.widget_btn_reset,
                colorRes = if (isResetConfirmActive) {
                    R.color.widget_on_error_container
                } else {
                    R.color.widget_on_surface_variant
                }
            )
            rv.setOnClickPendingIntent(
                R.id.widget_btn_reset,
                buildBroadcastPendingIntent(
                    context = context,
                    requestCode = REQ_RESET_STAGE,
                    command = TotalCheckWidgetReceiver.CMD_RESET_STAGE,
                    checklistId = checklist.id
                )
            )
        } else {
            rv.setViewVisibility(R.id.widget_btn_reset, View.GONE)
        }

        bindSwitchButtons(
            context = context,
            rv = rv,
            checklistId = checklist.id,
            enabled = hasMultipleChecklists
        )

        // Tier 4 Secondary Progress & Quick Add Bar
        if (showExpandedBar) {
            rv.setViewVisibility(R.id.widget_expanded_bar, View.VISIBLE)
            val progressPct = if (totalCount > 0) {
                ((checkedCount * 100f) / totalCount.toFloat()).toInt().coerceIn(0, 100)
            } else {
                0
            }
            if (isAllDone) {
                rv.setViewVisibility(R.id.widget_progress_active, View.GONE)
                rv.setViewVisibility(R.id.widget_progress_done, View.VISIBLE)
                rv.setProgressBar(R.id.widget_progress_done, 100, progressPct, false)
            } else {
                rv.setViewVisibility(R.id.widget_progress_done, View.GONE)
                rv.setViewVisibility(R.id.widget_progress_active, View.VISIBLE)
                rv.setProgressBar(R.id.widget_progress_active, 100, progressPct, false)
            }
            rv.setTextViewText(
                R.id.widget_progress_ratio,
                context.getString(R.string.widget_progress_ratio, checkedCount, totalCount)
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
        slotIds: FlipSlotViewIds,
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
        )

        // Bind only the active ViewFlipper slot so switching < > crossfades old slot -> new slot cleanly.
        rv.setDisplayedChild(R.id.widget_content_flipper, flipSlot)

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
                        columnCount = columnCount
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val serviceIntent = Intent(context, TotalCheckWidgetViewsService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    }
                    @Suppress("DEPRECATION")
                    rv.setRemoteAdapter(slotIds.listViewId, serviceIntent)
                }
            }
        }

        return rv
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun bindCollectionItemsApi31(
        context: Context,
        rv: RemoteViews,
        listViewId: Int,
        visibleItems: List<CheckItemEntity>,
        columnCount: Int
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
                idx += columnCount
            }
        }

        rv.setRemoteAdapter(listViewId, builder.build())
    }

    private fun bindTileSlot(
        rowRv: RemoteViews,
        tileId: Int,
        item: CheckItemEntity?
    ) {
        if (item == null) {
            rowRv.setViewVisibility(tileId, View.INVISIBLE)
            return
        }
        rowRv.setViewVisibility(tileId, View.VISIBLE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rowRv.setCompoundButtonChecked(tileId, item.isChecked)
        } else {
            rowRv.setBoolean(tileId, "setChecked", item.isChecked)
        }

        val styledLabel = if (item.isChecked) {
            SpannableString(item.label).apply {
                setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            item.label
        }
        rowRv.setTextViewText(tileId, styledLabel)

        val fillInIntent = Intent().apply {
            putExtra(TotalCheckWidgetReceiver.EXTRA_COMMAND, TotalCheckWidgetReceiver.CMD_TOGGLE_ITEM)
            putExtra(TotalCheckWidgetReceiver.EXTRA_ITEM_ID, item.id)
        }
        rowRv.setOnClickFillInIntent(tileId, fillInIntent)
    }

    private fun bindSwitchButtons(
        context: Context,
        rv: RemoteViews,
        checklistId: Long,
        enabled: Boolean
    ) {
        val textColorRes = if (enabled) {
            R.color.widget_on_surface
        } else {
            R.color.widget_on_surface_variant
        }
        applyTextColor(context, rv, R.id.widget_btn_prev, textColorRes)
        applyTextColor(context, rv, R.id.widget_btn_next, textColorRes)

        rv.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            buildBroadcastPendingIntent(
                context = context,
                requestCode = REQ_PREV_CHECKLIST,
                command = TotalCheckWidgetReceiver.CMD_SWITCH_CHECKLIST,
                checklistId = checklistId,
                direction = -1
            )
        )
        rv.setOnClickPendingIntent(
            R.id.widget_btn_next,
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
            widthDp < 180f && heightDp < 110f -> WidgetSizeTier.MICRO
            widthDp < 240f || heightDp < 140f -> WidgetSizeTier.COMPACT
            widthDp >= 320f && heightDp >= 220f -> WidgetSizeTier.EXPANDED
            else -> WidgetSizeTier.STANDARD
        }
    }
}
