package com.diaroutine.features.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaroutine.app.AppContainer
import com.diaroutine.core.time.plusDays
import com.diaroutine.core.time.ticker
import com.diaroutine.domain.GlucoseStats
import com.diaroutine.domain.GlucoseSummary
import com.diaroutine.domain.model.DailyHabit
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

data class TrackUiState(
    val isLoading: Boolean = true,
    val profile: Profile = Profile(),
    val now: LocalDateTime,
    val recent: List<GlucoseReading> = emptyList(),
    val week: GlucoseSummary? = null,
    val habit: DailyHabit,
) {
    val latest: GlucoseReading? get() = recent.firstOrNull()
}

sealed interface TrackAction {
    data class LogGlucose(val reading: GlucoseReading) : TrackAction
    data class DeleteReading(val id: Long) : TrackAction
    data class AddWater(val glasses: Int) : TrackAction
    data class SetActivity(val steps: Int, val minutes: Int) : TrackAction
}

class TrackViewModel(private val container: AppContainer) : ViewModel() {
    private val now = container.clock.ticker(1.minutes).map { it.toLocalDateTime(container.timeZone) }

    val state: StateFlow<TrackUiState> = now
        .map { it.date }
        .distinctUntilChanged()
        .flatMapLatest { today ->
            combine(container.snapshot(today.plusDays(-6), today), container.glucose.recent(RECENT_COUNT), now) { snapshot, recent, now ->
                TrackUiState(
                    isLoading = false,
                    profile = snapshot.profile,
                    now = now,
                    recent = recent,
                    week = GlucoseStats.summarize(snapshot.readings, today.plusDays(-6), today, snapshot.profile, now, container.timeZone),
                    habit = snapshot.habitOn(today) ?: DailyHabit(today),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    fun onAction(action: TrackAction) {
        viewModelScope.launch {
            val today = container.today()
            when (action) {
                is TrackAction.LogGlucose -> container.glucose.add(action.reading)
                is TrackAction.DeleteReading -> container.glucose.delete(action.id)
                is TrackAction.AddWater -> container.habits.addWater(today, action.glasses)
                is TrackAction.SetActivity -> container.habits.setActivity(today, action.steps, action.minutes)
            }
        }
    }

    private fun initialState(): TrackUiState {
        val now = container.now()
        return TrackUiState(now = now, habit = DailyHabit(now.date))
    }

    companion object {
        const val RECENT_COUNT = 20
    }
}
