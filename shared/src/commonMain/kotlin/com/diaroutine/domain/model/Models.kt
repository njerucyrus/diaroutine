package com.diaroutine.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class GlucoseUnit(val label: String) {
    MgDl("mg/dL"),
    MmolL("mmol/L"),
}

enum class MealContext(val label: String) {
    Fasting("Fasting"),
    BeforeMeal("Before meal"),
    AfterMeal("After meal"),
    Bedtime("Bedtime"),
    Other("Other"),
}

/** A glucose check the person plans to do every day at [time]. */
data class GlucoseCheckPlan(val time: LocalTime, val context: MealContext)

data class Caregiver(
    val enabled: Boolean = false,
    val name: String = "",
    val contact: String = "",
)

/** Whether opening the app needs the phone's biometrics or screen lock, and how soon it locks again. */
data class AppLockSettings(
    val enabled: Boolean = false,
    /** How long the app may be away before it locks again; 0 locks every time it's left. */
    val afterSeconds: Int = 0,
)

data class Profile(
    val name: String = "",
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MgDl,
    val targetLowMgDl: Int = 70,
    val targetHighMgDl: Int = 180,
    val waterGoalGlasses: Int = 8,
    val stepGoal: Int = 6000,
    val walkGoalMinutes: Int = 30,
    /** When the daily walk is planned; null when it isn't on the timeline. */
    val walkTime: LocalTime? = LocalTime(18, 0),
    val glucoseChecks: List<GlucoseCheckPlan> = listOf(
        GlucoseCheckPlan(LocalTime(7, 0), MealContext.Fasting),
        GlucoseCheckPlan(LocalTime(21, 30), MealContext.Bedtime),
    ),
    val remindersEnabled: Boolean = true,
    val caregiver: Caregiver = Caregiver(),
    val appLock: AppLockSettings = AppLockSettings(),
    val onboarded: Boolean = false,
    /** Whether the person has finished or skipped the app tour. */
    val tourCompleted: Boolean = false,
) {
    val firstName: String get() = name.trim().substringBefore(' ')

    val initials: String
        get() = name.trim().split(' ').filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

enum class DoseForm(val label: String, private val one: String, private val many: String) {
    Tablet("Tablet", "tablet", "tablets"),
    Capsule("Capsule", "capsule", "capsules"),
    Injection("Injection", "injection", "injections"),
    Liquid("Liquid", "dose", "doses"),
    Inhaler("Inhaler", "puff", "puffs"),
    Other("Other", "dose", "doses");

    fun amount(count: Int): String = "$count ${if (count == 1) one else many}"
}

data class Medication(
    val id: Long = 0,
    val name: String,
    /** As written on the prescription, e.g. "500 mg" or "10 units". */
    val strength: String = "",
    val form: DoseForm = DoseForm.Tablet,
    val amountPerDose: Int = 1,
    val times: List<LocalTime> = emptyList(),
    val asNeeded: Boolean = false,
    val instructions: String = "",
    /** Units on hand, or null when supply isn't tracked. */
    val supplyRemaining: Int? = null,
    val refillAt: Int = 7,
    val startDate: LocalDate,
    /** First day the medication is no longer part of the routine. */
    val endDate: LocalDate? = null,
) {
    fun isActiveOn(date: LocalDate): Boolean = date >= startDate && (endDate == null || date < endDate)

    val isScheduled: Boolean get() = !asNeeded && times.isNotEmpty()

    val needsRefill: Boolean get() = supplyRemaining != null && supplyRemaining <= refillAt

    /** Whole days the supply lasts at the prescribed rate; null when not tracked or as-needed. */
    val daysOfSupply: Int?
        get() {
            val perDay = amountPerDose * times.size
            return if (supplyRemaining == null || asNeeded || perDay == 0) null else supplyRemaining / perDay
        }

    /** "500 mg · 1 tablet" */
    val doseDescription: String
        get() = listOf(strength.trim(), form.amount(amountPerDose)).filter { it.isNotEmpty() }.joinToString(" · ")
}

enum class DoseStatus { Taken, Skipped }

data class DoseLog(
    val id: Long = 0,
    val medicationId: Long,
    val date: LocalDate,
    /** The scheduled slot this log answers; null for an as-needed dose. */
    val scheduledTime: LocalTime?,
    val status: DoseStatus,
    val loggedAt: Instant,
)

data class GlucoseReading(
    val id: Long = 0,
    val at: Instant,
    /** Stored in mg/dL whatever unit the person reads in. */
    val mgDl: Int,
    val context: MealContext,
    val note: String = "",
)

data class DailyHabit(
    val date: LocalDate,
    val waterGlasses: Int = 0,
    val steps: Int = 0,
    val activeMinutes: Int = 0,
)

data class Appointment(
    val id: Long = 0,
    val title: String,
    val withWhom: String = "",
    val at: LocalDateTime,
    val notes: String = "",
    /** 0 for no reminder. */
    val remindBeforeMinutes: Int = 24 * 60,
)
