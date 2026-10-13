@file:Suppress("MaxLineLength", "ReturnCount")

package com.emmah.numeris.data

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.WebAnalyticsCountryEntity
import com.emmah.numeris.core.database.WebAnalyticsDailyEntity
import com.emmah.numeris.core.database.WebAnalyticsPageEntity
import com.emmah.numeris.core.database.WebAnalyticsReferrerEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.DateRange
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.model.withPrevious
import com.emmah.numeris.core.network.WebAnalyticsApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant

class WebAnalyticsSyncService(
    private val database: NumerisDatabase,
    private val secretStore: SecretStore,
    private val client: WebAnalyticsApiClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.CloudflareWebAnalytics

    override suspend fun testConnection(connectionId: ConnectionId?): ConnectionTestResult {
        val connection =
            database.connectionDao().bySource(STORAGE_SOURCE).firstOrNull {
                connectionId == null ||
                    it.id == connectionId.value
            }
                ?: return ConnectionTestResult.Failure("No Web Analytics connection is configured.")
        val accountId =
            connection.accountId() ?: return ConnectionTestResult.Failure("Web Analytics account ID is missing.")
        val site =
            connection.metadata().webSites().firstOrNull()
                ?: return ConnectionTestResult.Failure("Add a Web Analytics site first.")
        return withToken(connection.id) { token ->
            val today =
                java.time.LocalDate
                    .now(clock)
                    .toString()
            client.fetch(token, accountId, site.siteTag, today, today)
            ConnectionTestResult.Success
        }
    }

    override suspend fun sync(request: SyncRequest): SyncResult {
        val connections = database.connectionDao().bySource(STORAGE_SOURCE)
        if (connections.isEmpty()) return SyncResult.Failure("No Web Analytics connection is configured.", false)
        val range = request.period.toDateRange(clock).withPrevious()
        return runCatching {
            syncTargets(connections) { connection ->
                val sites =
                    connection.metadata().webSites().filter {
                        request.domain == null ||
                            it.domain == request.domain
                    }
                if (sites.isEmpty()) return@syncTargets 0
                runCatching {
                    withRequiredToken(connection.id) { token ->
                        val account = requireNotNull(connection.accountId()) { "Web Analytics account ID is missing." }
                        val rows = syncTargets(sites) { site -> syncSite(token, account, site, range) }
                        // A selected site refresh is not proof that every site in this connection was refreshed.
                        val timestamp =
                            if (request.domain ==
                                null
                            ) {
                                Instant.now(clock).toString()
                            } else {
                                connection.lastSync
                            }
                        database.connectionDao().upsert(
                            listOf(connection.copy(status = "connected", lastSync = timestamp, lastError = null)),
                        )
                        rows
                    }
                }.onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    database.connectionDao().upsert(
                        listOf(connection.copy(status = "error", lastError = "Web Analytics sync failed.")),
                    )
                }.getOrThrow()
            }
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = {
                if (it is kotlinx.coroutines.CancellationException) throw it
                SyncResult.Failure(it.message ?: "Web Analytics sync failed.", true)
            },
        )
    }

    private suspend fun syncSite(
        token: CharArray,
        account: String,
        site: com.emmah.numeris.core.database.WebAnalyticsSiteEntity,
        range: DateRange,
    ): Int {
        val fetchedAt = Instant.now(clock).toString()
        val result = client.fetch(token, account, site.siteTag, range.start.toString(), range.end.toString())
        val daily =
            result.daily.map {
                WebAnalyticsDailyEntity(
                    site.domain,
                    it.date,
                    it.visits,
                    it.pageViews,
                    fetchedAt,
                )
            }
        val referrers = result.referrers.map { WebAnalyticsReferrerEntity(site.domain, it.date, it.name, it.value) }
        val pages = result.pages.map { WebAnalyticsPageEntity(site.domain, it.date, it.name, it.value) }
        val countries = result.countries.map { WebAnalyticsCountryEntity(site.domain, it.date, it.name, it.value) }
        database.writeTransaction {
            webAnalyticsDao().deleteWebDaily(site.domain, range.start.toString(), range.end.toString())
            webAnalyticsDao().deleteWebPages(site.domain, range.start.toString(), range.end.toString())
            webAnalyticsDao().deleteWebReferrers(site.domain, range.start.toString(), range.end.toString())
            webAnalyticsDao().deleteWebCountries(site.domain, range.start.toString(), range.end.toString())
            webAnalyticsDao().upsertWebAnalytics(daily)
            webAnalyticsDao().upsertWebAnalyticsReferrers(referrers)
            webAnalyticsDao().upsertWebAnalyticsPages(pages)
            webAnalyticsDao().upsertWebAnalyticsCountries(countries)
        }
        return daily.size + referrers.size + pages.size + countries.size
    }

    private suspend fun withToken(
        id: String,
        block: suspend (CharArray) -> ConnectionTestResult,
    ): ConnectionTestResult =
        runCatching { withRequiredToken(id, block) }
            .getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                ConnectionTestResult.Failure(it.message ?: "Web Analytics connection test failed.")
            }

    private suspend fun <T> withRequiredToken(
        id: String,
        block: suspend (CharArray) -> T,
    ): T {
        val token =
            secretStore.get(SecretKey("web_analytics:$id:api_token"))
                ?: error("Web Analytics API token is not configured.")
        return try {
            block(token)
        } finally {
            token.fill('\u0000')
        }
    }

    private fun com.emmah.numeris.core.database.ConnectionEntity.accountId(): String? =
        runCatching {
            Json
                .parseToJsonElement(config.orEmpty())
                .jsonObject
                .getValue("accountId")
                .jsonPrimitive.content
        }.getOrNull()

    private companion object {
        const val STORAGE_SOURCE = "web_analytics"
    }
}
