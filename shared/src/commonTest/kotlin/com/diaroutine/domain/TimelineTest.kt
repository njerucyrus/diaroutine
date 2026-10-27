package com.diaroutine.domain

import com.diaroutine.core.time.plusDays
import com.diaroutine.domain.model.DailyHabit
import com.diaroutine.domain.model.GlucoseCheckPlan
import com.diaroutine.domain.model.MealContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineTest {
    @Test
    fun scheduledStateFollowsTheClock() {
        val dose = t(8)
        assertEquals(EntryState.Upcoming, Timeline.scheduledState(day, dose, at(day, 7, 29)))
        assertEquals(EntryState.DueNow, Timeline.scheduledState(day, dose, at(day, 7, 30)))
        assertEquals(EntryState.DueNow, Timeline.scheduledState(day, dose, at(day, 9, 0)))
        assertEquals(EntryState.Overdue, Timeline.scheduledState(day, dose, at(day, 9, 1)))
        assertEquals(EntryState.Missed, Timeline.scheduledState(day, dose, at(day.plusDays(1), 0, 0)))
        assertEquals(EntryState.Upcoming, Timeline.scheduledState(day.plusDays(1), dose, at(day, 23, 0)))
    }

    @Test
    fun dosesShowWhatWasTakenSkippedAndWhatIsLeft() {
        val metformin = med(1, t(8), t(20))
        val statin = med(2, t(21))
        val plan = Timeline.build(
            day,
            at(day, 12),
            snapshot(medications = listOf(metformin, statin), doseLogs = listOf(taken(1, day, t(8)), skipped(2, day, t(21)))),
            zone,
        )
        val states = plan.entries.map { (it as TimelineEntry.Dose).medication.id to it.state }
        assertEquals(listOf(1L to EntryState.Done, 1L to EntryState.Upcoming, 2L to EntryState.Skipped), states)
        assertEquals(3, plan.summary.planned)
        assertEquals(1, plan.summary.done)
        assertEquals(t(20), plan.summary.next?.time)
    }

    @Test
    fun medicationsOutsideTheirDatesAreLeftOff() {
        val notStarted = med(1, t(8), start = day.plusDays(1))
        val stopped = med(2, t(8), start = day.plusDays(-10), end = day)
        val plan = Timeline.build(day, at(day, 6), snapshot(medications = listOf(notStarted, stopped)), zone)
        assertTrue(plan.entries.isEmpty())
    }

    @Test
    fun asNeededDosesAppearWhenTakenButAreNotPlanned() {
        val prn = med(1, asNeeded = true)
        val plan = Timeline.build(day, at(day, 15), snapshot(medications = listOf(prn), doseLogs = listOf(taken(1, day, null, id = 9))), zone)
        val entry = plan.entries.single()
        assertEquals(EntryState.Done, entry.state)
        assertEquals(false, entry.isPlanned)
        assertEquals(0, plan.summary.planned)
    }

    @Test
    fun readingsAnswerTheNearestPlannedCheckAndExtrasStandAlone() {
        val profile = onboarded.copy(
            glucoseChecks = listOf(GlucoseCheckPlan(t(7), MealContext.Fasting), GlucoseCheckPlan(t(21, 30), MealContext.Bedtime)),
        )
        val readings = listOf(reading(day, 7, 40, id = 1), reading(day, 14, 0, id = 2))
        val plan = Timeline.build(day, at(day, 22, 0), snapshot(profile = profile, readings = readings), zone)
        val checks = plan.entries.filterIsInstance<TimelineEntry.GlucoseCheck>()

        val fasting = checks.single { it.plan?.context == MealContext.Fasting }
        assertEquals(1L, fasting.reading?.id)
        assertEquals(EntryState.Done, fasting.state)

        val bedtime = checks.single { it.plan?.context == MealContext.Bedtime }
        assertNull(bedtime.reading)
        assertEquals(EntryState.DueNow, bedtime.state)

        val extra = checks.single { it.plan == null }
        assertEquals(2L, extra.reading?.id)
        assertEquals(2, plan.summary.planned)
    }

    @Test
    fun aReadingTooFarFromAnyCheckDoesNotCountForIt() {
        val profile = onboarded.copy(glucoseChecks = listOf(GlucoseCheckPlan(t(7), MealContext.Fasting)))
        val match = Timeline.matchReadings(profile.glucoseChecks, listOf(reading(day, 9, 1)), zone)
        assertNull(match.matched.single().second)
        assertEquals(1, match.unmatched.size)
    }

    @Test
    fun walkIsDoneOnceActiveMinutesReachTheGoal() {
        val profile = onboarded.copy(walkTime = t(18), walkGoalMinutes = 30)
        val partial = Timeline.build(day, at(day, 20), profile, emptyList(), emptyList(), emptyList(), DailyHabit(day, activeMinutes = 20), emptyList(), zone)
        assertEquals(EntryState.Overdue, partial.entries.single().state)
        val done = Timeline.build(day, at(day, 20), profile, emptyList(), emptyList(), emptyList(), DailyHabit(day, activeMinutes = 30), emptyList(), zone)
        assertEquals(EntryState.Done, done.entries.single().state)
    }
}
