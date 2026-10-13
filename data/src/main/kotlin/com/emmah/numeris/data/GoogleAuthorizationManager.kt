package com.emmah.numeris.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

data class GoogleAuthorizationFeedback(
    val authorized: Boolean,
    val message: String,
)

class GoogleAuthorizationManager(
    context: Context,
) {
    private val client = Identity.getAuthorizationClient(context)
    private val scopes = listOf(Scope(SEARCH_SCOPE))
    private var cachedToken: String? = null
    private val feedback = MutableStateFlow<GoogleAuthorizationFeedback?>(null)
    val authorizationFeedback = feedback.asStateFlow()

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
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            authorizationFailure("Google authorization is unavailable", error)
        }.also(::publishFeedback)

    fun completeAuthorization(
        intent: Intent?,
        resultCode: Int,
    ): GoogleAuthorizationState =
        runCatching {
            if (intent == null) {
                GoogleAuthorizationState.Failure("Google authorization was canceled before a result was returned.")
            } else {
                val result = client.getAuthorizationResultFromIntent(intent)
                if (resultCode == Activity.RESULT_OK) {
                    result.toState()
                } else {
                    GoogleAuthorizationState.Failure("Google authorization was canceled.")
                }
            }
        }.getOrElse { error -> authorizationFailure("Google authorization was not completed", error) }
            .also(::publishFeedback)

    private fun authorizationFailure(
        message: String,
        error: Throwable,
    ): GoogleAuthorizationState.Failure {
        val code = (error as? ApiException)?.statusCode
        return GoogleAuthorizationState.Failure(if (code == null) "$message." else "$message (error $code).")
    }

    private fun publishFeedback(state: GoogleAuthorizationState) {
        feedback.value =
            when (state) {
                is GoogleAuthorizationState.Authorized ->
                    // Kellonaika näyttää, että tarkistus tehtiin, vaikka valtuutus oli jo voimassa.
                    GoogleAuthorizationFeedback(
                        true,
                        "Google access confirmed at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}.",
                    )
                is GoogleAuthorizationState.UserActionRequired ->
                    GoogleAuthorizationFeedback(false, "Complete Google authorization to allow Search Console access.")
                is GoogleAuthorizationState.Failure -> GoogleAuthorizationFeedback(false, state.safeMessage)
            }
    }

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
        feedback.value = GoogleAuthorizationFeedback(false, "Google access was disconnected.")
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
    }
}
