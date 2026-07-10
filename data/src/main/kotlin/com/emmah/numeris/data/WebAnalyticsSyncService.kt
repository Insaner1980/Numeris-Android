@file:Suppress("MaxLineLength", "ReturnCount")

package com.emmah.numeris.data

import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.WebAnalyticsCountryEntity
import com.emmah.numeris.core.database.WebAnalyticsDailyEntity
import com.emmah.numeris.core.database.WebAnalyticsPageEntity
import com.emmah.numeris.core.database.WebAnalyticsReferrerEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
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

    override suspend fun testConnection(): ConnectionTestResult {
        val connection =
            database.connectionDao().bySource(STORAGE_SOURCE).firstOrNull()
                ?: return ConnectionTestResult.Failure("No Web Analytics connection is configured.")
        val accountId = connection.accountId() ?: return ConnectionTestResult.Failure("Web Analytics account ID is missing.")
        val site =
            database.cloudflareDao().webAnalyticsSites().firstOrNull()
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
        val connection =
            database.connectionDao().bySource(STORAGE_SOURCE).firstOrNull()
                ?: return SyncResult.Failure("No Web Analytics connection is configured.", false)
        val accountId = connection.accountId() ?: return SyncResult.Failure("Web Analytics account ID is missing.", false)
        val range = request.period.toDateRange()
        return runCatching {
            withRequiredToken(connection.id) { token ->
                var rows = 0
                database
                    .cloudflareDao()
                    .webAnalyticsSites()
                    .filter { request.domain == null || it.domain == request.domain }
                    .forEach { site ->
                        val fetchedAt = Instant.now(clock).toString()
                        val result = client.fetch(token, accountId, site.siteTag, range.start.toString(), range.end.toString())
                        val daily = result.daily.map { WebAnalyticsDailyEntity(site.domain, it.date, it.visits, it.pageViews, fetchedAt) }
                        val referrers =
                            result.referrers.map {
                                WebAnalyticsReferrerEntity(
                                    site.domain,
                                    range.end.toString(),
                                    it.name,
                                    it.value,
                                )
                            }
                        val pages = result.pages.map { WebAnalyticsPageEntity(site.domain, range.end.toString(), it.name, it.value) }
                        val countries =
                            result.countries.map {
                                WebAnalyticsCountryEntity(
                                    site.domain,
                                    range.end.toString(),
                                    it.name,
                                    it.value,
                                )
                            }
                        database.writeTransaction {
                            cloudflareDao().upsertWebAnalytics(daily)
                            cloudflareDao().upsertWebAnalyticsReferrers(referrers)
                            cloudflareDao().upsertWebAnalyticsPages(pages)
                            cloudflareDao().upsertWebAnalyticsCountries(countries)
                        }
                        rows += daily.size + referrers.size + pages.size + countries.size
                    }
                rows
            }
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = { SyncResult.Failure(it.message ?: "Web Analytics sync failed.", true) },
        )
    }

    private suspend fun withToken(
        id: String,
        block: suspend (CharArray) -> ConnectionTestResult,
    ): ConnectionTestResult =
        runCatching { withRequiredToken(id, block) }
            .getOrElse { ConnectionTestResult.Failure(it.message ?: "Web Analytics connection test failed.") }

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
