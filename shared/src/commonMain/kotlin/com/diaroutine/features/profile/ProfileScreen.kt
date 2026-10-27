package com.diaroutine.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAlarm
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diaroutine.app.AppContainer
import com.diaroutine.app.AppLinks
import com.diaroutine.core.format.Fmt
import com.diaroutine.domain.Glucose
import com.diaroutine.domain.model.GlucoseCheckPlan
import com.diaroutine.domain.model.GlucoseUnit
import com.diaroutine.domain.model.MealContext
import com.diaroutine.domain.model.Profile
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.ChoiceChip
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.InfoRow
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.ScreenHeader
import com.diaroutine.ui.components.SegmentedTabs
import com.diaroutine.ui.components.StatusLabel
import com.diaroutine.ui.components.Stepper
import com.diaroutine.ui.components.TextEntryDialog
import com.diaroutine.ui.components.TimePickerDialog
import com.diaroutine.ui.screenPadding
import com.diaroutine.ui.suggestedMealContext
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.datetime.LocalTime

@Composable
fun ProfileRoute(container: AppContainer) {
    val viewModel = viewModel { ProfileViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(ProfileAction.RefreshAccess)
        onPauseOrDispose { }
    }
    ProfileScreen(state, viewModel::onAction)
}

/** Which text the edit dialog is open for. */
private enum class TextField { Name, CaregiverName, CaregiverContact }

/** Which time the picker is open for: a check (by index, -1 for a new one) or the walk. */
private sealed interface TimeTarget {
    data class Check(val index: Int) : TimeTarget
    data object Walk : TimeTarget
}

@Composable
fun ProfileScreen(state: ProfileUiState, onAction: (ProfileAction) -> Unit) {
    val profile = state.profile
    val use24Hour = LocalPlatform.current.uses24HourClock
    var editingText by remember { mutableStateOf<TextField?>(null) }
    var pickingTime by remember { mutableStateOf<TimeTarget?>(null) }
    val update: ((Profile) -> Profile) -> Unit = { onAction(ProfileAction.Update(it)) }

    LazyColumn(
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { ScreenHeader("Profile") }
        item(key = "identity") { IdentityCard(profile, onEdit = { editingText = TextField.Name }) }
        item(key = "glucose") {
            GlucoseCard(
                profile = profile,
                use24Hour = use24Hour,
                update = update,
                onAddCheck = { pickingTime = TimeTarget.Check(-1) },
                onChangeCheckTime = { pickingTime = TimeTarget.Check(it) },
            )
        }
        item(key = "goals") { GoalsCard(profile, use24Hour, update, onPickWalkTime = { pickingTime = TimeTarget.Walk }) }
        item(key = "reminders") { RemindersCard(state, update, onAction) }
        item(key = "caregiver") {
            CaregiverCard(
                profile = profile,
                update = update,
                onEditName = { editingText = TextField.CaregiverName },
                onEditContact = { editingText = TextField.CaregiverContact },
                onShare = { onAction(ProfileAction.ShareSummary) },
            )
        }
        item(key = "premium") { PremiumCard() }
        item(key = "about") { AboutCard(state.appVersion, onAction) }
    }

    editingText?.let { field ->
        val (title, label, initial) = when (field) {
            TextField.Name -> Triple("Your name", "Name", profile.name)
            TextField.CaregiverName -> Triple("Caregiver's name", "Name", profile.caregiver.name)
            TextField.CaregiverContact -> Triple("How to reach them", "Phone or email", profile.caregiver.contact)
        }
        TextEntryDialog(
            title = title,
            label = label,
            initial = initial,
            onDismiss = { editingText = null },
            onConfirm = { value ->
                update {
                    when (field) {
                        TextField.Name -> it.copy(name = value)
                        TextField.CaregiverName -> it.copy(caregiver = it.caregiver.copy(name = value))
                        TextField.CaregiverContact -> it.copy(caregiver = it.caregiver.copy(contact = value))
                    }
                }
            },
            validate = { if (it.length > 60) "Keep it under 60 characters" else null },
        )
    }

    pickingTime?.let { target ->
        val checks = profile.glucoseChecks
        val initial = when (target) {
            TimeTarget.Walk -> profile.walkTime ?: LocalTime(18, 0)
            is TimeTarget.Check -> checks.getOrNull(target.index)?.time ?: LocalTime(12, 0)
        }
        TimePickerDialog(
            initial = initial,
            use24Hour = use24Hour,
            onDismiss = { pickingTime = null },
            onConfirm = { time ->
                update { p ->
                    when (target) {
                        TimeTarget.Walk -> p.copy(walkTime = time)
                        is TimeTarget.Check -> {
                            val list = p.glucoseChecks.toMutableList()
                            if (target.index in list.indices) {
                                list[target.index] = list[target.index].copy(time = time)
                            } else {
                                list += GlucoseCheckPlan(time, suggestedMealContext(time))
                            }
                            p.copy(glucoseChecks = list.distinctBy { it.time }.sortedBy { it.time })
                        }
                    }
                }
                pickingTime = null
            },
            title = if (target == TimeTarget.Walk) "Walk time" else "Check time",
        )
    }
}

@Composable
private fun IdentityCard(profile: Profile, onEdit: () -> Unit) {
    val colors = DiaTheme.colors
    DiaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(colors.primarySoft), contentAlignment = Alignment.Center) {
                if (profile.initials.isNotEmpty()) {
                    Text(profile.initials, style = MaterialTheme.typography.headlineSmall, color = colors.primaryStrong)
                } else {
                    Icon(Icons.Rounded.Person, null, tint = colors.primaryStrong, modifier = Modifier.size(32.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(profile.name.ifBlank { "Add your name" }, style = MaterialTheme.typography.titleLarge, color = colors.text)
                Text("Personal routine", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            TextButton(onClick = onEdit) { Text("Edit", color = colors.primaryStrong) }
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val colors = DiaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconBubble(icon, tint = colors.primaryStrong)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        trailing?.invoke()
    }
}

@Composable
private fun GlucoseCard(
    profile: Profile,
    use24Hour: Boolean,
    update: ((Profile) -> Profile) -> Unit,
    onAddCheck: () -> Unit,
    onChangeCheckTime: (Int) -> Unit,
) {
    val colors = DiaTheme.colors
    val unit = profile.glucoseUnit
    val step = if (unit == GlucoseUnit.MmolL) 2 else 5
    DiaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        CardTitle(Icons.Rounded.Bloodtype, "Blood glucose", "Set the targets your care team gave you")
        SegmentedTabs(
            options = GlucoseUnit.entries.map { it.label },
            selectedIndex = unit.ordinal,
            onSelect = { index -> update { it.copy(glucoseUnit = GlucoseUnit.entries[index]) } },
        )
        Stepper(
            label = "Low target",
            value = profile.targetLowMgDl,
            onValueChange = { v -> update { it.copy(targetLowMgDl = v) } },
            range = 54..(profile.targetHighMgDl - 20),
            step = step,
            valueText = { Glucose.format(it, unit) },
            supporting = unit.label,
        )
        Stepper(
            label = "High target",
            value = profile.targetHighMgDl,
            onValueChange = { v -> update { it.copy(targetHighMgDl = v) } },
            range = (profile.targetLowMgDl + 20)..300,
            step = step,
            valueText = { Glucose.format(it, unit) },
            supporting = unit.label,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Daily checks", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text("They appear on your timeline with a reminder.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                profile.glucoseChecks.forEachIndexed { index, plan ->
                    CheckChip(
                        plan = plan,
                        use24Hour = use24Hour,
                        onChangeContext = { context ->
                            update { p -> p.copy(glucoseChecks = p.glucoseChecks.mapIndexed { i, c -> if (i == index) c.copy(context = context) else c }) }
                        },
                        onChangeTime = { onChangeCheckTime(index) },
                        onRemove = { update { p -> p.copy(glucoseChecks = p.glucoseChecks.filterIndexed { i, _ -> i != index }) } },
                    )
                }
                ChoiceChip("Add check", selected = false, onClick = onAddCheck, icon = Icons.Rounded.Add, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
}

@Composable
private fun CheckChip(
    plan: GlucoseCheckPlan,
    use24Hour: Boolean,
    onChangeContext: (MealContext) -> Unit,
    onChangeTime: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = DiaTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = "${Fmt.time(plan.time, use24Hour)} · ${plan.context.label}"
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(DiaShapes.pill)
                .background(colors.raised)
                .border(1.dp, colors.outline, DiaShapes.pill)
                .clickable(onClickLabel = "Edit check at $label", role = Role.Button) { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Schedule, null, tint = colors.primaryStrong, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.raised) {
            MealContext.entries.forEach { context ->
                DropdownMenuItem(
                    text = { Text(context.label) },
                    leadingIcon = { if (context == plan.context) Icon(Icons.Rounded.Check, "Selected", tint = colors.primary) },
                    onClick = { onChangeContext(context); open = false },
                )
            }
            HorizontalDivider(color = colors.outline)
            DropdownMenuItem(
                text = { Text("Change time") },
                leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                onClick = { open = false; onChangeTime() },
            )
            DropdownMenuItem(
                text = { Text("Remove", color = colors.coralStrong) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.coralStrong) },
                onClick = { open = false; onRemove() },
            )
        }
    }
}

@Composable
private fun GoalsCard(profile: Profile, use24Hour: Boolean, update: ((Profile) -> Profile) -> Unit, onPickWalkTime: () -> Unit) {
    val colors = DiaTheme.colors
    DiaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        CardTitle(Icons.AutoMirrored.Rounded.DirectionsWalk, "Daily goals")
        Stepper(
            label = "Water",
            value = profile.waterGoalGlasses,
            onValueChange = { v -> update { it.copy(waterGoalGlasses = v) } },
            range = 1..20,
            valueText = { if (it == 1) "1 glass" else "$it glasses" },
        )
        Stepper(
            label = "Steps",
            value = profile.stepGoal,
            onValueChange = { v -> update { it.copy(stepGoal = v) } },
            range = 1000..30000,
            step = 500,
            valueText = Fmt::grouped,
        )
        Stepper(
            label = "Active minutes",
            value = profile.walkGoalMinutes,
            onValueChange = { v -> update { it.copy(walkGoalMinutes = v) } },
            range = 5..180,
            step = 5,
            valueText = { "$it min" },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Planned walk", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text("Puts a walk on your timeline each day", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Switch(
                checked = profile.walkTime != null,
                onCheckedChange = { on -> update { it.copy(walkTime = if (on) LocalTime(18, 0) else null) } },
                colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
            )
        }
        profile.walkTime?.let { time ->
            PillButton(
                text = "Walk at ${Fmt.time(time, use24Hour)}",
                onClick = onPickWalkTime,
                style = PillStyle.Raised,
                leadingIcon = Icons.Rounded.Schedule,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RemindersCard(state: ProfileUiState, update: ((Profile) -> Profile) -> Unit, onAction: (ProfileAction) -> Unit) {
    val colors = DiaTheme.colors
    val profile = state.profile
    val access = state.access
    DiaCard(Modifier.fillMaxWidth()) {
        CardTitle(
            icon = Icons.Rounded.Notifications,
            title = "Reminders",
            subtitle = "Doses, glucose checks, walks, appointments and refills",
            trailing = {
                Switch(
                    checked = profile.remindersEnabled,
                    onCheckedChange = { on -> update { it.copy(remindersEnabled = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                )
            },
        )
        if (profile.remindersEnabled) {
            AccessRow(
                title = "Notifications",
                granted = access.notificationsAllowed,
                grantedText = "Allowed",
                deniedText = "Off — reminders can't appear",
                actionLabel = "Allow",
                onAction = { onAction(ProfileAction.RequestNotifications) },
            )
            if (access.canRequestPreciseTiming) {
                AccessRow(
                    title = "On-time alerts",
                    granted = access.preciseTiming,
                    grantedText = "Reminders arrive on the minute",
                    deniedText = "Reminders may arrive a few minutes late",
                    actionLabel = "Allow",
                    onAction = { onAction(ProfileAction.OpenPreciseTiming) },
                )
            }
        }
    }
}

@Composable
private fun AccessRow(
    title: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = DiaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(DiaShapes.small).background(if (granted) colors.raised else colors.amberSoft).padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(if (granted) grantedText else deniedText, style = MaterialTheme.typography.bodySmall, color = if (granted) colors.textMuted else colors.amber)
        }
        if (granted) {
            Icon(Icons.Rounded.Check, "Allowed", tint = colors.sage, modifier = Modifier.padding(end = Spacing.md))
        } else {
            TextButton(onClick = onAction) { Text(actionLabel, color = colors.primaryStrong) }
        }
    }
}

@Composable
private fun CaregiverCard(
    profile: Profile,
    update: ((Profile) -> Profile) -> Unit,
    onEditName: () -> Unit,
    onEditContact: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = DiaTheme.colors
    val caregiver = profile.caregiver
    DiaCard(Modifier.fillMaxWidth()) {
        CardTitle(
            icon = Icons.Rounded.Group,
            title = "Caregiver mode",
            subtitle = "Share your progress with someone who supports you",
            trailing = {
                Switch(
                    checked = caregiver.enabled,
                    onCheckedChange = { on -> update { it.copy(caregiver = it.caregiver.copy(enabled = on)) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                )
            },
        )
        if (caregiver.enabled) {
            InfoRow(
                icon = Icons.Rounded.Person,
                title = caregiver.name.ifBlank { "Add their name" },
                detail = "Caregiver",
                trailing = { TextButton(onClick = onEditName) { Text("Edit", color = colors.primaryStrong) } },
            )
            InfoRow(
                icon = Icons.Rounded.ContactPhone,
                title = caregiver.contact.ifBlank { "Add a phone number or email" },
                detail = "How you reach them",
                trailing = { TextButton(onClick = onEditContact) { Text("Edit", color = colors.primaryStrong) } },
            )
            PillButton(
                text = "Send this week's summary",
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "Nothing is shared automatically. You decide when to send a summary, and through which app.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun PremiumCard() {
    val colors = DiaTheme.colors
    DiaCard(Modifier.fillMaxWidth(), color = colors.primarySoft) {
        CardTitle(
            icon = Icons.Rounded.WorkspacePremium,
            title = "DiaRoutine Premium",
            trailing = { StatusLabel("Coming soon", null, colors.raised, colors.primaryStrong) },
        )
        listOf(
            Triple(Icons.Rounded.AddAlarm, "Premium reminders", "Follow-up alerts when a dose is missed"),
            Triple(Icons.Rounded.Group, "Caregiver accounts", "A caregiver follows along from their own phone"),
            Triple(Icons.Rounded.FamilyRestroom, "More profiles", "Manage routines for family members"),
            Triple(Icons.Rounded.CloudSync, "Cloud sync", "Back up and sync across your devices"),
        ).forEach { (icon, title, detail) ->
            InfoRow(icon = icon, title = title, detail = detail, iconTint = colors.primaryStrong)
        }
    }
}

@Composable
private fun AboutCard(version: String, onAction: (ProfileAction) -> Unit) {
    val colors = DiaTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Rounded.HealthAndSafety, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            Text("DiaRoutine $version · ${AppLinks.PUBLISHER}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
        Text(
            "DiaRoutine helps you organize your routine. It doesn't give medical advice, diagnose or treat any condition. " +
                "Always follow your care team's instructions. Your data stays on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        FlowRow {
            TextButton(onClick = { onAction(ProfileAction.OpenLink(AppLinks.PRIVACY_POLICY)) }) {
                Text("Privacy policy", color = colors.primaryStrong)
            }
            TextButton(onClick = { onAction(ProfileAction.OpenLink(AppLinks.TERMS_OF_USE)) }) {
                Text("Terms of use", color = colors.primaryStrong)
            }
            TextButton(onClick = { onAction(ProfileAction.ContactSupport) }) {
                Text("Contact support", color = colors.primaryStrong)
            }
        }
    }
}
