package com.diaroutine.features.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diaroutine.app.AppContainer
import com.diaroutine.core.format.Fmt
import com.diaroutine.domain.Glucose
import com.diaroutine.domain.GlucoseSummary
import com.diaroutine.domain.model.GlucoseReading
import com.diaroutine.ui.GlucoseLevelLabel
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.colors
import com.diaroutine.ui.glucoseAxis
import com.diaroutine.ui.components.CircleIconButton
import com.diaroutine.ui.components.ConfirmDialog
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.ProgressBar
import com.diaroutine.ui.components.ProgressRing
import com.diaroutine.ui.components.ScreenHeader
import com.diaroutine.ui.components.SectionHeader
import com.diaroutine.ui.components.StatTile
import com.diaroutine.ui.components.TickDial
import com.diaroutine.ui.components.TrendChart
import com.diaroutine.ui.screenPadding
import com.diaroutine.ui.suggestedMealContext
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max

private const val DIAL_MIN_MG_DL = 40f
private const val DIAL_MAX_MG_DL = 300f

@Composable
fun TrackRoute(container: AppContainer) {
    val viewModel = viewModel { TrackViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrackScreen(state, viewModel::onAction, container.timeZone)
}

@Composable
fun TrackScreen(state: TrackUiState, onAction: (TrackAction) -> Unit, zone: TimeZone) {
    val use24Hour = LocalPlatform.current.uses24HourClock
    var glucoseRequest by remember { mutableStateOf<GlucoseSheetRequest?>(null) }
    var editingActivity by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GlucoseReading?>(null) }
    val openGlucose = {
        glucoseRequest = GlucoseSheetRequest(state.now.date, state.now.time, suggestedMealContext(state.now.time))
    }

    LazyColumn(
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Track",
                trailing = { CircleIconButton(Icons.Rounded.Add, "Log blood glucose", onClick = openGlucose) },
            )
        }
        item(key = "glucose") { GlucoseCard(state, use24Hour, zone, onLog = openGlucose) }
        state.week?.let { week ->
            if (week.readings > 0) {
                item(key = "week") { WeekCard(state, week) }
            }
        }
        item(key = "water") { WaterCard(state, onAction, Modifier.fillMaxWidth()) }
        item(key = "activity") { ActivityCard(state, onEdit = { editingActivity = true }) }
        if (state.recent.isNotEmpty()) {
            item(key = "recent-header") { SectionHeader("Recent readings") }
            items(state.recent, key = { "reading-${it.id}" }) { reading ->
                ReadingRow(reading, state, use24Hour, zone, onDelete = { deleting = reading })
            }
        }
    }

    glucoseRequest?.let { request ->
        GlucoseLogSheet(
            request = request,
            profile = state.profile,
            now = state.now,
            zone = zone,
            onSave = { onAction(TrackAction.LogGlucose(it)) },
            onDismiss = { glucoseRequest = null },
        )
    }
    if (editingActivity) {
        ActivitySheet(
            habit = state.habit,
            profile = state.profile,
            onSave = { steps, minutes -> onAction(TrackAction.SetActivity(steps, minutes)) },
            onDismiss = { editingActivity = false },
        )
    }
    deleting?.let { reading ->
        ConfirmDialog(
            title = "Delete reading?",
            message = "${Glucose.formatWithUnit(reading.mgDl, state.profile.glucoseUnit)} from ${Fmt.dateMedium(reading.at.toLocalDateTime(zone).date)} will be removed from your log.",
            confirmLabel = "Delete",
            onConfirm = { onAction(TrackAction.DeleteReading(reading.id)) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GlucoseCard(state: TrackUiState, use24Hour: Boolean, zone: TimeZone, onLog: () -> Unit) {
    val colors = DiaTheme.colors
    val profile = state.profile
    val unit = profile.glucoseUnit
    val latest = state.latest
    val level = latest?.let { Glucose.level(it.mgDl, profile) }
    DiaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconBubble(Icons.Rounded.Bloodtype, tint = colors.coralStrong)
            Column(Modifier.weight(1f)) {
                Text("Blood glucose", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(
                    latest?.let {
                        val at = it.at.toLocalDateTime(zone)
                        "${Fmt.relativeDate(at.date, state.now.date)} at ${Fmt.time(at.time, use24Hour)} · ${it.context.label}"
                    } ?: "No readings yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        TickDial(
            value = latest?.mgDl?.toFloat(),
            rangeMin = DIAL_MIN_MG_DL,
            rangeMax = DIAL_MAX_MG_DL,
            targetLow = profile.targetLowMgDl.toFloat(),
            targetHigh = profile.targetHighMgDl.toFloat(),
            description = latest?.let { "Latest reading ${Glucose.formatWithUnit(it.mgDl, unit)}, ${level?.label}. Target ${Glucose.targetLabel(profile)}" }
                ?: "No readings yet. Target ${Glucose.targetLabel(profile)}",
            marker = level?.colors()?.second ?: colors.coral,
            modifier = Modifier.fillMaxWidth(0.72f).align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    latest?.let { Glucose.format(it.mgDl, unit) } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.text,
                )
                Text(unit.label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                if (level != null) GlucoseLevelLabel(level, Modifier.padding(top = Spacing.xs))
            }
        }
        Text(
            "Your target: ${Glucose.targetLabel(profile)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PillButton(if (latest == null) "Log your first reading" else "Log a reading", onLog, Modifier.fillMaxWidth())
    }
}

@Composable
private fun WeekCard(state: TrackUiState, week: GlucoseSummary) {
    val colors = DiaTheme.colors
    val profile = state.profile
    val unit = profile.glucoseUnit
    val values = week.daily.map { it.averageMgDl?.toFloat() }
    val axis = glucoseAxis(values, profile)
    DiaCard(Modifier.fillMaxWidth()) {
        Text("Last 7 days", style = MaterialTheme.typography.titleMedium, color = colors.text)
        TrendChart(
            values = values,
            labels = week.daily.map { Fmt.weekdayShort(it.date).take(1) },
            yMin = axis.start,
            yMax = axis.endInclusive,
            band = profile.targetLowMgDl.toFloat()..profile.targetHighMgDl.toFloat(),
            bandLabels = Glucose.format(profile.targetLowMgDl, unit) to Glucose.format(profile.targetHighMgDl, unit),
            description = "Daily average glucose for the last 7 days, " +
                week.daily.joinToString { d -> "${Fmt.weekday(d.date)} ${d.averageMgDl?.let { Glucose.format(it, unit) } ?: "no readings"}" },
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(week.averageMgDl?.let { Glucose.format(it, unit) } ?: "—", "Avg ${unit.label}", Modifier.weight(1f).fillMaxHeight())
            StatTile(week.inRange?.let(Fmt::percent) ?: "—", "In range", Modifier.weight(1f).fillMaxHeight())
            StatTile(week.readings.toString(), "Readings", Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun WaterCard(state: TrackUiState, onAction: (TrackAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = DiaTheme.colors
    val glasses = state.habit.waterGlasses
    val goal = state.profile.waterGoalGlasses
    DiaCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconBubble(Icons.Rounded.LocalDrink, tint = colors.primaryStrong)
            Column(Modifier.weight(1f)) {
                Text("Water", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(
                    if (glasses >= goal) "Goal reached — $glasses of $goal glasses" else "$glasses of $goal glasses today",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(max(goal, glasses)) { i ->
                Icon(
                    Icons.Rounded.WaterDrop,
                    contentDescription = null,
                    tint = if (i < glasses) colors.primary else colors.outline,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Remove a glass of water",
                onClick = { onAction(TrackAction.AddWater(-1)) },
                enabled = glasses > 0,
            )
            Text(
                "$glasses",
                style = MaterialTheme.typography.displaySmall,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { contentDescription = "$glasses glasses" },
            )
            CircleIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Add a glass of water",
                onClick = { onAction(TrackAction.AddWater(1)) },
                container = colors.primary,
                tint = colors.onPrimary,
            )
        }
    }
}

@Composable
private fun ActivityCard(state: TrackUiState, onEdit: () -> Unit) {
    val colors = DiaTheme.colors
    val habit = state.habit
    val profile = state.profile
    DiaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ProgressRing(
                progress = habit.steps.toFloat() / profile.stepGoal.coerceAtLeast(1),
                modifier = Modifier.size(108.dp).clearAndSetSemantics {
                    contentDescription = "${Fmt.grouped(habit.steps)} of ${Fmt.grouped(profile.stepGoal)} steps"
                },
                strokeWidth = 11.dp,
                color = if (habit.steps >= profile.stepGoal) colors.sage else colors.primary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.compact(habit.steps), style = MaterialTheme.typography.titleLarge, color = colors.text)
                    Text("steps", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Activity", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text("Goal ${Fmt.grouped(profile.stepGoal)} steps", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Text(
                    "${habit.activeMinutes} of ${profile.walkGoalMinutes} active min",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                ProgressBar(
                    progress = habit.activeMinutes.toFloat() / profile.walkGoalMinutes.coerceAtLeast(1),
                    color = if (habit.activeMinutes >= profile.walkGoalMinutes) colors.sage else colors.primary,
                )
            }
        }
        PillButton("Update activity", onEdit, Modifier.fillMaxWidth(), style = PillStyle.Raised)
    }
}

@Composable
private fun ReadingRow(reading: GlucoseReading, state: TrackUiState, use24Hour: Boolean, zone: TimeZone, onDelete: () -> Unit) {
    val colors = DiaTheme.colors
    val unit = state.profile.glucoseUnit
    val at = reading.at.toLocalDateTime(zone)
    Row(
        modifier = Modifier.fillMaxWidth().clip(DiaShapes.inner).background(colors.card).padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(Glucose.formatWithUnit(reading.mgDl, unit), style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(
                "${reading.context.label} · ${Fmt.relativeDate(at.date, state.now.date)} ${Fmt.time(at.time, use24Hour)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            if (reading.note.isNotBlank()) Text(reading.note, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        GlucoseLevelLabel(Glucose.level(reading.mgDl, state.profile))
        CircleIconButton(
            icon = Icons.Rounded.Delete,
            contentDescription = "Delete reading ${Glucose.formatWithUnit(reading.mgDl, unit)} at ${Fmt.time(at.time, use24Hour)}",
            onClick = onDelete,
            container = colors.card,
            tint = colors.textMuted,
            size = 44.dp,
        )
    }
}
