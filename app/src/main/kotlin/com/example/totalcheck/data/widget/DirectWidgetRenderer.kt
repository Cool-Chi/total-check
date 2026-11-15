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

private data class ProgressBarGroupIds(
    val activeCuratedDarkId: Int,
    val doneCuratedDarkId: Int,
    val activeCuratedLightId: Int,
    val doneCuratedLightId: Int,
    val activeDynamicDarkId: Int,
    val doneDynamicDarkId: Int,
    val activeDynamicLightId: Int,
    val doneDynamicLightId: Int,
) {
    fun allIds(): IntArray = intArrayOf(
        activeCuratedDarkId,
        doneCuratedDarkId,
        activeCuratedLightId,
        doneCuratedLightId,
        activeDynamicDarkId,
        doneDynamicDarkId,
        activeDynamicLightId,
        doneDynamicLightId,
    )

    fun targetId(variant: WidgetThemeVariant, isAllDone: Boolean): Int = when (variant) {
        WidgetThemeVariant.CURATED_DARK -> if (isAllDone) doneCuratedDarkId else activeCuratedDarkId
        WidgetThemeVariant.CURATED_LIGHT -> if (isAllDone) doneCuratedLightId else activeCuratedLightId
        WidgetThemeVariant.DYNAMIC_DARK -> if (isAllDone) doneDynamicDarkId else activeDynamicDarkId
        WidgetThemeVariant.DYNAMIC_LIGHT -> if (isAllDone) doneDynamicLightId else activeDynamicLightId
    }
}

private data class CounterFlipSlotViewIds(
    val openAppId: Int,
    val titleId: Int,
    val countId: Int,
    val progressGroupIds: ProgressBarGroupIds,
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

    private const val REQ_LIST_TEMPLATE_BASE = 10_000
    private const val REQ_TOGGLE_FILTER_BASE = 20_000
    private const val REQ_RESET_STAGE_BASE = 30_000
    private const val REQ_PREV_CHECKLIST_BASE = 40_000
    private const val REQ_NEXT_CHECKLIST_BASE = 50_000
    private const val REQ_FORCE_RESET_BASE = 60_000
    private const val REQ_SHOW_ALL_BASE = 70_000
    private const val REQ_OPEN_APP_BASE = 80_000
    private const val REQ_OPEN_APP_ADD_BASE = 90_000

    private val FULL_PROGRESS_IDS = ProgressBarGroupIds(
        activeCuratedDarkId = R.id.widget_progress_active_curated_dark,
        doneCuratedDarkId = R.id.widget_progress_done_curated_dark,
        activeCuratedLightId = R.id.widget_progress_active_curated_light,
        doneCuratedLightId = R.id.widget_progress_done_curated_light,
        activeDynamicDarkId = R.id.widget_progress_active_dynamic_dark,
        doneDynamicDarkId = R.id.widget_progress_done_dynamic_dark,
        activeDynamicLightId = R.id.widget_progress_active_dynamic_light,
        doneDynamicLightId = R.id.widget_progress_done_dynamic_light,
    )

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
        countId = R.id.widget_counter_count_0,
        progressGroupIds = ProgressBarGroupIds(
            activeCuratedDarkId = R.id.widget_counter_progress_active_curated_dark_0,
            doneCuratedDarkId = R.id.widget_counter_progress_done_curated_dark_0,
            activeCuratedLightId = R.id.widget_counter_progress_active_curated_light_0,
            doneCuratedLightId = R.id.widget_counter_progress_done_curated_light_0,
            activeDynamicDarkId = R.id.widget_counter_progress_active_dynamic_dark_0,
            doneDynamicDarkId = R.id.widget_counter_progress_done_dynamic_dark_0,
            activeDynamicLightId = R.id.widget_counter_progress_active_dynamic_light_0,
            doneDynamicLightId = R.id.widget_counter_progress_done_dynamic_light_0,
        ),
    )

    private val COUNTER_SLOT_1_IDS = CounterFlipSlotViewIds(
        openAppId = R.id.widget_counter_open_app_1,
        titleId = R.id.widget_counter_title_1,
        countId = R.id.widget_counter_count_1,
        progressGroupIds = ProgressBarGroupIds(
            activeCuratedDarkId = R.id.widget_counter_progress_active_curated_dark_1,
            doneCuratedDarkId = R.id.widget_counter_progress_done_curated_dark_1,
            activeCuratedLightId = R.id.widget_counter_progress_active_curated_light_1,
            doneCuratedLightId = R.id.widget_counter_progress_done_curated_light_1,
            activeDynamicDarkId = R.id.widget_counter_progress_active_dynamic_dark_1,
            doneDynamicDarkId = R.id.widget_counter_progress_done_dynamic_dark_1,
            activeDynamicLightId = R.id.widget_counter_progress_active_dynamic_light_1,
            doneDynamicLightId = R.id.widget_counter_progress_done_dynamic_light_1,
        ),
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

    fun renderSingleWidgetById(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetUiSnapshot,
        isContentFlip: Boolean = false,
        isFilterFlip: Boolean = false,
    ) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val providerClass = manager.getAppWidgetInfo(appWidgetId)?.provider?.className
        if (providerClass == TotalCheckCounterWidgetReceiver::class.java.name) {
            renderSingleCounterWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                isContentFlip = isContentFlip,
            )
        } else {
            renderSingleGridWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                isContentFlip = isContentFlip,
                isFilterFlip = isFilterFlip,
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
            val widgetState = snapshot.stateForWidget(appWidgetId)
            renderSingleGridWidget(
                context = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                isContentFlip = false,
                isFilterFlip = widgetState.showUncheckedOnly,
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
        style: WidgetThemeStyle,
        checklistId: Long,
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
            item = rowItems.getOrNull(0),
            style = style,
            checklistId = checklistId,
        )
        if (columnCount >= 2) {
            bindTileSlot(
                rowRv = rowRv,
                tileId = R.id.tile_1,
                item = rowItems.getOrNull(1),
                style = style,
                checklistId = checklistId,
            )
        }
        if (columnCount >= 3) {
            bindTileSlot(
                rowRv = rowRv,
                tileId = R.id.tile_2,
                item = rowItems.getOrNull(2),
                style = style,
                checklistId = checklistId,
            )
        }
        return rowRv
    }

    internal fun buildBlankSpaceRowRemoteViews(
        context: Context,
        checklistId: Long,
    ): RemoteViews {
        val rowRv = RemoteViews(context.packageName, R.layout.widget_row_blank_space)
        val fillInIntent = Intent().apply {
            putExtra(TotalCheckWidgetReceiver.EXTRA_COMMAND, TotalCheckWidgetReceiver.CMD_OPEN_APP)
            putExtra(TotalCheckWidgetReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        rowRv.setOnClickFillInIntent(R.id.widget_blank_space_row, fillInIntent)
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
        val style = resolveWidgetStyle(context, snapshot.themeConfig)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val (widthDp, heightDp) = resolveDimensionsDp(context, options)
        val tier = resolveSizeTier(widthDp, heightDp)

        val widgetState = snapshot.stateForWidget(appWidgetId)
        val activeChecklist = snapshot.resolveActiveChecklist(appWidgetId)
        if (activeChecklist == null) {
            val emptyRv = buildEmptyRemoteViews(context, appWidgetId, style)
            manager.updateAppWidget(appWidgetId, emptyRv)
            return
        }

        val checklistItems = snapshot.itemsForChecklist(activeChecklist.id)
        val totalCount = checklistItems.size
        val uncheckedCount = checklistItems.count { !it.isChecked }
        val checkedCount = totalCount - uncheckedCount
        val hasMultipleChecklists = snapshot.checklists.size > 1

        val visibleItems = if (widgetState.showUncheckedOnly) {
            checklistItems.filter { !it.isChecked }
        } else {
            checklistItems
        }
        val showResetButton = tier == WidgetSizeTier.STANDARD || tier == WidgetSizeTier.EXPANDED
        val showExpandedBar = tier == WidgetSizeTier.EXPANDED
        val columnCount = resolveColumnCount(tier)
        val flipSlot = widgetState.checklistFlipSlot and 1
        val activeSlotIds = if (flipSlot == 0) SLOT_0_IDS else SLOT_1_IDS
        val inactiveSlotIds = if (flipSlot == 0) SLOT_1_IDS else SLOT_0_IDS

        val fullRv = buildFullRemoteViews(
            context = context,
            appWidgetId = appWidgetId,
            style = style,
            checklist = activeChecklist,
            totalCount = totalCount,
            checkedCount = checkedCount,
            uncheckedCount = uncheckedCount,
            visibleItems = visibleItems,
            showUncheckedOnly = widgetState.showUncheckedOnly,
            isResetConfirmActive = widgetState.isResetConfirmActive,
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
        val style = resolveWidgetStyle(context, snapshot.themeConfig)
        val widgetState = snapshot.stateForWidget(appWidgetId)
        val activeChecklist = snapshot.resolveActiveChecklist(appWidgetId)
        if (activeChecklist == null) {
            val emptyRv = buildEmptyRemoteViews(context, appWidgetId, style)
            manager.updateAppWidget(appWidgetId, emptyRv)
            return
        }

        val options = manager.getAppWidgetOptions(appWidgetId)
        val (widthDp, _) = resolveDimensionsDp(context, options)
        val isWide = widthDp >= 220f

        val checklistItems = snapshot.itemsForChecklist(activeChecklist.id)
        val totalCount = checklistItems.size
        val uncheckedCount = checklistItems.count { !it.isChecked }
        val checkedCount = totalCount - uncheckedCount
        val hasMultipleChecklists = snapshot.checklists.size > 1
        val flipSlot = widgetState.checklistFlipSlot and 1
        val activeSlotIds = if (flipSlot == 0) COUNTER_SLOT_0_IDS else COUNTER_SLOT_1_IDS
        val inactiveSlotIds = if (flipSlot == 0) COUNTER_SLOT_1_IDS else COUNTER_SLOT_0_IDS

        val counterRv = buildCounterBarRemoteViews(
            context = context,
            appWidgetId = appWidgetId,
            style = style,
            checklist = activeChecklist,
            totalCount = totalCount,
            checkedCount = checkedCount,
            uncheckedCount = uncheckedCount,
            isWide = isWide,
            isResetConfirmActive = widgetState.isResetConfirmActive,
            hasMultipleChecklists = hasMultipleChecklists,
            flipSlot = flipSlot,
            activeSlotIds = activeSlotIds,
            inactiveSlotIds = inactiveSlotIds,
            isContentFlip = isContentFlip,
        )
        manager.updateAppWidget(appWidgetId, counterRv)
    }

    private fun buildEmptyRemoteViews(
        context: Context,
        appWidgetId: Int,
        style: WidgetThemeStyle,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_empty).apply {
            setInt(R.id.widget_empty_root, "setBackgroundResource", style.containerBgRes)
            setTextColor(R.id.widget_empty_hint, style.textMuted)
            setOnClickPendingIntent(
                R.id.widget_empty_root,
                buildOpenAppPendingIntent(
                    context = context,
                    checklistId = null,
                    appWidgetId = appWidgetId,
                    autoFocusInput = true,
                )
            )
        }
    }

    private fun buildCounterBarRemoteViews(
        context: Context,
        appWidgetId: Int,
        style: WidgetThemeStyle,
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
            rv.setInt(R.id.widget_counter_root, "setBackgroundResource", style.containerBgRes)

            bindCounterSlot(
                context = context,
                appWidgetId = appWidgetId,
                style = style,
                rv = rv,
                slotIds = activeSlotIds,
                checklist = checklist,
                totalCount = totalCount,
                checkedCount = checkedCount,
                uncheckedCount = uncheckedCount,
            )

            if (isContentFlip) {
                rv.setDisplayedChild(R.id.widget_counter_flipper, flipSlot)
            } else {
                bindCounterSlot(
                    context = context,
                    appWidgetId = appWidgetId,
                    style = style,
                    rv = rv,
                    slotIds = inactiveSlotIds,
                    checklist = checklist,
                    totalCount = totalCount,
                    checkedCount = checkedCount,
                    uncheckedCount = uncheckedCount,
                )
            }

            bindResetButtons(
                context = context,
                rv = rv,
                appWidgetId = appWidgetId,
                style = style,
                resetButtonId = R.id.widget_counter_btn_reset,
                confirmButtonId = R.id.widget_counter_btn_reset_confirm,
                checklistId = checklist.id,
                visible = isWide && totalCount > 0,
                isResetConfirmActive = isResetConfirmActive,
            )

            bindSwitchButtons(
                context = context,
                rv = rv,
                appWidgetId = appWidgetId,
                style = style,
                checklistId = checklist.id,
                enabled = hasMultipleChecklists,
                prevButtonId = R.id.widget_counter_btn_prev,
                nextButtonId = R.id.widget_counter_btn_next,
            )
        }
    }

    private fun bindCounterSlot(
        context: Context,
        appWidgetId: Int,
        style: WidgetThemeStyle,
        rv: RemoteViews,
        slotIds: CounterFlipSlotViewIds,
        checklist: ChecklistEntity,
        totalCount: Int,
        checkedCount: Int,
        uncheckedCount: Int,
    ) {
        val isAllDone = totalCount > 0 && uncheckedCount == 0
        val openAppIntent = buildOpenAppPendingIntent(context, checklist.id, appWidgetId)

        rv.setTextViewText(slotIds.titleId, checklist.name)
        rv.setTextColor(slotIds.titleId, style.textPrimary)
        rv.setOnClickPendingIntent(slotIds.openAppId, openAppIntent)

        val countText = if (isAllDone) {
            "\u2713 $checkedCount/$totalCount"
        } else {
            context.getString(R.string.widget_left_count, uncheckedCount)
        }
        rv.setTextViewText(slotIds.countId, countText)
        rv.setTextColor(
            slotIds.countId,
            if (isAllDone) style.accentPrimary else style.accentTertiary
        )

        bindProgressViews(
            context = context,
            rv = rv,
            style = style,
            groupIds = slotIds.progressGroupIds,
            ratioViewId = null,
            checkedCount = checkedCount,
            totalCount = totalCount,
            isAllDone = isAllDone,
        )
    }

    private fun bindFullHeaderViews(
        context: Context,
        rv: RemoteViews,
        appWidgetId: Int,
        style: WidgetThemeStyle,
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
        val openAppIntent = buildOpenAppPendingIntent(context, checklist.id, appWidgetId)

        // Primary Header
        rv.setTextViewText(R.id.widget_header_title, checklist.name)
        rv.setTextColor(R.id.widget_header_title, style.textPrimary)
        rv.setOnClickPendingIntent(R.id.widget_header_title, openAppIntent)

        // Lock pill label to concise count so toggling filter never squeezes the checklist title.
        val filterLabel = if (isAllDone && !showUncheckedOnly) {
            context.getString(R.string.widget_all_set_short)
        } else {
            context.getString(R.string.widget_left_count, uncheckedCount)
        }
        val filterIntent = buildBroadcastPendingIntent(
            context = context,
            requestCodeBase = REQ_TOGGLE_FILTER_BASE,
            command = TotalCheckWidgetReceiver.CMD_TOGGLE_FILTER,
            appWidgetId = appWidgetId,
        )
        rv.setTextViewText(R.id.widget_btn_filter_normal, filterLabel)
        rv.setInt(
            R.id.widget_btn_filter_normal,
            "setBackgroundResource",
            if (isAllDone) style.primaryPillBgRes else style.buttonPillBgRes
        )
        rv.setTextColor(
            R.id.widget_btn_filter_normal,
            if (isAllDone) style.onPrimaryContainer else style.textPrimary
        )
        rv.setOnClickPendingIntent(R.id.widget_btn_filter_normal, filterIntent)

        rv.setTextViewText(R.id.widget_btn_filter_active, filterLabel)
        rv.setInt(
            R.id.widget_btn_filter_active,
            "setBackgroundResource",
            style.tertiaryPillBgRes
        )
        rv.setTextColor(R.id.widget_btn_filter_active, style.onTertiary)
        rv.setOnClickPendingIntent(R.id.widget_btn_filter_active, filterIntent)

        if (isFilterFlip) {
            rv.setDisplayedChild(R.id.widget_filter_pill_flipper, if (showUncheckedOnly) 1 else 0)
        }

        bindResetButtons(
            context = context,
            rv = rv,
            appWidgetId = appWidgetId,
            style = style,
            resetButtonId = R.id.widget_btn_reset,
            confirmButtonId = R.id.widget_btn_reset_confirm,
            checklistId = checklist.id,
            visible = showResetButton && totalCount > 0,
            isResetConfirmActive = isResetConfirmActive,
        )

        bindSwitchButtons(
            context = context,
            rv = rv,
            appWidgetId = appWidgetId,
            style = style,
            checklistId = checklist.id,
            enabled = hasMultipleChecklists
        )

        // Tier 4 Secondary Progress & Quick Add Bar
        if (showExpandedBar) {
            rv.setViewVisibility(R.id.widget_expanded_bar, View.VISIBLE)
            rv.setInt(R.id.widget_progress_pill, "setBackgroundResource", style.surfacePillBgRes)
            bindProgressViews(
                context = context,
                rv = rv,
                style = style,
                groupIds = FULL_PROGRESS_IDS,
                ratioViewId = R.id.widget_progress_ratio,
                checkedCount = checkedCount,
                totalCount = totalCount,
                isAllDone = isAllDone,
            )
            rv.setTextColor(R.id.widget_progress_ratio, style.textMuted)
            rv.setInt(R.id.widget_btn_add_short, "setBackgroundResource", style.primaryPillBgRes)
            rv.setTextColor(R.id.widget_btn_add_short, style.onPrimaryContainer)
            rv.setOnClickPendingIntent(R.id.widget_progress_pill, openAppIntent)
            rv.setOnClickPendingIntent(
                R.id.widget_btn_add_short,
                buildOpenAppPendingIntent(
                    context = context,
                    checklistId = checklist.id,
                    appWidgetId = appWidgetId,
                    autoFocusInput = true,
                )
            )
        } else {
            rv.setViewVisibility(R.id.widget_expanded_bar, View.GONE)
        }
    }

    private fun buildFullRemoteViews(
        context: Context,
        appWidgetId: Int,
        style: WidgetThemeStyle,
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
        rv.setInt(R.id.widget_root, "setBackgroundResource", style.containerBgRes)
        val openAppAddIntent = buildOpenAppPendingIntent(
            context = context,
            checklistId = checklist.id,
            appWidgetId = appWidgetId,
            autoFocusInput = true,
        )

        bindFullHeaderViews(
            context = context,
            rv = rv,
            appWidgetId = appWidgetId,
            style = style,
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
            style = style,
            slotIds = activeSlotIds,
            checklist = checklist,
            totalCount = totalCount,
            visibleItems = visibleItems,
            columnCount = columnCount,
            openAppAddIntent = openAppAddIntent,
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
                style = style,
                slotIds = inactiveSlotIds,
                checklist = checklist,
                totalCount = totalCount,
                visibleItems = visibleItems,
                columnCount = columnCount,
                openAppAddIntent = openAppAddIntent,
            )
        }

        return rv
    }

    private fun bindFlipSlotContent(
        context: Context,
        rv: RemoteViews,
        appWidgetId: Int,
        style: WidgetThemeStyle,
        slotIds: FlipSlotViewIds,
        checklist: ChecklistEntity,
        totalCount: Int,
        visibleItems: List<CheckItemEntity>,
        columnCount: Int,
        openAppAddIntent: PendingIntent,
    ) {
        when {
            totalCount == 0 -> {
                rv.setViewVisibility(slotIds.listViewId, View.GONE)
                rv.setViewVisibility(slotIds.allSetStateId, View.GONE)
                rv.setViewVisibility(slotIds.emptyStateId, View.VISIBLE)
                rv.setInt(slotIds.emptyStatePillId, "setBackgroundResource", style.buttonPillBgRes)
                rv.setTextColor(slotIds.emptyStatePillId, style.textPrimary)
                rv.setOnClickPendingIntent(slotIds.emptyStatePillId, openAppAddIntent)
            }

            visibleItems.isEmpty() -> {
                rv.setViewVisibility(slotIds.listViewId, View.GONE)
                rv.setViewVisibility(slotIds.emptyStateId, View.GONE)
                rv.setViewVisibility(slotIds.allSetStateId, View.VISIBLE)
                rv.setTextViewText(
                    slotIds.allSetBadgeId,
                    context.getString(R.string.widget_all_set_with_count, totalCount)
                )
                rv.setInt(slotIds.allSetBadgeId, "setBackgroundResource", style.primaryPillBgRes)
                rv.setTextColor(slotIds.allSetBadgeId, style.onPrimaryContainer)
                rv.setInt(slotIds.btnRecheckId, "setBackgroundResource", style.buttonPillBgRes)
                rv.setTextColor(slotIds.btnRecheckId, style.textPrimary)
                rv.setOnClickPendingIntent(
                    slotIds.btnRecheckId,
                    buildBroadcastPendingIntent(
                        context = context,
                        requestCodeBase = REQ_FORCE_RESET_BASE,
                        command = TotalCheckWidgetReceiver.CMD_FORCE_RESET,
                        appWidgetId = appWidgetId,
                        checklistId = checklist.id,
                    )
                )
                rv.setInt(slotIds.btnShowAllId, "setBackgroundResource", style.primaryPillBgRes)
                rv.setTextColor(slotIds.btnShowAllId, style.onPrimaryContainer)
                rv.setOnClickPendingIntent(
                    slotIds.btnShowAllId,
                    buildBroadcastPendingIntent(
                        context = context,
                        requestCodeBase = REQ_SHOW_ALL_BASE,
                        command = TotalCheckWidgetReceiver.CMD_SHOW_ALL,
                        appWidgetId = appWidgetId,
                    )
                )
            }

            else -> {
                rv.setViewVisibility(slotIds.emptyStateId, View.GONE)
                rv.setViewVisibility(slotIds.allSetStateId, View.GONE)
                rv.setViewVisibility(slotIds.listViewId, View.VISIBLE)

                val templateIntent = Intent(context, TotalCheckWidgetReceiver::class.java).apply {
                    action = TotalCheckWidgetReceiver.ACTION_WIDGET_COMMAND
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("totalcheck://widget/$appWidgetId/list")
                }
                val templateFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val templatePendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQ_LIST_TEMPLATE_BASE + (appWidgetId and 0xFFFF),
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
                        style = style,
                        checklistId = checklist.id,
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
        style: WidgetThemeStyle,
        checklistId: Long,
    ) {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(4)

        if (columnCount <= 1) {
            for (item in visibleItems) {
                builder.addItem(
                    item.id,
                    buildRowRemoteViews(context, listOf(item), 1, style, checklistId)
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
                    buildRowRemoteViews(context, rowItems, columnCount, style, checklistId)
                )
                idx = end
            }
        }

        if (visibleItems.isNotEmpty()) {
            builder.addItem(-1001L, buildBlankSpaceRowRemoteViews(context, checklistId))
            builder.addItem(-1002L, buildBlankSpaceRowRemoteViews(context, checklistId))
        }

        rv.setRemoteAdapter(listViewId, builder.build())
    }

    private fun bindTileSlot(
        rowRv: RemoteViews,
        tileId: Int,
        item: CheckItemEntity?,
        style: WidgetThemeStyle,
        checklistId: Long,
    ) {
        if (item == null) {
            rowRv.setViewVisibility(tileId, View.VISIBLE)
            rowRv.setInt(tileId, "setBackgroundResource", android.R.color.transparent)
            rowRv.setTextViewCompoundDrawablesRelative(tileId, 0, 0, 0, 0)
            rowRv.setTextViewText(tileId, "")
            val fillInIntent = Intent().apply {
                putExtra(TotalCheckWidgetReceiver.EXTRA_COMMAND, TotalCheckWidgetReceiver.CMD_OPEN_APP)
                putExtra(TotalCheckWidgetReceiver.EXTRA_CHECKLIST_ID, checklistId)
            }
            rowRv.setOnClickFillInIntent(tileId, fillInIntent)
            return
        }
        rowRv.setViewVisibility(tileId, View.VISIBLE)
        rowRv.setInt(
            tileId,
            "setBackgroundResource",
            if (item.isChecked) style.tileCheckedBgRes else style.tileUncheckedBgRes
        )
        rowRv.setTextViewCompoundDrawablesRelative(
            tileId,
            if (item.isChecked) style.checkIconRes else style.dotIconRes,
            0,
            0,
            0
        )
        rowRv.setTextColor(
            tileId,
            if (item.isChecked) style.textMuted else style.textPrimary
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
        style: WidgetThemeStyle,
        groupIds: ProgressBarGroupIds,
        ratioViewId: Int?,
        checkedCount: Int,
        totalCount: Int,
        isAllDone: Boolean,
    ) {
        val progressPct = if (totalCount > 0) {
            ((checkedCount * 100f) / totalCount.toFloat()).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val targetId = groupIds.targetId(style.variant, isAllDone)
        for (id in groupIds.allIds()) {
            rv.setViewVisibility(id, if (id == targetId) View.VISIBLE else View.GONE)
        }
        rv.setProgressBar(targetId, 100, progressPct, false)
        if (ratioViewId != null) {
            rv.setTextViewText(
                ratioViewId,
                context.getString(R.string.widget_progress_ratio, checkedCount, totalCount)
            )
        }
    }

    private fun bindResetButtons(
        context: Context,
        rv: RemoteViews,
        appWidgetId: Int,
        style: WidgetThemeStyle,
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
            requestCodeBase = REQ_RESET_STAGE_BASE,
            command = TotalCheckWidgetReceiver.CMD_RESET_STAGE,
            appWidgetId = appWidgetId,
            checklistId = checklistId,
        )
        if (isResetConfirmActive) {
            rv.setViewVisibility(resetButtonId, View.GONE)
            rv.setViewVisibility(confirmButtonId, View.VISIBLE)
            rv.setInt(confirmButtonId, "setBackgroundResource", style.errorPillBgRes)
            rv.setTextColor(confirmButtonId, style.onErrorContainer)
            rv.setOnClickPendingIntent(confirmButtonId, resetIntent)
        } else {
            rv.setViewVisibility(confirmButtonId, View.GONE)
            rv.setViewVisibility(resetButtonId, View.VISIBLE)
            rv.setInt(resetButtonId, "setBackgroundResource", style.buttonPillBgRes)
            rv.setTextColor(resetButtonId, style.textMuted)
            rv.setOnClickPendingIntent(resetButtonId, resetIntent)
        }
    }

    private fun bindSwitchButtons(
        context: Context,
        rv: RemoteViews,
        appWidgetId: Int,
        style: WidgetThemeStyle,
        checklistId: Long,
        enabled: Boolean,
        prevButtonId: Int = R.id.widget_btn_prev,
        nextButtonId: Int = R.id.widget_btn_next,
    ) {
        val textColor = if (enabled) style.textPrimary else style.textMuted
        rv.setInt(prevButtonId, "setBackgroundResource", style.buttonPillBgRes)
        rv.setInt(nextButtonId, "setBackgroundResource", style.buttonPillBgRes)
        rv.setTextColor(prevButtonId, textColor)
        rv.setTextColor(nextButtonId, textColor)

        rv.setOnClickPendingIntent(
            prevButtonId,
            buildBroadcastPendingIntent(
                context = context,
                requestCodeBase = REQ_PREV_CHECKLIST_BASE,
                command = TotalCheckWidgetReceiver.CMD_SWITCH_CHECKLIST,
                appWidgetId = appWidgetId,
                checklistId = checklistId,
                direction = -1
            )
        )
        rv.setOnClickPendingIntent(
            nextButtonId,
            buildBroadcastPendingIntent(
                context = context,
                requestCodeBase = REQ_NEXT_CHECKLIST_BASE,
                command = TotalCheckWidgetReceiver.CMD_SWITCH_CHECKLIST,
                appWidgetId = appWidgetId,
                checklistId = checklistId,
                direction = 1
            )
        )
    }

    private fun buildBroadcastPendingIntent(
        context: Context,
        requestCodeBase: Int,
        command: String,
        appWidgetId: Int,
        checklistId: Long? = null,
        direction: Int? = null
    ): PendingIntent {
        val intent = Intent(context, TotalCheckWidgetReceiver::class.java).apply {
            action = TotalCheckWidgetReceiver.ACTION_WIDGET_COMMAND
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(TotalCheckWidgetReceiver.EXTRA_COMMAND, command)
            if (checklistId != null) {
                putExtra(TotalCheckWidgetReceiver.EXTRA_CHECKLIST_ID, checklistId)
            }
            if (direction != null) {
                putExtra(TotalCheckWidgetReceiver.EXTRA_DIRECTION, direction)
            }
            data = Uri.parse("totalcheck://widget/$appWidgetId/cmd/$command/${direction ?: 0}")
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeBase + (appWidgetId and 0xFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildOpenAppPendingIntent(
        context: Context,
        checklistId: Long?,
        appWidgetId: Int,
        autoFocusInput: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            if (checklistId != null && checklistId > 0L) {
                putExtra(MainActivity.EXTRA_WIDGET_CHECKLIST_ID, checklistId)
            }
            if (autoFocusInput) {
                putExtra(MainActivity.EXTRA_AUTO_FOCUS_INPUT, true)
                data = Uri.parse("totalcheck://widget/$appWidgetId/add/${checklistId ?: 0L}")
            } else {
                data = Uri.parse("totalcheck://widget/$appWidgetId/checklist/${checklistId ?: 0L}")
            }
        }
        val requestCodeBase = if (autoFocusInput) REQ_OPEN_APP_ADD_BASE else REQ_OPEN_APP_BASE
        return PendingIntent.getActivity(
            context,
            requestCodeBase + (appWidgetId and 0xFFFF),
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
