package com.diaroutine.domain

import com.diaroutine.core.time.at
import com.diaroutine.core.time.instantIn
import com.diaroutine.domain.model.DoseLog
import com.diaroutine.domain.model.DoseStatus
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.MealContext
import com.diaroutine.domain.model.Medication
import com.diaroutine.domain.model.Profile
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

val zone = TimeZone.UTC
val day: LocalDate = LocalDate(2026, 10, 27)

fun t(hour: Int, minute: Int = 0) = LocalTime(hour, minute)

fun at(date: LocalDate, hour: Int, minute: Int = 0): LocalDateTime = date.at(t(hour, minute))

fun instant(date: LocalDate, hour: Int, minute: Int = 0): Instant = at(date, hour, minute).instantIn(zone)

val onboarded = Profile(name = "Lina", onboarded = true, glucoseChecks = emptyList(), walkTime = null)

fun med(
    id: Long,
    vararg times: LocalTime,
    start: LocalDate = day,
    end: LocalDate? = null,
    asNeeded: Boolean = false,
    supply: Int? = null,
    refillAt: Int = 7,
) = Medication(
    id = id,
    name = "Med $id",
    strength = "10 mg",
    times = times.toList(),
    asNeeded = asNeeded,
    supplyRemaining = supply,
    refillAt = refillAt,
    startDate = start,
    endDate = end,
)

fun taken(medicationId: Long, date: LocalDate, time: LocalTime?, id: Long = 0) =
    DoseLog(id, medicationId, date, time, DoseStatus.Taken, instant(date, time?.hour ?: 12, time?.minute ?: 0))

fun skipped(medicationId: Long, date: LocalDate, time: LocalTime) =
    DoseLog(0, medicationId, date, time, DoseStatus.Skipped, instant(date, time.hour, time.minute))

fun reading(date: LocalDate, hour: Int, minute: Int = 0, mgDl: Int = 110, id: Long = 0) =
    GlucoseReading(id, instant(date, hour, minute), mgDl, MealContext.Other)

fun snapshot(
    profile: Profile = onboarded,
    medications: List<Medication> = emptyList(),
    doseLogs: List<DoseLog> = emptyList(),
    readings: List<GlucoseReading> = emptyList(),
) = RoutineSnapshot(profile, medications, doseLogs, readings, habits = emptyList(), appointments = emptyList())
