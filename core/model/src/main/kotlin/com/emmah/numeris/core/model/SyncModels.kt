package com.emmah.numeris.core.model

import java.time.Instant

@JvmInline value class ConnectionId(
    val value: String,
)

@JvmInline value class SecretKey(
    val value: String,
)

enum class SourceId {
    CloudflareZone,
    CloudflareWebAnalytics,
    SearchConsole,
    GoogleAnalytics,
    Crux,
    PageSpeed,
    Bing,
    Uptime,
    Sitemap,
}

enum class ReportingGroup { Traffic, Search, Performance, Health }

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<T>(
        val value: T,
        val isStale: Boolean = false,
    ) : UiState<T>

    data object Empty : UiState<Nothing>

    data class Error<T>(
        val message: String,
        val cached: T? = null,
    ) : UiState<T>
}

sealed interface SyncState {
    data object Idle : SyncState

    data class Running(
        val source: SourceId?,
    ) : SyncState

    data class PartialSuccess(
        val succeeded: Set<SourceId>,
        val failed: Set<SourceId>,
    ) : SyncState

    data class Success(
        val completedAt: Instant,
    ) : SyncState

    data class AuthRequired(
        val source: SourceId,
    ) : SyncState

    data class RateLimited(
        val source: SourceId,
        val retryAt: Instant?,
    ) : SyncState

    data class Failed(
        val source: SourceId?,
        val safeMessage: String,
    ) : SyncState
}

data class SyncRequest(
    val period: Period,
    val domain: String? = null,
    val historyImport: Boolean = false,
)

data class Connection(
    val id: ConnectionId,
    val source: SourceId,
    val displayName: String,
    val configured: Boolean,
    val lastSuccessfulSync: Instant? = null,
    val lastError: String? = null,
)

data class SaveConnectionCommand(
    val id: ConnectionId,
    val source: SourceId,
    val displayName: String,
    val metadata: Map<String, String>,
    val secrets: Map<SecretKey, CharArray>,
)
