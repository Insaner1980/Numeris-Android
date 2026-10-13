@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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
    private var initialized by mutableStateOf(false)
    private var biometricLockEnabled = false
    private lateinit var appLock: BiometricAppLock
    private val lockViewModel: LockViewModel by viewModels()
    private val credentialLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            appLock.onCredentialResult(result.resultCode)
        }
    private val googleAuthorizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            (application as NumerisApplication).container.googleAuthorization.completeAuthorization(
                result.data,
                result.resultCode,
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        appLock =
            BiometricAppLock(this, ContextCompat.getMainExecutor(this), credentialLauncher::launch) { state ->
                lockState = state
                lockViewModel.unlocked = state == AppLockState.Unlocked
            }
        if (lockViewModel.unlocked) {
            biometricLockEnabled = true
            appLock.restoreUnlockedAfterConfigurationChange()
        }
        lifecycleScope.launch {
            (application as NumerisApplication).initialization.await()
            initialized = true
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
            if (initialized) {
                NumerisContent(
                    lockState = lockState,
                    appLock = appLock,
                    container = (application as NumerisApplication).container,
                    authorizeGoogle = ::authorizeGoogle,
                )
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

@Composable
@Suppress("FunctionNaming")
private fun NumerisContent(
    lockState: AppLockState,
    appLock: BiometricAppLock,
    container: AppContainer,
    authorizeGoogle: () -> Unit,
) {
    NumerisTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (lockState == AppLockState.Unlocked) {
                NumerisShell(
                    container = container,
                    requireBiometric = { action -> appLock.authenticate("Confirm data import", action) },
                    authorizeGoogle = authorizeGoogle,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(NumerisSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when (lockState) {
                            AppLockState.Authenticating -> "Waiting for authentication…"
                            is AppLockState.Unavailable -> lockState.safeMessage
                            else -> "Numeris is locked"
                        },
                    )
                    Button(
                        onClick = appLock::authenticate,
                        enabled = lockState != AppLockState.Authenticating,
                    ) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}

internal class LockViewModel : ViewModel() {
    var unlocked: Boolean = false
}
