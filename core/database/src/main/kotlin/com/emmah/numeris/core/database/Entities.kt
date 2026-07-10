package com.emmah.numeris.core.database

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val source: String,
    val status: String = "disconnected",
    val config: String? = null,
    val lastSync: String? = null,
    val lastError: String? = null,
)

@Entity(
    tableName = "cloudflare_traffic",
    primaryKeys = ["domain", "date"],
    indices = [Index(value = ["domain", "date"])],
)
data class CloudflareTrafficEntity(
    val domain: String,
    val date: String,
    val pageviews: Long = 0,
    val uniqueVisitors: Long = 0,
    val requests: Long = 0,
    val cachedRequests: Long = 0,
    val cachedBytes: Long = 0,
    val totalBytes: Long = 0,
    val threats: Long = 0,
    val topCountry: String? = null,
    val topPath: String? = null,
    val fetchedAt: String,
)

@Entity(tableName = "cloudflare_countries", primaryKeys = ["domain", "date", "country"])
data class CloudflareCountryEntity(
    val domain: String,
    val date: String,
    val country: String,
    val visitors: Long,
)

@Entity(tableName = "cloudflare_pages", primaryKeys = ["domain", "date", "path"])
data class CloudflarePageEntity(
    val domain: String,
    val date: String,
    val path: String,
    val requests: Long,
)

@Entity(tableName = "cloudflare_status_codes", primaryKeys = ["domain", "date", "statusCode"])
data class CloudflareStatusEntity(
    val domain: String,
    val date: String,
    val statusCode: Int,
    val requests: Long,
)

@Entity(tableName = "web_analytics_daily", primaryKeys = ["domain", "date"])
data class WebAnalyticsDailyEntity(
    val domain: String,
    val date: String,
    val visits: Long,
    val pageViews: Long,
    val fetchedAt: String,
)

@Entity(tableName = "search_console", indices = [Index(value = ["siteUrl", "date"])])
data class SearchConsoleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteUrl: String,
    val date: String,
    val kind: String = "query",
    val query: String,
    val page: String? = null,
    val clicks: Long = 0,
    val impressions: Long = 0,
    val ctr: Double = 0.0,
    val position: Double = 0.0,
    val country: String? = null,
    val fetchedAt: String,
)

@Entity(tableName = "search_devices", primaryKeys = ["siteUrl", "date", "device"])
data class SearchDeviceEntity(
    val siteUrl: String,
    val date: String,
    val device: String,
    val clicks: Long,
    val impressions: Long,
    val ctr: Double,
    val position: Double,
)

@Entity(tableName = "sitemap_urls", primaryKeys = ["domain", "url"])
data class SitemapUrlEntity(
    val domain: String,
    val url: String,
    val discoveredAt: String,
    val lastSeenAt: String,
    val removedAt: String? = null,
    val lastInspectedAt: String? = null,
    val verdict: String? = null,
    val coverageState: String? = null,
    val indexingState: String? = null,
    val robotsTxtState: String? = null,
    val pageFetchState: String? = null,
    val crawledAs: String? = null,
    val lastCrawlTime: String? = null,
)

@Entity(tableName = "performance_urls", indices = [Index(value = ["url"], unique = true)])
data class PerformanceUrlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val origin: String,
    val source: String = "manual",
    val enabled: Boolean = true,
    val createdAt: String,
)

@Entity(
    tableName = "crux_metric_points",
    primaryKeys = ["targetType", "target", "formFactor", "collectionEnd", "metric"],
)
data class CruxMetricPointEntity(
    val targetType: String,
    val target: String,
    val formFactor: String,
    val collectionStart: String,
    val collectionEnd: String,
    val metric: String,
    val p75: Double? = null,
    val goodDensity: Double? = null,
    val needsImprovementDensity: Double? = null,
    val poorDensity: Double? = null,
    val rawJson: String,
    val fetchedAt: String,
)

@Entity(tableName = "pagespeed_runs", primaryKeys = ["url", "strategy", "analysisUtc"])
data class PageSpeedRunEntity(
    val url: String,
    val strategy: String,
    val analysisUtc: String,
    val finalUrl: String? = null,
    val performanceScore: Double? = null,
    val accessibilityScore: Double? = null,
    val bestPracticesScore: Double? = null,
    val seoScore: Double? = null,
    val lighthouseVersion: String? = null,
    val runtimeError: String? = null,
    val warningsJson: String? = null,
    val rawJson: String,
    val fetchedAt: String,
)

@Entity(tableName = "bing_rank_traffic", primaryKeys = ["siteUrl", "date"])
data class BingRankTrafficEntity(
    val siteUrl: String,
    val date: String,
    val clicks: Long? = null,
    val impressions: Long? = null,
    val rawJson: String,
    val fetchedAt: String,
)

@Entity(tableName = "google_analytics_daily", primaryKeys = ["domain", "propertyId", "date"])
data class GoogleAnalyticsDailyEntity(
    val domain: String,
    val propertyId: String,
    val date: String,
    val activeUsers: Long,
    val sessions: Long,
    val pageViews: Long,
    val engagedSessions: Long,
    val eventCount: Long,
    val engagementRate: Double,
    val fetchedAt: String,
)

@Entity(tableName = "uptime_checks", primaryKeys = ["domain", "checkedAt"])
data class UptimeCheckEntity(
    val domain: String,
    val checkedAt: String,
    val status: String,
    val statusCode: Int? = null,
    val responseMs: Long? = null,
    val errorMessage: String? = null,
)

@Entity(tableName = "sync_runs", indices = [Index(value = ["source", "startedAt"])])
data class SyncRunEntity(
    @PrimaryKey val id: String,
    val source: String,
    val startedAt: String,
    val completedAt: String? = null,
    val state: String,
    val rowsWritten: Long = 0,
    val safeError: String? = null,
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
