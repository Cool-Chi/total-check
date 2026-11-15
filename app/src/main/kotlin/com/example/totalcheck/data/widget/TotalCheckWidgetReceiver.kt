package com.example.totalcheck.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TotalCheckWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_COMMAND) {
            val appContext = context.applicationContext
            val command = intent.getStringExtra(EXTRA_COMMAND) ?: return
            val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
            val checklistId = intent.getLongExtra(EXTRA_CHECKLIST_ID, -1L)
            val direction = intent.getIntExtra(EXTRA_DIRECTION, 1)

            val handledFast = WidgetSyncManager.tryHandleFastCommand(
                context = appContext,
                command = command,
                itemId = itemId,
                checklistId = checklistId,
                direction = direction,
                onScheduleAsync = { work ->
                    val pendingResult = goAsync()
                    WidgetSyncManager.launchAsyncWithPendingResult(
                        context = appContext,
                        pendingResult = pendingResult,
                        work = work,
                    )
                },
            )

            if (!handledFast) {
                val pendingResult = goAsync()
                coldScope.launch {
                    try {
                        WidgetSyncManager.handleColdCommand(
                            context = appContext,
                            command = command,
                            itemId = itemId,
                            checklistId = checklistId,
                            direction = direction,
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            return
        }

        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appContext = context.applicationContext
        val warmSnapshot = WidgetSyncManager.snapshotFlow.value
        if (warmSnapshot != null) {
            DirectWidgetRenderer.renderWidgetsByIds(
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
                DirectWidgetRenderer.renderWidgetsByIds(
                    context = appContext,
                    appWidgetIds = appWidgetIds,
                    snapshot = snapshot,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val appContext = context.applicationContext
        val warmSnapshot = WidgetSyncManager.snapshotFlow.value
        if (warmSnapshot != null) {
            DirectWidgetRenderer.renderWidgetsByIds(
                context = appContext,
                appWidgetIds = intArrayOf(appWidgetId),
                snapshot = warmSnapshot,
            )
            return
        }

        val pendingResult = goAsync()
        coldScope.launch {
            try {
                val snapshot = WidgetSyncManager.ensureInitialized(appContext)
                DirectWidgetRenderer.renderWidgetsByIds(
                    context = appContext,
                    appWidgetIds = intArrayOf(appWidgetId),
                    snapshot = snapshot,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_COMMAND = "com.example.totalcheck.widget.ACTION_COMMAND"
        const val EXTRA_COMMAND = "extra_widget_command"
        const val EXTRA_ITEM_ID = "extra_widget_item_id"
        const val EXTRA_CHECKLIST_ID = "extra_widget_checklist_id"
        const val EXTRA_DIRECTION = "extra_widget_direction"

        const val CMD_TOGGLE_ITEM = "toggle_item"
        const val CMD_TOGGLE_FILTER = "toggle_filter"
        const val CMD_SHOW_ALL = "show_all"
        const val CMD_SWITCH_CHECKLIST = "switch_checklist"
        const val CMD_RESET_STAGE = "reset_stage"
        const val CMD_FORCE_RESET = "force_reset"

        private val coldScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
