package com.example.totalcheck.data.reminder

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

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val checklistId = intent.getLongExtra(EXTRA_CHECKLIST_ID, -1L)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L).takeIf { it > 0L }
        val onlyIfUnchecked = intent.getBooleanExtra(EXTRA_ONLY_IF_UNCHECKED, true)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val reminderDao = db.reminderDao()
                val checklistDao = db.checklistDao()
                val repository = ChecklistRepository(appContext, checklistDao, reminderDao)

                when (action) {
                    ACTION_FIRE_REMINDER -> {
                        if (reminderId <= 0L) return@launch
                        val reminder = reminderDao.getReminderById(reminderId) ?: return@launch
                        if (!reminder.isEnabled) return@launch

                        val maskedDays = reminder.repeatDaysBitmask and ReminderFormatter.DAILY_MASK
                        if (maskedDays == 0) {
                            // One-time reminder: auto-disable after firing and keep off across checklist resets.
                            reminderDao.setReminderEnabled(
                                id = reminder.id,
                                isEnabled = false,
                                triggerEpochMillis = 0L
                            )
                        } else {
                            // Recurring reminder: compute and schedule next occurrence.
                            val nextTrigger = ReminderScheduler.computeNextTriggerEpochMillis(
                                hour = reminder.hour,
                                minute = reminder.minute,
                                repeatDaysBitmask = maskedDays
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

                        NotificationHelper.evaluateAndNotifyIfNeeded(
                            context = appContext,
                            reminder = reminder,
                            isTestNotification = false
                        )
                        repository.syncWidgetsNow()
                    }

                    ACTION_FIRE_SNOOZE -> {
                        val candidateReminder = if (reminderId > 0L) {
                            reminderDao.getReminderById(reminderId)
                        } else if (checklistId > 0L) {
                            ReminderEntity(
                                id = 0L,
                                checklistId = checklistId,
                                itemId = itemId,
                                hour = 0,
                                minute = 0,
                                onlyIfUnchecked = onlyIfUnchecked,
                                isEnabled = true
                            )
                        } else {
                            null
                        }
                        val targetReminder = candidateReminder ?: return@launch

                        NotificationHelper.evaluateAndNotifyIfNeeded(
                            context = appContext,
                            reminder = targetReminder,
                            isTestNotification = false
                        )
                    }

                    ACTION_SNOOZE_15M -> {
                        NotificationHelper.cancelNotificationById(appContext, notificationId)
                        val resolvedChecklistId = if (checklistId > 0L) {
                            checklistId
                        } else {
                            reminderDao.getReminderById(reminderId)?.checklistId ?: -1L
                        }
                        if (resolvedChecklistId > 0L) {
                            ReminderScheduler.scheduleSnooze(
                                context = appContext,
                                reminderId = reminderId,
                                checklistId = resolvedChecklistId,
                                itemId = itemId,
                                onlyIfUnchecked = onlyIfUnchecked,
                                delayMinutes = 15
                            )
                        }
                    }

                    ACTION_MARK_ALL_CHECKED -> {
                        NotificationHelper.cancelNotificationById(appContext, notificationId)
                        ReminderScheduler.cancelSnooze(appContext, reminderId)
                        if (checklistId > 0L) {
                            checklistDao.checkAllChecklistItems(checklistId)
                            repository.syncWidgetsNow()
                        }
                    }

                    ACTION_MARK_ITEM_CHECKED -> {
                        NotificationHelper.cancelNotificationById(appContext, notificationId)
                        ReminderScheduler.cancelSnooze(appContext, reminderId)
                        if (itemId != null && itemId > 0L) {
                            checklistDao.updateItemChecked(itemId = itemId, isChecked = true)
                            repository.syncWidgetsNow()
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "com.example.totalcheck.reminder.ACTION_FIRE_REMINDER"
        const val ACTION_FIRE_SNOOZE = "com.example.totalcheck.reminder.ACTION_FIRE_SNOOZE"
        const val ACTION_SNOOZE_15M = "com.example.totalcheck.reminder.ACTION_SNOOZE_15M"
        const val ACTION_MARK_ALL_CHECKED = "com.example.totalcheck.reminder.ACTION_MARK_ALL_CHECKED"
        const val ACTION_MARK_ITEM_CHECKED = "com.example.totalcheck.reminder.ACTION_MARK_ITEM_CHECKED"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_CHECKLIST_ID = "extra_reminder_checklist_id"
        const val EXTRA_ITEM_ID = "extra_reminder_item_id"
        const val EXTRA_ONLY_IF_UNCHECKED = "extra_reminder_only_if_unchecked"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
