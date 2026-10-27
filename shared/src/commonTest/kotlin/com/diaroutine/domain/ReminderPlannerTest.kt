package com.diaroutine.domain

import com.diaroutine.core.time.at
import com.diaroutine.core.time.epochDay
import com.diaroutine.core.time.plusDays
import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.GlucoseCheckPlan
import com.diaroutine.domain.model.MealContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPlannerTest {
    private fun plan(snapshot: RoutineSnapshot, hour: Int = 12) =
        ReminderPlanner.plan(instant(day, hour), zone, snapshot, use24Hour = true)

    @Test
    fun nothingIsPlannedBeforeOnboardingOrWithRemindersOff() {
        val meds = listOf(med(1, t(20)))
        assertTrue(plan(snapshot(profile = onboarded.copy(onboarded = false), medications = meds)).isEmpty())
        assertTrue(plan(snapshot(profile = onboarded.copy(remindersEnabled = false), medications = meds)).isEmpty())
    }

    @Test
    fun doseRemindersCoverTheNextTwoDaysExceptAnsweredOnes() {
        val meds = listOf(med(1, t(8), t(20)))
        val logs = listOf(taken(1, day.plusDays(1), t(8)))
        val doses = plan(snapshot(medications = meds, doseLogs = logs)).filter { it.kind == ReminderKind.Dose }
        // From noon today: 20:00 today, 20:00 tomorrow (08:00 tomorrow was taken early), 08:00 the day after.
        assertEquals(
            listOf(instant(day, 20), instant(day.plusDays(1), 20), instant(day.plusDays(2), 8)),
            doses.map { it.at },
        )
        assertEquals(DoseRef(1, day.plusDays(2).epochDay, 8 * 60), doses.last().dose)
    }

    @Test
    fun asNeededMedicationIsNeverReminded() {
        assertTrue(plan(snapshot(medications = listOf(med(1, asNeeded = true)))).isEmpty())
    }

    @Test
    fun glucoseChecksAreSkippedOnceAReadingAnswersThem() {
        val profile = onboarded.copy(glucoseChecks = listOf(GlucoseCheckPlan(t(13), MealContext.AfterMeal)))
        val early = reading(day, 12, 50)
        val checks = plan(snapshot(profile = profile, readings = listOf(early)), hour = 12).filter { it.kind == ReminderKind.Glucose }
        // Today's 13:00 check was answered at 12:50; the day after tomorrow's falls outside the 48 hours.
        assertEquals(listOf(instant(day.plusDays(1), 13)), checks.map { it.at })
    }

    @Test
    fun appointmentsAreRemindedAheadOfTime() {
        val visit = Appointment(id = 5, title = "Eye exam", withWhom = "Dr. Ade", at = day.plusDays(1).at(t(10)), remindBeforeMinutes = 24 * 60)
        val reminder = plan(snapshot().copy(appointments = listOf(visit)), hour = 9).single()
        assertEquals(instant(day, 10), reminder.at)
        assertEquals("Tomorrow at 10:00 · Dr. Ade", reminder.body)
    }

    @Test
    fun lowSupplyGetsOneMorningReminder() {
        val meds = listOf(med(1, t(21), supply = 5, refillAt = 7))
        val refill = plan(snapshot(medications = meds), hour = 12).single { it.kind == ReminderKind.Refill }
        assertEquals(instant(day.plusDays(1), 9), refill.at)
    }

    @Test
    fun idsAreStableAcrossReplans() {
        val snapshot = snapshot(medications = listOf(med(1, t(20))))
        val first = ReminderPlanner.plan(instant(day, 12), zone, snapshot, use24Hour = true)
        val later = ReminderPlanner.plan(instant(day, 13), zone, snapshot, use24Hour = true)
        assertEquals(first.first().id, later.first().id)
    }
}
