package com.diaroutine.domain

import com.diaroutine.core.time.through
import com.diaroutine.domain.model.DoseLog
import com.diaroutine.domain.model.DoseStatus
import com.diaroutine.domain.model.Medication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.math.max
import kotlin.math.min

data class DayAdherence(val date: LocalDate, val scheduled: Int, val taken: Int) {
    /** Null on a day with nothing scheduled. */
    val ratio: Float? get() = if (scheduled == 0) null else taken.toFloat() / scheduled
    val isPerfect: Boolean get() = scheduled > 0 && taken >= scheduled
}

data class Streaks(val current: Int, val longest: Int)

data class MedicationAdherence(val medication: Medication, val scheduled: Int, val taken: Int) {
    val ratio: Float? get() = if (scheduled == 0) null else taken.toFloat() / scheduled
}

/**
 * How closely scheduled medication was taken. Doses are counted per medication per
 * day rather than matched to their exact slot, so editing a schedule's times
 * doesn't rewrite past adherence.
 */
object Adherence {
    /**
     * One day's doses. With a [cutoff], only doses scheduled at or before it count
     * — today's figure shouldn't fall because the evening dose hasn't come yet.
     */
    fun day(date: LocalDate, medications: List<Medication>, logs: List<DoseLog>, cutoff: LocalTime? = null): DayAdherence {
        val takenByMed = logs.asSequence()
            .filter { it.date == date && it.scheduledTime != null && it.status == DoseStatus.Taken }
            .groupingBy { it.medicationId }
            .eachCount()
        var scheduled = 0
        var taken = 0
        for (med in medications) {
            if (!med.isScheduled || !med.isActiveOn(date)) continue
            val due = if (cutoff == null) med.times.distinct().size else med.times.distinct().count { it <= cutoff }
            if (due == 0) continue
            scheduled += due
            taken += min(takenByMed[med.id] ?: 0, due)
        }
        return DayAdherence(date, scheduled, taken)
    }

    /** Each day from [from] through [to]; today counts doses due so far, later days are left out. */
    fun days(from: LocalDate, to: LocalDate, medications: List<Medication>, logs: List<DoseLog>, now: LocalDateTime): List<DayAdherence> =
        from.through(minOf(to, now.date)).map { date ->
            day(date, medications, logs, cutoff = if (date == now.date) now.time else null)
        }

    /** Whole-day figures, for streaks: today only counts once every dose is in. */
    fun fullDays(from: LocalDate, to: LocalDate, medications: List<Medication>, logs: List<DoseLog>): List<DayAdherence> =
        from.through(to).map { day(it, medications, logs) }

    fun overall(days: List<DayAdherence>): Float? {
        val scheduled = days.sumOf { it.scheduled }
        return if (scheduled == 0) null else days.sumOf { it.taken }.toFloat() / scheduled
    }

    /**
     * Runs of days with every scheduled dose taken. Days with nothing scheduled
     * neither extend nor break a run, and an unfinished [today] doesn't break it.
     * [days] must be in date order and end no later than [today].
     */
    fun streaks(days: List<DayAdherence>, today: LocalDate): Streaks {
        var run = 0
        var longest = 0
        for (day in days) {
            when {
                day.scheduled == 0 -> Unit
                day.isPerfect -> {
                    run++
                    longest = max(longest, run)
                }
                day.date == today -> Unit
                else -> run = 0
            }
        }
        return Streaks(current = run, longest = longest)
    }

    fun byMedication(
        from: LocalDate,
        to: LocalDate,
        medications: List<Medication>,
        logs: List<DoseLog>,
        now: LocalDateTime,
    ): List<MedicationAdherence> = medications.filter { it.isScheduled }.mapNotNull { med ->
        val perDay = days(from, to, listOf(med), logs, now)
        val scheduled = perDay.sumOf { it.scheduled }
        if (scheduled == 0) null else MedicationAdherence(med, scheduled, perDay.sumOf { it.taken })
    }
}
