package com.diaroutine.features.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.diaroutine.app.AppLinks
import com.diaroutine.domain.EntryState
import com.diaroutine.domain.model.GlucoseUnit
import com.diaroutine.platform.LockAvailability
import com.diaroutine.ui.BackGesture
import com.diaroutine.ui.EntryStateLabel
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.FormField
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.InfoRow
import com.diaroutine.ui.components.NeedleGauge
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.SegmentedTabs
import com.diaroutine.ui.components.StatTile
import com.diaroutine.ui.components.diaSwitchColors
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.coroutines.launch

/** What the person chose while setting up. */
data class OnboardingResult(
    val name: String,
    val unit: GlucoseUnit,
    val exampleRoutine: Boolean,
    val appLock: Boolean,
)

enum class OnboardingStep { Intro, Setup, Lock, Reminders }

/**
 * First run: a few slides on what DiaRoutine does, the basic setup, then an offer of
 * app lock and of notification permission — each only when it applies to this phone.
 */
@Composable
fun OnboardingScreen(
    onAuthenticate: ((Boolean) -> Unit) -> Unit,
    onDone: (OnboardingResult) -> Unit,
    /** Offered on the setup step to people who've used DiaRoutine before. */
    onRestoreBackup: (() -> Unit)? = null,
    initialStep: OnboardingStep = OnboardingStep.Intro,
    initialSlide: Int = 0,
) {
    val platform = LocalPlatform.current
    val steps = remember {
        buildList {
            add(OnboardingStep.Intro)
            add(OnboardingStep.Setup)
            if (platform.lockAvailability() != LockAvailability.Unsupported) add(OnboardingStep.Lock)
            if (!platform.reminderAccess().notificationsAllowed) add(OnboardingStep.Reminders)
        }
    }
    var stepIndex by rememberSaveable { mutableIntStateOf(steps.indexOf(initialStep).coerceAtLeast(0)) }
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(GlucoseUnit.MgDl) }
    var example by rememberSaveable { mutableStateOf(true) }
    var appLock by rememberSaveable { mutableStateOf(false) }

    val finish: () -> Unit = { onDone(OnboardingResult(name, unit, example, appLock)) }
    val next: () -> Unit = { if (stepIndex < steps.lastIndex) stepIndex += 1 else finish() }
    val back: () -> Unit = { if (stepIndex > 0) stepIndex -= 1 }

    val pager = rememberPagerState(initialPage = initialSlide) { Slides.size }
    val scope = rememberCoroutineScope()
    BackGesture(enabled = stepIndex > 0 || pager.currentPage > 0) {
        if (stepIndex > 0) back() else scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    AnimatedContent(
        targetState = steps[stepIndex],
        transitionSpec = {
            val forward = steps.indexOf(targetState) > steps.indexOf(initialState)
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        label = "onboarding",
        modifier = Modifier.fillMaxSize().background(DiaTheme.colors.background),
    ) { step ->
        when (step) {
            OnboardingStep.Intro -> IntroStep(
                pagerPage = pager.currentPage,
                pager = { HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f)) { Slide(Slides[it]) } },
                onSkip = next,
                onNext = {
                    if (pager.currentPage < Slides.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else next()
                },
            )
            OnboardingStep.Setup -> SetupStep(
                name = name,
                onName = { name = it.take(40) },
                unit = unit,
                onUnit = { unit = it },
                example = example,
                onExample = { example = it },
                onBack = back,
                onContinue = next,
                onRestoreBackup = onRestoreBackup,
            )
            OnboardingStep.Lock -> LockStep(
                enabled = appLock,
                onAuthenticate = onAuthenticate,
                onEnabled = { appLock = true; next() },
                onSkip = next,
                onBack = back,
            )
            OnboardingStep.Reminders -> RemindersStep(onFinish = finish, onBack = back)
        }
    }
}

// Intro slides ---------------------------------------------------------------------------

private data class SlideContent(val title: String, val body: String, val art: @Composable () -> Unit)

private val Slides = listOf(
    SlideContent(
        "Your diabetes day, in one timeline",
        "Doses, glucose checks, walks and appointments in time order, so you always know what's done and what's next.",
    ) { TimelineArt() },
    SlideContent(
        "Reminders that match your prescription",
        "A reminder when each dose is due — mark it taken right from the notification.",
    ) { ReminderArt() },
    SlideContent(
        "See your progress",
        "A weekly summary of doses taken, streaks and time in range, ready to share with someone who supports you.",
    ) { ProgressArt() },
    SlideContent(
        "Private by design",
        "No account and no ads. Everything stays on your phone, and you can lock the app with your fingerprint or face.",
    ) { PrivacyArt() },
)

@Composable
private fun IntroStep(
    pagerPage: Int,
    pager: @Composable ColumnScope.() -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = DiaTheme.colors
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "DiaRoutine",
                style = MaterialTheme.typography.titleMedium,
                color = colors.primaryStrong,
                modifier = Modifier.padding(start = Spacing.md).weight(1f),
            )
            TextButton(onClick = onSkip) { Text("Skip", color = colors.textMuted) }
        }
        pager()
        PagerDots(count = Slides.size, current = pagerPage, modifier = Modifier.padding(vertical = Spacing.lg))
        PillButton(
            text = if (pagerPage == Slides.lastIndex) "Get started" else "Next",
            onClick = onNext,
            modifier = Modifier.padding(horizontal = Spacing.screen).widthIn(max = 480.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun Slide(content: SlideContent) {
    val colors = DiaTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            content.art()
        }
        Text(
            content.title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
        )
        Text(
            content.body,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = DiaTheme.colors
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(DiaShapes.pill)
                    .background(if (i == current) colors.primary else colors.outline),
            )
        }
    }
}

// Slide illustrations, drawn with the app's own components ----------------------------

private data class ArtRow(val icon: ImageVector, val title: String, val time: String, val state: EntryState)

@Composable
private fun TimelineArt() {
    val colors = DiaTheme.colors
    DiaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(
            ArtRow(Icons.Rounded.Medication, "Metformin", "8:00 AM", EntryState.Done),
            ArtRow(Icons.Rounded.Bloodtype, "Glucose check", "1:00 PM", EntryState.DueNow),
            ArtRow(Icons.AutoMirrored.Rounded.DirectionsWalk, "Evening walk", "6:00 PM", EntryState.Upcoming),
            ArtRow(Icons.Rounded.Event, "Eye exam", "Fri 10:30 AM", EntryState.Upcoming),
        ).forEach { row ->
            Row(
                Modifier.fillMaxWidth().clip(DiaShapes.inner).background(colors.raised).padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IconBubble(row.icon, size = 36.dp, container = colors.card)
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1)
                    Text(row.time, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
                }
                EntryStateLabel(row.state)
            }
        }
    }
}

@Composable
private fun ReminderArt() {
    val colors = DiaTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NotificationMock("Time for Metformin", "500 mg · 1 tablet · With breakfast", action = "Mark as taken")
        NotificationMock("Glucose check", "Fasting reading planned for 7:00 AM", modifier = Modifier.padding(horizontal = Spacing.lg), faded = true)
        NotificationMock("Refill Atorvastatin soon", "6 tablets left", modifier = Modifier.padding(horizontal = Spacing.xxxl), faded = true)
    }
}

@Composable
private fun NotificationMock(title: String, body: String, modifier: Modifier = Modifier, action: String? = null, faded: Boolean = false) {
    val colors = DiaTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(DiaShapes.inner)
            .background(if (faded) colors.card else colors.raised)
            .border(1.dp, colors.outline, DiaShapes.inner)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(colors.primary))
            Text("DiaRoutine · now", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
        Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = colors.primaryStrong, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

@Composable
private fun ProgressArt() {
    DiaCard(Modifier.fillMaxWidth()) {
        NeedleGauge(progress = 0.86f, description = "", modifier = Modifier.padding(horizontal = Spacing.xl))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile("5 days", "Current streak", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Rounded.LocalFireDepartment)
            StatTile("86%", "Doses taken", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Rounded.Check)
        }
    }
}

@Composable
private fun PrivacyArt() {
    val colors = DiaTheme.colors
    DiaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconBubble(Icons.Rounded.Fingerprint, size = 72.dp, container = colors.primary, tint = colors.onPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
        InfoRow(Icons.Rounded.Verified, "No account needed", iconTint = colors.primaryStrong)
        InfoRow(Icons.Rounded.PhonelinkLock, "Your data stays on your phone", iconTint = colors.primaryStrong)
        InfoRow(Icons.Rounded.Shield, "No ads, no tracking", iconTint = colors.primaryStrong)
    }
}

// Steps after the slides ---------------------------------------------------------------

/** A titled step with a back button, a hero icon and its content. */
@Composable
private fun StepLayout(
    title: String,
    body: String?,
    onBack: () -> Unit,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DiaTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.xs)) { Text("Back", color = colors.primaryStrong) }
        if (icon != null) {
            IconBubble(icon, size = 88.dp, container = colors.card, tint = colors.primary, modifier = Modifier.padding(top = Spacing.lg))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.text, modifier = Modifier.semantics { heading() })
        if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
        content()
    }
}

@Composable
private fun SetupStep(
    name: String,
    onName: (String) -> Unit,
    unit: GlucoseUnit,
    onUnit: (GlucoseUnit) -> Unit,
    example: Boolean,
    onExample: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRestoreBackup: (() -> Unit)?,
) {
    val colors = DiaTheme.colors
    val platform = LocalPlatform.current
    StepLayout(title = "Let's set up your day", body = "You can change any of this later in Profile.", onBack = onBack) {
        if (onRestoreBackup != null) {
            PillButton(
                text = "Used DiaRoutine before? Restore your backup",
                onClick = onRestoreBackup,
                style = PillStyle.Raised,
                leadingIcon = Icons.Rounded.Restore,
                trailingIcon = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FormField(
            label = "Your first name",
            value = name,
            onValueChange = onName,
            placeholder = "Optional",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Glucose units", style = MaterialTheme.typography.titleSmall, color = colors.text)
            SegmentedTabs(GlucoseUnit.entries.map { it.label }, unit.ordinal, onSelect = { onUnit(GlucoseUnit.entries[it]) })
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
                Switch(checked = example, onCheckedChange = onExample, colors = diaSwitchColors())
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
        PillButton("Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LockStep(
    enabled: Boolean,
    onAuthenticate: ((Boolean) -> Unit) -> Unit,
    onEnabled: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = DiaTheme.colors
    val platform = LocalPlatform.current
    var availability by remember { mutableStateOf(platform.lockAvailability()) }
    var failed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        availability = platform.lockAvailability()
        onPauseOrDispose { }
    }
    StepLayout(
        title = "Keep your health data private",
        body = "Turn on app lock and DiaRoutine asks for your fingerprint, face or screen lock each time you open it.",
        onBack = onBack,
        icon = Icons.Rounded.Fingerprint,
    ) {
        when {
            enabled -> {
                InfoRow(Icons.Rounded.Check, "App lock is on", iconTint = colors.sage)
                PillButton("Continue", onClick = onEnabled, modifier = Modifier.fillMaxWidth())
            }
            availability == LockAvailability.NoScreenLock -> {
                Text(
                    "To use app lock, first set a screen lock (PIN, pattern or password) on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.amber,
                )
                PillButton("Open security settings", onClick = platform::openSecuritySettings, style = PillStyle.Raised, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Skip for now", color = colors.textMuted)
                }
            }
            else -> {
                if (failed) {
                    Text("That didn't work. Try again, or skip for now.", style = MaterialTheme.typography.bodyMedium, color = colors.coralStrong)
                }
                PillButton(
                    text = "Turn on app lock",
                    onClick = { onAuthenticate { ok -> if (ok) onEnabled() else failed = true } },
                    leadingIcon = Icons.Rounded.Fingerprint,
                    trailingIcon = null,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                    Text("Not now", color = colors.textMuted)
                }
                Text(
                    "Your fingerprint and face are checked by your phone. DiaRoutine never sees them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RemindersStep(onFinish: () -> Unit, onBack: () -> Unit) {
    val colors = DiaTheme.colors
    val platform = LocalPlatform.current
    var allowed by remember { mutableStateOf(platform.reminderAccess().notificationsAllowed) }
    var asked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = platform.reminderAccess().notificationsAllowed
        onPauseOrDispose { }
    }
    // Once permission comes back granted, there's nothing left to do here.
    LaunchedEffect(allowed, asked) { if (allowed && asked) onFinish() }
    StepLayout(
        title = "Never miss a dose",
        body = "Allow notifications so DiaRoutine can remind you when doses, glucose checks and appointments are due.",
        onBack = onBack,
        icon = Icons.Rounded.NotificationsActive,
    ) {
        if (allowed) {
            InfoRow(Icons.Rounded.Check, "Reminders are on", iconTint = colors.sage)
            PillButton("Finish setup", onClick = onFinish, modifier = Modifier.fillMaxWidth())
        } else {
            PillButton(
                text = "Allow notifications",
                onClick = { asked = true; platform.requestNotificationAccess() },
                leadingIcon = Icons.Rounded.NotificationsActive,
                trailingIcon = null,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                Text("Not now", color = colors.textMuted)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "You can change this any time in Profile → Reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
