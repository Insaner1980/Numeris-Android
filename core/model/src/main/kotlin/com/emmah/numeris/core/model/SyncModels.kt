package com.emmah.numeris.core.model

import java.time.Instant

@JvmInline value class ConnectionId(
    val value: String,
)

@JvmInline value class SecretKey(
    val value: String,
)

enum class SourceId(
    val storageName: String,
) {
    CloudflareZone("cloudflare"),
    CloudflareWebAnalytics("web_analytics"),
    SearchConsole("search_console"),
    GoogleAnalytics("google_analytics"),
    Crux("crux"),
    PageSpeed("pagespeed"),
    Bing("bing_webmaster"),
    Uptime("uptime"),
    Sitemap("sitemap"),
}

fun sourceIdFromStorage(value: String): SourceId? = SourceId.entries.firstOrNull { it.storageName == value }

val SourceId.displayName: String
    get() =
        when (this) {
            SourceId.CloudflareZone -> "Cloudflare Zone Analytics"
            SourceId.CloudflareWebAnalytics -> "Cloudflare Web Analytics"
            SourceId.SearchConsole -> "Google Search Console"
            SourceId.GoogleAnalytics -> "Google Analytics"
            SourceId.Crux -> "Chrome UX Report"
            SourceId.PageSpeed -> "PageSpeed Insights"
            SourceId.Bing -> "Bing Webmaster"
            SourceId.Uptime -> "Uptime"
            SourceId.Sitemap -> "Sitemap"
        }

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
    val metadata: Map<String, String> = emptyMap(),
)

data class SaveConnectionCommand(
    val id: ConnectionId,
    val source: SourceId,
    val displayName: String,
    val metadata: Map<String, String>,
    val secrets: Map<SecretKey, CharArray>,
)
