package com.example.totalcheck.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TotalCheckCounterWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        renderIds(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        renderIds(context, intArrayOf(appWidgetId))
    }

    private fun renderIds(context: Context, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        val warmSnapshot = WidgetSyncManager.snapshotFlow.value
        if (warmSnapshot != null) {
            DirectWidgetRenderer.renderCounterWidgetsByIds(
                context = appContext,
                appWidgetIds = appWidgetIds,
                snapshot = warmSnapshot,
            )
            return
        }

        val pendingResult = goAsync()
        coldScope.launch {
            try {
                val snapshot = WidgetSyncManager.ensureInitialized(appContext)
                DirectWidgetRenderer.renderCounterWidgetsByIds(
                    context = appContext,
                    appWidgetIds = appWidgetIds,
                    snapshot = snapshot,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val coldScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
