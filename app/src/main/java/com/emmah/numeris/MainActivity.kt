@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.emmah.numeris.core.designsystem.NumerisTheme
import com.emmah.numeris.data.GoogleAuthorizationState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var initialized by mutableStateOf(false)
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
        lifecycleScope.launch {
            (application as NumerisApplication).initialization.await()
            initialized = true
        }
        setContent {
            if (initialized) {
                NumerisContent(
                    container = (application as NumerisApplication).container,
                    authorizeGoogle = ::authorizeGoogle,
                )
            }
        }
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
    container: AppContainer,
    authorizeGoogle: () -> Unit,
) {
    NumerisTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            NumerisShell(container = container, authorizeGoogle = authorizeGoogle)
        }
    }
}
