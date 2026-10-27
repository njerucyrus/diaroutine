package com.diaroutine.app

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.diaroutine.platform.PlatformServices
import com.diaroutine.platform.ReminderAccess

class AndroidPlatformServices(private val context: Context) : PlatformServices {
    private val prefs = context.getSharedPreferences("platform", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Set by the visible activity, which is the only thing that can show a permission prompt. */
    var permissionRequester: (() -> Unit)? = null

    override val uses24HourClock: Boolean
        get() = DateFormat.is24HourFormat(context)

    override val appVersion: String by lazy {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName.orEmpty()
    }

    override fun shareText(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openUrl(url: String) {
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun composeEmail(address: String, subject: String) {
        start(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                .putExtra(Intent.EXTRA_SUBJECT, subject),
        )
    }

    /** Starts another app's activity; quietly does nothing when no app can handle it. */
    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    override fun reminderAccess(): ReminderAccess = ReminderAccess(
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        preciseTiming = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms(),
        canRequestPreciseTiming = Build.VERSION.SDK_INT >= 31,
    )

    /**
     * Shows the system prompt the first time; after that Android may not show it
     * again, so the app's notification settings open instead.
     */
    override fun requestNotificationAccess() {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val requester = permissionRequester
        if (Build.VERSION.SDK_INT >= 33 && requester != null && !prefs.getBoolean(KEY_ASKED, false)) {
            prefs.edit { putBoolean(KEY_ASKED, true) }
            requester()
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun openPreciseTimingSettings() {
        if (Build.VERSION.SDK_INT < 31) return
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val KEY_ASKED = "notification_permission_asked"
    }
}
