package com.diaroutine.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaroutine.app.AppContainer
import com.diaroutine.app.AppLinks
import com.diaroutine.domain.model.Profile
import com.diaroutine.platform.ReminderAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: Profile = Profile(),
    val access: ReminderAccess,
    val appVersion: String,
)

sealed interface ProfileAction {
    data class Update(val transform: (Profile) -> Profile) : ProfileAction
    data object ShareSummary : ProfileAction
    data object RequestNotifications : ProfileAction
    data object OpenPreciseTiming : ProfileAction
    data class OpenLink(val url: String) : ProfileAction
    data object ContactSupport : ProfileAction

    /** Re-reads permission state, e.g. on returning from system settings. */
    data object RefreshAccess : ProfileAction
}

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val platform = container.platform
    private val access = MutableStateFlow(platform.reminderAccess())

    val state: StateFlow<ProfileUiState> = combine(container.profile.profile, access) { profile, access ->
        ProfileUiState(isLoading = false, profile = profile, access = access, appVersion = platform.appVersion)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProfileUiState(access = access.value, appVersion = platform.appVersion),
    )

    fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.Update -> viewModelScope.launch { container.profile.update(action.transform) }
            ProfileAction.ShareSummary -> viewModelScope.launch {
                platform.shareText("DiaRoutine weekly summary", container.weeklySummaryText())
            }
            ProfileAction.RequestNotifications -> platform.requestNotificationAccess()
            ProfileAction.OpenPreciseTiming -> platform.openPreciseTimingSettings()
            is ProfileAction.OpenLink -> platform.openUrl(action.url)
            ProfileAction.ContactSupport -> platform.composeEmail(AppLinks.SUPPORT_EMAIL, "DiaRoutine ${platform.appVersion}")
            ProfileAction.RefreshAccess -> {
                access.value = platform.reminderAccess()
                // Permission may have just been granted: schedule with it.
                container.rescheduleSoon()
            }
        }
    }
}
