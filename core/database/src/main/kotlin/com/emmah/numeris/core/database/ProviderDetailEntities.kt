package com.emmah.numeris.core.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "web_analytics_sites")
data class WebAnalyticsSiteEntity(
    @PrimaryKey val domain: String,
    val siteTag: String,
    val discoveredAt: String,
)

@Entity(tableName = "web_analytics_referrers", primaryKeys = ["domain", "date", "referrer"])
data class WebAnalyticsReferrerEntity(
    val domain: String,
    val date: String,
    val referrer: String,
    val visits: Long,
)

@Entity(tableName = "web_analytics_pages", primaryKeys = ["domain", "date", "path"])
data class WebAnalyticsPageEntity(
    val domain: String,
    val date: String,
    val path: String,
    val pageViews: Long,
)

@Entity(tableName = "web_analytics_countries", primaryKeys = ["domain", "date", "country"])
data class WebAnalyticsCountryEntity(
    val domain: String,
    val date: String,
    val country: String,
    val visits: Long,
)

@Entity(
    tableName = "search_page_queries",
    primaryKeys = ["siteUrl", "periodStart", "periodEnd", "page", "query"],
)
data class SearchPageQueryEntity(
    val siteUrl: String,
    val periodStart: String,
    val periodEnd: String,
    val page: String,
    val query: String,
    val clicks: Long,
    val impressions: Long,
    val ctr: Double,
    val position: Double,
)

@Entity(tableName = "pagespeed_audits", primaryKeys = ["url", "strategy", "analysisUtc", "auditId"])
data class PageSpeedAuditEntity(
    val url: String,
    val strategy: String,
    val analysisUtc: String,
    val auditId: String,
    val title: String? = null,
    val score: Double? = null,
    val numericValue: Double? = null,
    val numericUnit: String? = null,
    val displayValue: String? = null,
    val scoreDisplayMode: String? = null,
    val detailsJson: String? = null,
)

@Entity(tableName = "bing_sites")
data class BingSiteEntity(
    @PrimaryKey val siteUrl: String,
    val source: String = "manual",
    val enabled: Boolean = true,
    val discoveredAt: String,
)

@Entity(tableName = "bing_query_stats", primaryKeys = ["siteUrl", "query", "date"])
data class BingQueryStatEntity(
    val siteUrl: String,
    val query: String,
    val date: String,
    val clicks: Long? = null,
    val impressions: Long? = null,
    val avgClickPosition: Double? = null,
    val avgImpressionPosition: Double? = null,
    val rawJson: String,
    val fetchedAt: String,
)

@Entity(tableName = "bing_page_stats", primaryKeys = ["siteUrl", "pageUrl", "date"])
data class BingPageStatEntity(
    val siteUrl: String,
    val pageUrl: String,
    val date: String,
    val clicks: Long? = null,
    val impressions: Long? = null,
    val rawJson: String,
    val fetchedAt: String,
)

@Entity(tableName = "bing_raw_items", primaryKeys = ["method", "siteUrl", "itemKey"])
data class BingRawItemEntity(
    val method: String,
    val siteUrl: String,
    val itemKey: String,
    val rawJson: String,
    val fetchedAt: String,
)

@Entity(
    tableName = "google_analytics_pages",
    primaryKeys = ["domain", "propertyId", "periodStart", "periodEnd", "pagePath"],
)
data class GoogleAnalyticsPageEntity(
    val domain: String,
    val propertyId: String,
    val periodStart: String,
    val periodEnd: String,
    val pagePath: String,
    val activeUsers: Long,
    val sessions: Long,
    val pageViews: Long,
    val engagedSessions: Long,
    val engagementRate: Double,
    val fetchedAt: String,
)

@Entity(
    tableName = "google_analytics_sources",
    primaryKeys = ["domain", "propertyId", "periodStart", "periodEnd", "sourceMedium"],
)
data class GoogleAnalyticsSourceEntity(
    val domain: String,
    val propertyId: String,
    val periodStart: String,
    val periodEnd: String,
    val sourceMedium: String,
    val sessions: Long,
    val activeUsers: Long,
    val keyEvents: Long,
    val fetchedAt: String,
)

@Entity(
    tableName = "google_analytics_events",
    primaryKeys = ["domain", "propertyId", "periodStart", "periodEnd", "eventName"],
)
data class GoogleAnalyticsEventEntity(
    val domain: String,
    val propertyId: String,
    val periodStart: String,
    val periodEnd: String,
    val eventName: String,
    val eventCount: Long,
    val keyEvents: Long,
    val fetchedAt: String,
)

@Entity(tableName = "google_analytics_devices", primaryKeys = ["domain", "propertyId", "date", "deviceCategory"])
data class GoogleAnalyticsDeviceEntity(
    val domain: String,
    val propertyId: String,
    val date: String,
    val deviceCategory: String,
    val sessions: Long,
    val activeUsers: Long,
    val fetchedAt: String,
)
