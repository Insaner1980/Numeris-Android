package com.emmah.numeris

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.NumerisTheme
import com.emmah.numeris.core.security.AppLockState
import com.emmah.numeris.core.security.BiometricAppLock
import com.emmah.numeris.data.GoogleAuthorizationState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var lockState: AppLockState by mutableStateOf(AppLockState.Locked)
    private var biometricLockEnabled = false
    private lateinit var appLock: BiometricAppLock
    private val lockViewModel: LockViewModel by viewModels()
    private val googleAuthorizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    (application as NumerisApplication).container.googleAuthorization.completeAuthorization(intent)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appLock =
            BiometricAppLock(this, ContextCompat.getMainExecutor(this)) { state ->
                lockState = state
                lockViewModel.unlocked = state == AppLockState.Unlocked
            }
        if (lockViewModel.unlocked) {
            biometricLockEnabled = true
            appLock.restoreUnlockedAfterConfigurationChange()
        }
        lifecycleScope.launch {
            (application as NumerisApplication)
                .container.preferences.preferences
                .map { it.biometricLockEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    val wasEnabled = biometricLockEnabled
                    biometricLockEnabled = enabled
                    if (!enabled) {
                        lockState = AppLockState.Unlocked
                    } else if (!wasEnabled) {
                        lockState = AppLockState.Locked
                        appLock.authenticate()
                    }
                }
        }
        setContent {
            NumerisTheme {
                if (lockState == AppLockState.Unlocked) {
                    NumerisShell(
                        container = (application as NumerisApplication).container,
                        requireBiometric = { action -> appLock.authenticate("Confirm data import", action) },
                        authorizeGoogle = ::authorizeGoogle,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(NumerisSpacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            when (val state = lockState) {
                                AppLockState.Authenticating -> "Waiting for authentication…"
                                is AppLockState.Unavailable -> state.safeMessage
                                else -> "Numeris is locked"
                            },
                        )
                        Button(onClick = appLock::authenticate, enabled = lockState != AppLockState.Authenticating) {
                            Text("Unlock")
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::appLock.isInitialized && biometricLockEnabled) appLock.onForegrounded()
    }

    override fun onStop() {
        if (::appLock.isInitialized && biometricLockEnabled) appLock.onBackgrounded(isChangingConfigurations)
        super.onStop()
    }

    private fun authorizeGoogle() {
        lifecycleScope.launch {
            val manager = (application as NumerisApplication).container.googleAuthorization
            when (val state = manager.authorize()) {
                is GoogleAuthorizationState.UserActionRequired -> {
                    googleAuthorizationLauncher.launch(IntentSenderRequest.Builder(state.pendingIntent).build())
                }
                else -> Unit
            }
        }
    }
}

private class LockViewModel : ViewModel() {
    var unlocked: Boolean = false
}
