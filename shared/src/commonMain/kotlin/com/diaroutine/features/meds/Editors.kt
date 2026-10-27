package com.diaroutine.features.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.diaroutine.core.format.Fmt
import com.diaroutine.core.time.at
import com.diaroutine.core.time.plusDays
import com.diaroutine.domain.model.Appointment
import com.diaroutine.domain.model.DoseForm
import com.diaroutine.domain.model.Medication
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.ChoiceChip
import com.diaroutine.ui.components.DatePickerDialog
import com.diaroutine.ui.components.DiaSheet
import com.diaroutine.ui.components.FormField
import com.diaroutine.ui.components.NumberKeyboard
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.SegmentedTabs
import com.diaroutine.ui.components.Stepper
import com.diaroutine.ui.components.TimePickerDialog
import com.diaroutine.ui.icon
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

private val TextKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)

@Composable
fun MedicationEditorSheet(
    initial: Medication?,
    today: LocalDate,
    onSave: (Medication) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DiaTheme.colors
    val use24Hour = LocalPlatform.current.uses24HourClock
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var strength by remember { mutableStateOf(initial?.strength.orEmpty()) }
    var form by remember { mutableStateOf(initial?.form ?: DoseForm.Tablet) }
    var amount by remember { mutableIntStateOf(initial?.amountPerDose ?: 1) }
    var asNeeded by remember { mutableStateOf(initial?.asNeeded ?: false) }
    val times = remember { mutableStateListOf<LocalTime>().apply { addAll(initial?.times?.takeIf { it.isNotEmpty() } ?: listOf(LocalTime(8, 0))) } }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    var trackSupply by remember { mutableStateOf(initial?.supplyRemaining != null) }
    var supplyText by remember { mutableStateOf(initial?.supplyRemaining?.toString().orEmpty()) }
    var refillAt by remember { mutableIntStateOf(initial?.refillAt ?: 7) }
    /** The time being edited: its index, or -1 for a new one. */
    var pickingTime by remember { mutableStateOf<Int?>(null) }
    var attempted by remember { mutableStateOf(false) }

    val supply = supplyText.toIntOrNull()
    val nameError = if (name.isBlank()) "Enter the medication's name" else null
    val timesError = if (!asNeeded && times.isEmpty()) "Add at least one time" else null
    val supplyError = if (trackSupply && supply == null) "Enter how many you have on hand" else null
    val valid = nameError == null && timesError == null && supplyError == null

    DiaSheet(title = if (initial == null) "Add medication" else "Edit medication", onDismiss = onDismiss) { dismiss ->
        Text(
            "Enter it exactly as your prescriber wrote it.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        FormField(
            label = "Name",
            value = name,
            onValueChange = { name = it.take(60) },
            placeholder = "e.g. Metformin",
            error = nameError.takeIf { attempted },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        FormField(
            label = "Strength (optional)",
            value = strength,
            onValueChange = { strength = it.take(30) },
            placeholder = "e.g. 500 mg or 10 units",
            keyboardOptions = TextKeyboard,
        )

        Text("Form", style = MaterialTheme.typography.titleSmall, color = colors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DoseForm.entries.forEach { option ->
                ChoiceChip(option.label, selected = option == form, onClick = { form = option }, icon = option.icon)
            }
        }
        Stepper(
            label = "Each dose",
            value = amount,
            onValueChange = { amount = it },
            range = 1..20,
            valueText = { form.amount(it) },
        )

        Text("Schedule", style = MaterialTheme.typography.titleSmall, color = colors.text)
        SegmentedTabs(listOf("At set times", "As needed"), if (asNeeded) 1 else 0, onSelect = { asNeeded = it == 1 })
        if (!asNeeded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                times.sorted().forEach { time ->
                    TimeChip(
                        text = Fmt.time(time, use24Hour),
                        onEdit = { pickingTime = times.indexOf(time) },
                        onRemove = { times.remove(time) },
                    )
                }
                ChoiceChip("Add time", selected = false, onClick = { pickingTime = -1 }, icon = Icons.Rounded.Add)
            }
            timesError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.coralStrong) }
        } else {
            Text(
                "You'll log doses when you take them. They won't count toward your schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }

        FormField(
            label = "Instructions (optional)",
            value = instructions,
            onValueChange = { instructions = it.take(80) },
            placeholder = "e.g. With breakfast",
            keyboardOptions = TextKeyboard,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Track supply", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text("Get a refill reminder before you run out", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Switch(
                checked = trackSupply,
                onCheckedChange = { trackSupply = it },
                colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
            )
        }
        if (trackSupply) {
            FormField(
                label = "On hand",
                value = supplyText,
                onValueChange = { supplyText = it.filter(Char::isDigit).take(4) },
                suffix = form.amount(supply ?: 0).substringAfter(' '),
                error = supplyError.takeIf { attempted },
                keyboardOptions = NumberKeyboard,
            )
            Stepper(
                label = "Remind me at",
                value = refillAt,
                onValueChange = { refillAt = it },
                range = 0..120,
                valueText = { form.amount(it) },
            )
        }

        PillButton(
            text = if (initial == null) "Add medication" else "Save changes",
            onClick = {
                attempted = true
                if (!valid) return@PillButton
                val base = initial ?: Medication(name = "", startDate = today)
                onSave(
                    base.copy(
                        name = name.trim(),
                        strength = strength.trim(),
                        form = form,
                        amountPerDose = amount,
                        times = if (asNeeded) emptyList() else times.distinct().sorted(),
                        asNeeded = asNeeded,
                        instructions = instructions.trim(),
                        supplyRemaining = if (trackSupply) supply else null,
                        refillAt = refillAt,
                    ),
                )
                dismiss()
            },
            leadingIcon = Icons.Rounded.Check,
            trailingIcon = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    pickingTime?.let { index ->
        TimePickerDialog(
            initial = times.getOrNull(index) ?: times.maxOrNull()?.let { LocalTime((it.hour + 4).coerceAtMost(23), it.minute) } ?: LocalTime(8, 0),
            use24Hour = use24Hour,
            onDismiss = { pickingTime = null },
            onConfirm = { picked ->
                if (index >= 0) times.removeAt(index)
                if (picked !in times) times.add(picked)
                pickingTime = null
            },
            title = "Dose time",
        )
    }
}

@Composable
private fun TimeChip(text: String, onEdit: () -> Unit, onRemove: () -> Unit) {
    val colors = DiaTheme.colors
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(DiaShapes.pill)
            .background(colors.primarySoft)
            .border(1.dp, colors.primary, DiaShapes.pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = "Change $text", role = Role.Button, onClick = onEdit)
                .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Schedule, null, tint = colors.primaryStrong, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = colors.text)
        }
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Remove $text",
            tint = colors.textMuted,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(DiaShapes.pill)
                .clickable(role = Role.Button, onClick = onRemove)
                .padding(10.dp)
                .size(18.dp),
        )
    }
}

@Composable
fun SupplyDialog(medication: Medication, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val colors = DiaTheme.colors
    var text by remember { mutableStateOf(medication.supplyRemaining?.toString().orEmpty()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text("Supply of ${medication.name}", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FormField(
                    label = "On hand",
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    suffix = medication.form.amount(value ?: 0).substringAfter(' '),
                    keyboardOptions = NumberKeyboard,
                )
                Text("Just refilled? Add the new pack:", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(30, 60, 90).forEach { add ->
                        ChoiceChip("+$add", selected = false, onClick = { text = ((value ?: 0) + add).toString() })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave); onDismiss() }, enabled = value != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val ReminderChoices = listOf(
    0 to "No reminder",
    60 to "1 hour before",
    3 * 60 to "3 hours before",
    24 * 60 to "1 day before",
    2 * 24 * 60 to "2 days before",
)

@Composable
fun AppointmentEditorSheet(
    initial: Appointment?,
    now: LocalDateTime,
    onSave: (Appointment) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DiaTheme.colors
    val use24Hour = LocalPlatform.current.uses24HourClock
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var withWhom by remember { mutableStateOf(initial?.withWhom.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var date by remember { mutableStateOf(initial?.at?.date ?: now.date.plusDays(1)) }
    var time by remember { mutableStateOf(initial?.at?.time ?: LocalTime(9, 0)) }
    var remind by remember { mutableIntStateOf(initial?.remindBeforeMinutes ?: 24 * 60) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }

    val titleError = if (title.isBlank()) "Enter what the appointment is for" else null
    val inPast = initial == null && date.at(time) < now

    DiaSheet(title = if (initial == null) "Add appointment" else "Edit appointment", onDismiss = onDismiss) { dismiss ->
        FormField(
            label = "What",
            value = title,
            onValueChange = { title = it.take(60) },
            placeholder = "e.g. Eye exam or HbA1c blood test",
            error = titleError.takeIf { attempted },
            keyboardOptions = TextKeyboard,
        )
        FormField(
            label = "With (optional)",
            value = withWhom,
            onValueChange = { withWhom = it.take(60) },
            placeholder = "Doctor, clinic or lab",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PillButton(
                text = Fmt.dateMedium(date),
                onClick = { pickingDate = true },
                style = PillStyle.Raised,
                leadingIcon = Icons.Rounded.CalendarMonth,
                trailingIcon = null,
                modifier = Modifier.weight(1.2f),
            )
            PillButton(
                text = Fmt.time(time, use24Hour),
                onClick = { pickingTime = true },
                style = PillStyle.Raised,
                leadingIcon = Icons.Rounded.Schedule,
                trailingIcon = null,
                modifier = Modifier.weight(1f),
            )
        }
        if (inPast) Text("That's in the past — check the date.", style = MaterialTheme.typography.bodySmall, color = colors.coralStrong)

        Text("Remind me", style = MaterialTheme.typography.titleSmall, color = colors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ReminderChoices.forEach { (minutes, label) ->
                ChoiceChip(label, selected = remind == minutes, onClick = { remind = minutes })
            }
        }
        FormField(
            label = "Notes (optional)",
            value = notes,
            onValueChange = { notes = it.take(300) },
            placeholder = "Questions to ask, what to bring",
            singleLine = false,
        )
        PillButton(
            text = if (initial == null) "Add appointment" else "Save changes",
            onClick = {
                attempted = true
                if (titleError != null) return@PillButton
                val base = initial ?: Appointment(title = "", at = date.at(time))
                onSave(base.copy(title = title.trim(), withWhom = withWhom.trim(), at = date.at(time), notes = notes.trim(), remindBeforeMinutes = remind))
                dismiss()
            },
            leadingIcon = Icons.Rounded.Check,
            trailingIcon = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickingDate) {
        DatePickerDialog(initial = date, onDismiss = { pickingDate = false }, onConfirm = { date = it; pickingDate = false })
    }
    if (pickingTime) {
        TimePickerDialog(
            initial = time,
            use24Hour = use24Hour,
            onDismiss = { pickingTime = false },
            onConfirm = { time = it; pickingTime = false },
            title = "Appointment time",
        )
    }
}

internal fun reminderLabel(minutes: Int): String = ReminderChoices.firstOrNull { it.first == minutes }?.second
    ?: if (minutes % 60 == 0) "${minutes / 60} hours before" else "$minutes minutes before"
