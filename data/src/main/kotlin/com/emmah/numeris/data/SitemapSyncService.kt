package com.emmah.numeris.data

import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.SitemapUrlEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.SitemapClient
import java.time.Clock
import java.time.Instant

class SitemapSyncService(
    private val database: NumerisDatabase,
    private val client: SitemapClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.Sitemap

    override suspend fun testConnection(): ConnectionTestResult = ConnectionTestResult.Success

    override suspend fun sync(request: SyncRequest): SyncResult {
        val domainValue = request.domain ?: return SyncResult.Failure("A domain is required for sitemap sync.", false)
        val domain = SiteIdentity.normalizeDomain(domainValue)
        return runCatching {
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
        }.fold(
            onSuccess = { rowsWritten -> SyncResult.Success(rowsWritten) },
            onFailure = { failure ->
                SyncResult.Failure(failure.message ?: "Sitemap sync failed.", retryable = true)
            },
        )
    }
}
