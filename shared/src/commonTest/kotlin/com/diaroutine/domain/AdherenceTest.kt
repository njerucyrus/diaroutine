package com.diaroutine.domain

import com.diaroutine.core.time.plusDays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdherenceTest {
    private val twiceDaily = med(1, t(8), t(20), start = day.plusDays(-30))

    @Test
    fun todayCountsOnlyDosesDueSoFar() {
        val logs = listOf(taken(1, day, t(8)))
        val morning = Adherence.day(day, listOf(twiceDaily), logs, cutoff = t(12))
        assertEquals(DayAdherence(day, scheduled = 1, taken = 1), morning)
        val wholeDay = Adherence.day(day, listOf(twiceDaily), logs)
        assertEquals(DayAdherence(day, scheduled = 2, taken = 1), wholeDay)
    }

    @Test
    fun extraLogsDoNotPushPastOneHundredPercent() {
        val logs = listOf(taken(1, day, t(8)), taken(1, day, t(20)), taken(1, day, t(9)))
        assertEquals(1f, Adherence.day(day, listOf(twiceDaily), logs).ratio)
    }

    @Test
    fun asNeededAndSkippedDosesDoNotCountAsTaken() {
        val prn = med(2, asNeeded = true, start = day.plusDays(-30))
        val logs = listOf(skipped(1, day, t(8)), taken(2, day, null))
        val result = Adherence.day(day, listOf(twiceDaily, prn), logs)
        assertEquals(DayAdherence(day, scheduled = 2, taken = 0), result)
    }

    @Test
    fun aDayWithNothingScheduledHasNoRatio() {
        assertNull(Adherence.day(day, emptyList(), emptyList()).ratio)
        assertNull(Adherence.overall(listOf(DayAdherence(day, 0, 0))))
    }

    @Test
    fun streaksSkipEmptyDaysAndWaitForToday() {
        fun d(offset: Int, scheduled: Int, taken: Int) = DayAdherence(day.plusDays(offset), scheduled, taken)
        val days = listOf(
            d(-6, 2, 2),
            d(-5, 2, 1), // missed one: breaks the run
            d(-4, 2, 2),
            d(-3, 0, 0), // nothing scheduled: neither breaks nor extends
            d(-2, 2, 2),
            d(-1, 2, 2),
            d(0, 2, 1), // today, not finished yet
        )
        assertEquals(Streaks(current = 3, longest = 3), Adherence.streaks(days, today = day))
        assertEquals(Streaks(current = 4, longest = 4), Adherence.streaks(days.dropLast(1) + d(0, 2, 2), today = day))
    }

    @Test
    fun weekWindowStopsAtToday() {
        val days = Adherence.days(day.plusDays(-6), day.plusDays(3), listOf(twiceDaily), emptyList(), at(day, 12))
        assertEquals(7, days.size)
        assertEquals(1, days.last().scheduled)
    }
}
