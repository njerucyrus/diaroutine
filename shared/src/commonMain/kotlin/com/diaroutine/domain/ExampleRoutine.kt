package com.diaroutine.domain

import com.diaroutine.core.time.at
import com.diaroutine.core.time.plusDays
import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.DoseForm
import com.diaroutine.domain.model.Medication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * A sample routine for exploring the app, offered once during onboarding. It is
 * labelled as an example everywhere it's offered and is meant to be replaced with
 * the person's own prescribed schedule.
 */
object ExampleRoutine {
    fun medications(today: LocalDate): List<Medication> = listOf(
        Medication(
            name = "Metformin",
            strength = "500 mg",
            form = DoseForm.Tablet,
            times = listOf(LocalTime(8, 0), LocalTime(20, 0)),
            instructions = "With breakfast and dinner",
            supplyRemaining = 46,
            refillAt = 10,
            startDate = today,
        ),
        Medication(
            name = "Insulin glargine",
            strength = "10 units",
            form = DoseForm.Injection,
            times = listOf(LocalTime(22, 0)),
            instructions = "Same time each night",
            startDate = today,
        ),
        Medication(
            name = "Atorvastatin",
            strength = "20 mg",
            form = DoseForm.Tablet,
            times = listOf(LocalTime(21, 0)),
            supplyRemaining = 6,
            refillAt = 7,
            startDate = today,
        ),
        Medication(
            name = "Glucose tablets",
            strength = "4 g",
            form = DoseForm.Tablet,
            amountPerDose = 4,
            asNeeded = true,
            instructions = "As your care team directs for lows",
            startDate = today,
        ),
    )

    fun appointments(today: LocalDate): List<Appointment> = listOf(
        Appointment(
            title = "Diabetes check-up",
            withWhom = "Dr. Rivera · City Clinic",
            at = today.plusDays(3).at(LocalTime(10, 30)),
            notes = "Bring glucose log and medication list",
            remindBeforeMinutes = 24 * 60,
        ),
    )
}
