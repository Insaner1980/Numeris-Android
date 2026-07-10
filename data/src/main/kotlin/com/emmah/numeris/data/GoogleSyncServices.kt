@file:Suppress("MaxLineLength", "LongMethod", "ReturnCount", "ktlint:standard:max-line-length")

package com.emmah.numeris.data

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
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.GoogleAnalyticsApiClient
import com.emmah.numeris.core.network.SearchConsoleApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
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

    override suspend fun testConnection(): ConnectionTestResult =
        when (authorization.authorize()) {
            is GoogleAuthorizationState.Authorized -> ConnectionTestResult.Success
            is GoogleAuthorizationState.UserActionRequired -> ConnectionTestResult.Failure("Google authorization is required.")
            is GoogleAuthorizationState.Failure -> ConnectionTestResult.Failure("Google authorization is unavailable.")
        }

    override suspend fun sync(request: SyncRequest): SyncResult {
        val token =
            when (val auth = authorization.authorize()) {
                is GoogleAuthorizationState.Authorized -> auth.accessToken
                is GoogleAuthorizationState.UserActionRequired -> return SyncResult.Failure("Google authorization is required.", false)
                is GoogleAuthorizationState.Failure -> return SyncResult.Failure(auth.safeMessage, false)
            }
        val sites = configuredSites().filter { request.domain == null || it.contains(request.domain!!, ignoreCase = true) }
        if (sites.isEmpty()) return SyncResult.Failure("No Search Console property is configured.", false)
        val range = request.period.toDateRange()
        val start = range.start.toString()
        val end = range.end.toString()
        return runCatching {
            var written = 0
            sites.forEach { site ->
                val daily = client.query(token, site, start, end, listOf("date")).map { row -> row.toEntity(site, "daily", row.keys[0]) }
                val queries =
                    client.query(token, site, start, end, listOf("query")).map { row ->
                        row.toEntity(site, "query", end, query = row.keys[0])
                    }
                val pages =
                    client.query(token, site, start, end, listOf("page")).map { row ->
                        row.toEntity(site, "page", end, page = row.keys[0])
                    }
                val devices =
                    client.query(token, site, start, end, listOf("date", "device")).map { row ->
                        SearchDeviceEntity(site, row.keys[0], row.keys[1], row.clicks, row.impressions, row.ctr, row.position)
                    }
                val pageQueries =
                    client.query(token, site, start, end, listOf("page", "query")).map { row ->
                        SearchPageQueryEntity(
                            site,
                            start,
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
                    searchDao().deleteSearchRows(site, "query", start, end)
                    searchDao().deleteSearchRows(site, "page", start, end)
                    searchDao().upsertSearchConsole(daily + queries + pages)
                    searchDao().upsertSearchDevices(devices)
                    searchDao().upsertSearchPageQueries(pageQueries)
                }
                written += daily.size + queries.size + pages.size + devices.size + pageQueries.size
            }
            written
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = { SyncResult.Failure(it.message ?: "Search Console sync failed.", true) },
        )
    }

    suspend fun inspectSitemapUrls(domain: String): SyncResult {
        val token =
            when (val auth = authorization.authorize()) {
                is GoogleAuthorizationState.Authorized -> auth.accessToken
                is GoogleAuthorizationState.UserActionRequired -> return SyncResult.Failure("Google authorization is required.", false)
                is GoogleAuthorizationState.Failure -> return SyncResult.Failure(auth.safeMessage, false)
            }
        val property =
            configuredSites().firstOrNull { it.contains(domain, ignoreCase = true) }
                ?: return SyncResult.Failure("No Search Console property matches this domain.", false)
        return runCatching {
            val now = Instant.now(clock).toString()
            val rows =
                database.searchDao().sitemapUrls(domain).filter { it.removedAt == null }.map { row ->
                    val root = client.inspectUrl(token, property, row.url)
                    val status =
                        root["inspectionResult"]?.jsonObject?.get("indexStatusResult")?.jsonObject
                            ?: return@map row.copy(lastInspectedAt = now, verdict = "ERROR")
                    row.copy(
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
            database.writeTransaction { searchDao().upsertSitemapUrls(rows) }
            rows.size
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = { SyncResult.Failure(it.message ?: "URL Inspection failed.", true) },
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

    override suspend fun testConnection(): ConnectionTestResult =
        when (authorization.authorize()) {
            is GoogleAuthorizationState.Authorized -> ConnectionTestResult.Success
            is GoogleAuthorizationState.UserActionRequired -> ConnectionTestResult.Failure("Google authorization is required.")
            is GoogleAuthorizationState.Failure -> ConnectionTestResult.Failure("Google authorization is unavailable.")
        }

    override suspend fun sync(request: SyncRequest): SyncResult {
        val token =
            when (val auth = authorization.authorize()) {
                is GoogleAuthorizationState.Authorized -> auth.accessToken
                is GoogleAuthorizationState.UserActionRequired -> return SyncResult.Failure("Google authorization is required.", false)
                is GoogleAuthorizationState.Failure -> return SyncResult.Failure(auth.safeMessage, false)
            }
        val configs = analyticsConfigs().filter { request.domain == null || it.domain == request.domain }
        if (configs.isEmpty()) return SyncResult.Failure("No Google Analytics property is configured.", false)
        val range = request.period.toDateRange()
        val start = range.start.toString()
        val end = range.end.toString()
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
                            start,
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
                    client.runReport(token, config.propertyId, start, end, listOf("sessionSourceMedium"), SOURCE_METRICS).map { row ->
                        GoogleAnalyticsSourceEntity(
                            config.domain,
                            config.propertyId,
                            start,
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
                            start,
                            end,
                            row.dimensions[0],
                            row.long(0),
                            row.long(1),
                            fetchedAt,
                        )
                    }
                val devices =
                    client.runReport(token, config.propertyId, start, end, listOf("date", "deviceCategory"), DEVICE_METRICS).map { row ->
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
            onFailure = { SyncResult.Failure(it.message ?: "Google Analytics sync failed.", true) },
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

    private fun com.emmah.numeris.core.network.GoogleAnalyticsRowWire.long(index: Int) =
        metrics.getOrNull(index)?.toDoubleOrNull()?.toLong() ?: 0

    private fun com.emmah.numeris.core.network.GoogleAnalyticsRowWire.double(index: Int) = metrics.getOrNull(index)?.toDoubleOrNull() ?: 0.0

    private data class AnalyticsConfig(
        val domain: String,
        val propertyId: String,
    )

    private companion object {
        val GA_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val DAILY_METRICS = listOf("activeUsers", "sessions", "screenPageViews", "engagedSessions", "eventCount", "engagementRate")
        val PAGE_METRICS = listOf("activeUsers", "sessions", "screenPageViews", "engagedSessions", "engagementRate")
        val SOURCE_METRICS = listOf("sessions", "activeUsers", "keyEvents")
        val EVENT_METRICS = listOf("eventCount", "keyEvents")
        val DEVICE_METRICS = listOf("sessions", "activeUsers")
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
