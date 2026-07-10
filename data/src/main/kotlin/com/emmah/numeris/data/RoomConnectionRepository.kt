package com.emmah.numeris.data

import com.emmah.numeris.core.database.BingSiteEntity
import com.emmah.numeris.core.database.ConnectionDao
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.model.Connection
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionRepository
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SaveConnectionCommand
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

class RoomConnectionRepository(
    private val database: NumerisDatabase,
    private val secretStore: SecretStore,
    private val syncServices: () -> Map<SourceId, SourceSyncService>,
) : ConnectionRepository {
    private val dao: ConnectionDao get() = database.connectionDao()

    override fun observeConnections(): Flow<List<Connection>> = dao.observeConnections().map(::mapConnections)

    override suspend fun save(command: SaveConnectionCommand) {
        val existing = dao.find(command.id.value)
        command.secrets.forEach { (key, value) ->
            if (value.isNotEmpty()) secretStore.put(key, value)
        }
        dao.upsert(
            listOf(
                ConnectionEntity(
                    id = command.id.value,
                    source = command.source.storageName,
                    status = "configured",
                    config = JsonObject(command.metadata.mapValues { JsonPrimitive(it.value) }).toString(),
                    lastSync = existing?.lastSync,
                    lastError = null,
                ),
            ),
        )
        if (command.source == SourceId.Bing) {
            val sites =
                command.metadata["sites"]
                    .orEmpty()
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .map { site -> BingSiteEntity(siteUrl = site, discoveredAt = Instant.now().toString()) }
            database.bingDao().deleteAllSites()
            database.bingDao().upsertSites(sites)
        }
    }

    override suspend fun delete(id: ConnectionId) {
        val connection = dao.find(id.value) ?: return
        connection.source
            .toSourceId()
            ?.secretKeys(id.value)
            ?.forEach { secretStore.delete(it) }
        if (connection.source == "bing_webmaster") database.bingDao().deleteAllSites()
        dao.delete(id.value)
    }

    override suspend fun test(id: ConnectionId): ConnectionTestResult {
        val connection = dao.find(id.value)
        val source =
            connection?.source?.toSourceId()
                ?: return ConnectionTestResult.Failure("Connection is not configured")
        val result =
            syncServices()[source]?.testConnection()
                ?: ConnectionTestResult.Failure("Connection test is not available")
        dao.upsert(
            listOf(
                connection.copy(
                    status = if (result == ConnectionTestResult.Success) "connected" else "error",
                    lastError = (result as? ConnectionTestResult.Failure)?.safeMessage,
                ),
            ),
        )
        return result
    }

    private fun ConnectionEntity.toDomain(): Connection {
        val sourceId = source.toSourceId() ?: SourceId.Uptime
        return Connection(
            id = ConnectionId(id),
            source = sourceId,
            displayName = id.substringAfter(':', sourceId.displayName),
            configured = status != "disconnected",
            lastSuccessfulSync = lastSync?.let { runCatching { Instant.parse(it) }.getOrNull() },
            lastError = lastError,
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

    private val SourceId.storageName: String
        get() =
            when (this) {
                SourceId.CloudflareZone -> "cloudflare"
                SourceId.CloudflareWebAnalytics -> "web_analytics"
                SourceId.SearchConsole -> "search_console"
                SourceId.GoogleAnalytics -> "google_analytics"
                SourceId.Crux -> "crux"
                SourceId.PageSpeed -> "pagespeed"
                SourceId.Bing -> "bing_webmaster"
                SourceId.Uptime -> "uptime"
                SourceId.Sitemap -> "sitemap"
            }

    private val SourceId.displayName: String get() = name.replace(Regex("([a-z])([A-Z])"), "${'$'}1 ${'$'}2")

    private fun String.toSourceId(): SourceId? =
        when (this) {
            "cloudflare" -> SourceId.CloudflareZone
            "web_analytics" -> SourceId.CloudflareWebAnalytics
            "search_console" -> SourceId.SearchConsole
            "google_analytics" -> SourceId.GoogleAnalytics
            "crux" -> SourceId.Crux
            "pagespeed" -> SourceId.PageSpeed
            "bing_webmaster" -> SourceId.Bing
            "uptime" -> SourceId.Uptime
            "sitemap" -> SourceId.Sitemap
            else -> null
        }
}
