package com.emmah.numeris.core.security

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

sealed interface AppLockState {
    data object Locked : AppLockState

    data object Authenticating : AppLockState

    data object Unlocked : AppLockState

    data class Unavailable(
        val safeMessage: String,
    ) : AppLockState
}

class BiometricAppLock(
    private val activity: FragmentActivity,
    private val executor: Executor,
    private val onStateChanged: (AppLockState) -> Unit,
) {
    private var backgroundedAtMillis: Long? = null
    private var state: AppLockState = AppLockState.Locked

    fun authenticate(
        title: String = "Unlock Numeris",
        onAuthenticated: () -> Unit = {},
    ) {
        if (state == AppLockState.Authenticating) return
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            update(AppLockState.Unavailable("Set up a screen lock or biometrics to unlock Numeris."))
            return
        }
        update(AppLockState.Authenticating)
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    update(AppLockState.Unlocked)
                    onAuthenticated()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    if (errorCode != BiometricPrompt.ERROR_CANCELED) update(AppLockState.Locked)
                }

                override fun onAuthenticationFailed() {
                    update(AppLockState.Authenticating)
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setSubtitle("Use your device screen lock or biometrics")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    fun onBackgrounded(isChangingConfigurations: Boolean) {
        if (!isChangingConfigurations && state == AppLockState.Unlocked) {
            backgroundedAtMillis = SystemClock.elapsedRealtime()
        }
    }

    fun onForegrounded() {
        val backgroundedAt = backgroundedAtMillis ?: return
        backgroundedAtMillis = null
        if (SystemClock.elapsedRealtime() - backgroundedAt >= RELOCK_AFTER_MILLIS) {
            update(AppLockState.Locked)
            authenticate()
        }
    }

    fun restoreUnlockedAfterConfigurationChange() {
        update(AppLockState.Unlocked)
    }

    private fun update(newState: AppLockState) {
        state = newState
        onStateChanged(newState)
    }

    private companion object {
        const val RELOCK_AFTER_MILLIS = 5 * 60 * 1000L
    }
}
