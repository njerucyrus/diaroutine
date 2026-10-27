package com.diaroutine.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.diaroutine.domain.Reminder
import com.diaroutine.platform.ReminderScheduler

/**
 * Sets one alarm per reminder. With exact-alarm access they fire on the minute;
 * without it, within a ten-minute window, which needs no special permission.
 */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    override suspend fun replaceAll(reminders: List<Reminder>) {
        val ids = reminders.map { it.id }.toSet()
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull(String::toIntOrNull)
        previous.filterNot { it in ids }.forEach { id ->
            PendingIntent.getBroadcast(context, id, ReminderReceiver.showIntent(context), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { alarms.cancel(it); it.cancel() }
        }

        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        reminders.forEach { reminder ->
            val pending = PendingIntent.getBroadcast(
                context,
                reminder.id,
                ReminderReceiver.showIntent(context, reminder),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = reminder.at.toEpochMilliseconds()
            try {
                if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, pending)
            } catch (_: SecurityException) {
                // Exact-alarm access was withdrawn between the check and the call.
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, pending)
            }
        }
        prefs.edit { putStringSet(KEY_IDS, ids.map(Int::toString).toSet()) }
    }

    private companion object {
        const val KEY_IDS = "scheduled_ids"
        const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
