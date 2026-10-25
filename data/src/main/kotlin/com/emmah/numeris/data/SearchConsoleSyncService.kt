@file:Suppress("MaxLineLength", "LongMethod", "ReturnCount", "ktlint:standard:max-line-length")

package com.emmah.numeris.data

import com.emmah.numeris.core.common.NumerisFailure
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
import com.emmah.numeris.core.network.SearchAnalyticsRowWire
import com.emmah.numeris.core.network.SearchConsoleApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant

class SearchConsoleSyncService(
    private val database: NumerisDatabase,
    private val authorize: suspend () -> GoogleAuthorizationState,
    private val client: SearchConsoleApiClient,
    private val cacheDirectory: File,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.SearchConsole

    override suspend fun testConnection(connectionId: ConnectionId?): ConnectionTestResult {
        val auth = authorize()
        if (auth !is GoogleAuthorizationState.Authorized) {
            return ConnectionTestResult.Failure(
                GOOGLE_AUTHORIZATION_REQUIRED,
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
        val token = authorize().syncToken { return it }
        val sites = configuredSites().filter { request.domain == null || reportDomain(it) == request.domain }
        if (sites.isEmpty()) return SyncResult.Failure("No Search Console property is configured.", false)
        val range = request.period.toDateRange(clock)
        val start = range.previous().start.toString()
        val end = range.end.toString()
        return runCatching {
            syncTargets(sites) { site ->
                syncSite(token, site, start, end, range.end.minusDays(PAGE_QUERY_DAYS - 1).toString())
            }
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = {
                if (it is kotlinx.coroutines.CancellationException) throw it
                SyncResult.Failure(it.message ?: "Search Console sync failed.", true)
            },
        )
    }

    private suspend fun syncSite(
        token: String,
        site: String,
        start: String,
        end: String,
        pageQueryStart: String,
    ): Int =
        withContext(Dispatchers.IO) {
            val staged = File.createTempFile("search-console-", ".jsonl", cacheDirectory)
            try {
                var count = 0
                staged.bufferedWriter().use { writer ->
                    val dimensions =
                        listOf(
                            "daily" to listOf("date"),
                            "query_daily" to listOf("date", "query"),
                            "page_daily" to listOf("date", "page"),
                            "devices" to listOf("date", "device"),
                            "page_queries" to listOf("page", "query"),
                        )
                    for ((kind, keys) in dimensions) {
                        client.query(token, site, if (kind == "page_queries") pageQueryStart else start, end, keys) { rows ->
                            writer.appendLine(kind)
                            writer.appendLine(Json.encodeToString(rows))
                            count += rows.size
                        }
                    }
                }
                // All requests must succeed before replacing any cached rows. Replay only one page at a time.
                database.writeTransaction {
                    searchDao().deleteSearchRows(site, "daily", start, end)
                    searchDao().deleteSearchRows(site, "query_daily", start, end)
                    searchDao().deleteSearchRows(site, "page_daily", start, end)
                    staged.bufferedReader().use { reader ->
                        while (true) {
                            val kind = reader.readLine() ?: break
                            val rows = Json.decodeFromString<List<SearchAnalyticsRowWire>>(checkNotNull(reader.readLine()))
                            persistPage(site, kind, rows, pageQueryStart, end)
                        }
                    }
                }
                count
            } finally {
                staged.delete()
            }
        }

    private suspend fun persistPage(
        site: String,
        kind: String,
        rows: List<SearchAnalyticsRowWire>,
        pageQueryStart: String,
        end: String,
    ) {
        when (kind) {
            "devices" ->
                database.searchDao().upsertSearchDevices(
                    rows.map { row ->
                        SearchDeviceEntity(site, row.keys[0], row.keys[1], row.clicks, row.impressions, row.ctr, row.position)
                    },
                )
            "page_queries" ->
                database.searchDao().upsertSearchPageQueries(
                    rows.map { row ->
                        SearchPageQueryEntity(
                            site,
                            pageQueryStart,
                            end,
                            row.keys[0],
                            row.keys[1],
                            row.clicks,
                            row.impressions,
                            row.ctr,
                            row.position,
                        )
                    },
                )
            else ->
                database.searchDao().upsertSearchConsole(
                    rows.map { row ->
                        row.toEntity(
                            site,
                            kind,
                            row.keys[0],
                            query = if (kind == "query_daily") row.keys[1] else "",
                            page = if (kind == "page_daily") row.keys[1] else null,
                        )
                    },
                )
        }
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
        val token = authorize().syncToken { return it }
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

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

private const val INSPECTION_MAX_AGE_DAYS = 7L
private const val PAGE_QUERY_DAYS = 28L
private const val MAX_INSPECTIONS_PER_RUN = 200
private const val INSPECTION_RETRY_DELAY_MILLIS = 2_000L

/** Tarkistamaton tai jäsentymätön aikaleima tulkitaan vanhentuneeksi. */
private fun String?.inspectedBefore(limit: Instant): Boolean =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }?.isBefore(limit) ?: true

internal inline fun GoogleAuthorizationState.syncToken(onFailure: (SyncResult.Failure) -> Nothing): String =
    when (this) {
        is GoogleAuthorizationState.Authorized -> accessToken
        is GoogleAuthorizationState.UserActionRequired -> onFailure(SyncResult.Failure(GOOGLE_AUTHORIZATION_REQUIRED, false))
        is GoogleAuthorizationState.Failure -> onFailure(SyncResult.Failure(safeMessage, false))
    }

private const val GOOGLE_AUTHORIZATION_REQUIRED = "Google authorization is required."
