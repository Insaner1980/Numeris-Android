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
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.ImportPreview
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
)

class SourcesViewModel(
    private val connections: ConnectionRepository,
    private val imports: AndroidImportRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SourcesUiState())
    val state: StateFlow<SourcesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            connections.observeConnections().collect { rows -> mutableState.update { it.copy(connections = rows) } }
        }
    }

    fun test(id: ConnectionId) =
        viewModelScope.launch {
            mutableState.update { it.copy(busyConnection = id, message = null) }
            val result = connections.test(id)
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
            mutableState.update { it.copy(busyConnection = command.id, message = null) }
            runCatching { connections.save(command) }
                .onSuccess {
                    command.secrets.values.forEach { it.fill('\u0000') }
                    mutableState.update { it.copy(busyConnection = null, message = "Connection saved.") }
                }.onFailure {
                    command.secrets.values.forEach { it.fill('\u0000') }
                    mutableState.update { it.copy(busyConnection = null, message = "Connection could not be saved.") }
                }
        }

    fun delete(id: ConnectionId) =
        viewModelScope.launch {
            mutableState.update { it.copy(busyConnection = id, message = null) }
            runCatching { connections.delete(id) }
                .onSuccess { mutableState.update { it.copy(busyConnection = null, message = "Connection disconnected.") } }
                .onFailure {
                    mutableState.update { it.copy(busyConnection = null, message = "Connection could not be disconnected.") }
                }
        }

    fun inspectImport(
        uri: Uri,
        passphrase: CharArray,
    ) = viewModelScope.launch {
        mutableState.update { it.copy(importing = true, message = null) }
        runCatching { imports.inspect(uri, passphrase) }
            .onSuccess { preview -> mutableState.update { it.copy(importing = false, importPreview = preview) } }
            .onFailure { mutableState.update { it.copy(importing = false, message = "Import package could not be opened.") } }
    }

    fun replaceImport() =
        viewModelScope.launch {
            val preview = state.value.importPreview ?: return@launch
            mutableState.update { it.copy(importing = true, message = null) }
            runCatching { imports.replace(preview.id) }
                .onSuccess { result ->
                    mutableState.update {
                        it.copy(
                            importing = false,
                            importPreview = null,
                            message = "Imported ${result.importedRows} rows and ${result.importedSecrets} secrets.",
                        )
                    }
                }.onFailure {
                    mutableState.update { it.copy(importing = false, importPreview = null, message = "Import failed safely.") }
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
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SourcesViewModel(connections, imports) as T
            }
    }
}
