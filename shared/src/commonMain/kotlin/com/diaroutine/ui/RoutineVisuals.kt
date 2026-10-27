package com.diaroutine.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.diaroutine.domain.EntryState
import com.diaroutine.domain.GlucoseLevel
import com.diaroutine.domain.TimelineEntry
import com.diaroutine.domain.model.DoseForm
import com.diaroutine.domain.model.MealContext
import com.diaroutine.domain.model.Profile
import com.diaroutine.ui.components.StatusLabel
import com.diaroutine.ui.theme.DiaTheme
import kotlinx.datetime.LocalTime

val DoseForm.icon: ImageVector
    get() = when (this) {
        DoseForm.Injection -> Icons.Rounded.Vaccines
        DoseForm.Liquid -> Icons.Rounded.WaterDrop
        DoseForm.Inhaler -> Icons.Rounded.Air
        DoseForm.Tablet, DoseForm.Capsule, DoseForm.Other -> Icons.Rounded.Medication
    }

val TimelineEntry.icon: ImageVector
    get() = when (this) {
        is TimelineEntry.Dose -> medication.form.icon
        is TimelineEntry.GlucoseCheck -> Icons.Rounded.Bloodtype
        is TimelineEntry.Walk -> Icons.AutoMirrored.Rounded.DirectionsWalk
        is TimelineEntry.Visit -> Icons.Rounded.Event
    }

val TimelineEntry.title: String
    get() = when (this) {
        is TimelineEntry.Dose -> medication.name
        is TimelineEntry.GlucoseCheck -> if (plan != null) "Glucose check" else "Glucose reading"
        is TimelineEntry.Walk -> "Walk"
        is TimelineEntry.Visit -> appointment.title
    }

val EntryState.label: String
    get() = when (this) {
        EntryState.Done -> "Done"
        EntryState.Skipped -> "Skipped"
        EntryState.Upcoming -> "Upcoming"
        EntryState.DueNow -> "Due now"
        EntryState.Overdue -> "Overdue"
        EntryState.Missed -> "Missed"
        EntryState.Passed -> "Passed"
    }

private val EntryState.statusIcon: ImageVector
    get() = when (this) {
        EntryState.Done -> Icons.Rounded.Check
        EntryState.Skipped -> Icons.Rounded.Remove
        EntryState.Upcoming -> Icons.Rounded.Schedule
        EntryState.DueNow -> Icons.Rounded.NotificationsActive
        EntryState.Overdue -> Icons.Rounded.ErrorOutline
        EntryState.Missed -> Icons.Rounded.Close
        EntryState.Passed -> Icons.Rounded.EventAvailable
    }

/** The (container, content) colors for a state. */
@Composable
fun EntryState.colors(): Pair<Color, Color> {
    val c = DiaTheme.colors
    return when (this) {
        EntryState.Done -> c.sageSoft to c.sage
        EntryState.DueNow -> c.amberSoft to c.amber
        EntryState.Overdue, EntryState.Missed -> c.coralSoft to c.coralStrong
        EntryState.Upcoming -> c.raised to c.primaryStrong
        EntryState.Skipped, EntryState.Passed -> c.raised to c.textMuted
    }
}

@Composable
fun EntryStateLabel(state: EntryState, modifier: Modifier = Modifier) {
    val (container, content) = state.colors()
    StatusLabel(state.label, state.statusIcon, container, content, modifier)
}

@Composable
fun GlucoseLevel.colors(): Pair<Color, Color> {
    val c = DiaTheme.colors
    return when (this) {
        GlucoseLevel.Low -> c.coralSoft to c.coralStrong
        GlucoseLevel.InRange -> c.sageSoft to c.sage
        GlucoseLevel.High -> c.amberSoft to c.amber
    }
}

@Composable
fun GlucoseLevelLabel(level: GlucoseLevel, modifier: Modifier = Modifier) {
    val (container, content) = level.colors()
    val icon = when (level) {
        GlucoseLevel.Low -> Icons.Rounded.ArrowDownward
        GlucoseLevel.InRange -> Icons.Rounded.Check
        GlucoseLevel.High -> Icons.Rounded.ArrowUpward
    }
    StatusLabel(level.label, icon, container, content, modifier)
}

/** A y-axis for glucose charts that keeps the target band and every value in view without wasted space. */
fun glucoseAxis(values: List<Float?>, profile: Profile): ClosedFloatingPointRange<Float> {
    val present = values.filterNotNull()
    val low = minOf(profile.targetLowMgDl - 20f, (present.minOrNull() ?: Float.MAX_VALUE) - 15f).coerceAtLeast(20f)
    val high = maxOf(profile.targetHighMgDl + 30f, (present.maxOrNull() ?: 0f) + 15f)
    return low..high
}

/** A sensible default context for a reading taken at [time]. */
fun suggestedMealContext(time: LocalTime): MealContext = when {
    time.hour < 10 -> MealContext.Fasting
    time.hour >= 21 -> MealContext.Bedtime
    else -> MealContext.Other
}
