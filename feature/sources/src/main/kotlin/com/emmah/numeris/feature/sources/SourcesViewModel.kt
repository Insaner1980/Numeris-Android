@file:Suppress("MaxLineLength")

package com.emmah.numeris.feature.sources

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.emmah.numeris.core.model.Connection
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionRepository
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SaveConnectionCommand
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.DiscoveredTarget
import com.emmah.numeris.data.ImportPreview
import com.emmah.numeris.data.SourceDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourcesUiState(
    val connections: List<Connection> = emptyList(),
    val busyConnection: ConnectionId? = null,
    val importing: Boolean = false,
    val importPreview: ImportPreview? = null,
    val message: String? = null,
    val discovering: Boolean = false,
    val targets: List<com.emmah.numeris.data.DiscoveredTarget> = emptyList(),
)

class SourcesViewModel(
    private val connections: ConnectionRepository,
    private val imports: AndroidImportRepository,
    private val discovery: com.emmah.numeris.data.SourceDiscovery,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SourcesUiState())
    val state: StateFlow<SourcesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            connections.observeConnections().collect { rows -> mutableState.update { it.copy(connections = rows) } }
        }
    }

    fun clearDiscovery() {
        mutableState.update { it.copy(targets = emptyList(), message = null) }
    }

    fun discover(
        source: com.emmah.numeris.core.model.SourceId,
        id: ConnectionId?,
        metadata: Map<String, String>,
        secret: CharArray,
    ) = viewModelScope.launch {
        if (state.value.discovering) {
            secret.fill('\u0000')
            return@launch
        }
        mutableState.update { it.copy(discovering = true, targets = emptyList(), message = null) }
        try {
            val targets = discovery.discover(source, id, metadata, secret)
            mutableState.update {
                it.copy(targets = targets, message = if (targets.isEmpty()) "No accessible targets found." else null)
            }
        } catch (
            cancelled: kotlinx.coroutines.CancellationException,
        ) {
            throw cancelled
        } catch (
            _: Exception,
        ) {
            mutableState.update {
                it.copy(
                    message = "Discovery failed. Check authorization, permissions and account ID.",
                )
            }
        } finally {
            secret.fill('\u0000')
            mutableState.update { it.copy(discovering = false) }
        }
    }

    fun showValidationError() {
        mutableState.update { it.copy(message = "Enter valid domains, HTTPS properties and target mappings.") }
    }

    fun test(id: ConnectionId) =
        viewModelScope.launch {
            if (state.value.busyConnection != null) return@launch
            mutableState.update { it.copy(busyConnection = id, message = null) }
            val result =
                try {
                    connections.test(id)
                } catch (
                    cancelled: kotlinx.coroutines.CancellationException,
                ) {
                    mutableState.update { it.copy(busyConnection = null) }
                    throw cancelled
                } catch (
                    _: Exception,
                ) {
                    ConnectionTestResult.Failure("Connection test failed. Check provider settings.")
                }
            mutableState.update {
                it.copy(
                    busyConnection = null,
                    message =
                        when (result) {
                            ConnectionTestResult.Success -> "Connection test succeeded."
                            is ConnectionTestResult.Failure -> result.safeMessage
                        },
                )
            }
        }

    fun save(command: SaveConnectionCommand) =
        viewModelScope.launch {
            if (state.value.busyConnection != null) {
                command.secrets.values.forEach { it.fill('\u0000') }
                return@launch
            }
            mutableState.update { it.copy(busyConnection = command.id, message = null) }
            try {
                runCatching { connections.save(command) }.fold(
                    onSuccess = { mutableState.update { it.copy(message = "Connection saved.") } },
                    onFailure = { failure ->
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        mutableState.update { it.copy(message = "Connection could not be saved.") }
                    },
                )
            } finally {
                command.secrets.values.forEach { it.fill('\u0000') }
                mutableState.update { it.copy(busyConnection = null) }
            }
        }

    fun delete(id: ConnectionId) =
        viewModelScope.launch {
            if (state.value.busyConnection != null) return@launch
            mutableState.update { it.copy(busyConnection = id, message = null) }
            try {
                runCatching { connections.delete(id) }.fold(
                    onSuccess = { mutableState.update { it.copy(message = "Connection disconnected.") } },
                    onFailure = { failure ->
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        mutableState.update { it.copy(message = "Connection could not be disconnected.") }
                    },
                )
            } finally {
                mutableState.update { it.copy(busyConnection = null) }
            }
        }

    fun inspectImport(
        uri: Uri,
        passphrase: CharArray,
    ) = viewModelScope.launch {
        if (state.value.importing) {
            passphrase.fill('\u0000')
            return@launch
        }
        mutableState.update { it.copy(importing = true, message = null) }
        try {
            runCatching { imports.inspect(uri, passphrase) }.fold(
                onSuccess = { preview -> mutableState.update { it.copy(importPreview = preview) } },
                onFailure = { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    mutableState.update { it.copy(message = "Import package could not be opened.") }
                },
            )
        } finally {
            passphrase.fill('\u0000')
            mutableState.update { it.copy(importing = false) }
        }
    }

    fun replaceImport() =
        viewModelScope.launch {
            if (state.value.importing) return@launch
            val preview = state.value.importPreview ?: return@launch
            mutableState.update { it.copy(importing = true, message = null) }
            try {
                runCatching { imports.replace(preview.id) }.fold(
                    onSuccess = { result ->
                        mutableState.update {
                            it.copy(
                                message =
                                    if (preview.includesConfiguration) {
                                        "Imported ${result.importedRows} report rows, " +
                                            "${preview.connections} connections and ${result.importedSecrets} API keys. " +
                                            "Google sign-in is still required."
                                    } else {
                                        "Imported ${result.importedRows} report rows. Android " +
                                            "connections, secrets and settings were preserved."
                                    },
                            )
                        }
                    },
                    onFailure = { failure ->
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        mutableState.update {
                            it.copy(
                                message = "Import failed. Previous saved data remains available.",
                            )
                        }
                    },
                )
            } finally {
                mutableState.update { it.copy(importing = false, importPreview = null) }
            }
        }

    fun cancelImport() =
        viewModelScope.launch {
            imports.cancel()
            mutableState.update { it.copy(importPreview = null) }
        }

    companion object {
        fun factory(
            connections: ConnectionRepository,
            imports: AndroidImportRepository,
            discovery: com.emmah.numeris.data.SourceDiscovery,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SourcesViewModel(connections, imports, discovery) as T
            }
    }
}
