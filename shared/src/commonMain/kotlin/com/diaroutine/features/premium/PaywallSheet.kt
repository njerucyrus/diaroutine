package com.diaroutine.features.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlarm
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diaroutine.premium.PremiumFeature
import com.diaroutine.premium.PremiumPlan
import com.diaroutine.premium.PremiumState
import com.diaroutine.premium.PremiumStore
import com.diaroutine.ui.components.DiaSheet
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.InfoRow
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.StatusLabel
import com.diaroutine.ui.components.diaSwitchColors
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing

val PremiumFeature.icon: ImageVector
    get() = when (this) {
        PremiumFeature.CloudBackup -> Icons.Rounded.CloudSync
        PremiumFeature.FollowUps -> Icons.Rounded.AddAlarm
        PremiumFeature.LongerInsights -> Icons.Rounded.Insights
    }

/** What Premium includes, the store's plans, and the free trial when the person is eligible. */
@Composable
fun PaywallSheet(store: PremiumStore, onDismiss: () -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { store.refresh() }
    DiaSheet(title = if (state.isPremium) "You have Premium" else "DiaRoutine Premium", onDismiss = onDismiss) {
        PaywallContent(state, store)
    }
}

@Composable
fun ColumnScope.PaywallContent(state: PremiumState, store: PremiumStore) {
    val colors = DiaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconBubble(Icons.Rounded.WorkspacePremium, size = 56.dp, container = colors.primary, tint = colors.onPrimary)
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    state.isPremium -> "Thanks for supporting DiaRoutine"
                    state.trialOffered -> "Try everything free for 3 months"
                    else -> "Do more with your routine"
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            Text(
                if (state.isPremium) "Everything below is on." else "Your everyday features stay free, always.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
    PremiumFeature.entries.forEach { feature ->
        InfoRow(feature.icon, feature.title, detail = feature.detail, iconTint = colors.primaryStrong)
    }

    when {
        state.isPremium -> PillButton("Manage subscription", store::manageSubscription, Modifier.fillMaxWidth(), style = PillStyle.Raised)
        state.plansAvailable == null -> Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(28.dp)) }
        state.plansAvailable == false || state.plans.isEmpty() -> {
            Text(
                "Premium can't be bought right now. Check your connection and that Google Play is signed in, then try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            PillButton("Try again", store::refresh, Modifier.fillMaxWidth(), style = PillStyle.Raised)
        }
        else -> Plans(state, store)
    }
    if (state.pending) {
        Text(
            "Your purchase is waiting on Google Play. Premium switches on as soon as it's confirmed.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.amber,
        )
    }
    if (!state.isPremium) {
        TextButton(onClick = store::refresh, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Restore purchases", color = colors.primaryStrong)
        }
    }
    if (state.canSimulate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Developer build: simulate Premium", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.weight(1f))
            Switch(checked = state.isPremium, onCheckedChange = store::simulate, colors = diaSwitchColors())
        }
    }
}

@Composable
private fun Plans(state: PremiumState, store: PremiumStore) {
    val colors = DiaTheme.colors
    // Yearly first: it's the better value and the one most people choose.
    val plans = state.plans.sortedByDescending { it.period == "year" }
    var selectedId by remember(plans) { mutableStateOf(plans.first().id) }
    val selected = plans.first { it.id == selectedId }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        plans.forEach { plan -> PlanOption(plan, selected = plan.id == selectedId, onSelect = { selectedId = plan.id }) }
    }
    PillButton(
        text = selected.freeTrial?.let { "Start $it free" } ?: "Subscribe",
        onClick = { store.purchase(selected) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = selected.freeTrial?.let {
            "Free for $it, then ${selected.price} a ${selected.period}. Cancel before the trial ends and you won't be charged. " +
                "Cancel anytime in Google Play."
        } ?: "${selected.price} a ${selected.period}, renewing automatically. Cancel anytime in Google Play.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlanOption(plan: PremiumPlan, selected: Boolean, onSelect: () -> Unit) {
    val colors = DiaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiaShapes.inner)
            .background(if (selected) colors.primarySoft else colors.raised)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline, DiaShapes.inner)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(plan.title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text("${plan.price} a ${plan.period}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (plan.freeTrial != null) StatusLabel("${plan.freeTrial} free", null, colors.sageSoft, colors.sage)
    }
}
