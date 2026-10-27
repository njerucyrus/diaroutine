package com.diaroutine.features.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diaroutine.app.AppContainer
import com.diaroutine.core.format.Fmt
import com.diaroutine.domain.DaySummary
import com.diaroutine.domain.EntryState
import com.diaroutine.domain.Glucose
import com.diaroutine.domain.TimelineEntry
import com.diaroutine.domain.model.MealContext
import com.diaroutine.domain.model.Medication
import com.diaroutine.domain.model.Profile
import com.diaroutine.features.track.ActivitySheet
import com.diaroutine.features.track.GlucoseLogSheet
import com.diaroutine.features.track.GlucoseSheetRequest
import com.diaroutine.ui.EntryStateLabel
import com.diaroutine.ui.GlucoseLevelLabel
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.CircleIconButton
import com.diaroutine.ui.components.CountPill
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.EmptyState
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.ProgressBar
import com.diaroutine.ui.components.ProgressRing
import com.diaroutine.ui.components.SectionHeader
import com.diaroutine.ui.icon
import com.diaroutine.ui.screenPadding
import com.diaroutine.ui.suggestedMealContext
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import com.diaroutine.ui.title
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

@Composable
fun TodayRoute(container: AppContainer, onOpenMedications: () -> Unit) {
    val viewModel = viewModel { TodayViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(state, viewModel::onAction, onOpenMedications, container.timeZone)
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    onOpenMedications: () -> Unit,
    zone: TimeZone,
) {
    val use24Hour = LocalPlatform.current.uses24HourClock
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var glucoseRequest by remember { mutableStateOf<GlucoseSheetRequest?>(null) }
    var editingActivity by remember { mutableStateOf(false) }
    val entries = state.plan?.entries.orEmpty()
    val attention = if (state.isToday) state.plan?.summary?.needsAttention ?: 0 else 0

    fun openGlucose(time: LocalTime? = null, context: MealContext? = null) {
        val at = time ?: if (state.isToday) state.now.time else LocalTime(12, 0)
        glucoseRequest = GlucoseSheetRequest(state.selectedDate, at, context ?: suggestedMealContext(at))
    }

    LazyColumn(
        state = listState,
        contentPadding = screenPadding(horizontal = false),
        modifier = Modifier.fillMaxSize(),
    ) {
        padded("header") {
            GreetingHeader(
                profile = state.profile,
                attention = attention,
                onBell = {
                    // The timeline is always last, so its rows index back from the end.
                    val first = entries.indexOfFirst { it.state.needsAttention }
                    val total = listState.layoutInfo.totalItemsCount
                    val target = if (first >= 0) total - entries.size + first else total - entries.size.coerceAtLeast(1) - 1
                    scope.launch { listState.animateScrollToItem(target.coerceAtLeast(0)) }
                },
            )
        }
        padded("title", bottom = Spacing.md) {
            Column {
                Text(
                    "Your routine",
                    style = MaterialTheme.typography.headlineMedium,
                    color = DiaTheme.colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = (if (state.isToday) "Today · " else "") + Fmt.dateLong(state.selectedDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DiaTheme.colors.textMuted,
                )
            }
        }
        item(key = "days") {
            DayStrip(state.days, state.selectedDate, state.today, onSelect = { onAction(TodayAction.SelectDate(it)) })
        }
        val plan = state.plan
        if (plan != null) {
            padded("summary", top = Spacing.lg) { SummaryCard(state, plan.summary, use24Hour) }
        }
        padded("quick") {
            QuickStats(
                state = state,
                onGlucose = { openGlucose() },
                onWater = { onAction(TodayAction.AddWater(1)) },
                onActivity = { editingActivity = true },
            )
        }
        if (state.refills.isNotEmpty()) {
            padded("refills") { RefillCard(state.refills, onOpenMedications) }
        }
        if (!state.isLoading && !state.hasMedications) {
            padded("no-meds") {
                EmptyState(
                    icon = Icons.Rounded.Medication,
                    title = "Add your medications",
                    message = "Enter each medication as prescribed and its doses appear here, with reminders when they're due.",
                    actionLabel = "Add medication",
                    onAction = onOpenMedications,
                )
            }
        }
        padded("timeline-header", bottom = Spacing.sm) { SectionHeader("Timeline") }
        if (entries.isEmpty()) {
            padded("timeline-empty") {
                EmptyState(
                    icon = Icons.Rounded.WbSunny,
                    title = "Nothing planned",
                    message = "Medications, glucose checks, your walk and appointments for ${Fmt.relativeDate(state.selectedDate, state.today).lowercase()} will show up here.",
                )
            }
        } else {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                TimelineRow(
                    entry = entry,
                    isFirst = index == 0,
                    isLast = index == entries.lastIndex,
                    state = state,
                    use24Hour = use24Hour,
                    onAction = onAction,
                    onLogGlucose = { check -> openGlucose(check.plan?.time?.takeUnless { state.isToday }, check.plan?.context) },
                    onLogActivity = { editingActivity = true },
                    modifier = Modifier.padding(horizontal = Spacing.screen),
                )
            }
        }
    }

    glucoseRequest?.let { request ->
        GlucoseLogSheet(
            request = request,
            profile = state.profile,
            now = state.now,
            zone = zone,
            onSave = { onAction(TodayAction.LogGlucose(it)) },
            onDismiss = { glucoseRequest = null },
        )
    }
    if (editingActivity) {
        ActivitySheet(
            habit = state.habit,
            profile = state.profile,
            onSave = { steps, minutes -> onAction(TodayAction.SetActivity(steps, minutes)) },
            onDismiss = { editingActivity = false },
        )
    }
}

/** An item with the screen's side margins and a gap below. */
private fun LazyListScope.padded(
    key: String,
    top: Dp = 0.dp,
    bottom: Dp = Spacing.lg,
    content: @Composable () -> Unit,
) {
    item(key = key) {
        Box(Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = top, bottom = bottom)) { content() }
    }
}

@Composable
private fun GreetingHeader(profile: Profile, attention: Int, onBell: () -> Unit) {
    val colors = DiaTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .clip(DiaShapes.pill)
                .background(colors.raised)
                .border(1.dp, colors.outline, DiaShapes.pill)
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(profile.initials.ifEmpty { "🙂" }, style = MaterialTheme.typography.titleSmall, color = colors.primaryStrong)
            }
            Text(
                text = "Hi ${profile.firstName.ifEmpty { "there" }} 👋",
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f))
        CircleIconButton(
            icon = Icons.Rounded.Notifications,
            contentDescription = if (attention > 0) "$attention ${if (attention == 1) "item needs" else "items need"} attention. Show on timeline" else "Show timeline",
            onClick = onBell,
            badge = attention,
        )
    }
}

@Composable
private fun DayStrip(days: List<LocalDate>, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val colors = DiaTheme.colors
    val todayIndex = days.indexOf(today).coerceAtLeast(0)
    val stripState = rememberLazyListState(initialFirstVisibleItemIndex = (todayIndex - 2).coerceAtLeast(0))
    LazyRow(
        state = stripState,
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(days, key = { it.toString() }) { date ->
            val isSelected = date == selected
            val isToday = date == today
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .height(82.dp)
                    .clip(DiaShapes.pill)
                    .background(if (isSelected) colors.primarySoft else colors.raised)
                    .border(1.dp, if (isSelected) colors.primary else colors.outline, DiaShapes.pill)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(date) })
                    .clearAndSetSemantics { contentDescription = Fmt.dateLong(date) + if (isToday) ", today" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (isToday) colors.primary else colors.primary.copy(alpha = 0f)))
                Text(date.day.toString(), style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.padding(top = 2.dp))
                Text(Fmt.weekdayShort(date), style = MaterialTheme.typography.labelSmall, color = if (isSelected) colors.text else colors.textMuted)
            }
        }
    }
}

@Composable
private fun SummaryCard(state: TodayUiState, summary: DaySummary, use24Hour: Boolean) {
    val colors = DiaTheme.colors
    val next = summary.next
    val (headline, detail) = when {
        summary.planned == 0 -> "Nothing planned" to "Add medications or glucose checks to build your day."
        state.isFuture -> "${summary.planned} planned" to "Here's what's coming up on ${Fmt.weekday(state.selectedDate)}."
        state.selectedDate < state.today -> if (summary.missed == 0) {
            "Every task done" to "A complete day. Well done."
        } else {
            "${summary.done} of ${summary.planned} done" to "Missed doses can still be logged if you took them."
        }
        summary.needsAttention > 0 -> "${summary.needsAttention} ${if (summary.needsAttention == 1) "task needs" else "tasks need"} attention" to
            (next?.let { "Next: ${it.title} at ${Fmt.time(it.time, use24Hour)}" } ?: "Catch up when you can.")
        summary.done == summary.planned -> "All done for today" to "Nice work keeping your routine."
        next != null -> "Next: ${next.title}" to "At ${Fmt.time(next.time, use24Hour)} · ${summary.planned - summary.done} left today"
        else -> "${summary.done} of ${summary.planned} done" to "Keep going."
    }
    DiaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ProgressRing(
                progress = summary.progress,
                modifier = Modifier.size(92.dp).clearAndSetSemantics {
                    contentDescription = "${summary.done} of ${summary.planned} planned tasks done"
                },
                strokeWidth = 10.dp,
                color = if (summary.planned > 0 && summary.done == summary.planned) colors.sage else colors.primary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${summary.done}/${summary.planned}", style = MaterialTheme.typography.titleLarge, color = colors.text)
                    Text("done", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        if (summary.dueNow + summary.overdue + summary.missed > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (summary.dueNow > 0) CountPill("Due now", summary.dueNow, dot = colors.amber)
                if (summary.overdue > 0) CountPill("Overdue", summary.overdue, dot = colors.coral)
                if (summary.missed > 0) CountPill("Missed", summary.missed, dot = colors.coral)
            }
        }
    }
}

@Composable
private fun QuickStats(state: TodayUiState, onGlucose: () -> Unit, onWater: () -> Unit, onActivity: () -> Unit) {
    val profile = state.profile
    val unit = profile.glucoseUnit
    val reading = state.latestReading
    val enabled = !state.isFuture
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        QuickTile(
            icon = Icons.Rounded.Bloodtype,
            value = reading?.let { Glucose.format(it.mgDl, unit) } ?: "—",
            caption = reading?.let { "${unit.label} · ${Glucose.level(it.mgDl, profile).label}" } ?: "No reading yet",
            description = reading?.let { "Latest glucose ${Glucose.formatWithUnit(it.mgDl, unit)}, ${Glucose.level(it.mgDl, profile).label}" } ?: "No glucose reading yet",
            actionLabel = "Log blood glucose",
            enabled = enabled,
            onClick = onGlucose,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        QuickTile(
            icon = Icons.Rounded.LocalDrink,
            value = "${state.habit.waterGlasses}/${profile.waterGoalGlasses}",
            caption = "glasses · tap +1",
            description = "${state.habit.waterGlasses} of ${profile.waterGoalGlasses} glasses of water",
            actionLabel = "Add a glass of water",
            enabled = enabled,
            onClick = onWater,
            progress = state.habit.waterGlasses.toFloat() / profile.waterGoalGlasses.coerceAtLeast(1),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        QuickTile(
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
            value = Fmt.compact(state.habit.steps),
            caption = "of ${Fmt.compact(profile.stepGoal)} steps",
            description = "${Fmt.grouped(state.habit.steps)} of ${Fmt.grouped(profile.stepGoal)} steps",
            actionLabel = "Update activity",
            enabled = enabled,
            onClick = onActivity,
            progress = state.habit.steps.toFloat() / profile.stepGoal.coerceAtLeast(1),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    value: String,
    caption: String,
    description: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val colors = DiaTheme.colors
    Column(
        modifier = modifier
            .clip(DiaShapes.inner)
            .background(colors.card)
            .clickable(enabled = enabled, onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBubble(icon, size = 36.dp)
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 1)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 2)
        if (progress != null) ProgressBar(progress, height = 5.dp, color = if (progress >= 1f) colors.sage else colors.primary)
    }
}

@Composable
private fun RefillCard(refills: List<Medication>, onOpenMedications: () -> Unit) {
    val colors = DiaTheme.colors
    DiaCard(Modifier.fillMaxWidth(), color = colors.coralSoft) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Inventory2, tint = colors.coralStrong)
            Column(Modifier.weight(1f)) {
                Text(
                    if (refills.size == 1) "Refill ${refills.single().name} soon" else "${refills.size} medications to refill",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                )
                refills.forEach { med ->
                    val days = med.daysOfSupply
                    Text(
                        "${med.name}: ${med.form.amount(med.supplyRemaining ?: 0)} left" + (days?.let { " · about ${if (it == 1) "1 day" else "$it days"}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
        PillButton("Update supply", onOpenMedications, Modifier.fillMaxWidth(), style = PillStyle.Raised)
    }
}

@Composable
private fun TimelineRow(
    entry: TimelineEntry,
    isFirst: Boolean,
    isLast: Boolean,
    state: TodayUiState,
    use24Hour: Boolean,
    onAction: (TodayAction) -> Unit,
    onLogGlucose: (TimelineEntry.GlucoseCheck) -> Unit,
    onLogActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DiaTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            text = Fmt.time(entry.time, use24Hour),
            style = MaterialTheme.typography.labelMedium,
            color = if (entry.state.needsAttention) colors.text else colors.textMuted,
            modifier = Modifier.width(60.dp).padding(top = 24.dp),
            maxLines = 1,
        )
        Rail(entry.state, isFirst, isLast, Modifier.width(20.dp).fillMaxHeight())
        EntryCard(
            entry = entry,
            state = state,
            onAction = onAction,
            onLogGlucose = onLogGlucose,
            onLogActivity = onLogActivity,
            modifier = Modifier.weight(1f).padding(start = Spacing.sm, bottom = Spacing.md),
        )
    }
}

@Composable
private fun Rail(state: EntryState, isFirst: Boolean, isLast: Boolean, modifier: Modifier) {
    val colors = DiaTheme.colors
    val fill = when {
        state == EntryState.Done -> colors.sage
        state == EntryState.DueNow -> colors.amber
        state == EntryState.Overdue || state == EntryState.Missed -> colors.coral
        else -> colors.raised
    }
    val ring = if (fill == colors.raised) colors.outline else fill
    val line = colors.outline
    Canvas(modifier) {
        val x = size.width / 2f
        val nodeY = 32.dp.toPx()
        val width = 2.dp.toPx()
        if (!isFirst) drawLine(line, Offset(x, 0f), Offset(x, nodeY), width)
        if (!isLast) drawLine(line, Offset(x, nodeY), Offset(x, size.height), width)
        drawCircle(fill, 6.dp.toPx(), Offset(x, nodeY))
        drawCircle(ring, 6.dp.toPx(), Offset(x, nodeY), style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun EntryCard(
    entry: TimelineEntry,
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    onLogGlucose: (TimelineEntry.GlucoseCheck) -> Unit,
    onLogActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DiaTheme.colors
    val settled = entry.state == EntryState.Done || entry.state == EntryState.Skipped || entry.state == EntryState.Passed
    Row(
        modifier = modifier
            .clip(DiaShapes.inner)
            .background(if (settled) colors.card.copy(alpha = 0.55f) else colors.card)
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconBubble(entry.icon, tint = if (entry is TimelineEntry.Visit) colors.primaryStrong else colors.text)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle(entry, state.profile)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val reading = (entry as? TimelineEntry.GlucoseCheck)?.reading
                if (reading != null) GlucoseLevelLabel(Glucose.level(reading.mgDl, state.profile)) else EntryStateLabel(entry.state)
                val canSkip = entry is TimelineEntry.Dose && entry.state in setOf(EntryState.DueNow, EntryState.Overdue, EntryState.Missed)
                if (canSkip) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primaryStrong,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(DiaShapes.pill)
                            .clickable(onClickLabel = "Skip ${entry.title}", role = Role.Button) {
                                onAction(TodayAction.SkipDose(entry))
                            }
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
            }
        }
        if (!state.isFuture) Trailing(entry, onAction, onLogGlucose, onLogActivity)
    }
}

@Composable
private fun Trailing(
    entry: TimelineEntry,
    onAction: (TodayAction) -> Unit,
    onLogGlucose: (TimelineEntry.GlucoseCheck) -> Unit,
    onLogActivity: () -> Unit,
) {
    val colors = DiaTheme.colors
    val haptics = LocalHapticFeedback.current
    when (entry) {
        is TimelineEntry.Dose -> {
            val taken = entry.state == EntryState.Done
            val skipped = entry.state == EntryState.Skipped
            CircleIconButton(
                icon = if (skipped) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.Check,
                contentDescription = when {
                    taken -> "${entry.title} taken. Undo"
                    skipped -> "${entry.title} skipped. Undo"
                    else -> "Mark ${entry.title} as taken"
                },
                onClick = {
                    if (taken || skipped) {
                        onAction(TodayAction.ClearDose(entry))
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAction(TodayAction.TakeDose(entry))
                    }
                },
                container = if (taken) colors.sage else colors.raised,
                tint = when {
                    taken -> colors.onPrimary
                    skipped -> colors.textMuted
                    else -> colors.primary
                },
                size = 44.dp,
            )
        }
        is TimelineEntry.GlucoseCheck -> if (entry.reading == null) {
            CircleIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Log ${entry.plan?.context?.label?.lowercase()?.plus(" ").orEmpty()}glucose reading",
                onClick = { onLogGlucose(entry) },
                size = 44.dp,
            )
        }
        is TimelineEntry.Walk -> {
            val done = entry.state == EntryState.Done
            CircleIconButton(
                icon = if (done) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = if (done) "Walk done. Edit activity" else "Log activity",
                onClick = onLogActivity,
                container = if (done) colors.sage else colors.raised,
                tint = if (done) colors.onPrimary else colors.primary,
                size = 44.dp,
            )
        }
        is TimelineEntry.Visit -> Unit
    }
}

private fun subtitle(entry: TimelineEntry, profile: Profile): String? = when (entry) {
    is TimelineEntry.Dose -> buildList {
        if (entry.medication.asNeeded) add("As needed")
        add(entry.medication.doseDescription)
        entry.medication.instructions.trim().takeIf { it.isNotEmpty() }?.let(::add)
    }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
    is TimelineEntry.GlucoseCheck -> entry.reading?.let { "${Glucose.formatWithUnit(it.mgDl, profile.glucoseUnit)} · ${it.context.label}" }
        ?: entry.plan?.context?.label
    is TimelineEntry.Walk -> "${entry.minutesDone} of ${entry.goalMinutes} active minutes"
    is TimelineEntry.Visit -> listOf(entry.appointment.withWhom, entry.appointment.notes).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }
}
