package com.emmah.numeris.core.model

import java.time.LocalDate

data class DatedValue(
    val date: LocalDate,
    val value: Double?,
)

data class ReportSeries(
    val label: String,
    val unit: String,
    val points: List<DatedValue>,
    val previous: Boolean = false,
)

data class Metric(
    val label: String,
    val value: Double?,
    val unit: String = "count",
    val change: Double? = null,
    val detail: String? = null,
    val higherIsBetter: Boolean = true,
)

data class Breakdown(
    val key: String,
    val label: String,
    val value: Long,
    val domain: String = "",
)

data class TrafficDay(
    val domain: String,
    val date: LocalDate,
    val visitors: Long,
    val pageViews: Long,
    val requests: Long,
    val cachedRequests: Long,
    val cachedBytes: Long,
    val totalBytes: Long,
    val threats: Long,
)

data class StatusDay(
    val date: LocalDate,
    val code: Int,
    val requests: Long,
)

data class SearchObservation(
    val domain: String,
    val date: LocalDate,
    val kind: String,
    val identity: String,
    val clicks: Long?,
    val impressions: Long?,
    val position: Double? = null,
)

data class SearchRow(
    val domain: String,
    val identity: String,
    val clicks: Long?,
    val impressions: Long?,
    val ctr: Double?,
    val position: Double?,
    val previousClicks: Long? = null,
) {
    val key: String get() = "$domain:$identity"
}

data class SitemapPage(
    val domain: String,
    val url: String,
    val lastSeen: String,
    val removedAt: String?,
    val inspectedAt: String?,
    val verdict: String?,
    val coverage: String?,
    val indexing: String?,
    val robots: String?,
    val fetch: String?,
    val crawledAs: String?,
    val lastCrawl: String?,
)

data class MeasuredUrl(
    val id: Long,
    val url: String,
    val enabled: Boolean,
    val source: String,
)

data class FieldMetric(
    val target: String,
    val targetType: String,
    val device: String,
    val metric: String,
    val start: LocalDate,
    val end: LocalDate,
    val p75: Double?,
    val good: Double?,
    val needsWork: Double?,
    val poor: Double?,
)

data class LabRun(
    val url: String,
    val device: String,
    val time: String,
    val performance: Double?,
    val accessibility: Double?,
    val bestPractices: Double?,
    val seo: Double?,
    val finalUrl: String?,
    val version: String?,
    val error: String?,
    val warnings: String?,
)

data class LabAudit(
    val url: String,
    val device: String,
    val time: String,
    val id: String,
    val title: String,
    val score: Double?,
    val displayValue: String?,
    val details: String?,
)

data class Probe(
    val domain: String,
    val time: String,
    val status: String,
    val code: Int?,
    val responseMs: Long?,
    val error: String?,
)

data class CrawlReport(
    val domain: String,
    val method: String,
    val key: String,
    val time: String,
    val detail: String,
    val summary: String = "Stored report",
)

data class PageQueryWindow(
    val domain: String,
    val page: String,
    val start: String,
    val end: String,
    val row: SearchRow,
)

data class TrafficReport(
    val range: DateRange,
    val days: List<TrafficDay> = emptyList(),
    val countries: List<Breakdown> = emptyList(),
    val pages: List<Breakdown> = emptyList(),
    val statuses: List<Breakdown> = emptyList(),
    val webVisits: List<SearchObservation> = emptyList(),
    val webPages: List<Breakdown> = emptyList(),
    val referrers: List<Breakdown> = emptyList(),
    val webCountries: List<Breakdown> = emptyList(),
    val freshness: String? = null,
    val expectedSites: Set<String> = emptySet(),
    val expectedWebSites: Set<String> = emptySet(),
    val statusDays: List<StatusDay> = emptyList(),
)

data class SearchReport(
    val range: DateRange,
    val observations: List<SearchObservation> = emptyList(),
    val devices: List<SearchObservation> = emptyList(),
    val sitemap: List<SitemapPage> = emptyList(),
    val crawl: List<CrawlReport> = emptyList(),
    val freshness: String? = null,
    val expectedSites: Set<String> = emptySet(),
) {
    val daily get() = observations.filter { it.kind == "daily" }
    val comparisonReady get() = ReportMath.complete(daily, range, expectedSites)

    fun rows(kind: String): List<SearchRow> =
        ReportMath.searchRows(
            observations.filter {
                it.kind == kind
            },
            range,
            comparisonReady,
        )
}

data class PerformanceReport(
    val urls: List<MeasuredUrl> = emptyList(),
    val field: List<FieldMetric> = emptyList(),
    val runs: List<LabRun> = emptyList(),
    val audits: List<LabAudit> = emptyList(),
)

data class HealthReport(
    val range: DateRange,
    val probes: List<Probe> = emptyList(),
    val sitemap: List<SitemapPage> = emptyList(),
)

data class PortfolioPage(
    val domain: String,
    val path: String,
    val pageViews: Long?,
    val googleClicks: Long?,
    val bingClicks: Long?,
    val position: Double?,
    val indexed: String?,
) {
    val key: String get() = "$domain:$path"
}

data class SiteSummary(
    val domain: String,
    val visitors: Double?,
    val change: Double?,
    val googleClicks: Long?,
    val bingClicks: Long?,
    val vitals: String,
    val mobileScore: Double?,
    val uptime: Double?,
)

data class PortfolioReport(
    val range: DateRange,
    val traffic: TrafficReport,
    val google: SearchReport,
    val bing: SearchReport,
    val performance: PerformanceReport,
    val health: HealthReport,
    val sites: List<SiteSummary> = emptyList(),
    val pages: List<PortfolioPage> = emptyList(),
    val insights: List<InsightCard> = emptyList(),
    val insightEmptyText: String = "Connect or refresh sources to generate insights.",
    val hasCache: Boolean = false,
)

data class PageDetailReport(
    val page: PortfolioPage,
    val range: DateRange,
    val series: List<ReportSeries>,
    val queries: List<PageQueryWindow>,
    val inspection: SitemapPage?,
    val performance: PerformanceReport,
    val metrics: List<Metric> = emptyList(),
    val coverage: String = "",
)

fun List<ReportSeries>.values(previous: Boolean): List<Double?> =
    firstOrNull { it.previous == previous }?.points?.map { it.value }.orEmpty()
