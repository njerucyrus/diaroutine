package com.diaroutine.domain

import com.diaroutine.core.format.Fmt
import com.diaroutine.core.time.at
import com.diaroutine.core.time.epochDay
import com.diaroutine.core.time.instantIn
import com.diaroutine.core.time.minuteOfDay
import com.diaroutine.core.time.plusDays
import com.diaroutine.core.time.through
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class ReminderKind { Dose, Glucose, Walk, Visit, Refill }

/** The scheduled dose a reminder is about, so "Mark as taken" can answer it. */
data class DoseRef(val medicationId: Long, val epochDay: Long, val minuteOfDay: Int)

data class Reminder(
    /** Stable across re-plans, so the platform can replace or cancel the same alarm. */
    val id: Int,
    val at: Instant,
    val kind: ReminderKind,
    val title: String,
    val body: String,
    val dose: DoseRef? = null,
)

/**
 * Turns the routine into the reminders due within the next [HORIZON]. The platform
 * schedules them; re-planning on every change (and whenever a reminder fires)
 * keeps the window rolling forward.
 */
object ReminderPlanner {
    val HORIZON: Duration = 48.hours
    val REFILL_TIME = LocalTime(9, 0)

    fun plan(
        now: Instant,
        zone: TimeZone,
        snapshot: RoutineSnapshot,
        use24Hour: Boolean,
        horizon: Duration = HORIZON,
    ): List<Reminder> {
        val profile = snapshot.profile
        if (!profile.onboarded || !profile.remindersEnabled) return emptyList()
        val end = now + horizon
        fun inWindow(at: Instant) = at > now && at <= end

        val today = now.toLocalDateTime(zone).date
        val reminders = mutableListOf<Reminder>()

        for (date in today.through(end.toLocalDateTime(zone).date)) {
            for (med in snapshot.medications) {
                if (!med.isScheduled || !med.isActiveOn(date)) continue
                for (time in med.times.distinct()) {
                    val at = date.at(time).instantIn(zone)
                    if (!inWindow(at)) continue
                    val answered = snapshot.doseLogs.any { it.medicationId == med.id && it.date == date && it.scheduledTime == time }
                    if (answered) continue
                    reminders += Reminder(
                        id = stableId("dose", med.id, date.epochDay, time.minuteOfDay),
                        at = at,
                        kind = ReminderKind.Dose,
                        title = "Time for ${med.name}",
                        body = listOf(med.doseDescription, med.instructions.trim()).filter { it.isNotEmpty() }.joinToString(" · "),
                        dose = DoseRef(med.id, date.epochDay, time.minuteOfDay),
                    )
                }
            }

            val dayReadings = snapshot.readings.filter { it.at.toLocalDateTime(zone).date == date }
            for ((plan, reading) in Timeline.matchReadings(profile.glucoseChecks, dayReadings, zone).matched) {
                val at = date.at(plan.time).instantIn(zone)
                if (reading != null || !inWindow(at)) continue
                reminders += Reminder(
                    id = stableId("glucose", date.epochDay, plan.time.minuteOfDay),
                    at = at,
                    kind = ReminderKind.Glucose,
                    title = "Glucose check",
                    body = "${plan.context.label} reading planned for ${Fmt.time(plan.time, use24Hour)}",
                )
            }

            val walkTime = profile.walkTime
            if (walkTime != null && profile.walkGoalMinutes > 0) {
                val at = date.at(walkTime).instantIn(zone)
                val minutes = snapshot.habitOn(date)?.activeMinutes ?: 0
                if (inWindow(at) && minutes < profile.walkGoalMinutes) {
                    reminders += Reminder(
                        id = stableId("walk", date.epochDay),
                        at = at,
                        kind = ReminderKind.Walk,
                        title = "Time for your walk",
                        body = "Aim for ${profile.walkGoalMinutes} active minutes today",
                    )
                }
            }
        }

        for (appointment in snapshot.appointments) {
            if (appointment.remindBeforeMinutes <= 0) continue
            val at = appointment.at.instantIn(zone) - appointment.remindBeforeMinutes.minutes
            if (!inWindow(at)) continue
            val day = Fmt.relativeDate(appointment.at.date, at.toLocalDateTime(zone).date)
            reminders += Reminder(
                id = stableId("visit", appointment.id),
                at = at,
                kind = ReminderKind.Visit,
                title = appointment.title,
                body = listOf("$day at ${Fmt.time(appointment.at.time, use24Hour)}", appointment.withWhom.trim())
                    .filter { it.isNotEmpty() }.joinToString(" · "),
            )
        }

        val refills = snapshot.medications.filter { it.isActiveOn(today) && it.needsRefill }
        if (refills.isNotEmpty()) {
            val todayAt = today.at(REFILL_TIME).instantIn(zone)
            val at = if (todayAt > now) todayAt else today.plusDays(1).at(REFILL_TIME).instantIn(zone)
            if (inWindow(at)) {
                reminders += Reminder(
                    id = stableId("refill", at.toLocalDateTime(zone).date.epochDay),
                    at = at,
                    kind = ReminderKind.Refill,
                    title = if (refills.size == 1) "Refill ${refills.single().name} soon" else "${refills.size} medications need a refill",
                    body = refills.joinToString(" · ") { "${it.name}: ${it.form.amount(it.supplyRemaining ?: 0)} left" },
                )
            }
        }

        return reminders.sortedBy { it.at }
    }

    private fun stableId(vararg parts: Any): Int = parts.joinToString(":").hashCode()
}
