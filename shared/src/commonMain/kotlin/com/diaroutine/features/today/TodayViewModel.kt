package com.diaroutine.features.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaroutine.app.AppContainer
import com.diaroutine.core.time.plusDays
import com.diaroutine.core.time.ticker
import com.diaroutine.core.time.through
import com.diaroutine.domain.DayPlan
import com.diaroutine.domain.RoutineSnapshot
import com.diaroutine.domain.Timeline
import com.diaroutine.domain.TimelineEntry
import com.diaroutine.domain.model.DailyHabit
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.Medication
import com.diaroutine.domain.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds

data class TodayUiState(
    val isLoading: Boolean = true,
    val profile: Profile = Profile(),
    val now: LocalDateTime,
    val selectedDate: LocalDate,
    val days: List<LocalDate>,
    val plan: DayPlan? = null,
    val refills: List<Medication> = emptyList(),
    val latestReading: GlucoseReading? = null,
    val habit: DailyHabit,
    val hasMedications: Boolean = false,
) {
    val today: LocalDate get() = now.date
    val isToday: Boolean get() = selectedDate == today
    val isFuture: Boolean get() = selectedDate > today
}

sealed interface TodayAction {
    data class SelectDate(val date: LocalDate) : TodayAction
    data class TakeDose(val entry: TimelineEntry.Dose) : TodayAction
    data class SkipDose(val entry: TimelineEntry.Dose) : TodayAction
    data class ClearDose(val entry: TimelineEntry.Dose) : TodayAction
    data class AddWater(val glasses: Int) : TodayAction
    data class LogGlucose(val reading: GlucoseReading) : TodayAction
    data class SetActivity(val steps: Int, val minutes: Int) : TodayAction
}

class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedDate = MutableStateFlow(container.today())

    private val now = container.clock.ticker(30.seconds).map { it.toLocalDateTime(container.timeZone) }

    val state: StateFlow<TodayUiState> = selectedDate
        .flatMapLatest { date -> combine(container.snapshot(date, date), now) { snapshot, now -> build(date, snapshot, now) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    fun onAction(action: TodayAction) {
        val date = selectedDate.value
        when (action) {
            is TodayAction.SelectDate -> selectedDate.value = action.date
            is TodayAction.TakeDose -> launch { container.medications.markTaken(action.entry.medication.id, date, action.entry.time) }
            is TodayAction.SkipDose -> launch { container.medications.markSkipped(action.entry.medication.id, date, action.entry.time) }
            is TodayAction.ClearDose -> launch {
                val log = action.entry.log
                if (action.entry.medication.asNeeded && log != null) {
                    container.medications.deleteLog(log.id)
                } else {
                    container.medications.clearDose(action.entry.medication.id, date, action.entry.time)
                }
            }
            is TodayAction.AddWater -> launch { container.habits.addWater(date, action.glasses) }
            is TodayAction.LogGlucose -> launch { container.glucose.add(action.reading) }
            is TodayAction.SetActivity -> launch { container.habits.setActivity(date, action.steps, action.minutes) }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun build(date: LocalDate, snapshot: RoutineSnapshot, now: LocalDateTime): TodayUiState {
        val today = now.date
        return TodayUiState(
            isLoading = false,
            profile = snapshot.profile,
            now = now,
            selectedDate = date,
            days = daysAround(today),
            plan = Timeline.build(date, now, snapshot, container.timeZone),
            refills = if (date == today) snapshot.medications.filter { it.isActiveOn(today) && it.needsRefill } else emptyList(),
            latestReading = snapshot.readings.maxByOrNull { it.at },
            habit = snapshot.habitOn(date) ?: DailyHabit(date),
            hasMedications = snapshot.medications.any { it.isActiveOn(date) || it.isActiveOn(today) },
        )
    }

    private fun initialState(): TodayUiState {
        val now = container.now()
        return TodayUiState(now = now, selectedDate = now.date, days = daysAround(now.date), habit = DailyHabit(now.date))
    }

    private fun daysAround(today: LocalDate): List<LocalDate> = today.plusDays(-DAYS_BACK).through(today.plusDays(DAYS_AHEAD))

    companion object {
        const val DAYS_BACK = 6
        const val DAYS_AHEAD = 7
    }
}
