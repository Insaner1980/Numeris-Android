@file:Suppress("MaxLineLength", "LongMethod", "ReturnCount", "ktlint:standard:max-line-length")

package com.emmah.numeris.data

import com.emmah.numeris.core.common.NumerisFailure
import com.emmah.numeris.core.database.GoogleAnalyticsDailyEntity
import com.emmah.numeris.core.database.GoogleAnalyticsDeviceEntity
import com.emmah.numeris.core.database.GoogleAnalyticsEventEntity
import com.emmah.numeris.core.database.GoogleAnalyticsPageEntity
import com.emmah.numeris.core.database.GoogleAnalyticsSourceEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.SearchConsoleEntity
import com.emmah.numeris.core.database.SearchDeviceEntity
import com.emmah.numeris.core.database.SearchPageQueryEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.model.previous
import com.emmah.numeris.core.model.reportDomain
import com.emmah.numeris.core.network.GoogleAnalyticsApiClient
import com.emmah.numeris.core.network.GoogleAnalyticsRowWire
import com.emmah.numeris.core.network.SearchAnalyticsRowWire
import com.emmah.numeris.core.network.SearchConsoleApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SearchConsoleSyncService(
    private val database: NumerisDatabase,
    private val authorization: GoogleAuthorizationManager,
    private val client: SearchConsoleApiClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.SearchConsole

    override suspend fun testConnection(connectionId: ConnectionId?): ConnectionTestResult {
        val auth = authorization.authorize()
        if (auth !is GoogleAuthorizationState.Authorized) {
            return ConnectionTestResult.Failure(
                "Google authorization is required.",
            )
        }
        val properties = configuredSites()
        if (properties.isEmpty()) return ConnectionTestResult.Failure("Configure a Search Console property first.")
        return runCatching {
            val authorized = client.sites(auth.accessToken).toSet()
            if (properties.all { it in authorized }) {
                ConnectionTestResult.Success
            } else {
                ConnectionTestResult.Failure("A configured property is unavailable to the authorized Google account.")
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            ConnectionTestResult.Failure("Search Console property access could not be verified.")
        }
    }

    override suspend fun sync(request: SyncRequest): SyncResult {
        val token = authorization.authorize().syncToken { return it }
        val sites = configuredSites().filter { request.domain == null || reportDomain(it) == request.domain }
        if (sites.isEmpty()) return SyncResult.Failure("No Search Console property is configured.", false)
        val range = request.period.toDateRange(clock)
        val start = range.previous().start.toString()
        val end = range.end.toString()
        return runCatching {
            syncTargets(sites) { site ->
                val daily =
                    client.query(token, site, start, end, listOf("date")).map { row ->
                        row.toEntity(site, "daily", row.keys[0])
                    }
                val queries =
                    client.query(token, site, start, end, listOf("date", "query")).map { row ->
                        row.toEntity(site, "query_daily", row.keys[0], query = row.keys[1])
                    }
                val pages =
                    client.query(token, site, start, end, listOf("date", "page")).map { row ->
                        row.toEntity(site, "page_daily", row.keys[0], page = row.keys[1])
                    }
                val devices =
                    client.query(token, site, start, end, listOf("date", "device")).map { row ->
                        SearchDeviceEntity(
                            site,
                            row.keys[0],
                            row.keys[1],
                            row.clicks,
                            row.impressions,
                            row.ctr,
                            row.position,
                        )
                    }
                val pageQueries =
                    client.query(token, site, range.end.minusDays(27).toString(), end, listOf("page", "query")).map { row ->
                        SearchPageQueryEntity(
                            site,
                            range.end.minusDays(27).toString(),
                            end,
                            row.keys[0],
                            row.keys[1],
                            row.clicks,
                            row.impressions,
                            row.ctr,
                            row.position,
                        )
                    }
                database.writeTransaction {
                    searchDao().deleteSearchRows(site, "daily", start, end)
                    searchDao().deleteSearchRows(site, "query_daily", start, end)
                    searchDao().deleteSearchRows(site, "page_daily", start, end)
                    searchDao().upsertSearchConsole(daily + queries + pages)
                    searchDao().upsertSearchDevices(devices)
                    searchDao().upsertSearchPageQueries(pageQueries)
                }
                daily.size + queries.size + pages.size + devices.size + pageQueries.size
            }
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = {
                if (it is kotlinx.coroutines.CancellationException) throw it
                SyncResult.Failure(it.message ?: "Search Console sync failed.", true)
            },
        )
    }

    /**
     * URL Inspection sivuston aktiivisille sivustokartan URL:ille, joita ei ole tarkistettu viikkoon.
     * Vanhimmat ensin ja enintään [MAX_INSPECTIONS_PER_RUN] kerralla, jotta Googlen päiväkiintiö riittää.
     * Sivusto ilman Search Console -ominaisuutta ohitetaan virheettä.
     */
    suspend fun inspectStaleSitemapUrls(domain: String): SyncResult {
        val property = configuredSites().firstOrNull { reportDomain(it) == domain } ?: return SyncResult.Success(0)
        val staleBefore = Instant.now(clock).minus(Duration.ofDays(INSPECTION_MAX_AGE_DAYS))
        val due =
            database
                .searchDao()
                .sitemapUrls(domain)
                .filter { row -> row.removedAt == null && row.lastInspectedAt.inspectedBefore(staleBefore) }
                .sortedBy { it.lastInspectedAt.orEmpty() }
                .take(MAX_INSPECTIONS_PER_RUN)
        if (due.isEmpty()) return SyncResult.Success(0)
        val token = authorization.authorize().syncToken { return it }
        // Onnistuneet tarkistukset tallennetaan aina. Googlen palvelinvirhe (5xx) koskee yleensä yksittäistä URL:ia,
        // joten se ohitetaan ja tarkistetaan seuraavalla päivityksellä; oikeus-, kirjautumis- ja kiintiövirhe pysäyttää.
        val run = inspectAll(token, property, due, Instant.now(clock).toString())
        if (run.inspected.isNotEmpty()) database.writeTransaction { searchDao().upsertSitemapUrls(run.inspected) }
        val failure = run.stop ?: run.skipped?.takeIf { run.inspected.isEmpty() }
        return if (failure == null) {
            SyncResult.Success(run.inspected.size)
        } else {
            SyncResult.Failure(
                "URL Inspection for $domain stopped after ${run.inspected.size} of ${due.size} URLs: " +
                    (failure.message ?: "request failed."),
                true,
            )
        }
    }

    private class InspectionRun(
        val inspected: List<com.emmah.numeris.core.database.SitemapUrlEntity>,
        val skipped: Throwable?,
        val stop: Throwable?,
    )

    private suspend fun inspectAll(
        token: String,
        property: String,
        due: List<com.emmah.numeris.core.database.SitemapUrlEntity>,
        now: String,
    ): InspectionRun {
        val inspected = mutableListOf<com.emmah.numeris.core.database.SitemapUrlEntity>()
        var skipped: Throwable? = null
        for (row in due) {
            val outcome = runCatching { inspectWithRetry(token, property, row.url) }
            val error = outcome.exceptionOrNull()
            if (error is kotlinx.coroutines.CancellationException) throw error
            outcome.onSuccess { inspected += row.inspectedWith(it, now) }
            if (error is NumerisFailure.TransientNetwork) {
                skipped = error
            } else if (error !=
                null
            ) {
                return InspectionRun(inspected, skipped, error)
            }
        }
        return InspectionRun(inspected, skipped, null)
    }

    /** Yksi uusintayritys hetkelliselle virheelle ennen kuin URL ohitetaan. */
    private suspend fun inspectWithRetry(
        token: String,
        property: String,
        url: String,
    ): kotlinx.serialization.json.JsonObject =
        try {
            client.inspectUrl(token, property, url)
        } catch (_: NumerisFailure.TransientNetwork) {
            kotlinx.coroutines.delay(INSPECTION_RETRY_DELAY_MILLIS)
            client.inspectUrl(token, property, url)
        }

    private fun com.emmah.numeris.core.database.SitemapUrlEntity.inspectedWith(
        root: kotlinx.serialization.json.JsonObject,
        now: String,
    ): com.emmah.numeris.core.database.SitemapUrlEntity {
        val status =
            root["inspectionResult"]?.jsonObject?.get("indexStatusResult")?.jsonObject
                ?: return copy(lastInspectedAt = now, verdict = "ERROR")
        return copy(
            lastInspectedAt = now,
            verdict = status["verdict"]?.jsonPrimitive?.contentOrNull,
            coverageState = status["coverageState"]?.jsonPrimitive?.contentOrNull,
            indexingState = status["indexingState"]?.jsonPrimitive?.contentOrNull,
            robotsTxtState = status["robotsTxtState"]?.jsonPrimitive?.contentOrNull,
            pageFetchState = status["pageFetchState"]?.jsonPrimitive?.contentOrNull,
            crawledAs = status["crawledAs"]?.jsonPrimitive?.contentOrNull,
            lastCrawlTime = status["lastCrawlTime"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private suspend fun configuredSites(): List<String> =
        database
            .connectionDao()
            .bySource("search_console")
            .flatMap { connection ->
                runCatching {
                    Json
                        .parseToJsonElement(connection.config.orEmpty())
                        .jsonObject["sites"]
                        ?.let { sites ->
                            runCatching { sites.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }
                                .getOrElse {
                                    sites.jsonPrimitive.contentOrNull
                                        .orEmpty()
                                        .split(',')
                                        .map(String::trim)
                                        .filter(String::isNotBlank)
                                }
                        }.orEmpty()
                }.getOrDefault(emptyList())
            }.distinct()

    private fun com.emmah.numeris.core.network.SearchAnalyticsRowWire.toEntity(
        site: String,
        kind: String,
        date: String,
        query: String = "",
        page: String? = null,
    ) = SearchConsoleEntity(
        siteUrl = site,
        date = date,
        kind = kind,
        query = query,
        page = page,
        clicks = clicks,
        impressions = impressions,
        ctr = ctr,
        position = position,
        fetchedAt = Instant.now(clock).toString(),
    )
}

class GoogleAnalyticsSyncService(
    private val database: NumerisDatabase,
    private val authorization: GoogleAuthorizationManager,
    private val client: GoogleAnalyticsApiClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.GoogleAnalytics

    override suspend fun testConnection(connectionId: ConnectionId?): ConnectionTestResult =
        when (authorization.authorize()) {
            is GoogleAuthorizationState.Authorized -> ConnectionTestResult.Success
            is GoogleAuthorizationState.UserActionRequired ->
                ConnectionTestResult.Failure(
                    "Google authorization is required.",
                )
            is GoogleAuthorizationState.Failure -> ConnectionTestResult.Failure("Google authorization is unavailable.")
        }

    override suspend fun sync(request: SyncRequest): SyncResult {
        val token = authorization.authorize().syncToken { return it }
        val configs = analyticsConfigs().filter { request.domain == null || it.domain == request.domain }
        if (configs.isEmpty()) return SyncResult.Failure("No Google Analytics property is configured.", false)
        val range = request.period.toDateRange(clock)
        val start = range.start.toString()
        val end = range.end.toString()
        val aggregateStart = range.end.minusDays(27).toString()
        return runCatching {
            var written = 0
            configs.forEach { config ->
                val fetchedAt = Instant.now(clock).toString()
                val daily =
                    client.runReport(token, config.propertyId, start, end, listOf("date"), DAILY_METRICS).map { row ->
                        GoogleAnalyticsDailyEntity(
                            config.domain,
                            config.propertyId,
                            row.dimensions[0].gaDate(),
                            row.long(0),
                            row.long(1),
                            row.long(2),
                            row.long(3),
                            row.long(4),
                            row.double(5),
                            fetchedAt,
                        )
                    }
                val pages =
                    client.runReport(token, config.propertyId, start, end, listOf("pagePath"), PAGE_METRICS).map { row ->
                        GoogleAnalyticsPageEntity(
                            config.domain,
                            config.propertyId,
                            aggregateStart,
                            end,
                            row.dimensions[0],
                            row.long(0),
                            row.long(1),
                            row.long(2),
                            row.long(3),
                            row.double(4),
                            fetchedAt,
                        )
                    }
                val sources =
                    client
                        .runReport(
                            token,
                            config.propertyId,
                            start,
                            end,
                            listOf("sessionSourceMedium"),
                            SOURCE_METRICS,
                        ).map { row ->
                            GoogleAnalyticsSourceEntity(
                                config.domain,
                                config.propertyId,
                                aggregateStart,
                                end,
                                row.dimensions[0],
                                row.long(0),
                                row.long(1),
                                row.long(2),
                                fetchedAt,
                            )
                        }
                val events =
                    client.runReport(token, config.propertyId, start, end, listOf("eventName"), EVENT_METRICS).map { row ->
                        GoogleAnalyticsEventEntity(
                            config.domain,
                            config.propertyId,
                            aggregateStart,
                            end,
                            row.dimensions[0],
                            row.long(0),
                            row.long(1),
                            fetchedAt,
                        )
                    }
                val devices =
                    client
                        .runReport(
                            token,
                            config.propertyId,
                            start,
                            end,
                            listOf("date", "deviceCategory"),
                            DEVICE_METRICS,
                        ).map { row ->
                            GoogleAnalyticsDeviceEntity(
                                config.domain,
                                config.propertyId,
                                row.dimensions[0].gaDate(),
                                row.dimensions[1],
                                row.long(0),
                                row.long(1),
                                fetchedAt,
                            )
                        }
                database.writeTransaction {
                    googleAnalyticsDao().upsertGoogleAnalytics(daily)
                    googleAnalyticsDao().upsertGoogleAnalyticsPages(pages)
                    googleAnalyticsDao().upsertGoogleAnalyticsSources(sources)
                    googleAnalyticsDao().upsertGoogleAnalyticsEvents(events)
                    googleAnalyticsDao().upsertGoogleAnalyticsDevices(devices)
                }
                written += daily.size + pages.size + sources.size + events.size + devices.size
            }
            written
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = {
                if (it is kotlinx.coroutines.CancellationException) throw it
                SyncResult.Failure(it.message ?: "Google Analytics sync failed.", true)
            },
        )
    }

    private suspend fun analyticsConfigs(): List<AnalyticsConfig> =
        database.connectionDao().bySource("google_analytics").mapNotNull { connection ->
            runCatching {
                val root = Json.parseToJsonElement(connection.config.orEmpty()).jsonObject
                AnalyticsConfig(
                    domain =
                        root
                            .getValue("domain")
                            .jsonPrimitive.content
                            .lowercase(),
                    propertyId = root.getValue("propertyId").jsonPrimitive.content,
                )
            }.getOrNull()
        }

    private fun String.gaDate(): String = runCatching { LocalDate.parse(this, GA_DATE).toString() }.getOrDefault(this)

    private fun GoogleAnalyticsRowWire.long(index: Int) = metrics.getOrNull(index)?.toDoubleOrNull()?.toLong() ?: 0

    private fun GoogleAnalyticsRowWire.double(index: Int) = metrics.getOrNull(index)?.toDoubleOrNull() ?: 0.0

    private data class AnalyticsConfig(
        val domain: String,
        val propertyId: String,
    )

    private companion object {
        val GA_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val DAILY_METRICS =
            listOf("activeUsers", "sessions", "screenPageViews", "engagedSessions", "eventCount", "engagementRate")
        val PAGE_METRICS = listOf("activeUsers", "sessions", "screenPageViews", "engagedSessions", "engagementRate")
        val SOURCE_METRICS = listOf("sessions", "activeUsers", "keyEvents")
        val EVENT_METRICS = listOf("eventCount", "keyEvents")
        val DEVICE_METRICS = listOf("sessions", "activeUsers")
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

private const val INSPECTION_MAX_AGE_DAYS = 7L
private const val MAX_INSPECTIONS_PER_RUN = 200
private const val INSPECTION_RETRY_DELAY_MILLIS = 2_000L

/** Tarkistamaton tai jäsentymätön aikaleima tulkitaan vanhentuneeksi. */
private fun String?.inspectedBefore(limit: Instant): Boolean =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }?.isBefore(limit) ?: true

internal inline fun GoogleAuthorizationState.syncToken(onFailure: (SyncResult.Failure) -> Nothing): String =
    when (this) {
        is GoogleAuthorizationState.Authorized -> accessToken
        is GoogleAuthorizationState.UserActionRequired -> onFailure(SyncResult.Failure("Google authorization is required.", false))
        is GoogleAuthorizationState.Failure -> onFailure(SyncResult.Failure(safeMessage, false))
    }
