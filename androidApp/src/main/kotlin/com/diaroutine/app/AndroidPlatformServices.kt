package com.diaroutine.app

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.diaroutine.platform.LockAvailability
import com.diaroutine.platform.PlatformServices
import com.diaroutine.platform.ReminderAccess

class AndroidPlatformServices(private val context: Context) : PlatformServices {
    private val prefs = context.getSharedPreferences("platform", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Set by the visible activity, which is the only thing that can show a permission prompt. */
    var permissionRequester: (() -> Unit)? = null

    /** Set by the visible activity, which hosts the system's biometric prompt. */
    var authenticator: (((Boolean) -> Unit) -> Unit)? = null

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

    override fun copyText(text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText("DiaRoutine", text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
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

    override fun lockAvailability(): LockAvailability =
        when (BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockAvailability.NoScreenLock
            else -> LockAvailability.Unsupported
        }

    override fun authenticate(onResult: (Boolean) -> Unit) {
        val prompt = authenticator ?: return onResult(false)
        prompt(onResult)
    }

    override fun openSecuritySettings() {
        start(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    companion object {
        private const val KEY_ASKED = "notification_permission_asked"

        /** Fingerprint or face, with the phone's PIN, pattern or password as the fallback. */
        const val LOCK_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
