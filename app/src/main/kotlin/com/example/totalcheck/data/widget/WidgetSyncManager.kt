package com.example.totalcheck.data.widget

import android.content.Context

object WidgetSyncManager {

    /**
     * Decoupled hook for future Jetpack Glance Widget synchronization (`updateAll()`).
     */
    suspend fun notifyDataChanged(context: Context) {
        // Reserved for Milestone 4: GlanceAppWidget.updateAll(context.applicationContext)
    }
}
