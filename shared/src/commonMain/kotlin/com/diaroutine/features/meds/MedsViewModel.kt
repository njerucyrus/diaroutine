package com.diaroutine.features.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaroutine.app.AppContainer
import com.diaroutine.core.time.ticker
import com.diaroutine.domain.RoutineSnapshot
import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.DoseStatus
import com.diaroutine.domain.model.Medication
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
import kotlin.time.Instant

data class MedicationItem(
    val medication: Medication,
    val dosesToday: Int,
    val takenToday: Int,
    /** For as-needed medication: when the last dose today was logged. */
    val lastTakenAt: Instant?,
)

data class MedsUiState(
    val isLoading: Boolean = true,
    val now: LocalDateTime,
    val medications: List<MedicationItem> = emptyList(),
    val upcoming: List<Appointment> = emptyList(),
    val past: List<Appointment> = emptyList(),
) {
    val scheduledCount: Int get() = medications.count { !it.medication.asNeeded }
    val asNeededCount: Int get() = medications.count { it.medication.asNeeded }
    val dosesToday: Int get() = medications.sumOf { it.dosesToday }
    val takenToday: Int get() = medications.sumOf { it.takenToday }
}

sealed interface MedsAction {
    data class SaveMedication(val medication: Medication) : MedsAction
    data class StopMedication(val id: Long) : MedsAction
    data class SetSupply(val id: Long, val amount: Int?) : MedsAction
    data class TakeAsNeeded(val id: Long) : MedsAction
    data class SaveAppointment(val appointment: Appointment) : MedsAction
    data class DeleteAppointment(val id: Long) : MedsAction
}

class MedsViewModel(private val container: AppContainer) : ViewModel() {
    private val now = container.clock.ticker(1.minutes).map { it.toLocalDateTime(container.timeZone) }

    val state: StateFlow<MedsUiState> = now
        .map { it.date }
        .distinctUntilChanged()
        .flatMapLatest { today -> combine(container.snapshot(today, today), now) { snapshot, now -> build(snapshot, now) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedsUiState(now = container.now()))

    fun onAction(action: MedsAction) {
        viewModelScope.launch {
            when (action) {
                is MedsAction.SaveMedication -> container.medications.save(action.medication)
                is MedsAction.StopMedication -> container.medications.stop(action.id, container.today())
                is MedsAction.SetSupply -> container.medications.setSupply(action.id, action.amount)
                is MedsAction.TakeAsNeeded -> container.medications.logAsNeeded(action.id)
                is MedsAction.SaveAppointment -> container.appointments.save(action.appointment)
                is MedsAction.DeleteAppointment -> container.appointments.delete(action.id)
            }
        }
    }

    private fun build(snapshot: RoutineSnapshot, now: LocalDateTime): MedsUiState {
        val today = now.date
        val items = snapshot.medications
            .filter { it.isActiveOn(today) || it.startDate > today }
            .map { med ->
                val taken = snapshot.doseLogs.filter { it.medicationId == med.id && it.date == today && it.status == DoseStatus.Taken }
                MedicationItem(
                    medication = med,
                    dosesToday = if (med.asNeeded) 0 else med.times.size,
                    takenToday = if (med.asNeeded) taken.size else taken.count { it.scheduledTime != null }.coerceAtMost(med.times.size),
                    lastTakenAt = taken.maxOfOrNull { it.loggedAt },
                )
            }
            .sortedWith(compareBy({ it.medication.asNeeded }, { it.medication.times.firstOrNull() }, { it.medication.name.lowercase() }))
        val (upcoming, past) = snapshot.appointments.partition { it.at >= now }
        return MedsUiState(
            isLoading = false,
            now = now,
            medications = items,
            upcoming = upcoming.sortedBy { it.at },
            past = past.sortedByDescending { it.at }.take(PAST_SHOWN),
        )
    }

    companion object {
        const val PAST_SHOWN = 10
    }
}
