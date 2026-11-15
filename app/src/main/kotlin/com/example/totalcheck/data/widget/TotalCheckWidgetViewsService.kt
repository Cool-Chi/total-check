package com.example.totalcheck.data.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.totalcheck.data.local.CheckItemEntity
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
    private var columnCount: Int = 2

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val snapshot = WidgetSyncManager.snapshotFlow.value ?: runBlocking {
            WidgetSyncManager.ensureInitialized(context)
        }
        val activeChecklist = snapshot.resolveActiveChecklist(appWidgetId)
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
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        val rowItems = rows.getOrNull(position) ?: return null
        return DirectWidgetRenderer.buildRowRemoteViews(
            context = context,
            rowItems = rowItems,
            columnCount = columnCount
        )
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.firstOrNull()?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
