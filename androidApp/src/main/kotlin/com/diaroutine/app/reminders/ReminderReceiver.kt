package com.diaroutine.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.diaroutine.app.diaRoutine
import com.diaroutine.core.time.dateOfEpochDay
import com.diaroutine.core.time.minuteOfDay
import com.diaroutine.core.time.timeOfMinute
import com.diaroutine.domain.DoseRef
import com.diaroutine.domain.Reminder
import com.diaroutine.domain.ReminderKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Shows a reminder when its alarm fires, and answers "Mark as taken" and "Snooze" from the notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.diaRoutine.container
        when (intent.action) {
            ACTION_SHOW -> {
                val shown = intent.toShown()
                runAsync(context) {
                    // A snoozed or follow-up reminder may fire after the dose was taken in the app.
                    if (shown.dose?.let { isAnswered(context, it) } != true) Notifications.show(context, shown)
                    // Each reminder that fires moves the scheduling window forward.
                    container.rescheduleSoon().join()
                }
            }
            ACTION_SNOOZE -> {
                val shown = intent.toShown()
                NotificationManagerCompat.from(context).cancel(shown.id)
                val again = Reminder(shown.id, Clock.System.now() + SNOOZE, shown.kind, shown.title, shown.body, shown.dose)
                // Its own request code, so the planned alarm for this reminder isn't replaced.
                AndroidReminderScheduler.setAlarm(context, shown.id xor SNOOZE_REQUEST, again)
            }
            ACTION_TAKE -> {
                val notificationId = intent.getIntExtra(EXTRA_ID, 0)
                val dose = intent.toDose() ?: return
                runAsync(context) {
                    container.medications.markTaken(dose.medicationId, dateOfEpochDay(dose.epochDay), timeOfMinute(dose.minuteOfDay))
                    NotificationManagerCompat.from(context).cancel(notificationId)
                    container.rescheduleSoon().join()
                }
            }
        }
    }

    private suspend fun isAnswered(context: Context, dose: DoseRef): Boolean {
        val date = dateOfEpochDay(dose.epochDay)
        return context.diaRoutine.container.medications.doseLogs(date, date).first()
            .any { it.medicationId == dose.medicationId && it.scheduledTime?.minuteOfDay == dose.minuteOfDay }
    }

    private fun runAsync(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        context.diaRoutine.container.scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    /** What a fired alarm carries to its notification. */
    data class Shown(val id: Int, val kind: ReminderKind, val title: String, val body: String, val dose: DoseRef?)

    companion object {
        const val ACTION_SHOW = "com.diaroutine.action.SHOW_REMINDER"
        const val ACTION_TAKE = "com.diaroutine.action.MARK_TAKEN"
        const val ACTION_SNOOZE = "com.diaroutine.action.SNOOZE"
        private val SNOOZE = 10.minutes
        private const val SNOOZE_REQUEST = 0x5A00_0000
        private const val EXTRA_ID = "id"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_MEDICATION = "medication"
        private const val EXTRA_DAY = "day"
        private const val EXTRA_MINUTE = "minute"

        /** The alarm's intent; without a reminder, the bare form used to find and cancel one. */
        fun showIntent(context: Context, reminder: Reminder? = null): Intent =
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SHOW).apply {
                if (reminder == null) return@apply
                putExtra(EXTRA_ID, reminder.id)
                putExtra(EXTRA_KIND, reminder.kind.name)
                putExtra(EXTRA_TITLE, reminder.title)
                putExtra(EXTRA_BODY, reminder.body)
                reminder.dose?.let { putDose(it) }
            }

        /** Snoozes [shown]: the same reminder again in ten minutes. */
        fun snoozeIntent(context: Context, shown: Shown): Intent =
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_ID, shown.id)
                .putExtra(EXTRA_KIND, shown.kind.name)
                .putExtra(EXTRA_TITLE, shown.title)
                .putExtra(EXTRA_BODY, shown.body)
                .apply { shown.dose?.let { putDose(it) } }

        fun takeIntent(context: Context, notificationId: Int, dose: DoseRef): Intent =
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_TAKE)
                .putExtra(EXTRA_ID, notificationId)
                .apply { putDose(dose) }

        private fun Intent.putDose(dose: DoseRef) {
            putExtra(EXTRA_MEDICATION, dose.medicationId)
            putExtra(EXTRA_DAY, dose.epochDay)
            putExtra(EXTRA_MINUTE, dose.minuteOfDay)
        }

        private fun Intent.toDose(): DoseRef? {
            if (!hasExtra(EXTRA_MEDICATION)) return null
            return DoseRef(getLongExtra(EXTRA_MEDICATION, 0), getLongExtra(EXTRA_DAY, 0), getIntExtra(EXTRA_MINUTE, 0))
        }

        private fun Intent.toShown(): Shown = Shown(
            id = getIntExtra(EXTRA_ID, 0),
            kind = ReminderKind.entries.firstOrNull { it.name == getStringExtra(EXTRA_KIND) } ?: ReminderKind.Dose,
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            body = getStringExtra(EXTRA_BODY).orEmpty(),
            dose = toDose(),
        )
    }
}
