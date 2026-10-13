package com.emmah.numeris.data

import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.SitemapUrlEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.SitemapClient
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

class SitemapSyncService(
    private val database: NumerisDatabase,
    private val client: SitemapClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.Sitemap

    override suspend fun testConnection(connectionId: ConnectionId?) = ConnectionTestResult.Success

    override suspend fun sync(request: SyncRequest): SyncResult {
        val targets =
            request.domain?.let { listOf(SiteIdentity.normalizeDomain(it)) }
                ?: ReportRepository(database, clock).observeDomains().first()
        return runCatching {
            syncTargets(targets) { domain ->
                val now = Instant.now(clock).toString()
                val discovered = client.discoverUrls(domain)
                val existing = database.searchDao().sitemapUrls(domain).associateBy { it.url }
                val active =
                    discovered.map { url ->
                        existing[url]?.copy(lastSeenAt = now, removedAt = null)
                            ?: SitemapUrlEntity(domain, url, discoveredAt = now, lastSeenAt = now)
                    }
                val removed =
                    existing.values.filter { it.url !in discovered }.map { row ->
                        if (row.removedAt == null) row.copy(removedAt = now) else row
                    }
                database.writeTransaction { searchDao().upsertSitemapUrls(active + removed) }
                active.size
            }
        }.fold(
            onSuccess = { rowsWritten -> SyncResult.Success(rowsWritten) },
            onFailure = { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                SyncResult.Failure(failure.message ?: "Sitemap sync failed.", retryable = true)
            },
        )
    }
}
