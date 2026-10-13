package com.emmah.numeris.data

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.SitemapUrlEntity
import com.emmah.numeris.core.model.Breakdown
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.HealthReport
import com.emmah.numeris.core.model.InsightMetrics
import com.emmah.numeris.core.model.PortfolioPage
import com.emmah.numeris.core.model.PortfolioReport
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.SearchObservation
import com.emmah.numeris.core.model.SearchPageTrend
import com.emmah.numeris.core.model.SearchReport
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.SiteSummary
import com.emmah.numeris.core.model.SitemapPage
import com.emmah.numeris.core.model.SourceFreshnessSummary
import com.emmah.numeris.core.model.StatusCodeSummary
import com.emmah.numeris.core.model.TrafficReport
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.model.reportDomain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Clock
import java.time.Instant

internal fun siteSummaries(
    siteNames: List<String>,
    domain: String,
    report: PortfolioReport,
): List<SiteSummary> {
    val range = report.range
    val traffic = report.traffic
    val google = report.google
    val bing = report.bing
    val performance = report.performance
    val health = report.health

    return siteNames
        .filter { domain == "all" || it == domain }
        .map { site ->
            val visitors = trafficWindow(traffic.days.filter { it.domain == site }, range) { it.visitors }
            SiteSummary(
                site,
                visitors.current.takeIf { visitors.hasCurrent },
                ReportMath.change(visitors),
                sumClicks(google.daily.filter { it.domain == site && it.date in range }),
                sumClicks(
                    bing.daily.filter {
                        it.domain == site &&
                            it.date in range
                    },
                ),
                ReportMath.vitals(
                    performance.field.filter {
                        it.target == "https://$site" &&
                            it.targetType == "origin" &&
                            it.device == "PHONE"
                    },
                ),
                performance.runs
                    .firstOrNull { it.url == "https://$site/" && it.device == "mobile" }
                    ?.performance,
                ReportMath.uptime(
                    health.probes.filter {
                        it.domain ==
                            site
                    },
                ),
            )
        }.sortedWith(compareByDescending<SiteSummary> { it.visitors }.thenBy { it.domain })
}

internal fun sourceFreshness(
    connections: List<ConnectionEntity>,
    clock: Clock,
): SourceFreshnessSummary =
    SourceFreshnessSummary(
        connections.size,
        connections.count { it.lastSync == null },
        connections.count { row ->
            row.lastSync?.let {
                runCatching {
                    Instant
                        .parse(
                            it,
                        ).isBefore(Instant.now(clock).minusSeconds(FRESHNESS_AGE_SECONDS))
                }.getOrDefault(false)
            } ==
                true
        },
    )

internal fun portfolioMetrics(
    domain: String,
    report: PortfolioReport,
    freshness: SourceFreshnessSummary,
): InsightMetrics {
    val range = report.range
    val traffic = report.traffic
    val google = report.google
    val bing = report.bing
    val mobile = google.devices.filter { it.identity == "mobile" }
    val statuses = traffic.statuses
    val declined =
        google
            .rows("page")
            .filter { (it.previousClicks ?: 0) > (it.clicks ?: 0) }
            .minByOrNull { (it.clicks ?: 0) - it.previousClicks!! }
    return InsightMetrics(
        cloudflareVisitors = trafficWindow(traffic.days, range, traffic.expectedSites) { it.visitors },
        googleClicks = ReportMath.window(google.daily, range, google.expectedSites) { it.clicks },
        googleImpressions = ReportMath.window(google.daily, range, google.expectedSites) { it.impressions },
        bingClicks = ReportMath.window(bing.daily, range, bing.expectedSites) { it.clicks },
        bingImpressions = ReportMath.window(bing.daily, range, bing.expectedSites) { it.impressions },
        googleMobileClicks = ReportMath.window(mobile, range, google.expectedSites) { it.clicks },
        googleMobileImpressions = ReportMath.window(mobile, range, google.expectedSites) { it.impressions },
        cloudflareThreats = trafficWindow(traffic.days, range, traffic.expectedSites) { it.threats },
        cloudflareCacheHitRatio = cacheWindow(traffic.days, range, traffic.expectedSites),
        httpStatus =
            StatusCodeSummary(
                statuses.sumOf { it.value },
                statuses.filter { it.key.toInt() in CLIENT_ERROR_MIN..CLIENT_ERROR_MAX }.sumOf { it.value },
                statuses.filter { it.key.toInt() in SERVER_ERROR_MIN..SERVER_ERROR_MAX }.sumOf { it.value },
            ),
        indexing = ReportMath.indexing(report.health.sitemap),
        freshness = freshness,
        pageSpeedMobileScore = mobileScoreWindow(report.performance.runs, range, domain),
        worstDecliningPage = declined?.let { SearchPageTrend(it.identity, it.clicks ?: 0, it.previousClicks!!) },
        newSearchQueryCount = google.rows("query").count { it.previousClicks == 0L && (it.clicks ?: 0) > 0 },
        isSingleDomain = domain != "all",
    )
}

internal fun hasReports(
    report: PortfolioReport,
    pages: List<PortfolioPage>,
): Boolean =
    listOf(
        report.traffic.days,
        report.google.observations,
        report.bing.observations,
        report.performance.field,
        report.performance.runs,
        report.health.probes,
        report.health.sitemap,
        pages,
    ).any { it.isNotEmpty() }

internal fun portfolioPages(
    traffic: TrafficReport,
    google: SearchReport,
    bing: SearchReport,
    health: HealthReport,
): List<PortfolioPage> {
    val web = traffic.webPages.groupBy { it.domain to SiteIdentity.pagePath(it.label) }
    val g = google.rows("page").groupBy { it.domain to SiteIdentity.pagePath(it.identity) }
    val b = bing.rows("page").groupBy { it.domain to SiteIdentity.pagePath(it.identity) }
    return (web.keys + g.keys + b.keys)
        .distinct()
        .map { (site, path) ->
            val weighted = g[site to path].orEmpty().filter { it.position != null && (it.impressions ?: 0) > 0 }
            PortfolioPage(
                site,
                path,
                web[site to path]?.sumOf { it.value },
                g[site to path]?.mapNotNull { it.clicks }?.sum(),
                b[
                    site to
                        path,
                ]?.mapNotNull { it.clicks }?.sum(),
                weighted.takeIf { it.isNotEmpty() }?.let { list ->
                    list.sumOf { it.position!! * it.impressions!! } /
                        list.sumOf { it.impressions!! }
                },
                health.sitemap.firstOrNull { it.domain == site && SiteIdentity.pagePath(it.url) == path }?.verdict,
            )
        }.filter { (it.pageViews ?: 0) + (it.googleClicks ?: 0) + (it.bingClicks ?: 0) > 0 }
        .sortedWith(
            compareByDescending<PortfolioPage> {
                it.pageViews
                    ?: 0L
            }.thenByDescending {
                (it.googleClicks ?: 0L) + (it.bingClicks ?: 0L)
            }.thenBy { it.domain }
                .thenBy { it.path },
        )
}

internal fun sitemapPage(it: SitemapUrlEntity): SitemapPage =
    SitemapPage(
        it.domain,
        it.url,
        it.lastSeenAt,
        it.removedAt,
        it.lastInspectedAt,
        it.verdict,
        it.coverageState,
        it.indexingState,
        it.robotsTxtState,
        it.pageFetchState,
        it.crawledAs,
        it.lastCrawlTime,
    )

internal fun matches(
    value: String,
    domain: String,
): Boolean = reportDomain(value) != null && (domain == "all" || reportDomain(value) == domain)

internal fun aggregate(rows: List<Breakdown>): List<Breakdown> =
    rows
        .groupBy {
            it.key
        }.map { (_, list) ->
            list.first().copy(
                value =
                    list.sumOf {
                        it.value
                    },
            )
        }.sortedWith(compareByDescending<Breakdown> { it.value }.thenBy { it.key })

internal fun sumClicks(rows: List<SearchObservation>): Long? =
    rows
        .mapNotNull {
            it.clicks
        }.takeIf { it.isNotEmpty() }
        ?.sum()

internal fun readableCrawl(raw: String): String {
    val fields =
        runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return "Stored report details available"
    return fields.entries
        .mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.let { "${DisplayFormat.humanize(key)}: $it" }
        }.joinToString("\n")
        .ifEmpty { "Stored report details available" }
}

private const val FRESHNESS_AGE_SECONDS = 172800L
private const val PAGE_QUERY_LIMIT = 8

private const val CLIENT_ERROR_MIN = 400
private const val CLIENT_ERROR_MAX = 499
private const val SERVER_ERROR_MIN = 500
private const val SERVER_ERROR_MAX = 599
