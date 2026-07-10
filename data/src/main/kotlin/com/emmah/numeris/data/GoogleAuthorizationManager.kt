package com.emmah.numeris.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface GoogleAuthorizationState {
    data class Authorized(
        val accessToken: String,
    ) : GoogleAuthorizationState

    data class UserActionRequired(
        val pendingIntent: PendingIntent,
    ) : GoogleAuthorizationState

    data class Failure(
        val safeMessage: String,
    ) : GoogleAuthorizationState
}

class GoogleAuthorizationManager(
    context: Context,
) {
    private val client = Identity.getAuthorizationClient(context)
    private val scopes = listOf(Scope(SEARCH_SCOPE), Scope(ANALYTICS_SCOPE))
    private var cachedToken: String? = null

    suspend fun authorize(): GoogleAuthorizationState =
        runCatching {
            client
                .authorize(
                    AuthorizationRequest
                        .builder()
                        .setRequestedScopes(scopes)
                        .build(),
                ).await()
                .toState()
        }.getOrElse { GoogleAuthorizationState.Failure("Google authorization is unavailable.") }

    fun completeAuthorization(intent: Intent): GoogleAuthorizationState =
        runCatching { client.getAuthorizationResultFromIntent(intent).toState() }
            .getOrElse { GoogleAuthorizationState.Failure("Google authorization was not completed.") }

    suspend fun clearCachedToken() {
        val token = cachedToken ?: return
        try {
            client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        } finally {
            cachedToken = null
        }
    }

    suspend fun revokeAccess() {
        client.revokeAccess(RevokeAccessRequest.builder().setScopes(scopes).build()).await()
        cachedToken = null
    }

    @Suppress("ReturnCount")
    private fun AuthorizationResult.toState(): GoogleAuthorizationState {
        if (hasResolution()) {
            return GoogleAuthorizationState.UserActionRequired(requireNotNull(pendingIntent))
        }
        val token = accessToken ?: return GoogleAuthorizationState.Failure("Google did not return an access token.")
        cachedToken = token
        return GoogleAuthorizationState.Authorized(token)
    }

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value -> continuation.resume(value) }
            addOnFailureListener { error -> continuation.resumeWithException(error) }
            addOnCanceledListener { continuation.cancel() }
        }

    private companion object {
        const val SEARCH_SCOPE = "https://www.googleapis.com/auth/webmasters.readonly"
        const val ANALYTICS_SCOPE = "https://www.googleapis.com/auth/analytics.readonly"
    }
}
