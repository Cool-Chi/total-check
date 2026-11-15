package com.example.totalcheck.data.reminder

import android.content.Context
import com.example.totalcheck.R
import java.util.Locale

object ReminderFormatter {

    const val WEEKDAYS_MASK = 0b0011111 // Mon..Fri (Bit 0..4)
    const val WEEKENDS_MASK = 0b1100000 // Sat..Sun (Bit 5..6)
    const val DAILY_MASK = 0b1111111    // Mon..Sun (Bit 0..6)

    fun formatTime(hour: Int, minute: Int): String {
        return String.format(
            Locale.US,
            "%02d:%02d",
            hour.coerceIn(0, 23),
            minute.coerceIn(0, 59)
        )
    }

    fun formatRepeatSummary(context: Context, repeatDaysBitmask: Int): String {
        val masked = repeatDaysBitmask and DAILY_MASK
        return when (masked) {
            0 -> context.getString(R.string.reminder_repeat_once)
            DAILY_MASK -> context.getString(R.string.reminder_repeat_daily)
            WEEKDAYS_MASK -> context.getString(R.string.reminder_repeat_weekdays)
            WEEKENDS_MASK -> context.getString(R.string.reminder_repeat_weekends)
            else -> {
                val dayResIds = intArrayOf(
                    R.string.reminder_day_mon,
                    R.string.reminder_day_tue,
                    R.string.reminder_day_wed,
                    R.string.reminder_day_thu,
                    R.string.reminder_day_fri,
                    R.string.reminder_day_sat,
                    R.string.reminder_day_sun,
                )
                val selected = buildList {
                    for (bitIdx in 0..6) {
                        if ((masked and (1 shl bitIdx)) != 0) {
                            add(context.getString(dayResIds[bitIdx]))
                        }
                    }
                }
                context.getString(R.string.reminder_day_prefix_short, selected.joinToString("/"))
            }
        }
    }

    fun formatCompactBadge(
        context: Context,
        hour: Int,
        minute: Int,
        repeatDaysBitmask: Int,
    ): String {
        val timeText = formatTime(hour, minute)
        val masked = repeatDaysBitmask and DAILY_MASK
        return if (masked == 0) {
            timeText
        } else {
            "${formatRepeatSummary(context, masked)} $timeText"
        }
    }

    fun formatSavedSummary(
        context: Context,
        hour: Int,
        minute: Int,
        repeatDaysBitmask: Int,
    ): String {
        val timeText = formatTime(hour, minute)
        return "${formatRepeatSummary(context, repeatDaysBitmask)} $timeText"
    }
}
