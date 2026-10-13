package com.emmah.numeris.data

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.model.CrawlReport
import com.emmah.numeris.core.model.DateRange
import com.emmah.numeris.core.model.HealthReport
import com.emmah.numeris.core.model.InsightEngine
import com.emmah.numeris.core.model.MeasuredUrl
import com.emmah.numeris.core.model.PageDetailReport
import com.emmah.numeris.core.model.PerformanceReport
import com.emmah.numeris.core.model.PortfolioPage
import com.emmah.numeris.core.model.PortfolioReport
import com.emmah.numeris.core.model.Probe
import com.emmah.numeris.core.model.SearchObservation
import com.emmah.numeris.core.model.SearchReport
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.StatusDay
import com.emmah.numeris.core.model.TrafficReport
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.model.previous
import com.emmah.numeris.core.model.reportDomain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.LocalDate

internal class ReportReader(
    private val database: NumerisDatabase,
    private val clock: Clock,
) {
    private val dao get() = database.reportSnapshotDao()

    suspend fun domains(): List<String> {
        val configured =
            dao.readConnections().flatMap { connection ->
                val metadata =
                    runCatching {
                        Json
                            .parseToJsonElement(
                                connection.config.orEmpty(),
                            ).jsonObject
                    }.getOrNull()
                buildList {
                    metadata
                        ?.get("domain")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.let(::add)
                    metadata?.get("sites")?.let { sites ->
                        when (sites) {
                            is JsonArray -> addAll(sites.mapNotNull { it.jsonPrimitive.contentOrNull })
                            is JsonPrimitive -> addAll(sites.content.split(',').map(String::trim))
                            else -> Unit
                        }
                    }
                }
            }
        return (dao.readIdentities() + configured).mapNotNull(::reportDomain).distinct().sorted()
    }

    suspend fun traffic(
        domain: String,
        range: DateRange,
    ): TrafficReport {
        val start = range.previous().start.toString()
        val current = range.start.toString()
        val end = range.end.toString()
        val statuses = dao.readCloudflareStatuses(domain, current, end)
        return TrafficReport(
            range = range,
            days = dao.readCloudflareTraffic(domain, start, end).map(::trafficDay),
            countries = aggregate(dao.readCloudflareCountries(domain, current, end).map(::zoneCountry)),
            pages = aggregate(dao.readCloudflarePages(domain, current, end).map(::zonePage)),
            statuses = aggregate(statuses.map(::zoneStatus)),
            webVisits = dao.readWebAnalytics(domain, start, end).map(::webDay),
            webPages = aggregate(dao.readWebPages(current, end).filter { matches(it.domain, domain) }.map(::webPage)),
            referrers =
                aggregate(
                    dao.readWebReferrers(current, end).filter { matches(it.domain, domain) }.map(::webReferrer),
                ),
            webCountries =
                aggregate(
                    dao.readWebCountries(current, end).filter { matches(it.domain, domain) }.map(::webCountry),
                ),
            freshness = latestSync(setOf("cloudflare", "web_analytics"), domain),
            expectedSites = expectedSites("cloudflare", domain),
            expectedWebSites = expectedSites("web_analytics", domain),
            statusDays = statuses.map { StatusDay(LocalDate.parse(it.date), it.statusCode, it.requests) },
        )
    }

    suspend fun search(
        domain: String,
        range: DateRange,
        provider: String,
    ): SearchReport {
        val storageSource = if (provider == "Google") "search_console" else "bing_webmaster"
        val start = range.previous().start.toString()
        val end = range.end.toString()
        val observations =
            if (provider == "Google") {
                dao.readSearchConsole("all", start, end).filter { matches(it.siteUrl, domain) }.map(::googleObservation)
            } else {
                dao.readBingTraffic("all", start, end).filter { matches(it.siteUrl, domain) }.map(::bingTraffic) +
                    dao.readBingQueries("all", start, end).filter { matches(it.siteUrl, domain) }.map(::bingQuery) +
                    dao.readBingPages("all", start, end).filter { matches(it.siteUrl, domain) }.map(::bingPage)
            }
        val devices =
            if (provider == "Google") {
                dao.readSearchDevices("all", start, end).filter { matches(it.siteUrl, domain) }.map {
                    SearchObservation(
                        reportDomain(it.siteUrl)!!,
                        LocalDate.parse(it.date),
                        "device",
                        it.device,
                        it.clicks,
                        it.impressions,
                        it.position,
                    )
                }
            } else {
                emptyList()
            }
        val crawl =
            if (provider == "Bing") {
                dao.readBingRaw("all").filter { matches(it.siteUrl, domain) }.map {
                    CrawlReport(
                        reportDomain(it.siteUrl)!!,
                        it.method,
                        it.itemKey,
                        it.fetchedAt,
                        it.rawJson,
                        readableCrawl(it.rawJson),
                    )
                }
            } else {
                emptyList()
            }
        return SearchReport(
            range,
            observations,
            devices,
            dao.readSitemap(domain).map(::sitemapPage),
            crawl,
            latestSync(setOf(storageSource), domain),
            expectedSites(storageSource, domain),
        )
    }

    suspend fun performance(domain: String): PerformanceReport =
        PerformanceReport(
            dao
                .readPerformanceUrls("all")
                .filter {
                    matches(it.url, domain)
                }.map { MeasuredUrl(it.id, it.url, it.enabled, it.source) },
            dao.readCrux("all").filter { matches(it.target, domain) }.map(::fieldMetric),
            dao.readPageSpeedRuns("all").filter { matches(it.url, domain) }.map(::labRun),
            dao.readPageSpeedAudits("all").filter { matches(it.url, domain) }.map(::labAudit),
        )

    suspend fun health(
        domain: String,
        range: DateRange,
    ): HealthReport =
        HealthReport(
            range,
            dao.readAllUptime(domain).map {
                Probe(it.domain, it.checkedAt, it.status, it.statusCode, it.responseMs, it.errorMessage)
            },
            dao.readSitemap(domain).map(::sitemapPage),
        )

    suspend fun portfolio(
        domain: String,
        range: DateRange,
    ): PortfolioReport {
        val report =
            PortfolioReport(
                range,
                traffic(domain, range),
                search(domain, range, "Google"),
                search(domain, range, "Bing"),
                performance(domain),
                health(domain, range),
            )
        val sites = siteSummaries(domains(), domain, report)
        val pages = portfolioPages(report.traffic, report.google, report.bing, report.health)
        val connections =
            dao.readConnections().filter {
                it.status != "disconnected" &&
                    (domain == "all" || domain in connectionSites(it))
            }
        val metrics = portfolioMetrics(domain, report, sourceFreshness(connections, clock))
        return report.copy(
            sites = sites,
            pages = pages,
            insights = InsightEngine.generate(metrics),
            insightEmptyText = InsightEngine.emptyStateText(metrics),
            hasCache = hasReports(report, pages),
        )
    }

    private suspend fun latestSync(
        sources: Set<String>,
        domain: String,
    ): String? =
        dao
            .readConnections()
            .filter {
                it.source in sources &&
                    (domain == "all" || connectionSites(it).isEmpty() || domain in connectionSites(it))
            }.mapNotNull { it.lastSync }
            .maxOrNull()

    private suspend fun expectedSites(
        source: String,
        domain: String,
    ): Set<String> =
        dao
            .readConnections()
            .filter {
                it.source == source && it.status != "disconnected"
            }.flatMap(::connectionSites)
            .filter { domain == "all" || it == domain }
            .toSet()

    private fun connectionSites(connection: com.emmah.numeris.core.database.ConnectionEntity): List<String> {
        val metadata =
            runCatching { Json.parseToJsonElement(connection.config.orEmpty()).jsonObject }.getOrNull()
                ?: return emptyList()
        val mapped =
            metadata["siteMappings"]
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
                .lines()
                .filter(String::isNotBlank)
                .map { it.substringBefore('=') }
        return (
            mapped + listOfNotNull(metadata["domain"]?.jsonPrimitive?.contentOrNull) +
                when (val sites = metadata["sites"]) {
                    is JsonArray -> sites.mapNotNull { it.jsonPrimitive.contentOrNull }
                    is JsonPrimitive -> sites.content.split(',')
                    else -> emptyList()
                }
        ).mapNotNull(::reportDomain)
    }

    suspend fun page(
        domain: String,
        path: String,
        range: DateRange,
    ): PageDetailReport {
        val portfolio = portfolio(domain, range)
        val page =
            portfolio.pages.firstOrNull { it.path == path } ?: PortfolioPage(domain, path, null, null, null, null, null)
        val windows = pageQueries(dao.readPageQueries(), domain, path)
        val pages =
            dao.readWebPages(range.previous().start.toString(), range.end.toString()).filter {
                matches(it.domain, domain) &&
                    SiteIdentity.pagePath(it.path) == path
            }
        val history =
            comparisonSeries(
                "Page views",
                pages.map { SearchObservation(domain, LocalDate.parse(it.date), "page", path, it.pageViews, null) },
                range,
            ) +
                comparisonSeries(
                    "Google clicks",
                    portfolio.google.observations.filter {
                        it.kind == "page" &&
                            SiteIdentity.pagePath(it.identity) == path
                    },
                    range,
                ) +
                comparisonSeries(
                    "Bing clicks",
                    portfolio.bing.observations.filter {
                        it.kind == "page" &&
                            SiteIdentity.pagePath(it.identity) == path
                    },
                    range,
                )
        val url = "https://$domain$path"
        val perf = portfolio.performance
        val pageMetrics = pageMetrics(history, range, page)
        val coverage =
            history.chunked(2).joinToString("\n") { lines ->
                "${lines.first().label}: ${lines.first().points.count {
                    it.value != null
                }}/${range.days} current days; " +
                    "${lines.last().points.count { it.value != null }}/${range.days} previous days"
            }
        return PageDetailReport(
            page,
            range,
            history,
            windows,
            portfolio.health.sitemap.firstOrNull { SiteIdentity.pagePath(it.url) == path },
            matchingPerformance(perf, url),
            pageMetrics,
            coverage,
        )
    }
}
