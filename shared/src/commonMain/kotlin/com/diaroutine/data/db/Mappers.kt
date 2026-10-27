package com.diaroutine.data.db

import com.diaroutine.core.time.dateOfEpochDay
import com.diaroutine.core.time.epochDay
import com.diaroutine.core.time.minuteOfDay
import com.diaroutine.core.time.timeOfMinute
import com.diaroutine.domain.model.AppLockSettings
import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.Caregiver
import com.diaroutine.domain.model.DailyHabit
import com.diaroutine.domain.model.DoseForm
import com.diaroutine.domain.model.DoseLog
import com.diaroutine.domain.model.DoseStatus
import com.diaroutine.domain.model.GlucoseCheckPlan
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.GlucoseUnit
import com.diaroutine.domain.model.MealContext
import com.diaroutine.domain.model.Medication
import com.diaroutine.domain.model.Profile
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun minutesToCsv(minutes: List<Int>): String = minutes.joinToString(",")

private fun csvToMinutes(csv: String): List<Int> = csv.split(',').mapNotNull { it.trim().toIntOrNull() }

fun ProfileEntity.toDomain(): Profile = Profile(
    name = name,
    glucoseUnit = enumOr(glucoseUnit, GlucoseUnit.MgDl),
    targetLowMgDl = targetLowMgDl,
    targetHighMgDl = targetHighMgDl,
    waterGoalGlasses = waterGoalGlasses,
    stepGoal = stepGoal,
    walkGoalMinutes = walkGoalMinutes,
    walkTime = walkMinuteOfDay?.let(::timeOfMinute),
    glucoseChecks = glucoseChecks.split(',').mapNotNull { part ->
        val minute = part.substringBefore(':').trim().toIntOrNull() ?: return@mapNotNull null
        GlucoseCheckPlan(timeOfMinute(minute), enumOr(part.substringAfter(':', ""), MealContext.Other))
    }.sortedBy { it.time },
    remindersEnabled = remindersEnabled,
    followUpMinutes = followUpMinutes,
    caregiver = Caregiver(caregiverEnabled, caregiverName, caregiverContact),
    appLock = AppLockSettings(appLockEnabled, appLockAfterSeconds),
    onboarded = onboarded,
    tourCompleted = tourCompleted,
)

fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    name = name.trim(),
    glucoseUnit = glucoseUnit.name,
    targetLowMgDl = targetLowMgDl,
    targetHighMgDl = targetHighMgDl,
    waterGoalGlasses = waterGoalGlasses,
    stepGoal = stepGoal,
    walkGoalMinutes = walkGoalMinutes,
    walkMinuteOfDay = walkTime?.minuteOfDay,
    glucoseChecks = glucoseChecks.sortedBy { it.time }.joinToString(",") { "${it.time.minuteOfDay}:${it.context.name}" },
    remindersEnabled = remindersEnabled,
    caregiverEnabled = caregiver.enabled,
    caregiverName = caregiver.name.trim(),
    caregiverContact = caregiver.contact.trim(),
    onboarded = onboarded,
    appLockEnabled = appLock.enabled,
    appLockAfterSeconds = appLock.afterSeconds,
    tourCompleted = tourCompleted,
    followUpMinutes = followUpMinutes,
)

fun MedicationEntity.toDomain(): Medication = Medication(
    id = id,
    name = name,
    strength = strength,
    form = enumOr(form, DoseForm.Other),
    amountPerDose = amountPerDose,
    times = csvToMinutes(times).distinct().sorted().map(::timeOfMinute),
    asNeeded = asNeeded,
    instructions = instructions,
    supplyRemaining = supplyRemaining,
    refillAt = refillAt,
    startDate = dateOfEpochDay(startEpochDay),
    endDate = endEpochDay?.let(::dateOfEpochDay),
)

fun Medication.toEntity(): MedicationEntity = MedicationEntity(
    id = id,
    name = name.trim(),
    strength = strength.trim(),
    form = form.name,
    amountPerDose = amountPerDose,
    times = minutesToCsv(if (asNeeded) emptyList() else times.map { it.minuteOfDay }.distinct().sorted()),
    asNeeded = asNeeded,
    instructions = instructions.trim(),
    supplyRemaining = supplyRemaining,
    refillAt = refillAt,
    startEpochDay = startDate.epochDay,
    endEpochDay = endDate?.epochDay,
)

fun DoseLogEntity.toDomain(): DoseLog = DoseLog(
    id = id,
    medicationId = medicationId,
    date = dateOfEpochDay(epochDay),
    scheduledTime = scheduledMinute?.let(::timeOfMinute),
    status = enumOr(status, DoseStatus.Taken),
    loggedAt = Instant.fromEpochMilliseconds(loggedAtMillis),
)

fun GlucoseReadingEntity.toDomain(): GlucoseReading = GlucoseReading(
    id = id,
    at = Instant.fromEpochMilliseconds(atMillis),
    mgDl = mgDl,
    context = enumOr(context, MealContext.Other),
    note = note,
)

fun GlucoseReading.toEntity(): GlucoseReadingEntity = GlucoseReadingEntity(
    id = id,
    atMillis = at.toEpochMilliseconds(),
    mgDl = mgDl,
    context = context.name,
    note = note.trim(),
)

fun DailyHabitEntity.toDomain(): DailyHabit = DailyHabit(
    date = dateOfEpochDay(epochDay),
    waterGlasses = waterGlasses,
    steps = steps,
    activeMinutes = activeMinutes,
)

fun DailyHabit.toEntity(): DailyHabitEntity = DailyHabitEntity(
    epochDay = date.epochDay,
    waterGlasses = waterGlasses,
    steps = steps,
    activeMinutes = activeMinutes,
)

fun AppointmentEntity.toDomain(): Appointment = Appointment(
    id = id,
    title = title,
    withWhom = withWhom,
    at = LocalDateTime.parse(dateTime),
    notes = notes,
    remindBeforeMinutes = remindBeforeMinutes,
)

fun Appointment.toEntity(): AppointmentEntity = AppointmentEntity(
    id = id,
    title = title.trim(),
    withWhom = withWhom.trim(),
    dateTime = at.toString(),
    notes = notes.trim(),
    remindBeforeMinutes = remindBeforeMinutes,
)
