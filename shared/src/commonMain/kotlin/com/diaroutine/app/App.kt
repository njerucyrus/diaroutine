package com.diaroutine.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertChart
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diaroutine.features.backup.BackupSetupSheet
import com.diaroutine.features.backup.BackupViewModel
import com.diaroutine.features.backup.RestoreSheet
import com.diaroutine.features.insights.InsightsRoute
import com.diaroutine.features.lock.LockScreen
import com.diaroutine.features.meds.MedsRoute
import com.diaroutine.features.onboarding.OnboardingResult
import com.diaroutine.features.onboarding.OnboardingScreen
import com.diaroutine.features.profile.ProfileRoute
import com.diaroutine.features.today.TodayRoute
import com.diaroutine.features.track.TrackRoute
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.MaxContentWidth
import com.diaroutine.ui.components.FloatingNavBar
import com.diaroutine.ui.components.NavItem
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.tour.LocalTour
import com.diaroutine.ui.tour.TourController
import com.diaroutine.ui.tour.TourOverlay
import com.diaroutine.ui.tour.TourTarget
import com.diaroutine.ui.tour.tourTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The whole app, for any platform's entry point to show. */
@Composable
fun App(container: AppContainer) {
    DiaTheme {
        CompositionLocalProvider(LocalPlatform provides container.platform) {
            val root = viewModel { RootViewModel(container) }
            val state by root.state.collectAsStateWithLifecycle()
            val backup = viewModel { BackupViewModel(container) }
            val backupState by backup.state.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().background(DiaTheme.colors.background), contentAlignment = Alignment.TopCenter) {
                Crossfade(state.stage, modifier = Modifier.widthIn(max = MaxContentWidth), label = "stage") { stage ->
                    when (stage) {
                        Stage.Loading -> Box(Modifier.fillMaxSize())
                        Stage.Onboarding -> OnboardingScreen(
                            onAuthenticate = root::authenticate,
                            onDone = root::completeOnboarding,
                            onRestoreBackup = backup::startRestore,
                        )
                        Stage.Main -> Box(Modifier.fillMaxSize()) {
                            // Locking covers the app rather than removing it, so tabs and scroll
                            // positions are where the person left them when they unlock.
                            MainShell(
                                container = container,
                                tourPending = state.tourPending,
                                locked = state.locked,
                                onTourDone = root::completeTour,
                                modifier = if (state.locked) Modifier.clearAndSetSemantics { } else Modifier,
                            )
                            if (state.locked) LockScreen(onUnlock = root::unlock)
                        }
                    }
                }
                // Above every stage: a restore started during onboarding finishes in the main app.
                if (!state.locked) {
                    backupState.setup?.let { BackupSetupSheet(it, backup, container.timeZone) }
                    backupState.restore?.let { RestoreSheet(it, backup, container.timeZone) }
                }
            }
        }
    }
}

enum class Stage { Loading, Onboarding, Main }

data class RootState(
    val stage: Stage,
    val locked: Boolean = false,
    val tourPending: Boolean = false,
)

class RootViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<RootState> = combine(container.profile.profile, container.appLock.locked) { profile, locked ->
        when {
            !profile.onboarded -> RootState(Stage.Onboarding)
            locked == null -> RootState(Stage.Loading)
            else -> RootState(Stage.Main, locked = locked, tourPending = !profile.tourCompleted)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState(Stage.Loading))

    fun authenticate(onResult: (Boolean) -> Unit) = container.appLock.authenticate(container.platform::authenticate, onResult)

    fun unlock() = authenticate { }

    fun completeOnboarding(result: OnboardingResult) {
        viewModelScope.launch {
            container.completeOnboarding(result.name, result.unit, result.exampleRoutine, result.appLock)
        }
    }

    fun completeTour() {
        viewModelScope.launch { container.completeTour() }
    }
}

enum class MainTab { Today, Meds, Track, Insights, Profile }

private val tabs = listOf(
    NavItem(MainTab.Today, "Today", Icons.Rounded.Home),
    NavItem(MainTab.Meds, "Medications and appointments", Icons.Rounded.Medication),
    NavItem(MainTab.Track, "Track glucose, water and activity", Icons.Rounded.MonitorHeart),
    NavItem(MainTab.Insights, "Weekly summary", Icons.Rounded.InsertChart),
    NavItem(MainTab.Profile, "Profile and settings", Icons.Rounded.Person),
)

@Composable
private fun MainShell(
    container: AppContainer,
    tourPending: Boolean,
    locked: Boolean,
    onTourDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Today) }
    val tour = remember { TourController(onFinished = onTourDone) }
    val startTour = {
        tab = MainTab.Today
        tour.start()
    }

    // The first time in, once the screen has settled and nothing is covering it.
    LaunchedEffect(tourPending, locked) {
        if (tourPending && !locked && !tour.isActive) {
            tab = MainTab.Today
            delay(700)
            tour.start()
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalTour provides tour) {
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (tour.isActive) Modifier.clearAndSetSemantics { } else Modifier)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "tab",
                ) { current ->
                    when (current) {
                        MainTab.Today -> TodayRoute(container, onOpenMedications = { tab = MainTab.Meds })
                        MainTab.Meds -> MedsRoute(container)
                        MainTab.Track -> TrackRoute(container)
                        MainTab.Insights -> InsightsRoute(container)
                        MainTab.Profile -> ProfileRoute(container, onStartTour = startTour)
                    }
                }
                // Content scrolls out of sight beneath the floating bar rather than behind it.
                val background = DiaTheme.colors.background
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(96.dp + navInset)
                        .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background.copy(alpha = 0.92f), background))),
                )
                FloatingNavBar(
                    items = tabs,
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .tourTarget(tour, TourTarget.NavBar),
                )
            }
            TourOverlay(tour)
        }
    }
}
