package com.example.totalcheck.data.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.ReminderEntity
import com.example.totalcheck.data.repository.ChecklistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val handledActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        if (action !in handledActions) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        bootScope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val reminderDao = db.reminderDao()
                val checklistDao = db.checklistDao()
                val repository = ChecklistRepository(appContext, checklistDao, reminderDao)

                val enabledReminders = reminderDao.getEnabledReminders()
                val nowMillis = System.currentTimeMillis()
                for (reminder in enabledReminders) {
                    val maskedDays = reminder.repeatDaysBitmask and ReminderFormatter.DAILY_MASK
                    if (maskedDays == 0) {
                        if (reminder.triggerEpochMillis <= nowMillis) {
                            reminderDao.setReminderEnabled(
                                id = reminder.id,
                                isEnabled = false,
                                triggerEpochMillis = 0L
                            )
                            val overdueMillis = nowMillis - reminder.triggerEpochMillis
                            if (overdueMillis in 0L..OVERDUE_CATCHUP_WINDOW_MS) {
                                if (reminder.scheduleKind == ReminderEntity.KIND_AUTO_RESTORE) {
                                    reminder.itemId?.let { itemId ->
                                        repository.applyAutoRestoreForItem(itemId)
                                    }
                                } else {
                                    NotificationHelper.evaluateAndNotifyIfNeeded(
                                        context = appContext,
                                        reminder = reminder,
                                        isTestNotification = false
                                    )
                                }
                            }
                        } else {
                            ReminderScheduler.scheduleReminder(appContext, reminder)
                        }
                    } else {
                        val nextTrigger = ReminderScheduler.computeNextTriggerEpochMillis(
                            hour = reminder.hour,
                            minute = reminder.minute,
                            repeatDaysBitmask = maskedDays,
                            nowMillis = nowMillis
                        )
                        val updated = reminder.copy(
                            isEnabled = true,
                            triggerEpochMillis = nextTrigger
                        )
                        reminderDao.setReminderEnabled(
                            id = updated.id,
                            isEnabled = true,
                            triggerEpochMillis = nextTrigger
                        )
                        ReminderScheduler.scheduleReminder(appContext, updated)
                    }
                }
                repository.syncWidgetsNow()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val OVERDUE_CATCHUP_WINDOW_MS = 12L * 60L * 60L * 1000L
        private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
