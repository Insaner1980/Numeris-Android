@file:Suppress("MaxLineLength", "LongMethod", "TooGenericExceptionCaught", "ktlint:standard:max-line-length")

package com.emmah.numeris.data

import com.emmah.numeris.core.database.BingPageStatEntity
import com.emmah.numeris.core.database.BingQueryStatEntity
import com.emmah.numeris.core.database.BingRankTrafficEntity
import com.emmah.numeris.core.database.BingRawItemEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.ProviderDate
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.BingApiClient
import com.emmah.numeris.core.network.RawJsonStoragePolicy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

class BingSyncService(
    private val database: NumerisDatabase,
    private val secretStore: SecretStore,
    private val client: BingApiClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.Bing

    override suspend fun testConnection(connectionId: ConnectionId?): ConnectionTestResult =
        withKey { key ->
            client.call(key, "GetUserSites")
            ConnectionTestResult.Success
        }

    override suspend fun sync(request: SyncRequest): SyncResult {
        val key = secretStore.get(API_KEY) ?: return SyncResult.Failure("No Bing API key is configured.", false)
        return try {
            val fetchedAt = Instant.now(clock).toString()
            val requestedDomain = request.domain
            val targets =
                database
                    .connectionDao()
                    .bySource("bing_webmaster")
                    .flatMap { it.configuredSites() }
                    .distinct()
                    .filter {
                        requestedDomain == null ||
                            com.emmah.numeris.core.model
                                .reportDomain(it) == requestedDomain
                    }
            require(targets.isNotEmpty()) { "No Bing site is configured." }
            syncTargets(targets) { site ->
                val rawItems = mutableListOf<BingRawItemEntity>()
                val rankRows = mutableListOf<BingRankTrafficEntity>()
                val queryRows = mutableListOf<BingQueryStatEntity>()
                val pageRows = mutableListOf<BingPageStatEntity>()
                if (requestedDomain == null) {
                    rawItems += client.call(key, "GetUserSites").toRaw("GetUserSites", "", fetchedAt)
                }
                listOf("GetCrawlStats", "GetCrawlIssues").forEach { method ->
                    rawItems += client.call(key, method, site).toRaw(method, site, fetchedAt)
                }
                client.call(key, "GetRankAndTrafficStats", site).forEach { item ->
                    val date =
                        com.emmah.numeris.core.model.ProviderDate
                            .normalize(item.firstString("Date", "date", "Day", "day"))
                            .orEmpty()
                    val raw = item.safeRaw()
                    rankRows +=
                        BingRankTrafficEntity(
                            site,
                            date,
                            item.firstLong("Clicks", "clicks"),
                            item.firstLong("Impressions", "impressions"),
                            raw,
                            fetchedAt,
                        )
                    rawItems += BingRawItemEntity("GetRankAndTrafficStats", site, date, raw, fetchedAt)
                }
                client.call(key, "GetQueryStats", site).forEach { item ->
                    val query = item.firstString("Query", "query", "Keyword", "keyword") ?: return@forEach
                    val date =
                        com.emmah.numeris.core.model.ProviderDate
                            .normalize(item.firstString("Date", "date"))
                            .orEmpty()
                    val raw = item.safeRaw()
                    queryRows +=
                        BingQueryStatEntity(
                            site,
                            query,
                            date,
                            item.firstLong("Clicks", "clicks"),
                            item.firstLong("Impressions", "impressions"),
                            item.firstDouble("AvgClickPosition", "avgClickPosition", "AverageClickPosition"),
                            item.firstDouble(
                                "AvgImpressionPosition",
                                "avgImpressionPosition",
                                "AverageImpressionPosition",
                            ),
                            raw,
                            fetchedAt,
                        )
                    rawItems += BingRawItemEntity("GetQueryStats", site, itemKey(query, date), raw, fetchedAt)
                }
                client.call(key, "GetPageStats", site).forEach { item ->
                    val page = item.firstString("Url", "url", "Page", "page") ?: return@forEach
                    val date =
                        com.emmah.numeris.core.model.ProviderDate
                            .normalize(item.firstString("Date", "date"))
                            .orEmpty()
                    val raw = item.safeRaw()
                    pageRows +=
                        BingPageStatEntity(
                            site,
                            page,
                            date,
                            item.firstLong("Clicks", "clicks"),
                            item.firstLong("Impressions", "impressions"),
                            raw,
                            fetchedAt,
                        )
                    rawItems += BingRawItemEntity("GetPageStats", site, itemKey(page, date), raw, fetchedAt)
                }
                database.writeTransaction {
                    bingDao().upsertTraffic(rankRows)
                    bingDao().upsertQueries(queryRows)
                    bingDao().upsertPages(pageRows)
                    bingDao().upsertRawItems(rawItems)
                    bingDao().deleteOldRawItems(
                        Instant.now(clock).minus(BING_RETENTION_DAYS, ChronoUnit.DAYS).toString(),
                    )
                }
                rankRows.size + queryRows.size + pageRows.size + rawItems.size
            }.let { SyncResult.Success(it) }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            SyncResult.Failure(failure.message ?: "Bing sync failed.", retryable = true)
        } finally {
            key.fill('\u0000')
        }
    }

    private suspend fun withKey(block: suspend (CharArray) -> ConnectionTestResult): ConnectionTestResult {
        val key = secretStore.get(API_KEY) ?: return ConnectionTestResult.Failure("No Bing API key is configured.")
        return try {
            block(key)
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            ConnectionTestResult.Failure(failure.message ?: "Bing connection test failed.")
        } finally {
            key.fill('\u0000')
        }
    }

    private fun List<JsonObject>.toRaw(
        method: String,
        siteUrl: String,
        fetchedAt: String,
    ): List<BingRawItemEntity> =
        mapIndexed { index, item ->
            BingRawItemEntity(
                method,
                siteUrl,
                item.firstString("Url", "Query", "Date", "SiteUrl") ?: index.toString(),
                item.safeRaw(),
                fetchedAt,
            )
        }

    private fun JsonObject.firstString(vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> get(name)?.jsonPrimitive?.content?.takeIf(String::isNotBlank) }

    private fun JsonObject.firstLong(vararg names: String): Long? =
        names.firstNotNullOfOrNull { name -> get(name)?.jsonPrimitive?.longOrNull }

    private fun JsonObject.firstDouble(vararg names: String): Double? =
        names.firstNotNullOfOrNull { name -> get(name)?.jsonPrimitive?.doubleOrNull }

    private fun JsonObject.safeRaw() = RawJsonStoragePolicy.redactAndLimit(toString())

    private fun itemKey(
        value: String,
        date: String,
    ) = if (date.isBlank()) value else "$value|$date"

    private companion object {
        val API_KEY = SecretKey("bing:bing:api_key")
        const val BING_RETENTION_DAYS = 180L
    }
}
