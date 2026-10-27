package com.diaroutine.domain

import com.diaroutine.core.format.Fmt
import com.diaroutine.domain.model.GlucoseUnit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlucoseAndFormatTest {
    @Test
    fun parsesBothUnitsIntoMgDl() {
        assertEquals(112, Glucose.parse("112", GlucoseUnit.MgDl))
        assertEquals(112, Glucose.parse("6.2", GlucoseUnit.MmolL))
        assertEquals(112, Glucose.parse("6,2", GlucoseUnit.MmolL))
    }

    @Test
    fun rejectsValuesNoMeterWouldShow() {
        assertNull(Glucose.parse("", GlucoseUnit.MgDl))
        assertNull(Glucose.parse("abc", GlucoseUnit.MgDl))
        assertNull(Glucose.parse("19", GlucoseUnit.MgDl))
        assertNull(Glucose.parse("601", GlucoseUnit.MgDl))
        assertNull(Glucose.parse("112", GlucoseUnit.MmolL))
    }

    @Test
    fun formatsMmolToOneDecimal() {
        assertEquals("6.2", Glucose.format(112, GlucoseUnit.MmolL))
        assertEquals("10.0", Glucose.format(180, GlucoseUnit.MmolL))
        assertEquals("180", Glucose.format(180, GlucoseUnit.MgDl))
    }

    @Test
    fun levelsUseTheProfileTarget() {
        val profile = onboarded.copy(targetLowMgDl = 70, targetHighMgDl = 180)
        assertEquals(GlucoseLevel.Low, Glucose.level(69, profile))
        assertEquals(GlucoseLevel.InRange, Glucose.level(70, profile))
        assertEquals(GlucoseLevel.InRange, Glucose.level(180, profile))
        assertEquals(GlucoseLevel.High, Glucose.level(181, profile))
    }

    @Test
    fun formatsTimesAndNumbers() {
        assertEquals("8:05 AM", Fmt.time(t(8, 5), use24Hour = false))
        assertEquals("12:00 PM", Fmt.time(t(12), use24Hour = false))
        assertEquals("12:30 AM", Fmt.time(t(0, 30), use24Hour = false))
        assertEquals("20:00", Fmt.time(t(20), use24Hour = true))
        assertEquals("12,345", Fmt.grouped(12345))
        assertEquals("950", Fmt.compact(950))
        assertEquals("3.2k", Fmt.compact(3200))
        assertEquals("6k", Fmt.compact(6000))
        assertEquals("Tuesday, 27 October", Fmt.dateLong(day))
        assertEquals("21–27 Oct", Fmt.dateRange(LocalDate(2026, 10, 21), day))
        assertEquals("29 Sep – 5 Oct", Fmt.dateRange(LocalDate(2026, 9, 29), LocalDate(2026, 10, 5)))
    }
}
