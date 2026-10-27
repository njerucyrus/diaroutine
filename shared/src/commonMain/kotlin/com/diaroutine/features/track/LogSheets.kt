package com.diaroutine.features.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.diaroutine.core.format.Fmt
import com.diaroutine.core.time.at
import com.diaroutine.core.time.instantIn
import com.diaroutine.data.HabitRepository
import com.diaroutine.domain.Glucose
import com.diaroutine.domain.GlucoseLevel
import com.diaroutine.domain.model.DailyHabit
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.domain.model.GlucoseUnit
import com.diaroutine.domain.model.MealContext
import com.diaroutine.domain.model.Profile
import com.diaroutine.ui.GlucoseLevelLabel
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.ChoiceChip
import com.diaroutine.ui.components.DecimalKeyboard
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.DiaSheet
import com.diaroutine.ui.components.FormField
import com.diaroutine.ui.components.NumberKeyboard
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.TimePickerDialog
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** What the glucose sheet opens with. */
data class GlucoseSheetRequest(val date: LocalDate, val time: LocalTime, val context: MealContext)

@Composable
fun GlucoseLogSheet(
    request: GlucoseSheetRequest,
    profile: Profile,
    now: LocalDateTime,
    zone: TimeZone,
    onSave: (GlucoseReading) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DiaTheme.colors
    val use24Hour = LocalPlatform.current.uses24HourClock
    val unit = profile.glucoseUnit
    var valueText by remember { mutableStateOf("") }
    var context by remember { mutableStateOf(request.context) }
    var time by remember { mutableStateOf(request.time) }
    var note by remember { mutableStateOf("") }
    var pickingTime by remember { mutableStateOf(false) }

    val mgDl = Glucose.parse(valueText, unit)
    val valueError = if (valueText.isNotBlank() && mgDl == null) "Enter a value between ${Glucose.validRangeLabel(unit)}" else null
    val inFuture = request.date.at(time) > now
    val level = mgDl?.let { Glucose.level(it, profile) }

    DiaSheet(title = "Log blood glucose", onDismiss = onDismiss) { dismiss ->
        FormField(
            label = "Reading",
            value = valueText,
            onValueChange = { valueText = it.take(5) },
            placeholder = if (unit == GlucoseUnit.MgDl) "e.g. 112" else "e.g. 6.2",
            suffix = unit.label,
            error = valueError,
            supporting = "Your target is ${Glucose.targetLabel(profile)}",
            keyboardOptions = if (unit == GlucoseUnit.MgDl) NumberKeyboard else DecimalKeyboard,
        )

        if (level != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GlucoseLevelLabel(level)
                Text(
                    text = when (level) {
                        GlucoseLevel.InRange -> "Within your target"
                        GlucoseLevel.Low -> "Below your target"
                        GlucoseLevel.High -> "Above your target"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            if (level == GlucoseLevel.Low) {
                DiaCard(color = colors.coralSoft, contentPadding = PaddingValues(Spacing.md)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(Icons.Rounded.Info, null, tint = colors.coralStrong)
                        Text(
                            "Follow the plan your care team gave you for treating a low, and check again as they advised.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text,
                        )
                    }
                }
            }
        }

        Text("When", style = MaterialTheme.typography.titleSmall, color = colors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MealContext.entries.forEach { option ->
                ChoiceChip(option.label, selected = option == context, onClick = { context = option })
            }
        }

        PillButton(
            text = "${Fmt.relativeDate(request.date, now.date)} at ${Fmt.time(time, use24Hour)}",
            onClick = { pickingTime = true },
            style = PillStyle.Raised,
            leadingIcon = Icons.Rounded.Schedule,
            modifier = Modifier.fillMaxWidth(),
        )
        if (inFuture) {
            Text("That time hasn't happened yet.", style = MaterialTheme.typography.bodySmall, color = colors.coralStrong)
        }

        FormField(label = "Note (optional)", value = note, onValueChange = { note = it.take(200) }, singleLine = false)

        PillButton(
            text = "Save reading",
            onClick = {
                val value = mgDl ?: return@PillButton
                onSave(GlucoseReading(at = request.date.at(time).instantIn(zone), mgDl = value, context = context, note = note))
                dismiss()
            },
            enabled = mgDl != null && !inFuture,
            leadingIcon = Icons.Rounded.Check,
            trailingIcon = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickingTime) {
        TimePickerDialog(
            initial = time,
            use24Hour = use24Hour,
            onDismiss = { pickingTime = false },
            onConfirm = { time = it; pickingTime = false },
            title = "Time of reading",
        )
    }
}

/** Sets the day's step and active-minute totals, with quick additions for a walk just finished. */
@Composable
fun ActivitySheet(
    habit: DailyHabit,
    profile: Profile,
    onSave: (steps: Int, minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DiaTheme.colors
    var stepsText by remember { mutableStateOf(habit.steps.takeIf { it > 0 }?.toString().orEmpty()) }
    var minutesText by remember { mutableStateOf(habit.activeMinutes.takeIf { it > 0 }?.toString().orEmpty()) }

    val steps = stepsText.toIntOrNull() ?: if (stepsText.isBlank()) 0 else null
    val minutes = minutesText.toIntOrNull() ?: if (minutesText.isBlank()) 0 else null
    val stepsError = if (steps == null || steps > HabitRepository.MAX_STEPS) "Enter a whole number of steps" else null
    val minutesError = if (minutes == null || minutes > HabitRepository.MAX_MINUTES) "Enter whole minutes" else null

    DiaSheet(title = "Activity on ${Fmt.dateMedium(habit.date)}", onDismiss = onDismiss) { dismiss ->
        Text(
            "Enter today's totals from your phone or watch, or add a walk you just finished.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FormField(
                label = "Steps",
                value = stepsText,
                onValueChange = { stepsText = it.filter(Char::isDigit).take(6) },
                suffix = "of ${Fmt.grouped(profile.stepGoal)}",
                error = stepsError,
                keyboardOptions = NumberKeyboard,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(500, 1000, 2000).forEach { add ->
                    ChoiceChip("+${Fmt.grouped(add)}", selected = false, onClick = { stepsText = ((steps ?: 0) + add).toString() })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FormField(
                label = "Active minutes",
                value = minutesText,
                onValueChange = { minutesText = it.filter(Char::isDigit).take(4) },
                suffix = "of ${profile.walkGoalMinutes} min",
                error = minutesError,
                keyboardOptions = NumberKeyboard,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(10, 20, 30).forEach { add ->
                    ChoiceChip("+$add min", selected = false, onClick = { minutesText = ((minutes ?: 0) + add).toString() })
                }
            }
        }
        PillButton(
            text = "Save activity",
            onClick = {
                onSave(steps ?: return@PillButton, minutes ?: return@PillButton)
                dismiss()
            },
            enabled = stepsError == null && minutesError == null,
            leadingIcon = Icons.Rounded.Check,
            trailingIcon = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
