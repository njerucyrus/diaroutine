package com.diaroutine.domain

import com.diaroutine.core.time.minuteOfDay
import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.DailyHabit
import com.diaroutine.domain.model.DoseLog
import com.diaroutine.domain.model.DoseStatus
import com.diaroutine.domain.model.GlucoseCheckPlan
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.Medication
import com.diaroutine.domain.model.Profile
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

enum class EntryState {
    Done,
    Skipped,
    Upcoming,
    DueNow,
    Overdue,
    /** Not done on a day that has ended. */
    Missed,
    /** An appointment whose time has gone by. */
    Passed;

    val needsAttention: Boolean get() = this == DueNow || this == Overdue
}

/** One row on the daily timeline. */
sealed interface TimelineEntry {
    val key: String
    val time: LocalTime
    val state: EntryState

    /** Planned entries make up the day's checklist; logs made outside the plan don't count toward it. */
    val isPlanned: Boolean

    data class Dose(
        override val key: String,
        override val time: LocalTime,
        override val state: EntryState,
        val medication: Medication,
        val log: DoseLog?,
    ) : TimelineEntry {
        override val isPlanned: Boolean get() = !medication.asNeeded
    }

    data class GlucoseCheck(
        override val key: String,
        override val time: LocalTime,
        override val state: EntryState,
        /** Null for a reading logged outside the planned checks. */
        val plan: GlucoseCheckPlan?,
        val reading: GlucoseReading?,
    ) : TimelineEntry {
        override val isPlanned: Boolean get() = plan != null
    }

    data class Walk(
        override val key: String,
        override val time: LocalTime,
        override val state: EntryState,
        val minutesDone: Int,
        val goalMinutes: Int,
    ) : TimelineEntry {
        override val isPlanned: Boolean get() = true
    }

    data class Visit(
        override val key: String,
        override val time: LocalTime,
        override val state: EntryState,
        val appointment: Appointment,
    ) : TimelineEntry {
        override val isPlanned: Boolean get() = false
    }
}

data class DaySummary(
    val planned: Int,
    val done: Int,
    val dueNow: Int,
    val overdue: Int,
    val missed: Int,
    val next: TimelineEntry?,
) {
    val progress: Float get() = if (planned == 0) 0f else done.toFloat() / planned
    val needsAttention: Int get() = dueNow + overdue
}

data class DayPlan(
    val date: LocalDate,
    val entries: List<TimelineEntry>,
    val summary: DaySummary,
)

/** Planned glucose checks paired with the readings that answer them. */
data class CheckMatch(
    val matched: List<Pair<GlucoseCheckPlan, GlucoseReading?>>,
    val unmatched: List<GlucoseReading>,
)

/**
 * Builds the personalized daily timeline: medication doses, glucose checks, the
 * planned walk and appointments, each with where it stands right now.
 */
object Timeline {
    /** An item becomes "due now" this long before its time... */
    const val DUE_LEAD_MINUTES = 30

    /** ...and "overdue" this long after. */
    const val DUE_GRACE_MINUTES = 60

    /** A reading counts toward a planned check when taken within this many minutes of it. */
    const val CHECK_WINDOW_MINUTES = 120

    fun build(
        date: LocalDate,
        now: LocalDateTime,
        profile: Profile,
        medications: List<Medication>,
        doseLogs: List<DoseLog>,
        readings: List<GlucoseReading>,
        habit: DailyHabit?,
        appointments: List<Appointment>,
        zone: TimeZone,
    ): DayPlan {
        val entries = buildList {
            addAll(doseEntries(date, now, medications, doseLogs, zone))
            addAll(glucoseEntries(date, now, profile, readings, zone))
            walkEntry(date, now, profile, habit)?.let(::add)
            addAll(visitEntries(date, now, appointments))
        }.sortedWith(compareBy<TimelineEntry>({ it.time }, { it.order }))
        return DayPlan(date, entries, summarize(entries))
    }

    fun build(date: LocalDate, now: LocalDateTime, snapshot: RoutineSnapshot, zone: TimeZone): DayPlan = build(
        date = date,
        now = now,
        profile = snapshot.profile,
        medications = snapshot.medications,
        doseLogs = snapshot.doseLogs,
        readings = snapshot.readings,
        habit = snapshot.habitOn(date),
        appointments = snapshot.appointments,
        zone = zone,
    )

    /** Where an item planned for [time] on [date] stands, when it hasn't been done. */
    fun scheduledState(date: LocalDate, time: LocalTime, now: LocalDateTime): EntryState {
        val today = now.date
        return when {
            date < today -> EntryState.Missed
            date > today -> EntryState.Upcoming
            else -> {
                val minutesUntil = time.minuteOfDay - now.time.minuteOfDay
                when {
                    minutesUntil > DUE_LEAD_MINUTES -> EntryState.Upcoming
                    minutesUntil >= -DUE_GRACE_MINUTES -> EntryState.DueNow
                    else -> EntryState.Overdue
                }
            }
        }
    }

    /**
     * Pairs each planned check with the closest unused reading within
     * [CHECK_WINDOW_MINUTES]. [readings] should all be from the same day.
     */
    fun matchReadings(plans: List<GlucoseCheckPlan>, readings: List<GlucoseReading>, zone: TimeZone): CheckMatch {
        val minutes = readings.map { it.at.toLocalDateTime(zone).time.minuteOfDay }
        val used = BooleanArray(readings.size)
        val matched = plans.sortedBy { it.time }.map { plan ->
            val target = plan.time.minuteOfDay
            var best = -1
            var bestDistance = Int.MAX_VALUE
            minutes.forEachIndexed { i, m ->
                val distance = abs(m - target)
                if (!used[i] && distance <= CHECK_WINDOW_MINUTES && distance < bestDistance) {
                    best = i
                    bestDistance = distance
                }
            }
            if (best >= 0) used[best] = true
            plan to readings.getOrNull(best)
        }
        val unmatched = readings.filterIndexed { i, _ -> !used[i] }
        return CheckMatch(matched, unmatched)
    }

    private fun doseEntries(
        date: LocalDate,
        now: LocalDateTime,
        medications: List<Medication>,
        doseLogs: List<DoseLog>,
        zone: TimeZone,
    ): List<TimelineEntry> = buildList {
        val logsToday = doseLogs.filter { it.date == date }
        for (med in medications) {
            if (!med.isActiveOn(date)) continue
            val logs = logsToday.filter { it.medicationId == med.id }
            if (med.asNeeded) {
                logs.filter { it.status == DoseStatus.Taken }.forEach { log ->
                    add(
                        TimelineEntry.Dose(
                            key = "prn-${log.id}",
                            time = log.loggedAt.toLocalDateTime(zone).time,
                            state = EntryState.Done,
                            medication = med,
                            log = log,
                        ),
                    )
                }
            } else {
                med.times.distinct().forEach { time ->
                    val log = logs.firstOrNull { it.scheduledTime == time }
                    val state = when (log?.status) {
                        DoseStatus.Taken -> EntryState.Done
                        DoseStatus.Skipped -> EntryState.Skipped
                        null -> scheduledState(date, time, now)
                    }
                    add(TimelineEntry.Dose("dose-${med.id}-${time.minuteOfDay}", time, state, med, log))
                }
            }
        }
    }

    private fun glucoseEntries(
        date: LocalDate,
        now: LocalDateTime,
        profile: Profile,
        readings: List<GlucoseReading>,
        zone: TimeZone,
    ): List<TimelineEntry> {
        val dayReadings = readings.filter { it.at.toLocalDateTime(zone).date == date }.sortedBy { it.at }
        val match = matchReadings(profile.glucoseChecks, dayReadings, zone)
        val planned = match.matched.mapIndexed { i, (plan, reading) ->
            TimelineEntry.GlucoseCheck(
                key = "check-$i-${plan.time.minuteOfDay}",
                time = plan.time,
                state = if (reading != null) EntryState.Done else scheduledState(date, plan.time, now),
                plan = plan,
                reading = reading,
            )
        }
        val extra = match.unmatched.map { reading ->
            TimelineEntry.GlucoseCheck(
                key = "reading-${reading.id}",
                time = reading.at.toLocalDateTime(zone).time,
                state = EntryState.Done,
                plan = null,
                reading = reading,
            )
        }
        return planned + extra
    }

    private fun walkEntry(date: LocalDate, now: LocalDateTime, profile: Profile, habit: DailyHabit?): TimelineEntry? {
        val time = profile.walkTime ?: return null
        if (profile.walkGoalMinutes <= 0) return null
        val minutes = habit?.activeMinutes ?: 0
        val state = if (minutes >= profile.walkGoalMinutes) EntryState.Done else scheduledState(date, time, now)
        return TimelineEntry.Walk("walk", time, state, minutes, profile.walkGoalMinutes)
    }

    private fun visitEntries(date: LocalDate, now: LocalDateTime, appointments: List<Appointment>): List<TimelineEntry> =
        appointments.filter { it.at.date == date }.map { appointment ->
            val state = when {
                appointment.at <= now -> EntryState.Passed
                date == now.date && appointment.at.time.minuteOfDay - now.time.minuteOfDay <= DUE_GRACE_MINUTES -> EntryState.DueNow
                else -> EntryState.Upcoming
            }
            TimelineEntry.Visit("visit-${appointment.id}", appointment.at.time, state, appointment)
        }

    private fun summarize(entries: List<TimelineEntry>): DaySummary {
        val planned = entries.filter { it.isPlanned }
        return DaySummary(
            planned = planned.size,
            done = planned.count { it.state == EntryState.Done },
            dueNow = planned.count { it.state == EntryState.DueNow },
            overdue = planned.count { it.state == EntryState.Overdue },
            missed = planned.count { it.state == EntryState.Missed },
            next = entries.firstOrNull { it.state == EntryState.DueNow || it.state == EntryState.Upcoming },
        )
    }

    private val TimelineEntry.order: Int
        get() = when (this) {
            is TimelineEntry.Dose -> 0
            is TimelineEntry.GlucoseCheck -> 1
            is TimelineEntry.Walk -> 2
            is TimelineEntry.Visit -> 3
        }
}
