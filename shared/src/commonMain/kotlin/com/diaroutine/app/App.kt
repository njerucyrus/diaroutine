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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertChart
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diaroutine.domain.model.GlucoseUnit
import com.diaroutine.features.insights.InsightsRoute
import com.diaroutine.features.meds.MedsRoute
import com.diaroutine.features.onboarding.OnboardingScreen
import com.diaroutine.features.profile.ProfileRoute
import com.diaroutine.features.today.TodayRoute
import com.diaroutine.features.track.TrackRoute
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.FloatingNavBar
import com.diaroutine.ui.components.NavItem
import com.diaroutine.ui.theme.DiaTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The whole app, for any platform's entry point to show. */
@Composable
fun App(container: AppContainer) {
    DiaTheme {
        CompositionLocalProvider(LocalPlatform provides container.platform) {
            val root = viewModel { RootViewModel(container) }
            val stage by root.stage.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().background(DiaTheme.colors.background)) {
                Crossfade(stage, label = "stage") { current ->
                    when (current) {
                        Stage.Loading -> Box(Modifier.fillMaxSize())
                        Stage.Onboarding -> OnboardingScreen(onDone = root::completeOnboarding)
                        Stage.Ready -> MainShell(container)
                    }
                }
            }
        }
    }
}

enum class Stage { Loading, Onboarding, Ready }

class RootViewModel(private val container: AppContainer) : ViewModel() {
    val stage: StateFlow<Stage> = container.profile.profile
        .map { if (it.onboarded) Stage.Ready else Stage.Onboarding }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Stage.Loading)

    fun completeOnboarding(name: String, unit: GlucoseUnit, withExampleRoutine: Boolean) {
        viewModelScope.launch {
            container.completeOnboarding(name, unit, withExampleRoutine)
            // Ask once the person has seen what reminders are for.
            container.platform.requestNotificationAccess()
        }
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
private fun MainShell(container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Today) }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
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
                MainTab.Profile -> ProfileRoute(container)
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
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        )
    }
}
