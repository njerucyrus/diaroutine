package com.diaroutine.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diaroutine.ui.BackGesture
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing

/** Things on screen the tour can point at. */
enum class TourTarget { Days, Summary, QuickTiles, Bell, Timeline, NavBar }

/** One stop on the tour; without a [target] the card sits in the middle of the screen. */
data class TourStep(val target: TourTarget?, val title: String, val body: String)

val AppTourSteps = listOf(
    TourStep(
        null,
        "Welcome to your day",
        "A quick look at where everything lives. It takes about 30 seconds — you can replay it any time from Profile.",
    ),
    TourStep(TourTarget.Days, "Move between days", "Tap any day to look back at what you did or ahead at what's planned."),
    TourStep(TourTarget.Summary, "Today at a glance", "See how much of today's plan is done and what's coming up next."),
    TourStep(TourTarget.QuickTiles, "Log in one tap", "Add a glass of water, log a glucose reading or update your steps from here."),
    TourStep(TourTarget.Bell, "Catch up quickly", "When something is due or overdue, the bell shows how many. Tap it to jump straight there."),
    TourStep(
        TourTarget.Timeline,
        "Your daily timeline",
        "Every dose, check, walk and appointment in order. Tap the check when you take a dose — tap again to undo.",
    ),
    TourStep(
        TourTarget.NavBar,
        "Everything else",
        "Medications and appointments, glucose tracking, your weekly summary, and Profile for reminders, caregiver sharing and app lock.",
    ),
)

/**
 * Steps through a tour. Screens report where their targets are with [tourTarget];
 * the [TourOverlay] spotlights the current one.
 */
@Stable
class TourController(
    val steps: List<TourStep> = AppTourSteps,
    private val onFinished: () -> Unit = {},
) {
    var index: Int? by mutableStateOf(null)
        private set

    val current: TourStep? get() = index?.let(steps::getOrNull)
    val isActive: Boolean get() = index != null

    private val bounds = mutableStateMapOf<TourTarget, Rect>()

    fun start(at: Int = 0) {
        index = at.coerceIn(steps.indices)
    }

    fun next() {
        val i = index ?: return
        if (i < steps.lastIndex) index = i + 1 else finish()
    }

    fun back() {
        val i = index ?: return
        if (i > 0) index = i - 1
    }

    /** Ends the tour, whether it was completed or skipped. */
    fun finish() {
        if (index == null) return
        index = null
        onFinished()
    }

    fun report(target: TourTarget, rect: Rect) {
        bounds[target] = rect
    }

    fun boundsOf(target: TourTarget): Rect? = bounds[target]
}

val LocalTour = staticCompositionLocalOf<TourController?> { null }

/** Lets the tour find this element on screen. */
fun Modifier.tourTarget(tour: TourController?, target: TourTarget): Modifier =
    if (tour == null) this else onGloballyPositioned { tour.report(target, it.boundsInRoot()) }

/** Dims the screen around the current step's target and explains it in a card. */
@Composable
fun TourOverlay(tour: TourController) {
    val step = tour.current ?: return
    val index = tour.index ?: return
    val density = LocalDensity.current
    val colors = DiaTheme.colors
    val ring = colors.primary

    BackGesture(onBack = tour::finish)

    // Targets report window positions; the overlay may itself be offset (a centred tablet column).
    var origin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // The spotlit content is shown, not used: taps go nowhere until the tour ends.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .onGloballyPositioned { origin = it.positionInRoot() }
            .semantics { paneTitle = "App tour" },
    ) {
        val screenHeight = constraints.maxHeight.toFloat()
        val pad = with(density) { 8.dp.toPx() }
        val hole = step.target?.let(tour::boundsOf)
            ?.takeIf { it.height > 0f && it.bottom > 0f && it.top < screenHeight }
            ?.translate(-origin)
            ?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }

        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = 0.66f))
            if (hole != null) {
                val radius = CornerRadius(24.dp.toPx())
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, radius, blendMode = BlendMode.Clear)
                drawRoundRect(ring, hole.topLeft, hole.size, radius, style = Stroke(2.dp.toPx()))
            }
        }

        val card = Modifier
            .padding(horizontal = Spacing.screen)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
        val gap = 12.dp
        when {
            hole == null -> TourCard(step, index, tour, card.align(Alignment.Center))
            hole.center.y < screenHeight / 2 -> TourCard(
                step, index, tour,
                card.align(Alignment.TopCenter).padding(top = with(density) { hole.bottom.toDp() } + gap),
            )
            else -> TourCard(
                step, index, tour,
                card.align(Alignment.BottomCenter).padding(bottom = with(density) { (screenHeight - hole.top).toDp() } + gap),
            )
        }
    }
}

@Composable
private fun TourCard(step: TourStep, index: Int, tour: TourController, modifier: Modifier) {
    val colors = DiaTheme.colors
    val last = index == tour.steps.lastIndex
    DiaCard(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.raised,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconBubble(Icons.Rounded.Explore, size = 36.dp, container = colors.primarySoft, tint = colors.primaryStrong)
            Column(Modifier.weight(1f)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text("${index + 1} of ${tour.steps.size}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!last) {
                TextButton(onClick = tour::finish) { Text("Skip tour", color = colors.textMuted) }
            }
            if (index > 0) {
                TextButton(onClick = tour::back) { Text("Back", color = colors.primaryStrong) }
            }
            PillButton(
                text = if (last) "Done" else "Next",
                onClick = tour::next,
                trailingIcon = null,
                modifier = Modifier.weight(1f).padding(start = Spacing.sm),
            )
        }
    }
}
