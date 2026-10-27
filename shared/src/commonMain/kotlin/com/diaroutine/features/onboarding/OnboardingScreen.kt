package com.diaroutine.features.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.diaroutine.app.AppLinks
import com.diaroutine.domain.model.GlucoseUnit
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.FormField
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.SegmentedTabs
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing

@Composable
fun OnboardingScreen(onDone: (name: String, unit: GlucoseUnit, withExampleRoutine: Boolean) -> Unit) {
    val colors = DiaTheme.colors
    val platform = LocalPlatform.current
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(GlucoseUnit.MgDl) }
    var example by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        DiaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), modifier = Modifier.clearAndSetSemantics { }) {
                listOf(
                    Icons.Rounded.Medication,
                    Icons.Rounded.Bloodtype,
                    Icons.Rounded.LocalDrink,
                    Icons.AutoMirrored.Rounded.DirectionsWalk,
                    Icons.Rounded.Event,
                ).forEachIndexed { i, icon ->
                    IconBubble(
                        icon = icon,
                        size = 52.dp,
                        container = if (i == 0) colors.primary else colors.raised,
                        tint = if (i == 0) colors.onPrimary else colors.primaryStrong,
                        modifier = Modifier.border(3.dp, colors.card, CircleShape),
                    )
                }
            }
            Text(
                "DiaRoutine",
                style = MaterialTheme.typography.displaySmall,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Medications, glucose checks, water, walks and appointments — organized into one daily timeline.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
            )
        }

        Text("Let's set up your day", style = MaterialTheme.typography.titleLarge, color = colors.text)
        FormField(
            label = "Your first name",
            value = name,
            onValueChange = { name = it.take(40) },
            placeholder = "Optional",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Glucose units", style = MaterialTheme.typography.titleSmall, color = colors.text)
            SegmentedTabs(GlucoseUnit.entries.map { it.label }, unit.ordinal, onSelect = { unit = GlucoseUnit.entries[it] })
        }

        DiaCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Start with an example routine", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        "Adds sample medications and an appointment so you can explore. Replace them with your own prescribed schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Switch(
                    checked = example,
                    onCheckedChange = { example = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                )
            }
        }

        Text(
            "DiaRoutine keeps you organized. It isn't medical advice — always follow your care team's instructions. " +
                "By continuing you agree to the Terms of Use; the Privacy Policy explains how your data stays on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Row {
            TextButton(onClick = { platform.openUrl(AppLinks.TERMS_OF_USE) }) { Text("Terms of Use", color = colors.primaryStrong) }
            TextButton(onClick = { platform.openUrl(AppLinks.PRIVACY_POLICY) }) { Text("Privacy Policy", color = colors.primaryStrong) }
        }
        PillButton("Get started", onClick = { onDone(name, unit, example) }, modifier = Modifier.fillMaxWidth())
    }
}
