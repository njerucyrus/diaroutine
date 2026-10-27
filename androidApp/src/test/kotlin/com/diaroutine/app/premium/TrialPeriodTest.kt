package com.diaroutine.app.premium

import org.junit.Assert.assertEquals
import org.junit.Test

class TrialPeriodTest {
    @Test
    fun playPeriodsReadAsWords() {
        assertEquals("3 months", AndroidPremiumStore.describe("P3M"))
        assertEquals("1 month", AndroidPremiumStore.describe("P1M"))
        assertEquals("2 weeks", AndroidPremiumStore.describe("P2W"))
        assertEquals("1 year", AndroidPremiumStore.describe("P1Y"))
        assertEquals("P1Y2M", AndroidPremiumStore.describe("P1Y2M"))
    }
}
