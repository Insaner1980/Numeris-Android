package com.emmah.numeris.data

import com.emmah.numeris.core.database.BingSiteEntity
import com.emmah.numeris.core.database.ConnectionDao
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.WebAnalyticsSiteEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.Connection
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionRepository
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SaveConnectionCommand
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.sourceIdFromStorage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class RoomConnectionRepository(
    private val database: NumerisDatabase,
    private val secretStore: SecretStore,
    private val serialize: suspend (suspend () -> Unit) -> Unit = { it() },
    private val syncServices: () -> Map<SourceId, SourceSyncService>,
) : ConnectionRepository {
    private val dao: ConnectionDao get() = database.connectionDao()

    override fun observeConnections(): Flow<List<Connection>> = dao.observeConnections().map(::mapConnections)

    override suspend fun save(command: SaveConnectionCommand) =
        serialize {
            val existing = dao.find(command.id.value)
            val webSites =
                if (command.source ==
                    SourceId.CloudflareWebAnalytics
                ) {
                    command.metadata.webSites()
                } else {
                    emptyList()
                }
            val sites =
                command.metadata["sites"]
                    .orEmpty()
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
            require(sites.mapNotNull(::normalizeSiteDomain).size == sites.size) { "Invalid configured site" }
            val replacements = command.secrets.filterValues { it.isNotEmpty() }
            val previous = replacements.keys.associateWith { secretStore.get(it) }
            try {
                runCatching {
                    replacements.forEach { (key, value) -> secretStore.put(key, value) }
                    database.writeTransaction {
                        connectionDao().upsert(
                            listOf(
                                ConnectionEntity(
                                    id = command.id.value,
                                    source = command.source.storageName,
                                    status = "configured",
                                    config =
                                        JsonObject(
                                            command.metadata.mapValues { JsonPrimitive(it.value) },
                                        ).toString(),
                                    lastSync = existing?.lastSync,
                                    lastError = null,
                                ),
                            ),
                        )
                        replaceTargets(command, existing, webSites, sites)
                    }
                }.onFailure { failure ->
                    withContext(NonCancellable) {
                        previous.forEach { (key, value) ->
                            runCatching { if (value == null) secretStore.delete(key) else secretStore.put(key, value) }
                                .onFailure(failure::addSuppressed)
                        }
                    }
                    throw failure
                }
            } finally {
                previous.values.forEach { it?.fill('\u0000') }
            }
        }

    private suspend fun replaceTargets(
        command: SaveConnectionCommand,
        existing: ConnectionEntity?,
        webSites: List<WebAnalyticsSiteEntity>,
        sites: List<String>,
    ) {
        if (command.source == SourceId.CloudflareWebAnalytics) {
            val otherDomains =
                dao
                    .bySource("web_analytics")
                    .filter { it.id != command.id.value }
                    .flatMap { it.metadata().webSites() }
                    .map { it.domain }
                    .toSet()
            require(webSites.none { it.domain in otherDomains }) { "A site is already assigned to another connection" }
            database.webAnalyticsDao().deleteSites(
                existing
                    ?.metadata()
                    ?.webSites()
                    .orEmpty()
                    .map { it.domain },
            )
            database.webAnalyticsDao().upsertWebAnalyticsSites(
                webSites.map { it.copy(discoveredAt = Instant.now().toString()) },
            )
        }
        if (command.source == SourceId.Bing) {
            database.bingDao().deleteAllSites()
            database.bingDao().upsertSites(
                sites.distinct().map {
                    BingSiteEntity(siteUrl = it, discoveredAt = Instant.now().toString())
                },
            )
        }
    }

    override suspend fun delete(id: ConnectionId) =
        serialize {
            val connection = dao.find(id.value) ?: return@serialize
            val previous =
                sourceIdFromStorage(connection.source)
                    ?.secretKeys(
                        id.value,
                    ).orEmpty()
                    .associateWith { secretStore.get(it) }
            try {
                runCatching {
                    previous.keys.forEach { secretStore.delete(it) }
                    database.writeTransaction {
                        if (connection.source == "bing_webmaster") bingDao().deleteAllSites()
                        if (connection.source ==
                            "web_analytics"
                        ) {
                            webAnalyticsDao().deleteSites(connection.metadata().webSites().map { it.domain })
                        }
                        connectionDao().delete(id.value)
                    }
                }.onFailure { failure ->
                    withContext(NonCancellable) {
                        previous.forEach { (key, value) ->
                            if (value !=
                                null
                            ) {
                                runCatching { secretStore.put(key, value) }.onFailure(failure::addSuppressed)
                            }
                        }
                    }
                    throw failure
                }
            } finally {
                previous.values.forEach { it?.fill('\u0000') }
            }
        }

    override suspend fun test(id: ConnectionId): ConnectionTestResult {
        var outcome: ConnectionTestResult = ConnectionTestResult.Failure("Connection is not configured")
        serialize {
            val connection = dao.find(id.value) ?: return@serialize
            val source = sourceIdFromStorage(connection.source) ?: return@serialize
            outcome = syncServices()[source]?.testConnection(id)
                ?: ConnectionTestResult.Failure("Connection test is not available")
            dao.upsert(
                listOf(
                    connection.copy(
                        status = if (outcome == ConnectionTestResult.Success) "connected" else "error",
                        lastError = (outcome as? ConnectionTestResult.Failure)?.safeMessage,
                    ),
                ),
            )
        }
        return outcome
    }

    private fun ConnectionEntity.toDomain(): Connection {
        val sourceId = sourceIdFromStorage(source) ?: SourceId.Uptime
        return Connection(
            id = ConnectionId(id),
            source = sourceId,
            displayName = id.substringAfter(':', sourceId.displayName),
            configured = status != "disconnected",
            lastSuccessfulSync = lastSync?.let { runCatching { Instant.parse(it) }.getOrNull() },
            lastError = lastError,
            metadata =
                runCatching {
                    Json.parseToJsonElement(config.orEmpty()).jsonObject.mapValues { it.value.jsonPrimitive.content }
                }.getOrDefault(emptyMap()),
        )
    }

    private fun mapConnections(rows: List<ConnectionEntity>): List<Connection> = rows.map { it.toDomain() }

    private fun SourceId.secretKeys(connectionId: String): List<SecretKey> =
        when (this) {
            SourceId.CloudflareZone -> listOf(SecretKey("cloudflare:$connectionId:api_token"))
            SourceId.CloudflareWebAnalytics -> listOf(SecretKey("web_analytics:$connectionId:api_token"))
            SourceId.Crux -> listOf(SecretKey("crux:crux:api_key"))
            SourceId.PageSpeed -> listOf(SecretKey("pagespeed:pagespeed:api_key"))
            SourceId.Bing -> listOf(SecretKey("bing:bing:api_key"))
            else -> emptyList()
        }

    private val SourceId.displayName: String get() = name.replace(Regex("([a-z])([A-Z])"), "${'$'}1 ${'$'}2")
}

private fun normalizeSiteDomain(value: String) =
    com.emmah.numeris.core.model
        .reportDomain(value)
