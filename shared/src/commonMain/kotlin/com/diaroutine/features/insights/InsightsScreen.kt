package com.diaroutine.features.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.InsertChart
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diaroutine.app.AppContainer
import com.diaroutine.core.format.Fmt
import com.diaroutine.domain.Glucose
import com.diaroutine.domain.WeeklyFigures
import com.diaroutine.ui.components.BarDatum
import com.diaroutine.ui.components.CircleIconButton
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.EmptyState
import com.diaroutine.ui.components.HatchedBarChart
import com.diaroutine.ui.components.NeedleGauge
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.ProgressBar
import com.diaroutine.ui.components.ScreenHeader
import com.diaroutine.ui.components.SegmentedTabs
import com.diaroutine.ui.components.StatTile
import com.diaroutine.ui.components.TrendChart
import com.diaroutine.ui.glucoseAxis
import com.diaroutine.ui.screenPadding
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.datetime.LocalDate

@Composable
fun InsightsRoute(container: AppContainer) {
    val viewModel = viewModel { InsightsViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsScreen(state, onShare = viewModel::shareSummary)
}

@Composable
fun InsightsScreen(state: InsightsUiState, onShare: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val figures = state.figures
    LazyColumn(
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScreenHeader(
                    title = "Weekly summary",
                    trailing = { CircleIconButton(Icons.Rounded.Share, "Share weekly summary", onClick = onShare) },
                )
                if (figures != null) {
                    Text(Fmt.dateRange(figures.from, figures.to), style = MaterialTheme.typography.bodyMedium, color = DiaTheme.colors.textMuted)
                }
            }
        }
        item(key = "tabs") { SegmentedTabs(listOf("Medication", "Glucose", "Habits"), tab, onSelect = { tab = it }) }
        if (figures != null) {
            when (tab) {
                0 -> medicationSection(state, figures, onShare)
                1 -> glucoseSection(state, figures)
                else -> habitsSection(state, figures)
            }
        }
    }
}

private fun LazyListScope.medicationSection(state: InsightsUiState, figures: WeeklyFigures, onShare: () -> Unit) {
    val adherence = figures.adherence
    if (adherence == null) {
        item(key = "meds-empty") {
            EmptyState(
                icon = Icons.Rounded.InsertChart,
                title = if (state.hasScheduledMedication) "No doses due yet" else "No scheduled medication",
                message = "Once scheduled doses come due, you'll see how consistently you've taken them here.",
            )
        }
        return
    }
    item(key = "gauge") {
        val colors = DiaTheme.colors
        val scheduled = figures.days.sumOf { it.scheduled }
        val taken = figures.days.sumOf { it.taken }
        DiaCard(Modifier.fillMaxWidth()) {
            Text("Medication taken", style = MaterialTheme.typography.titleLarge, color = colors.text)
            NeedleGauge(
                progress = adherence,
                description = "${Fmt.percent(adherence)} of doses taken in the past 7 days",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            Text(
                "Past 7 days: ${Fmt.percent(adherence)}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "$taken of $scheduled doses" + (figures.previousAdherence?.let { " · week before ${Fmt.percent(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val caregiver = state.profile.caregiver
            PillButton(
                text = if (caregiver.enabled && caregiver.name.isNotBlank()) "Share with ${caregiver.name}" else "Share this summary",
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item(key = "bars") {
        val colors = DiaTheme.colors
        DiaCard(Modifier.fillMaxWidth()) {
            Text("Day by day", style = MaterialTheme.typography.titleMedium, color = colors.text)
            HatchedBarChart(
                bars = figures.days.map { day ->
                    BarDatum(
                        label = Fmt.weekdayShort(day.date),
                        value = day.ratio,
                        highlight = day.date == state.today,
                        valueLabel = day.ratio?.let(Fmt::percent),
                    )
                },
                description = "Doses taken each day: " + figures.days.joinToString { d ->
                    "${Fmt.weekday(d.date)} ${d.ratio?.let(Fmt::percent) ?: "nothing scheduled"}"
                },
            )
        }
    }
    item(key = "streaks") {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(days(figures.streaks.current), "Current streak", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Rounded.LocalFireDepartment)
            StatTile(days(figures.streaks.longest), "Longest streak", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Rounded.EmojiEvents)
        }
    }
    if (figures.byMedication.isNotEmpty()) {
        item(key = "by-med-header") {
            Text("By medication", style = MaterialTheme.typography.titleLarge, color = DiaTheme.colors.text)
        }
        items(figures.byMedication, key = { "adh-${it.medication.id}" }) { item ->
            val colors = DiaTheme.colors
            val ratio = item.ratio ?: 0f
            DiaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.medication.name, style = MaterialTheme.typography.titleSmall, color = colors.text)
                        Text("${item.taken} of ${item.scheduled} doses", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Text(Fmt.percent(ratio), style = MaterialTheme.typography.titleMedium, color = colors.text)
                }
                ProgressBar(ratio, color = if (ratio >= 1f) colors.sage else colors.primary)
            }
        }
    }
}

private fun LazyListScope.glucoseSection(state: InsightsUiState, figures: WeeklyFigures) {
    val glucose = figures.glucose
    val profile = state.profile
    val unit = profile.glucoseUnit
    val inRange = glucose.inRange
    if (glucose.readings == 0 || inRange == null) {
        item(key = "glucose-empty") {
            EmptyState(
                icon = Icons.Rounded.Bloodtype,
                title = "No readings this week",
                message = "Log readings on the Track tab to see your average and time in your target range.",
            )
        }
        return
    }
    item(key = "tir") {
        val colors = DiaTheme.colors
        DiaCard(Modifier.fillMaxWidth()) {
            Text("Time in range", style = MaterialTheme.typography.titleLarge, color = colors.text)
            NeedleGauge(
                progress = inRange,
                description = "${Fmt.percent(inRange)} of readings in your target range",
                fill = colors.sage,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            Text(
                "${Fmt.percent(inRange)} of readings in range",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Target ${Glucose.targetLabel(profile)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item(key = "glucose-stats") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(glucose.averageMgDl?.let { Glucose.format(it, unit) } ?: "—", "Average ${unit.label}", Modifier.weight(1f).fillMaxHeight())
                StatTile(glucose.readings.toString(), "Readings", Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(glucose.lows.toString(), "Below target", Modifier.weight(1f).fillMaxHeight())
                StatTile(glucose.highs.toString(), "Above target", Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
    item(key = "glucose-trend") {
        val colors = DiaTheme.colors
        val values = glucose.daily.map { it.averageMgDl?.toFloat() }
        val axis = glucoseAxis(values, profile)
        DiaCard(Modifier.fillMaxWidth()) {
            Text("Daily average", style = MaterialTheme.typography.titleMedium, color = colors.text)
            TrendChart(
                values = values,
                labels = glucose.daily.map { Fmt.weekdayShort(it.date).take(1) },
                yMin = axis.start,
                yMax = axis.endInclusive,
                band = profile.targetLowMgDl.toFloat()..profile.targetHighMgDl.toFloat(),
                bandLabels = Glucose.format(profile.targetLowMgDl, unit) to Glucose.format(profile.targetHighMgDl, unit),
                description = "Daily average glucose: " + glucose.daily.joinToString { d ->
                    "${Fmt.weekday(d.date)} ${d.averageMgDl?.let { Glucose.format(it, unit) } ?: "no readings"}"
                },
            )
        }
    }
    if (glucose.checksPlanned > 0) {
        item(key = "checks") {
            val colors = DiaTheme.colors
            val ratio = glucose.checksDone.toFloat() / glucose.checksPlanned
            DiaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Planned checks", style = MaterialTheme.typography.titleSmall, color = colors.text)
                        Text("${glucose.checksDone} of ${glucose.checksPlanned} done", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Text(Fmt.percent(ratio), style = MaterialTheme.typography.titleMedium, color = colors.text)
                }
                ProgressBar(ratio, color = if (ratio >= 1f) colors.sage else colors.primary)
            }
        }
    }
}

private fun LazyListScope.habitsSection(state: InsightsUiState, figures: WeeklyFigures) {
    val profile = state.profile
    item(key = "water") {
        HabitCard(
            title = "Water",
            values = figures.waterByDay,
            goal = profile.waterGoalGlasses,
            today = state.today,
            format = { it.toString() },
            unitLabel = "glasses",
        )
    }
    item(key = "steps") {
        HabitCard(
            title = "Steps",
            values = figures.stepsByDay,
            goal = profile.stepGoal,
            today = state.today,
            format = Fmt::compact,
            unitLabel = "steps",
        )
    }
    item(key = "minutes") {
        HabitCard(
            title = "Active minutes",
            values = figures.minutesByDay,
            goal = profile.walkGoalMinutes,
            today = state.today,
            format = { it.toString() },
            unitLabel = "minutes",
        )
    }
}

/** A week of one habit against its daily goal, drawn as bars with a dashed goal line. */
@Composable
private fun HabitCard(
    title: String,
    values: List<Pair<LocalDate, Int>>,
    goal: Int,
    today: LocalDate,
    format: (Int) -> String,
    unitLabel: String,
) {
    val colors = DiaTheme.colors
    // The goal sits at 80% of the chart height, leaving room to show days above it.
    val scale = goal.coerceAtLeast(1) / GOAL_HEIGHT
    val logged = values.filter { it.second > 0 }
    val metGoal = values.count { it.second >= goal }
    DiaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(
                    if (logged.isEmpty()) "Nothing logged this week"
                    else "Average ${format(logged.sumOf { it.second } / logged.size)} $unitLabel on days logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
                Text("$metGoal of 7", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text("days at goal", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
        HatchedBarChart(
            bars = values.map { (date, value) ->
                BarDatum(
                    label = Fmt.weekdayShort(date),
                    value = if (value == 0) null else value / scale,
                    highlight = date == today,
                    valueLabel = format(value),
                )
            },
            goal = GOAL_HEIGHT,
            height = 120.dp,
            description = "$title each day against a goal of ${format(goal)}: " +
                values.joinToString { (date, value) -> "${Fmt.weekday(date)} ${format(value)}" },
        )
    }
}

private const val GOAL_HEIGHT = 0.8f

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"
