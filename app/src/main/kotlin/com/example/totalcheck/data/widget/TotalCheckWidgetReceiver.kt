package com.example.totalcheck.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.totalcheck.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TotalCheckWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_COMMAND) {
            val appContext = context.applicationContext
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            val command = intent.getStringExtra(EXTRA_COMMAND) ?: return
            val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
            val checklistId = intent.getLongExtra(EXTRA_CHECKLIST_ID, -1L)
            val direction = intent.getIntExtra(EXTRA_DIRECTION, 1)

            if (command == CMD_OPEN_APP) {
                val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (checklistId > 0L) {
                        putExtra(MainActivity.EXTRA_WIDGET_CHECKLIST_ID, checklistId)
                    }
                    if (intent.getBooleanExtra(MainActivity.EXTRA_AUTO_FOCUS_INPUT, false)) {
                        putExtra(MainActivity.EXTRA_AUTO_FOCUS_INPUT, true)
                    }
                }
                appContext.startActivity(launchIntent)
                return
            }

            val handledFast = WidgetSyncManager.tryHandleFastCommand(
                context = appContext,
                appWidgetId = appWidgetId,
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
                            appWidgetId = appWidgetId,
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetSyncManager.onWidgetsDeleted(context.applicationContext, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
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
        const val CMD_OPEN_APP = "open_app"

        private val coldScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
