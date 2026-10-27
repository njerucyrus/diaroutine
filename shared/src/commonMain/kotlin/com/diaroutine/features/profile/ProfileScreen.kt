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
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
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
import com.diaroutine.features.backup.BackupCard
import com.diaroutine.features.backup.BackupViewModel
import com.diaroutine.features.premium.PaywallSheet
import com.diaroutine.features.premium.icon
import com.diaroutine.premium.PremiumFeature
import com.diaroutine.premium.PremiumState
import com.diaroutine.platform.LockAvailability
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
import com.diaroutine.ui.components.diaSwitchColors
import com.diaroutine.ui.screenPadding
import com.diaroutine.ui.suggestedMealContext
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.datetime.LocalTime

@Composable
fun ProfileRoute(container: AppContainer, onStartTour: () -> Unit) {
    val viewModel = viewModel { ProfileViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The same instance the app root uses to show the backup setup and restore sheets.
    val backup = viewModel { BackupViewModel(container) }
    val backupState by backup.state.collectAsStateWithLifecycle()
    var offerPremium by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(ProfileAction.RefreshAccess)
        onPauseOrDispose { }
    }
    ProfileScreen(
        state = state,
        onAction = viewModel::onAction,
        onStartTour = onStartTour,
        onOfferPremium = { offerPremium = true },
        backupCard = {
            BackupCard(
                state = backupState,
                zone = container.timeZone,
                onSetUp = backup::startSetup,
                onOfferPremium = { offerPremium = true },
                actions = backup,
            )
        },
    )
    if (offerPremium) PaywallSheet(container.premium, onDismiss = { offerPremium = false })
}

/** Which text the edit dialog is open for. */
private enum class TextField { Name, CaregiverName, CaregiverContact }

/** Which time the picker is open for: a check (by index, -1 for a new one) or the walk. */
private sealed interface TimeTarget {
    data class Check(val index: Int) : TimeTarget
    data object Walk : TimeTarget
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    onStartTour: () -> Unit,
    onOfferPremium: () -> Unit = {},
    backupCard: @Composable () -> Unit = {},
) {
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
        item(key = "premium") { PremiumCard(state.premium, onOpen = onOfferPremium) }
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
        item(key = "reminders") { RemindersCard(state, update, onAction, onOfferPremium) }
        item(key = "lock") { AppLockCard(state, onAction) }
        item(key = "backup") { backupCard() }
        item(key = "caregiver") {
            CaregiverCard(
                profile = profile,
                update = update,
                onEditName = { editingText = TextField.CaregiverName },
                onEditContact = { editingText = TextField.CaregiverContact },
                onShare = { onAction(ProfileAction.ShareSummary) },
            )
        }
        item(key = "about") { AboutCard(state.appVersion, onAction, onStartTour) }
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
                colors = diaSwitchColors(),
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

private val FollowUpChoices = listOf(0 to "Off", 15 to "Every 15 min", 30 to "Every 30 min", 60 to "Every hour")

@Composable
private fun RemindersCard(
    state: ProfileUiState,
    update: ((Profile) -> Profile) -> Unit,
    onAction: (ProfileAction) -> Unit,
    onOfferPremium: () -> Unit,
) {
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
                    colors = diaSwitchColors(),
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
            val premium = state.premium.isPremium
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Missed-dose follow-ups", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        "Remind me again if a dose isn't marked taken",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                if (!premium) StatusLabel("Premium", Icons.Rounded.Lock, colors.primarySoft, colors.primaryStrong)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FollowUpChoices.forEach { (minutes, label) ->
                    ChoiceChip(
                        text = label,
                        selected = premium && profile.followUpMinutes == minutes,
                        onClick = { if (premium) update { it.copy(followUpMinutes = minutes) } else onOfferPremium() },
                    )
                }
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

private val LockDelays = listOf(0 to "Immediately", 60 to "After 1 minute", 5 * 60 to "After 5 minutes")

@Composable
private fun AppLockCard(state: ProfileUiState, onAction: (ProfileAction) -> Unit) {
    val colors = DiaTheme.colors
    val lock = state.profile.appLock
    val availability = state.lockAvailability
    DiaCard(Modifier.fillMaxWidth()) {
        CardTitle(
            icon = Icons.Rounded.Fingerprint,
            title = "App lock",
            subtitle = "Ask for your fingerprint, face or screen lock to open DiaRoutine",
            trailing = {
                Switch(
                    checked = lock.enabled,
                    // Always allowed to turn off; turning on needs something to verify with.
                    enabled = lock.enabled || availability == LockAvailability.Available,
                    onCheckedChange = { onAction(ProfileAction.SetAppLock(it)) },
                    colors = diaSwitchColors(),
                )
            },
        )
        when {
            lock.enabled -> {
                Text("Lock again after leaving the app", style = MaterialTheme.typography.titleSmall, color = colors.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    LockDelays.forEach { (seconds, label) ->
                        ChoiceChip(label, selected = lock.afterSeconds == seconds, onClick = { onAction(ProfileAction.SetLockDelay(seconds)) })
                    }
                }
            }
            availability == LockAvailability.NoScreenLock -> AccessRow(
                title = "Screen lock needed",
                granted = false,
                grantedText = "",
                deniedText = "Set a PIN, pattern or password on your phone first",
                actionLabel = "Settings",
                onAction = { onAction(ProfileAction.OpenSecuritySettings) },
            )
            availability == LockAvailability.Unsupported -> Text(
                "This phone doesn't support app lock.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        Text(
            "Your fingerprint and face are checked by your phone. DiaRoutine never sees them.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
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
                    colors = diaSwitchColors(),
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
private fun PremiumCard(premium: PremiumState, onOpen: () -> Unit) {
    val colors = DiaTheme.colors
    DiaCard(Modifier.fillMaxWidth(), color = colors.primarySoft) {
        CardTitle(
            icon = Icons.Rounded.WorkspacePremium,
            title = "DiaRoutine Premium",
            subtitle = when {
                premium.isPremium -> "Active — thank you for your support"
                premium.trialOffered -> "Try it free for 3 months"
                else -> "Backup, follow-ups and longer insights"
            },
            trailing = if (premium.isPremium) {
                { StatusLabel("Active", Icons.Rounded.Check, colors.sageSoft, colors.sage) }
            } else {
                null
            },
        )
        PremiumFeature.entries.forEach { feature ->
            InfoRow(icon = feature.icon, title = feature.title, detail = feature.detail, iconTint = colors.primaryStrong)
        }
        PillButton(
            text = when {
                premium.isPremium -> "Manage Premium"
                premium.trialOffered -> "Start your free trial"
                else -> "See Premium"
            },
            onClick = onOpen,
            style = if (premium.isPremium) PillStyle.Raised else PillStyle.Filled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AboutCard(version: String, onAction: (ProfileAction) -> Unit, onStartTour: () -> Unit) {
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
            TextButton(onClick = onStartTour) {
                Text("Take the app tour", color = colors.primaryStrong)
            }
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
