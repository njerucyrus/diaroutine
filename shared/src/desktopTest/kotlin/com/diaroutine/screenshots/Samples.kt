package com.diaroutine.screenshots

import com.diaroutine.core.time.at
import com.diaroutine.core.time.instantIn
import com.diaroutine.core.time.plusDays
import com.diaroutine.core.time.through
import com.diaroutine.domain.ExampleRoutine
import com.diaroutine.domain.GlucoseStats
import com.diaroutine.domain.RoutineSnapshot
import com.diaroutine.domain.Timeline
import com.diaroutine.domain.Report
import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.Caregiver
import com.diaroutine.domain.model.DailyHabit
import com.diaroutine.domain.model.DoseLog
import com.diaroutine.domain.model.DoseStatus
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.MealContext
import com.diaroutine.domain.model.Profile
import com.diaroutine.features.insights.InsightsUiState
import com.diaroutine.features.meds.MedicationItem
import com.diaroutine.features.meds.MedsUiState
import com.diaroutine.features.profile.ProfileUiState
import com.diaroutine.features.today.TodayUiState
import com.diaroutine.features.track.TrackUiState
import com.diaroutine.platform.LockAvailability
import com.diaroutine.platform.ReminderAccess
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** Two weeks of a realistic routine, ending on a Tuesday afternoon, in every screen's state. */
object Samples {
    val zone = TimeZone.UTC
    val today = LocalDate(2026, 10, 27)
    val now = today.at(LocalTime(13, 10))

    val profile = Profile(
        name = "Lina Haddad",
        onboarded = true,
        caregiver = Caregiver(enabled = true, name = "Sam", contact = "+1 555 0100"),
    )
    val meds = ExampleRoutine.medications(today.plusDays(-20)).mapIndexed { i, m -> m.copy(id = i + 1L) }
    val snapshot: RoutineSnapshot = buildSnapshot()

    private fun buildSnapshot(): RoutineSnapshot {
        val logs = mutableListOf<DoseLog>()
        val readings = mutableListOf<GlucoseReading>()
        val habits = mutableListOf<DailyHabit>()
        var id = 1L
        for ((i, date) in today.plusDays(-13).through(today).withIndex()) {
            for (med in meds.filter { it.isScheduled }) {
                for (time in med.times) {
                    if (date == today && time > now.time) continue
                    val missed = (i * 7 + time.hour + med.id.toInt()) % 9 == 0
                    if (!missed) logs += DoseLog(id++, med.id, date, time, DoseStatus.Taken, date.at(time).instantIn(zone))
                }
            }
            readings += GlucoseReading(id++, date.at(LocalTime(7, 10 + i % 20)).instantIn(zone), 96 + (i * 13) % 50, MealContext.Fasting)
            if (date != today) {
                readings += GlucoseReading(id++, date.at(LocalTime(21, 40)).instantIn(zone), 128 + (i * 29) % 80, MealContext.Bedtime)
            }
            habits += DailyHabit(
                date,
                waterGlasses = if (date == today) 5 else 4 + (i * 3) % 6,
                steps = if (date == today) 3240 else 2800 + (i * 1370) % 5200,
                activeMinutes = if (date == today) 12 else 15 + (i * 11) % 30,
            )
        }
        readings += GlucoseReading(id++, today.at(LocalTime(12, 40)).instantIn(zone), 196, MealContext.AfterMeal, "Pasta lunch")
        val appointments = listOf(
            Appointment(1, "Diabetes check-up", "Dr. Rivera · City Clinic", today.plusDays(3).at(LocalTime(10, 30)), "Bring glucose log and medication list", 24 * 60),
            Appointment(2, "Eye exam", "Brightview Optometry", today.plusDays(-9).at(LocalTime(15, 0)), "", 60),
        )
        return RoutineSnapshot(profile, meds, logs, readings, habits, appointments)
    }

    val todayState = TodayUiState(
        isLoading = false,
        profile = profile,
        now = now,
        selectedDate = today,
        days = today.plusDays(-6).through(today.plusDays(7)),
        plan = Timeline.build(today, now, snapshot, zone),
        refills = meds.filter { it.needsRefill },
        latestReading = snapshot.readings.filter { it.at <= now.instantIn(zone) }.maxBy { it.at },
        habit = snapshot.habitOn(today)!!,
        hasMedications = true,
    )

    val medsState = MedsUiState(
        isLoading = false,
        now = now,
        medications = meds.map { med ->
            val taken = snapshot.doseLogs.filter { it.medicationId == med.id && it.date == today }
            MedicationItem(med, if (med.asNeeded) 0 else med.times.size, taken.size, taken.maxOfOrNull { it.loggedAt })
        },
        upcoming = snapshot.appointments.filter { it.at >= now },
        past = snapshot.appointments.filter { it.at < now },
    )

    val trackState = TrackUiState(
        isLoading = false,
        profile = profile,
        now = now,
        recent = snapshot.readings.sortedByDescending { it.at }.take(6),
        week = GlucoseStats.summarize(snapshot.readings, today.plusDays(-6), today, profile, now, zone),
        habit = snapshot.habitOn(today)!!,
    )

    val insightsState = InsightsUiState(
        isLoading = false,
        profile = profile,
        today = today,
        figures = Report.figures(snapshot, now, zone),
        hasScheduledMedication = true,
    )

    val profileState = ProfileUiState(
        isLoading = false,
        profile = profile,
        access = ReminderAccess(notificationsAllowed = true, preciseTiming = false, canRequestPreciseTiming = true),
        lockAvailability = LockAvailability.Available,
        appVersion = "1.0.0",
    )
}
