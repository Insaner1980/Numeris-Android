@file:Suppress("MaxLineLength", "ReturnCount")

package com.emmah.numeris.data

import com.emmah.numeris.core.common.NumerisFailure
import com.emmah.numeris.core.database.CloudflareCountryEntity
import com.emmah.numeris.core.database.CloudflarePageEntity
import com.emmah.numeris.core.database.CloudflareStatusEntity
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.DateRange
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.model.withPrevious
import com.emmah.numeris.core.network.CloudflareApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant

class CloudflareSyncService(
    private val database: NumerisDatabase,
    private val secretStore: SecretStore,
    private val client: CloudflareApiClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.CloudflareZone

    override suspend fun testConnection(connectionId: ConnectionId?): ConnectionTestResult {
        val connection =
            database.connectionDao().bySource(STORAGE_SOURCE).firstOrNull {
                connectionId == null ||
                    it.id == connectionId.value
            }
                ?: return ConnectionTestResult.Failure("No Cloudflare connection is configured.")
        val config =
            connection.config.toConfig() ?: return ConnectionTestResult.Failure("Cloudflare configuration is invalid.")
        return withToken(connection.id) { token ->
            val actualDomain = client.validateZone(token, config.zoneId)
            if (SiteIdentity.normalizeDomain(actualDomain) == config.domain) {
                ConnectionTestResult.Success
            } else {
                ConnectionTestResult.Failure("The configured zone does not match ${config.domain}.")
            }
        }
    }

    override suspend fun sync(request: SyncRequest): SyncResult {
        val requestedDomain = request.domain?.let(SiteIdentity::normalizeDomain)
        val connections =
            database.connectionDao().bySource(STORAGE_SOURCE).filter { row ->
                val domain = row.config.toConfig()?.domain
                requestedDomain == null || domain == requestedDomain
            }
        if (connections.isEmpty()) return SyncResult.Failure("No Cloudflare connection is configured.", false)
        val range = request.period.toDateRange(clock).withPrevious()
        return runCatching {
            syncTargets(connections) { connection ->
                runCatching { syncConnection(connection, range) }
                    .onFailure { failure ->
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        database.connectionDao().upsert(
                            listOf(connection.copy(status = "error", lastError = syncFailureMessage(failure))),
                        )
                    }.getOrThrow()
            }
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = {
                if (it is kotlinx.coroutines.CancellationException) throw it
                SyncResult.Failure(syncFailureMessage(it), retryable = true)
            },
        )
    }

    private fun syncFailureMessage(failure: Throwable): String =
        when (failure) {
            is NumerisFailure -> failure.message ?: "Cloudflare request failed."
            is IllegalArgumentException -> "Cloudflare returned an invalid or oversized analytics response."
            is IllegalStateException -> "Cloudflare analytics request or response could not be processed."
            else -> "Cloudflare report could not be saved. Previously saved data is still available."
        }

    private suspend fun syncConnection(
        connection: com.emmah.numeris.core.database.ConnectionEntity,
        range: DateRange,
    ): Int {
        val config = requireNotNull(connection.config.toConfig())
        val result =
            withRequiredToken(connection.id) { token ->
                client.fetchTraffic(token, config.zoneId, range.start.toString(), range.end.toString())
            }
        val fetchedAt = Instant.now(clock).toString()
        val daily =
            result.daily.map { row ->
                CloudflareTrafficEntity(
                    config.domain,
                    row.date,
                    row.pageViews,
                    row.uniqueVisitors,
                    row.requests,
                    row.cachedRequests,
                    row.cachedBytes,
                    row.totalBytes,
                    row.threats,
                    fetchedAt = fetchedAt,
                )
            }
        val countries =
            result.countries.map {
                CloudflareCountryEntity(
                    config.domain,
                    it.date,
                    it.name,
                    it.value,
                )
            }
        val pages = result.pages.map { CloudflarePageEntity(config.domain, it.date, it.name, it.value) }
        val statuses =
            result.statuses.map {
                CloudflareStatusEntity(
                    config.domain,
                    it.date,
                    it.statusCode,
                    it.requests,
                )
            }
        database.writeTransaction {
            cloudflareDao().upsertTraffic(daily)
            cloudflareDao().upsertCountries(countries)
            cloudflareDao().upsertPages(pages)
            cloudflareDao().upsertStatuses(statuses)
            connectionDao().upsert(
                listOf(connection.copy(status = "connected", lastSync = fetchedAt, lastError = null)),
            )
        }
        return daily.size + countries.size + pages.size + statuses.size
    }

    private suspend fun withToken(
        connectionId: String,
        block: suspend (CharArray) -> ConnectionTestResult,
    ): ConnectionTestResult =
        runCatching { withRequiredToken(connectionId, block) }
            .getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                ConnectionTestResult.Failure(it.message ?: "Cloudflare connection test failed.")
            }

    private suspend fun <T> withRequiredToken(
        connectionId: String,
        block: suspend (CharArray) -> T,
    ): T {
        val token =
            secretStore.get(SecretKey("cloudflare:$connectionId:api_token"))
                ?: throw NumerisFailure.InvalidConfiguration("Cloudflare API token is not configured.")
        return try {
            block(token)
        } finally {
            token.fill('\u0000')
        }
    }

    private fun String?.toConfig(): Config? =
        runCatching {
            val root = Json.parseToJsonElement(requireNotNull(this)).jsonObject
            Config(
                domain = SiteIdentity.normalizeDomain(root.getValue("domain").jsonPrimitive.content),
                zoneId = root.getValue("zoneId").jsonPrimitive.content,
            )
        }.getOrNull()

    private data class Config(
        val domain: String,
        val zoneId: String,
    )

    private companion object {
        const val STORAGE_SOURCE = "cloudflare"
    }
}
