package com.diaroutine.domain

import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.DailyHabit
import com.diaroutine.domain.model.DoseLog
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.Medication
import com.diaroutine.domain.model.Profile
import kotlinx.datetime.LocalDate

/** Everything the routine logic reads, for one window of dates. */
data class RoutineSnapshot(
    val profile: Profile,
    val medications: List<Medication>,
    val doseLogs: List<DoseLog>,
    val readings: List<GlucoseReading>,
    val habits: List<DailyHabit>,
    val appointments: List<Appointment>,
) {
    fun habitOn(date: LocalDate): DailyHabit? = habits.firstOrNull { it.date == date }
}
