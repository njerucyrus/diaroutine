package com.diaroutine.app

import com.diaroutine.domain.model.AppLockSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Decides when DiaRoutine is locked behind the phone's biometrics or screen lock: on
 * launch, and when it comes back after being away longer than the chosen delay.
 * The platform reports when the app is shown and hidden, and does the verifying.
 */
class AppLock(private val clock: Clock) {
    private val state = MutableStateFlow<Boolean?>(null)

    /** Null until the saved setting is known, so nothing private shows before the lock decides. */
    val locked: StateFlow<Boolean?> = state.asStateFlow()

    private var settings = AppLockSettings()
    private var hiddenAt: Instant? = null

    /** True while the system prompt is up; it can hide the app, which mustn't count as leaving. */
    var isAuthenticating: Boolean = false
        private set

    /** Applies the saved setting. The first time (a cold start) it locks straight away if lock is on. */
    fun apply(settings: AppLockSettings) {
        val coldStart = state.value == null
        this.settings = settings
        state.value = when {
            !settings.enabled -> false
            coldStart -> true
            else -> state.value
        }
    }

    fun onAppHidden() {
        if (!isAuthenticating) hiddenAt = clock.now()
    }

    fun onAppShown() {
        val hidden = hiddenAt ?: return
        hiddenAt = null
        if (settings.enabled && !isAuthenticating && clock.now() - hidden >= settings.afterSeconds.seconds) {
            state.value = true
        }
    }

    /**
     * Verifies the person with [authenticator] (the platform's prompt) and unlocks on success.
     * Also used to confirm before app lock is turned on or off.
     */
    fun authenticate(authenticator: ((Boolean) -> Unit) -> Unit, onResult: (Boolean) -> Unit = {}) {
        if (isAuthenticating) return
        isAuthenticating = true
        authenticator { success ->
            isAuthenticating = false
            if (success) state.value = false
            onResult(success)
        }
    }
}
