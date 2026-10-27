package com.diaroutine.app

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A FragmentActivity because the system's BiometricPrompt is hosted in a fragment. */
class MainActivity : FragmentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        diaRoutine.container.rescheduleSoon()
    }

    private val askForNotifications: () -> Unit = {
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private var pendingConsent: ((Intent?) -> Unit)? = null
    private val driveConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pendingConsent?.let { callback ->
            pendingConsent = null
            callback(result.data)
        }
    }

    private val askForDriveConsent: (IntentSender, (Intent?) -> Unit) -> Unit = { sender, onResult ->
        pendingConsent?.invoke(null)
        pendingConsent = onResult
        driveConsent.launch(IntentSenderRequest.Builder(sender).build())
    }

    private lateinit var biometricPrompt: BiometricPrompt
    private var pendingAuthentication: ((Boolean) -> Unit)? = null

    private val promptInfo by lazy {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.verify_title))
            .setSubtitle(getString(R.string.verify_subtitle))
            .setAllowedAuthenticators(AndroidPlatformServices.LOCK_AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
    }

    private val authenticate: ((Boolean) -> Unit) -> Unit = { onResult ->
        finishAuthentication(false)
        pendingAuthentication = onResult
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finishAuthentication(true)

                // Cancelled, locked out or no longer possible; a single unrecognized touch
                // only reaches onAuthenticationFailed and the prompt stays up.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = finishAuthentication(false)
            },
        )
        // Permission and biometric prompts need an activity; the shared app asks through the platform services.
        diaRoutine.platform.permissionRequester = askForNotifications
        diaRoutine.platform.authenticator = authenticate
        diaRoutine.driveAuth.consentLauncher = askForDriveConsent
        diaRoutine.premium.activity = this
        hideFromRecentsWhileLockIsOn()
        setContent { App(diaRoutine.container) }
    }

    override fun onResume() {
        super.onResume()
        // Purchases can change outside the app (renewed, cancelled, refunded).
        diaRoutine.premium.refresh()
    }

    override fun onStart() {
        super.onStart()
        diaRoutine.container.appLock.onAppShown()
    }

    override fun onStop() {
        // A rotation isn't leaving the app.
        if (!isChangingConfigurations) diaRoutine.container.appLock.onAppHidden()
        super.onStop()
    }

    override fun onDestroy() {
        finishAuthentication(false)
        val platform = diaRoutine.platform
        if (platform.permissionRequester === askForNotifications) platform.permissionRequester = null
        if (platform.authenticator === authenticate) platform.authenticator = null
        if (diaRoutine.driveAuth.consentLauncher === askForDriveConsent) diaRoutine.driveAuth.consentLauncher = null
        if (diaRoutine.premium.activity === this) diaRoutine.premium.activity = null
        pendingConsent?.invoke(null)
        pendingConsent = null
        super.onDestroy()
    }

    private fun finishAuthentication(success: Boolean) {
        val callback = pendingAuthentication ?: return
        pendingAuthentication = null
        callback(success)
    }

    /** With app lock on, the recents screen shows a blank card instead of the person's data. */
    private fun hideFromRecentsWhileLockIsOn() {
        if (Build.VERSION.SDK_INT < 33) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                diaRoutine.container.profile.profile
                    .map { it.appLock.enabled }
                    .distinctUntilChanged()
                    .collect { setRecentsScreenshotEnabled(!it) }
            }
        }
    }
}
