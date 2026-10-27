package com.diaroutine.features.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diaroutine.app.AppContainer
import com.diaroutine.core.format.Fmt
import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.Medication
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.CircleIconButton
import com.diaroutine.ui.components.ConfirmDialog
import com.diaroutine.ui.components.CountPill
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.EmptyState
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.ProgressBar
import com.diaroutine.ui.components.ScreenHeader
import com.diaroutine.ui.components.SectionHeader
import com.diaroutine.ui.components.SegmentedTabs
import com.diaroutine.ui.components.StatusLabel
import com.diaroutine.ui.icon
import com.diaroutine.ui.screenPadding
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun MedsRoute(container: AppContainer) {
    val viewModel = viewModel { MedsViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MedsScreen(state, viewModel::onAction, container.timeZone)
}

@Composable
fun MedsScreen(state: MedsUiState, onAction: (MedsAction) -> Unit, zone: TimeZone) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var medicationEditor by remember { mutableStateOf<MedicationEditor?>(null) }
    var appointmentEditor by remember { mutableStateOf<AppointmentEditor?>(null) }
    var supplyFor by remember { mutableStateOf<Medication?>(null) }
    var stopping by remember { mutableStateOf<Medication?>(null) }
    var deleting by remember { mutableStateOf<Appointment?>(null) }
    val use24Hour = LocalPlatform.current.uses24HourClock

    LazyColumn(
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Medications & care",
                trailing = {
                    CircleIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = if (tab == 0) "Add medication" else "Add appointment",
                        onClick = {
                            if (tab == 0) medicationEditor = MedicationEditor(null) else appointmentEditor = AppointmentEditor(null)
                        },
                    )
                },
            )
        }
        item(key = "tabs") {
            SegmentedTabs(listOf("Medications", "Appointments"), tab, onSelect = { tab = it })
        }

        if (tab == 0) {
            if (state.medications.isEmpty()) {
                item(key = "meds-empty") {
                    EmptyState(
                        icon = Icons.Rounded.Medication,
                        title = "No medications yet",
                        message = "Add each medication with its dose and times as prescribed. DiaRoutine reminds you when each dose is due.",
                        actionLabel = "Add medication",
                        onAction = { medicationEditor = MedicationEditor(null) },
                    )
                }
            } else {
                item(key = "meds-summary") { MedsSummary(state) }
                items(state.medications, key = { "med-${it.medication.id}" }) { item ->
                    MedicationCard(
                        item = item,
                        use24Hour = use24Hour,
                        zone = zone,
                        onEdit = { medicationEditor = MedicationEditor(item.medication) },
                        onSupply = { supplyFor = item.medication },
                        onStop = { stopping = item.medication },
                        onTakeAsNeeded = { onAction(MedsAction.TakeAsNeeded(item.medication.id)) },
                    )
                }
            }
            item(key = "meds-note") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(horizontal = Spacing.xs)) {
                    Icon(Icons.Rounded.Info, null, tint = DiaTheme.colors.textMuted, modifier = Modifier.size(18.dp))
                    Text(
                        "DiaRoutine reminds you of your schedule. It doesn't give medical advice — follow your prescriber's instructions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DiaTheme.colors.textMuted,
                    )
                }
            }
        } else {
            if (state.upcoming.isEmpty() && state.past.isEmpty()) {
                item(key = "visits-empty") {
                    EmptyState(
                        icon = Icons.Rounded.Event,
                        title = "No appointments",
                        message = "Add check-ups, lab tests and eye or foot exams so they appear on your timeline with a reminder.",
                        actionLabel = "Add appointment",
                        onAction = { appointmentEditor = AppointmentEditor(null) },
                    )
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming-header") { SectionHeader("Upcoming") }
                items(state.upcoming, key = { "visit-${it.id}" }) { appointment ->
                    AppointmentCard(
                        appointment = appointment,
                        state = state,
                        use24Hour = use24Hour,
                        onEdit = { appointmentEditor = AppointmentEditor(appointment) },
                        onDelete = { deleting = appointment },
                    )
                }
            }
            if (state.past.isNotEmpty()) {
                item(key = "past-header") { SectionHeader("Past") }
                items(state.past, key = { "past-${it.id}" }) { appointment ->
                    AppointmentCard(
                        appointment = appointment,
                        state = state,
                        use24Hour = use24Hour,
                        onEdit = { appointmentEditor = AppointmentEditor(appointment) },
                        onDelete = { deleting = appointment },
                        isPast = true,
                    )
                }
            }
        }
    }

    medicationEditor?.let { editor ->
        MedicationEditorSheet(
            initial = editor.medication,
            today = state.now.date,
            onSave = { onAction(MedsAction.SaveMedication(it)) },
            onDismiss = { medicationEditor = null },
        )
    }
    appointmentEditor?.let { editor ->
        AppointmentEditorSheet(
            initial = editor.appointment,
            now = state.now,
            onSave = { onAction(MedsAction.SaveAppointment(it)) },
            onDismiss = { appointmentEditor = null },
        )
    }
    supplyFor?.let { med ->
        SupplyDialog(med, onDismiss = { supplyFor = null }, onSave = { onAction(MedsAction.SetSupply(med.id, it)) })
    }
    stopping?.let { med ->
        ConfirmDialog(
            title = "Stop ${med.name}?",
            message = "It comes off your routine and reminders from today. Doses you've already logged stay in your history.",
            confirmLabel = "Stop medication",
            onConfirm = { onAction(MedsAction.StopMedication(med.id)) },
            onDismiss = { stopping = null },
        )
    }
    deleting?.let { appointment ->
        ConfirmDialog(
            title = "Delete appointment?",
            message = "${appointment.title} on ${Fmt.dateMedium(appointment.at.date)} will be removed.",
            confirmLabel = "Delete",
            onConfirm = { onAction(MedsAction.DeleteAppointment(appointment.id)) },
            onDismiss = { deleting = null },
        )
    }
}

/** Which medication the editor sheet is open for; null inside means a new one. */
private data class MedicationEditor(val medication: Medication?)

private data class AppointmentEditor(val appointment: Appointment?)

@Composable
private fun MedsSummary(state: MedsUiState) {
    val colors = DiaTheme.colors
    DiaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconBubble(Icons.Rounded.Medication)
            Column(Modifier.weight(1f)) {
                Text("Today's doses", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(
                    if (state.dosesToday == 0) "Nothing scheduled today" else "${state.takenToday} of ${state.dosesToday} taken",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CountPill("Scheduled", state.scheduledCount, Modifier.weight(1f))
            CountPill("As-needed", state.asNeededCount, Modifier.weight(1f), dot = colors.primarySoft)
        }
        if (state.dosesToday > 0) {
            ProgressBar(
                progress = state.takenToday.toFloat() / state.dosesToday,
                color = if (state.takenToday >= state.dosesToday) colors.sage else colors.primary,
            )
        }
    }
}

@Composable
private fun MedicationCard(
    item: MedicationItem,
    use24Hour: Boolean,
    zone: TimeZone,
    onEdit: () -> Unit,
    onSupply: () -> Unit,
    onStop: () -> Unit,
    onTakeAsNeeded: () -> Unit,
) {
    val colors = DiaTheme.colors
    val med = item.medication
    DiaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconBubble(med.form.icon, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.titleLarge, color = colors.text)
                Text(med.doseDescription, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            OverflowMenu(
                name = med.name,
                actions = buildList {
                    add(MenuAction("Edit", Icons.Rounded.Edit, onEdit))
                    add(MenuAction("Update supply", Icons.Rounded.Inventory2, onSupply))
                    add(MenuAction("Stop medication", Icons.Rounded.StopCircle, onStop, destructive = true))
                },
            )
        }

        if (med.asNeeded) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CountPill("Taken today", item.takenToday)
                item.lastTakenAt?.let {
                    Text(
                        "Last at ${Fmt.time(it.toLocalDateTime(zone).time, use24Hour)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
            PillButton("Log a dose now", onTakeAsNeeded, Modifier.fillMaxWidth(), style = PillStyle.Raised)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                med.times.forEach { time ->
                    StatusLabel(Fmt.time(time, use24Hour), Icons.Rounded.Schedule, colors.raised, colors.text)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ProgressBar(
                    progress = if (item.dosesToday == 0) 0f else item.takenToday.toFloat() / item.dosesToday,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    color = if (item.takenToday >= item.dosesToday) colors.sage else colors.primary,
                )
                Text(
                    "${item.takenToday}/${item.dosesToday} today",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        }

        if (med.instructions.isNotBlank()) {
            Text(med.instructions, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }

        med.supplyRemaining?.let { supply ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(DiaShapes.small).background(if (med.needsRefill) colors.coralSoft else colors.raised).padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(Icons.Rounded.Inventory2, null, tint = if (med.needsRefill) colors.coralStrong else colors.textMuted, modifier = Modifier.size(18.dp))
                Text(
                    "${med.form.amount(supply)} left" + (med.daysOfSupply?.let { " · about ${if (it == 1) "1 day" else "$it days"}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                if (med.needsRefill) Text("Refill soon", style = MaterialTheme.typography.labelMedium, color = colors.coralStrong)
            }
        }
    }
}

@Composable
private fun AppointmentCard(
    appointment: Appointment,
    state: MedsUiState,
    use24Hour: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isPast: Boolean = false,
) {
    val colors = DiaTheme.colors
    val date = appointment.at.date
    DiaCard(Modifier.fillMaxWidth(), color = if (isPast) colors.card.copy(alpha = 0.55f) else colors.card) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .width(58.dp)
                    .clip(DiaShapes.small)
                    .background(colors.raised)
                    .padding(vertical = Spacing.sm)
                    .clearAndSetSemantics { },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Fmt.monthShort(date).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.primaryStrong)
                Text(date.day.toString(), style = MaterialTheme.typography.headlineSmall, color = colors.text)
                Text(Fmt.weekdayShort(date), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(appointment.title, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(
                    "${Fmt.relativeDate(date, state.now.date)} at ${Fmt.time(appointment.at.time, use24Hour)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text,
                )
                if (appointment.withWhom.isNotBlank()) {
                    Text(appointment.withWhom, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
            OverflowMenu(
                name = appointment.title,
                actions = listOf(
                    MenuAction("Edit", Icons.Rounded.Edit, onEdit),
                    MenuAction("Delete", Icons.Rounded.Delete, onDelete, destructive = true),
                ),
            )
        }
        if (appointment.notes.isNotBlank()) {
            Text(appointment.notes, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (!isPast && appointment.remindBeforeMinutes > 0) {
            StatusLabel("Reminder ${reminderLabel(appointment.remindBeforeMinutes)}", Icons.Rounded.Alarm, colors.raised, colors.primaryStrong)
        }
    }
}

private data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit, val destructive: Boolean = false)

@Composable
private fun OverflowMenu(name: String, actions: List<MenuAction>) {
    val colors = DiaTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(Icons.Rounded.MoreVert, "More options for $name", onClick = { open = true }, size = 44.dp)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.raised) {
            actions.forEach { action ->
                val tint = if (action.destructive) colors.coralStrong else colors.text
                DropdownMenuItem(
                    text = { Text(action.label, color = tint) },
                    leadingIcon = { Icon(action.icon, null, tint = tint) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
