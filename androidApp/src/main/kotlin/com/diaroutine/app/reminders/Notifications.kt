package com.diaroutine.app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.diaroutine.app.MainActivity
import com.diaroutine.app.R
import com.diaroutine.app.premium.AndroidPremiumStore
import com.diaroutine.domain.ReminderKind

object Notifications {
    private const val CHANNEL_DOSES = "doses"
    private const val CHANNEL_ROUTINE = "routine"

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_DOSES, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_doses))
                    .setDescription(context.getString(R.string.channel_doses_description))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ROUTINE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_routine))
                    .setDescription(context.getString(R.string.channel_routine_description))
                    .build(),
            ),
        )
    }

    @SuppressLint("MissingPermission") // Checked first, below.
    fun show(context: Context, reminder: ReminderReceiver.Shown) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        val isDose = reminder.kind == ReminderKind.Dose
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, if (isDose) CHANNEL_DOSES else CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(reminder.title)
            .setContentText(reminder.body.ifEmpty { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setCategory(if (reminder.kind == ReminderKind.Visit) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (isDose) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)

        reminder.dose?.let { dose ->
            val take = PendingIntent.getBroadcast(
                context,
                reminder.id,
                ReminderReceiver.takeIntent(context, reminder.id, dose),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, context.getString(R.string.action_mark_taken), take)
            if (AndroidPremiumStore.isPremium(context)) {
                val snooze = PendingIntent.getBroadcast(
                    context,
                    reminder.id,
                    ReminderReceiver.snoozeIntent(context, reminder),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, context.getString(R.string.action_snooze), snooze)
            }
        }
        NotificationManagerCompat.from(context).notify(reminder.id, builder.build())
    }
}
