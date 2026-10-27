package com.diaroutine.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaroutine.app.AppContainer
import com.diaroutine.core.time.plusDays
import com.diaroutine.core.time.ticker
import com.diaroutine.domain.WeeklyFigures
import com.diaroutine.domain.WeeklyReport
import com.diaroutine.domain.model.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

data class InsightsUiState(
    val isLoading: Boolean = true,
    val profile: Profile = Profile(),
    val today: LocalDate,
    val figures: WeeklyFigures? = null,
    val hasScheduledMedication: Boolean = false,
)

class InsightsViewModel(private val container: AppContainer) : ViewModel() {
    private val now = container.clock.ticker(1.minutes).map { it.toLocalDateTime(container.timeZone) }

    val state: StateFlow<InsightsUiState> = now
        .map { it.date }
        .distinctUntilChanged()
        .flatMapLatest { today ->
            val from = today.plusDays(-(WeeklyReport.HISTORY_DAYS - 1))
            combine(container.snapshot(from, today), now) { snapshot, now ->
                InsightsUiState(
                    isLoading = false,
                    profile = snapshot.profile,
                    today = now.date,
                    figures = WeeklyReport.figures(snapshot, now, container.timeZone),
                    hasScheduledMedication = snapshot.medications.any { it.isScheduled },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState(today = container.today()))

    fun shareSummary() {
        viewModelScope.launch {
            container.platform.shareText("DiaRoutine weekly summary", container.weeklySummaryText())
        }
    }
}
