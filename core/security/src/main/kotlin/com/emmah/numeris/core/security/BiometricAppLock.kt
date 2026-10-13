package com.emmah.numeris.core.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
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
    private val launchCredential: (Intent) -> Unit,
    private val onStateChanged: (AppLockState) -> Unit,
) {
    private var backgroundedAtMillis: Long? = null
    private var state: AppLockState = AppLockState.Locked
    private var pendingAction: (() -> Unit)? = null

    fun onCredentialResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            completeAuthentication()
        } else {
            pendingAction = null
            update(AppLockState.Locked)
        }
    }

    fun authenticate(
        title: String = "Unlock Numeris",
        onAuthenticated: () -> Unit = {},
    ) {
        if (state == AppLockState.Authenticating) return
        val legacyCredential = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                if (legacyCredential) 0 else BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            if (legacyCredential && keyguard.isDeviceSecure) {
                pendingAction = onAuthenticated
                authenticateCredential(title, keyguard)
            } else {
                update(AppLockState.Unavailable("Set up a screen lock or biometrics to unlock Numeris."))
            }
            return
        }
        pendingAction = onAuthenticated
        update(AppLockState.Authenticating)
        authenticateBiometric(title, legacyCredential, keyguard, authenticators)
    }

    private fun authenticateBiometric(
        title: String,
        legacyCredential: Boolean,
        keyguard: KeyguardManager,
        authenticators: Int,
    ) {
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    completeAuthentication()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    val useCredential = legacyCredential && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    if (useCredential && keyguard.isDeviceSecure) {
                        authenticateCredential(title, keyguard)
                    } else {
                        pendingAction = null
                        update(AppLockState.Locked)
                    }
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
                .apply { if (legacyCredential) setNegativeButtonText("Use screen lock") }
                .build(),
        )
    }

    @Suppress("DEPRECATION")
    private fun authenticateCredential(
        title: String,
        keyguard: KeyguardManager,
    ) {
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, "Use your device screen lock")
        if (intent == null) {
            pendingAction = null
            update(AppLockState.Unavailable("Set up a screen lock to unlock Numeris."))
            return
        }
        update(AppLockState.Authenticating)
        launchCredential(intent)
    }

    private fun completeAuthentication() {
        val action = pendingAction
        pendingAction = null
        update(AppLockState.Unlocked)
        action?.invoke()
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
