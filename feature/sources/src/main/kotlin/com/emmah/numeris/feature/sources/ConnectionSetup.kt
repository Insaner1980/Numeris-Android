package com.emmah.numeris.feature.sources

import com.emmah.numeris.core.model.Connection
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.displayName

internal data class ConnectionSetup(
    val source: SourceId,
    val fields: List<String>,
    val secretField: String? = null,
    val description: String,
) {
    val title: String get() = source.displayName
}

/** Lähteet ryhmiteltyinä Sources-näkymän osioihin. */
internal enum class SourceGroup(
    val title: String,
) {
    Analytics("Analytics"),
    Search("Search"),
    Performance("Performance"),
    Health("Health"),
}

internal val SourceId.group: SourceGroup
    get() =
        when (this) {
            SourceId.CloudflareZone, SourceId.CloudflareWebAnalytics -> SourceGroup.Analytics
            SourceId.SearchConsole, SourceId.Bing -> SourceGroup.Search
            SourceId.Crux, SourceId.PageSpeed -> SourceGroup.Performance
            SourceId.Uptime, SourceId.Sitemap -> SourceGroup.Health
        }

internal val connectionSetups =
    listOf(
        ConnectionSetup(
            SourceId.CloudflareZone,
            listOf("domain", "zoneId"),
            "API token",
            "Visitors, requests, cache, and security for one zone",
        ),
        ConnectionSetup(
            SourceId.CloudflareWebAnalytics,
            listOf("accountId", "siteMappings"),
            "API token",
            "Privacy-first visits, pages, and referrers",
        ),
        ConnectionSetup(
            SourceId.SearchConsole,
            listOf("sites (comma separated)"),
            description = "Google clicks, impressions, and indexing",
        ),
        ConnectionSetup(SourceId.Crux, emptyList(), API_KEY_LABEL, "Real-user Core Web Vitals"),
        ConnectionSetup(SourceId.PageSpeed, emptyList(), API_KEY_LABEL, "Lighthouse lab scores"),
        ConnectionSetup(
            SourceId.Bing,
            listOf("sites (comma separated)"),
            API_KEY_LABEL,
            "Bing clicks, impressions, and crawl reports",
        ),
        ConnectionSetup(SourceId.Uptime, emptyList(), description = "Response checks for your sites"),
        ConnectionSetup(SourceId.Sitemap, emptyList(), description = "Sitemap URL discovery"),
    )

/** Yhteysrivin ⋮-valikon toiminnot. */
internal class ConnectionActions(
    val busy: Boolean,
    val onTest: (Connection) -> Unit,
    val onDelete: (Connection) -> Unit,
    val onEdit: (Connection) -> Unit,
)

private const val API_KEY_LABEL = "API key"
