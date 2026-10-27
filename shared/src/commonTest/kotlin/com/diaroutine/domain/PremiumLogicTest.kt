package com.diaroutine.domain

import com.diaroutine.core.time.plusDays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PremiumLogicTest {
    @Test
    fun followUpsRemindAgainUntilTheDoseIsAnswered() {
        val meds = listOf(med(1, t(8)))
        // 08:10: the 08:00 reminder has gone; its follow-ups at 08:30 and 09:00 are still ahead.
        val plan = ReminderPlanner.plan(instant(day, 8, 10), zone, snapshot(medications = meds), use24Hour = true, followUpMinutes = 30)
            .filter { it.at < instant(day.plusDays(1), 0) }
        assertEquals(listOf(instant(day, 8, 30), instant(day, 9, 0)), plan.map { it.at })
        assertTrue(plan.all { it.title == "Still due: Med 1" && it.dose != null })

        val taken = ReminderPlanner.plan(
            instant(day, 8, 10), zone,
            snapshot(medications = meds, doseLogs = listOf(taken(1, day, t(8)))),
            use24Hour = true, followUpMinutes = 30,
        ).filter { it.at < instant(day.plusDays(1), 0) }
        assertTrue(taken.isEmpty())
    }

    @Test
    fun withoutFollowUpsOnlyTheDoseItselfIsReminded() {
        val plan = ReminderPlanner.plan(instant(day, 7), zone, snapshot(medications = listOf(med(1, t(8)))), use24Hour = true)
            .filter { it.at < instant(day.plusDays(1), 0) }
        assertEquals(listOf(instant(day, 8)), plan.map { it.at })
    }

    @Test
    fun bucketsKeepTheMostRecentRunWhole() {
        val days = (1..10).toList()
        assertEquals(listOf(listOf(1), listOf(2, 3, 4), listOf(5, 6, 7), listOf(8, 9, 10)), days.bucketedFromEnd(3))
        assertEquals(days.map { listOf(it) }, days.bucketedFromEnd(1))
    }

    @Test
    fun longerPeriodsCoverTheirWholeSpanAndCompareWithTheOneBefore() {
        val meds = listOf(med(1, t(8), start = day.plusDays(-200)))
        val figures = Report.figures(snapshot(medications = meds), at(day, 12), zone, ReportPeriod.Month)
        assertEquals(30, figures.days.size)
        assertEquals(day.plusDays(-29), figures.from)
        assertEquals(0f, figures.adherence)
        assertEquals(0f, figures.previousAdherence)
        assertEquals(180, Report.snapshotDays(ReportPeriod.Quarter))
    }
}
