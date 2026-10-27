package com.diaroutine.domain

import com.diaroutine.core.format.Fmt
import com.diaroutine.core.time.plusDays
import com.diaroutine.core.time.through
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/** How far back a summary looks, and how many days each bar of its charts covers. */
enum class ReportPeriod(val days: Int, val bucketDays: Int, val label: String) {
    Week(7, 1, "7 days"),
    Month(30, 3, "30 days"),
    Quarter(90, 7, "90 days"),
}

/** Everything a summary shows, for the screen and for sharing. */
data class ReportFigures(
    val period: ReportPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DayAdherence>,
    val adherence: Float?,
    /** Adherence over the same length of time just before [from]. */
    val previousAdherence: Float?,
    val streaks: Streaks,
    val byMedication: List<MedicationAdherence>,
    val glucose: GlucoseSummary,
    val waterByDay: List<Pair<LocalDate, Int>>,
    val stepsByDay: List<Pair<LocalDate, Int>>,
    val minutesByDay: List<Pair<LocalDate, Int>>,
)

/**
 * Splits days (oldest first) into runs of [size], counted back from the newest so the
 * most recent run is always whole and only the oldest may be short.
 */
fun <T> List<T>.bucketedFromEnd(size: Int): List<List<T>> =
    asReversed().chunked(size).map { it.asReversed() }.asReversed()

object Report {
    /** Days of history kept in view for streaks. */
    const val HISTORY_DAYS = 90

    /** The days a snapshot must cover for [figures] over [period]. */
    fun snapshotDays(period: ReportPeriod): Int = maxOf(HISTORY_DAYS, period.days * 2)

    /** [snapshot] should cover [snapshotDays] up to today. */
    fun figures(
        snapshot: RoutineSnapshot,
        now: LocalDateTime,
        zone: TimeZone,
        period: ReportPeriod = ReportPeriod.Week,
    ): ReportFigures {
        val today = now.date
        val from = today.plusDays(-(period.days - 1))
        val meds = snapshot.medications
        val logs = snapshot.doseLogs
        val days = Adherence.days(from, today, meds, logs, now)
        val previous = Adherence.days(from.plusDays(-period.days), from.plusDays(-1), meds, logs, now)
        val history = Adherence.fullDays(today.plusDays(-(HISTORY_DAYS - 1)), today, meds, logs)
        val range = from.through(today)
        return ReportFigures(
            period = period,
            from = from,
            to = today,
            days = days,
            adherence = Adherence.overall(days),
            previousAdherence = Adherence.overall(previous),
            streaks = Adherence.streaks(history, today),
            byMedication = Adherence.byMedication(from, today, meds, logs, now),
            glucose = GlucoseStats.summarize(snapshot.readings, from, today, snapshot.profile, now, zone),
            waterByDay = range.map { it to (snapshot.habitOn(it)?.waterGlasses ?: 0) },
            stepsByDay = range.map { it to (snapshot.habitOn(it)?.steps ?: 0) },
            minutesByDay = range.map { it to (snapshot.habitOn(it)?.activeMinutes ?: 0) },
        )
    }

    /** Plain text a person can send to a caregiver or bring to an appointment. */
    fun text(snapshot: RoutineSnapshot, figures: ReportFigures): String {
        val profile = snapshot.profile
        val unit = profile.glucoseUnit
        val glucose = figures.glucose
        val span = if (figures.period == ReportPeriod.Week) "this week" else "in the last ${figures.period.label}"
        return buildString {
            append(if (figures.period == ReportPeriod.Week) "DiaRoutine weekly summary" else "DiaRoutine ${figures.period.days}-day summary")
            if (profile.name.isNotBlank()) append(" — ${profile.name.trim()}")
            appendLine()
            appendLine(Fmt.dateRange(figures.from, figures.to))
            appendLine()

            val scheduled = figures.days.sumOf { it.scheduled }
            val taken = figures.days.sumOf { it.taken }
            if (figures.adherence != null) {
                appendLine("Medication: ${Fmt.percent(figures.adherence)} of doses taken ($taken of $scheduled)")
                appendLine("Streak: ${days(figures.streaks.current)} with every dose taken (best: ${days(figures.streaks.longest)})")
            } else {
                appendLine("Medication: no scheduled doses $span")
            }
            appendLine()

            if (glucose.averageMgDl != null && glucose.inRange != null) {
                appendLine("Blood glucose: ${glucose.readings} readings · average ${Glucose.formatWithUnit(glucose.averageMgDl, unit)}")
                appendLine("In target range (${Glucose.targetLabel(profile)}): ${Fmt.percent(glucose.inRange)} · ${glucose.lows} low · ${glucose.highs} high")
            } else {
                appendLine("Blood glucose: no readings logged $span")
            }
            if (glucose.checksPlanned > 0) appendLine("Planned checks done: ${glucose.checksDone} of ${glucose.checksPlanned}")
            appendLine()

            val waterDays = figures.waterByDay.filter { it.second > 0 }
            if (waterDays.isNotEmpty()) {
                appendLine("Water: ${Fmt.oneDecimal(waterDays.sumOf { it.second }.toDouble() / waterDays.size)} glasses a day on ${days(waterDays.size)} logged (goal ${profile.waterGoalGlasses})")
            }
            val stepDays = figures.stepsByDay.filter { it.second > 0 }
            if (stepDays.isNotEmpty()) {
                appendLine("Steps: ${Fmt.grouped(stepDays.sumOf { it.second } / stepDays.size)} a day on ${days(stepDays.size)} logged (goal ${Fmt.grouped(profile.stepGoal)})")
            }
            appendLine()
            append("Shared from DiaRoutine. A personal log, not medical advice.")
        }
    }

    private fun days(n: Int) = if (n == 1) "1 day" else "$n days"
}
