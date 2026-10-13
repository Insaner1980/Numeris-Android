package com.emmah.numeris.core.model

import kotlinx.coroutines.flow.Flow

interface SecretStore {
    suspend fun put(
        key: SecretKey,
        value: CharArray,
    )

    suspend fun get(key: SecretKey): CharArray?

    suspend fun contains(key: SecretKey): Boolean

    suspend fun delete(key: SecretKey)

    suspend fun clearAll()
}

sealed interface ConnectionTestResult {
    data object Success : ConnectionTestResult

    data class Failure(
        val safeMessage: String,
    ) : ConnectionTestResult
}

sealed interface SyncResult {
    data class Success(
        val rowsWritten: Int,
    ) : SyncResult

    data class Failure(
        val safeMessage: String,
        val retryable: Boolean,
    ) : SyncResult
}

data class SyncBatchResult(
    val state: SyncState,
)

interface SourceSyncService {
    val source: SourceId

    suspend fun testConnection(connectionId: ConnectionId? = null): ConnectionTestResult

    suspend fun sync(request: SyncRequest): SyncResult
}

interface SyncOrchestrator {
    fun observeState(): Flow<SyncState>

    suspend fun syncAll(request: SyncRequest): SyncBatchResult
}

interface ConnectionRepository {
    fun observeConnections(): Flow<List<Connection>>

    suspend fun save(command: SaveConnectionCommand)

    suspend fun delete(id: ConnectionId)

    suspend fun test(id: ConnectionId): ConnectionTestResult
}
