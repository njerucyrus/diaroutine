package com.diaroutine.domain

import com.diaroutine.core.time.through
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.Profile
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

data class DailyGlucose(val date: LocalDate, val averageMgDl: Int?)

data class GlucoseSummary(
    val readings: Int,
    val averageMgDl: Int?,
    /** Share of readings inside the target range; null without readings. */
    val inRange: Float?,
    val lows: Int,
    val highs: Int,
    val daily: List<DailyGlucose>,
    val checksPlanned: Int,
    val checksDone: Int,
)

object GlucoseStats {
    fun summarize(
        readings: List<GlucoseReading>,
        from: LocalDate,
        to: LocalDate,
        profile: Profile,
        now: LocalDateTime,
        zone: TimeZone,
    ): GlucoseSummary {
        val byDate = readings.groupBy { it.at.toLocalDateTime(zone).date }
        val days = from.through(minOf(to, now.date))
        val inWindow = days.flatMap { byDate[it].orEmpty() }
        val levels = inWindow.map { Glucose.level(it.mgDl, profile) }

        var planned = 0
        var done = 0
        for (date in days) {
            val match = Timeline.matchReadings(profile.glucoseChecks, byDate[date].orEmpty(), zone)
            for ((plan, reading) in match.matched) {
                val due = date < now.date || plan.time <= now.time
                if (!due) continue
                planned++
                if (reading != null) done++
            }
        }

        return GlucoseSummary(
            readings = inWindow.size,
            averageMgDl = inWindow.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.mgDl }.toDouble().div(list.size).roundToInt() },
            inRange = levels.takeIf { it.isNotEmpty() }?.let { list -> list.count { it == GlucoseLevel.InRange }.toFloat() / list.size },
            lows = levels.count { it == GlucoseLevel.Low },
            highs = levels.count { it == GlucoseLevel.High },
            daily = days.map { date ->
                val list = byDate[date].orEmpty()
                DailyGlucose(date, list.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.mgDl }.toDouble().div(l.size).roundToInt() })
            },
            checksPlanned = planned,
            checksDone = done,
        )
    }
}
