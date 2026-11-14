package com.example.totalcheck.data.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.totalcheck.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object WidgetSyncManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var resetTimeoutJob: Job? = null

    fun notifyDataChanged(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                TotalCheckAppWidget().updateAll(appContext)
            }
        }
    }

    fun scheduleResetConfirmRevert(context: Context, delayMillis: Long = 3100L) {
        val appContext = context.applicationContext
        resetTimeoutJob?.cancel()
        resetTimeoutJob = scope.launch {
            delay(delayMillis)
            runCatching {
                val prefsRepo = UserPreferencesRepository(appContext)
                val until = prefsRepo.widgetResetConfirmUntilMillis.first()
                if (until != 0L && System.currentTimeMillis() >= until) {
                    prefsRepo.setWidgetResetConfirmUntilMillis(0L)
                }
                TotalCheckAppWidget().updateAll(appContext)
            }
        }
    }

    fun cancelResetConfirmRevert() {
        resetTimeoutJob?.cancel()
        resetTimeoutJob = null
    }
}
