package com.example.totalcheck.data.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.preferences.ThemeConfig
import kotlinx.coroutines.runBlocking

class TotalCheckWidgetViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return TotalCheckViewsFactory(applicationContext, appWidgetId)
    }
}

private class TotalCheckViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<List<CheckItemEntity>> = emptyList()
    private var activeChecklistId: Long = -1L
    private var activeItemReminderIds: Set<Long> = emptySet()
    private var columnCount: Int = 2
    private var hasBlankRows: Boolean = false
    private var style: WidgetThemeStyle = resolveWidgetStyle(
        context,
        ThemeConfig(isDarkMode = true, useDynamicColor = false)
    )

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val snapshot = WidgetSyncManager.snapshotFlow.value ?: runBlocking {
            WidgetSyncManager.ensureInitialized(context)
        }
        style = resolveWidgetStyle(context, snapshot.themeConfig)
        val activeChecklist = snapshot.resolveActiveChecklist(appWidgetId)
        activeChecklistId = activeChecklist?.id ?: -1L
        activeItemReminderIds = activeChecklist?.let {
            snapshot.reminderSummaryForChecklist(it.id).activeItemRemindersByItemId.keys
        }.orEmpty()
        val items = activeChecklist?.let { snapshot.itemsForChecklist(it.id) }.orEmpty()
        val visibleItems = if (snapshot.stateForWidget(appWidgetId).showUncheckedOnly) {
            items.filter { !it.isChecked }
        } else {
            items
        }
        columnCount = DirectWidgetRenderer.resolveColumnCountForWidget(context, appWidgetId)
        rows = if (visibleItems.isEmpty()) {
            emptyList()
        } else {
            visibleItems.chunked(columnCount)
        }
        hasBlankRows = visibleItems.isNotEmpty()
    }

    override fun onDestroy() {
        rows = emptyList()
        activeItemReminderIds = emptySet()
        hasBlankRows = false
    }

    override fun getCount(): Int = rows.size + if (hasBlankRows) 2 else 0

    override fun getViewAt(position: Int): RemoteViews? {
        if (position in rows.indices) {
            return DirectWidgetRenderer.buildRowRemoteViews(
                context = context,
                rowItems = rows[position],
                columnCount = columnCount,
                style = style,
                checklistId = activeChecklistId,
                activeItemReminderIds = activeItemReminderIds,
            )
        }
        if (hasBlankRows && position < getCount()) {
            return DirectWidgetRenderer.buildBlankSpaceRowRemoteViews(
                context = context,
                checklistId = activeChecklistId,
            )
        }
        return null
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 4

    override fun getItemId(position: Int): Long = when {
        position in rows.indices -> rows[position].firstOrNull()?.id ?: position.toLong()
        hasBlankRows -> -1001L - (position - rows.size).toLong()
        else -> position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
