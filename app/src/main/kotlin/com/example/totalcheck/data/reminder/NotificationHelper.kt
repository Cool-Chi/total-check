package com.example.totalcheck.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.totalcheck.MainActivity
import com.example.totalcheck.R
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.ReminderEntity

object NotificationHelper {

    const val CHANNEL_ID = "totalcheck_reminders_channel"

    private const val REQ_CONTENT_BASE = 400_000
    private const val REQ_SNOOZE_ACTION_BASE = 410_000
    private const val REQ_CHECK_ACTION_BASE = 420_000

    fun ensureChannel(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.notif_channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.notif_channel_reminders_desc)
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context.applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    suspend fun evaluateAndNotifyIfNeeded(
        context: Context,
        reminder: ReminderEntity,
        isTestNotification: Boolean = false,
    ): Boolean {
        val appContext = context.applicationContext
        if (reminder.scheduleKind == ReminderEntity.KIND_AUTO_RESTORE && !isTestNotification) return false
        if (!hasNotificationPermission(appContext)) return false
        ensureChannel(appContext)

        val dao = AppDatabase.getInstance(appContext).checklistDao()
        val checklist = dao.getChecklistById(reminder.checklistId) ?: return false
        val items = dao.getItemsByChecklist(reminder.checklistId).filter { !it.isArchived }

        val notificationId = resolveNotificationId(reminder)
        val contentIntent = buildOpenChecklistPendingIntent(appContext, reminder, notificationId)

        val snoozeIntent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_SNOOZE_15M
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderBroadcastReceiver.EXTRA_CHECKLIST_ID, reminder.checklistId)
            if (reminder.itemId != null) {
                putExtra(ReminderBroadcastReceiver.EXTRA_ITEM_ID, reminder.itemId)
            }
            putExtra(ReminderBroadcastReceiver.EXTRA_ONLY_IF_UNCHECKED, reminder.onlyIfUnchecked)
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            data = Uri.parse("totalcheck://notif/$notificationId/snooze")
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQ_SNOOZE_ACTION_BASE + (notificationId and 0xFFFF),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                0,
                appContext.getString(R.string.notif_action_snooze_15m),
                snoozePendingIntent
            )

        if (reminder.itemId == null) {
            val uncheckedItems = items.filter { !it.isChecked }
            if (!isTestNotification && reminder.onlyIfUnchecked && uncheckedItems.isEmpty()) {
                return false
            }

            val title = if (uncheckedItems.isEmpty()) {
                appContext.getString(R.string.notif_checklist_title_all_set, checklist.name)
            } else {
                appContext.getString(
                    R.string.notif_checklist_title_unchecked,
                    checklist.name,
                    uncheckedItems.size
                )
            }

            val body = if (uncheckedItems.isEmpty()) {
                appContext.getString(R.string.notif_checklist_body_all_set)
            } else {
                val previewNames = uncheckedItems.take(3).joinToString(", ") { it.label }
                if (uncheckedItems.size <= 3) {
                    appContext.getString(R.string.notif_checklist_body_items, previewNames)
                } else {
                    appContext.getString(
                        R.string.notif_checklist_body_more,
                        previewNames,
                        uncheckedItems.size
                    )
                }
            }

            builder
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))

            if (uncheckedItems.isNotEmpty() || isTestNotification) {
                val markAllIntent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                    action = ReminderBroadcastReceiver.ACTION_MARK_ALL_CHECKED
                    putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminder.id)
                    putExtra(ReminderBroadcastReceiver.EXTRA_CHECKLIST_ID, reminder.checklistId)
                    putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    data = Uri.parse("totalcheck://notif/$notificationId/mark_all")
                }
                val markAllPendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    REQ_CHECK_ACTION_BASE + (notificationId and 0xFFFF),
                    markAllIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    0,
                    appContext.getString(R.string.notif_action_mark_all_checked),
                    markAllPendingIntent
                )
            }
        } else {
            val targetItem = dao.getItemById(reminder.itemId) ?: return false
            if (targetItem.isArchived && !isTestNotification) {
                return false
            }
            if (!isTestNotification && reminder.onlyIfUnchecked && targetItem.isChecked) {
                return false
            }

            val title = appContext.getString(R.string.notif_item_title, targetItem.label)
            val body = appContext.getString(R.string.notif_item_body, checklist.name)
            builder
                .setContentTitle(title)
                .setContentText(body)

            if (!targetItem.isChecked || isTestNotification) {
                val markItemIntent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                    action = ReminderBroadcastReceiver.ACTION_MARK_ITEM_CHECKED
                    putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminder.id)
                    putExtra(ReminderBroadcastReceiver.EXTRA_CHECKLIST_ID, reminder.checklistId)
                    putExtra(ReminderBroadcastReceiver.EXTRA_ITEM_ID, targetItem.id)
                    putExtra(ReminderBroadcastReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    data = Uri.parse("totalcheck://notif/$notificationId/mark_item")
                }
                val markItemPendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    REQ_CHECK_ACTION_BASE + (notificationId and 0xFFFF),
                    markItemIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    0,
                    appContext.getString(R.string.notif_action_mark_item_checked),
                    markItemPendingIntent
                )
            }
        }

        return try {
            NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancelNotificationById(context: Context, notificationId: Int) {
        if (notificationId <= 0) return
        NotificationManagerCompat.from(context.applicationContext).cancel(notificationId)
    }

    fun cancelNotificationForReminder(context: Context, reminderId: Long) {
        if (reminderId <= 0L) return
        val notificationId = (reminderId.toInt() and 0x0FFFFFFF).coerceAtLeast(1)
        NotificationManagerCompat.from(context.applicationContext).cancel(notificationId)
    }

    private fun resolveNotificationId(reminder: ReminderEntity): Int {
        return if (reminder.id > 0L) {
            (reminder.id.toInt() and 0x0FFFFFFF).coerceAtLeast(1)
        } else {
            val hash = ((reminder.checklistId * 131L) + (reminder.itemId ?: 0L) * 17L).toInt()
            10_000_000 + (hash and 0x00FFFFFF)
        }
    }

    private fun buildOpenChecklistPendingIntent(
        context: Context,
        reminder: ReminderEntity,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_WIDGET_CHECKLIST_ID, reminder.checklistId)
            data = Uri.parse(
                "totalcheck://notification/open/${reminder.checklistId}/${reminder.itemId ?: 0L}"
            )
        }
        return PendingIntent.getActivity(
            context,
            REQ_CONTENT_BASE + (notificationId and 0xFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
