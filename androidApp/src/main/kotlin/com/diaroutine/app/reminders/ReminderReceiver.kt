package com.diaroutine.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.diaroutine.app.diaRoutine
import com.diaroutine.core.time.dateOfEpochDay
import com.diaroutine.core.time.timeOfMinute
import com.diaroutine.domain.DoseRef
import com.diaroutine.domain.Reminder
import com.diaroutine.domain.ReminderKind
import kotlinx.coroutines.launch

/** Shows a reminder when its alarm fires, and answers "Mark as taken" from the notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.diaRoutine.container
        when (intent.action) {
            ACTION_SHOW -> {
                Notifications.show(context, intent.toShown())
                // Each reminder that fires moves the scheduling window forward.
                runAsync(context) { container.rescheduleSoon().join() }
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
