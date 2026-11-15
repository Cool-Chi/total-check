package com.example.totalcheck.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.totalcheck.data.local.ReminderEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {

    private const val REQ_FIRE_BASE = 200_000
    private const val REQ_SNOOZE_BASE = 300_000

    fun computeNextTriggerEpochMillis(
        hour: Int,
        minute: Int,
        repeatDaysBitmask: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val zone = ZoneId.systemDefault()
        val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val targetTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val masked = repeatDaysBitmask and ReminderFormatter.DAILY_MASK

        if (masked == 0) {
            var candidate = nowZdt.toLocalDate().atTime(targetTime).atZone(zone)
            if (candidate.toInstant().toEpochMilli() <= nowMillis) {
                candidate = candidate.plusDays(1)
            }
            return candidate.toInstant().toEpochMilli()
        }

        for (dayOffset in 0L..7L) {
            val date = nowZdt.toLocalDate().plusDays(dayOffset)
            val bit = 1 shl (date.dayOfWeek.value - 1)
            if ((masked and bit) != 0) {
                val candidate = date.atTime(targetTime).atZone(zone)
                val candidateMillis = candidate.toInstant().toEpochMilli()
                if (candidateMillis > nowMillis) {
                    return candidateMillis
                }
            }
        }
        return nowMillis + 86_400_000L
    }

    fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        val appContext = context.applicationContext
        if (reminder.id <= 0L) return
        if (!reminder.isEnabled) {
            cancelReminder(appContext, reminder.id)
            return
        }
        val triggerAtMillis = if (reminder.triggerEpochMillis > System.currentTimeMillis()) {
            reminder.triggerEpochMillis
        } else {
            computeNextTriggerEpochMillis(
                hour = reminder.hour,
                minute = reminder.minute,
                repeatDaysBitmask = reminder.repeatDaysBitmask,
            )
        }
        val pi = buildFirePendingIntent(appContext, reminder.id)
        scheduleExactOrFallback(appContext, triggerAtMillis, pi)
    }

    fun scheduleSnooze(
        context: Context,
        reminderId: Long,
        checklistId: Long,
        itemId: Long?,
        onlyIfUnchecked: Boolean,
        delayMinutes: Int = 15,
    ) {
        val appContext = context.applicationContext
        val triggerAtMillis = System.currentTimeMillis() + delayMinutes * 60_000L
        val pi = buildSnoozePendingIntent(
            context = appContext,
            reminderId = reminderId,
            checklistId = checklistId,
            itemId = itemId,
            onlyIfUnchecked = onlyIfUnchecked,
        )
        scheduleExactOrFallback(appContext, triggerAtMillis, pi)
    }

    fun cancelReminder(context: Context, reminderId: Long) {
        if (reminderId <= 0L) return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildFirePendingIntent(appContext, reminderId))
        cancelSnooze(appContext, reminderId)
    }

    fun cancelSnooze(context: Context, reminderId: Long) {
        if (reminderId <= 0L) return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val snoozeIntent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE_SNOOZE
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminderId)
            data = Uri.parse("totalcheck://reminder/snooze/$reminderId")
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            REQ_SNOOZE_BASE + (reminderId.toInt() and 0xFFFF),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    fun cancelReminders(context: Context, reminders: List<ReminderEntity>) {
        reminders.forEach { cancelReminder(context, it.id) }
    }

    private fun buildFirePendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE_REMINDER
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminderId)
            data = Uri.parse("totalcheck://reminder/fire/$reminderId")
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_FIRE_BASE + (reminderId.toInt() and 0xFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSnoozePendingIntent(
        context: Context,
        reminderId: Long,
        checklistId: Long,
        itemId: Long?,
        onlyIfUnchecked: Boolean,
    ): PendingIntent {
        val stableKey = if (reminderId > 0L) reminderId else (checklistId * 31L + (itemId ?: 0L)).coerceAtLeast(1L)
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE_SNOOZE
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderBroadcastReceiver.EXTRA_CHECKLIST_ID, checklistId)
            if (itemId != null) {
                putExtra(ReminderBroadcastReceiver.EXTRA_ITEM_ID, itemId)
            }
            putExtra(ReminderBroadcastReceiver.EXTRA_ONLY_IF_UNCHECKED, onlyIfUnchecked)
            data = Uri.parse("totalcheck://reminder/snooze/$stableKey")
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_SNOOZE_BASE + (stableKey.toInt() and 0xFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleExactOrFallback(
        context: Context,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            } catch (_: SecurityException) {
                // Smooth fallback to inexact Doze-safe alarm when exact alarm permission is revoked.
            }
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }
}
