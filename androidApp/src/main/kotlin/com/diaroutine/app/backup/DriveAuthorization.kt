package com.diaroutine.app.backup

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DriveAuthException(message: String) : Exception(message)

/**
 * Access to the app's own hidden folder in the person's Google Drive (the narrow
 * drive.appdata scope: it can't see any of their other files).
 */
class DriveAuthorization(private val context: Context) {
    /** Set by the visible activity, which can show Google's consent screen. */
    var consentLauncher: ((IntentSender, (Intent?) -> Unit) -> Unit)? = null

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APP_DATA)))
        .build()

    /**
     * An access token. With [interactive], asks for consent if Google needs it; in the
     * background, fails instead so the person can reconnect from the app.
     */
    suspend fun token(interactive: Boolean): String {
        val client = Identity.getAuthorizationClient(context)
        val result = try {
            client.authorize(request).await()
        } catch (e: ApiException) {
            throw DriveAuthException(describe(e))
        }
        if (!result.hasResolution()) return result.accessToken ?: throw DriveAuthException(GENERIC)
        if (!interactive) throw DriveAuthException("Open DiaRoutine and reconnect Google Drive to keep backing up.")
        val launch = consentLauncher ?: throw DriveAuthException(GENERIC)
        val sender = result.pendingIntent?.intentSender ?: throw DriveAuthException(GENERIC)
        val data = suspendCancellableCoroutine<Intent?> { continuation -> launch(sender) { continuation.resume(it) } }
            ?: throw DriveAuthException("Google Drive access wasn't granted.")
        return try {
            client.getAuthorizationResultFromIntent(data).accessToken ?: throw DriveAuthException(GENERIC)
        } catch (e: ApiException) {
            throw DriveAuthException(describe(e))
        }
    }

    private fun describe(e: ApiException): String = when (e.statusCode) {
        CommonStatusCodes.NETWORK_ERROR -> "Couldn't reach Google. Check your connection and try again."
        CommonStatusCodes.CANCELED -> "Google Drive access wasn't granted."
        // The app's Google Cloud OAuth client doesn't match this build's package or signing key.
        CommonStatusCodes.DEVELOPER_ERROR -> "Google Drive backup isn't set up for this version of the app yet."
        else -> GENERIC
    }

    private companion object {
        const val DRIVE_APP_DATA = "https://www.googleapis.com/auth/drive.appdata"
        const val GENERIC = "Couldn't connect to Google Drive. Try again."
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
