package com.diaroutine.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaroutine.app.AppContainer
import com.diaroutine.core.time.plusDays
import com.diaroutine.core.time.ticker
import com.diaroutine.domain.Report
import com.diaroutine.domain.ReportFigures
import com.diaroutine.domain.ReportPeriod
import com.diaroutine.domain.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
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
    val period: ReportPeriod = ReportPeriod.Week,
    val figures: ReportFigures? = null,
    val hasScheduledMedication: Boolean = false,
    /** Longer periods are Premium. */
    val isPremium: Boolean = false,
)

class InsightsViewModel(private val container: AppContainer) : ViewModel() {
    private val now = container.clock.ticker(1.minutes).map { it.toLocalDateTime(container.timeZone) }
    private val period = MutableStateFlow(ReportPeriod.Week)

    val state: StateFlow<InsightsUiState> = combine(now.map { it.date }.distinctUntilChanged(), period) { today, period -> today to period }
        .flatMapLatest { (today, period) ->
            val from = today.plusDays(-(Report.snapshotDays(period) - 1))
            combine(container.snapshot(from, today), now, container.premium.state) { snapshot, now, premium ->
                // A lapsed subscription falls back to the free week rather than showing a locked view.
                val shown = if (premium.isPremium) period else ReportPeriod.Week
                InsightsUiState(
                    isLoading = false,
                    profile = snapshot.profile,
                    today = now.date,
                    period = shown,
                    figures = Report.figures(snapshot, now, container.timeZone, shown),
                    hasScheduledMedication = snapshot.medications.any { it.isScheduled },
                    isPremium = premium.isPremium,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState(today = container.today()))

    /** Longer periods need Premium; the screen offers it instead of calling this when it's missing. */
    fun selectPeriod(period: ReportPeriod) {
        this.period.value = period
    }

    fun shareSummary() {
        viewModelScope.launch {
            val period = state.value.period
            container.platform.shareText("DiaRoutine summary", container.summaryText(period))
        }
    }
}
