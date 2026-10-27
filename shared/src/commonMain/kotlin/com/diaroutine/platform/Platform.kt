package com.diaroutine.platform

import com.diaroutine.domain.Reminder

/** Schedules the planned reminders as local notifications. */
interface ReminderScheduler {
    /** Makes [reminders] the complete set: anything scheduled before and not in the list is cancelled. */
    suspend fun replaceAll(reminders: List<Reminder>)
}

data class ReminderAccess(
    val notificationsAllowed: Boolean,
    /** Whether reminders fire at the minute rather than within a few minutes of it. */
    val preciseTiming: Boolean,
    /** Whether the person can grant precise timing from settings. */
    val canRequestPreciseTiming: Boolean,
)

/** What the shared app asks of the operating system. */
interface PlatformServices {
    val uses24HourClock: Boolean
    val appVersion: String

    fun shareText(subject: String, text: String)

    /** Opens a web page in the person's browser; the app itself never goes online. */
    fun openUrl(url: String)

    /** Starts an email in the person's mail app. */
    fun composeEmail(address: String, subject: String)

    fun reminderAccess(): ReminderAccess

    /** Asks for notification permission, or opens the app's notification settings once asking is no longer possible. */
    fun requestNotificationAccess()

    fun openPreciseTimingSettings()
}
