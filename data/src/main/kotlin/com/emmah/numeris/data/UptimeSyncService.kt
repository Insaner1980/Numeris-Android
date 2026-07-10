package com.emmah.numeris.data

import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.SyncRunEntity
import com.emmah.numeris.core.database.UptimeCheckEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.UptimeClient
import java.time.Clock
import java.time.Instant
import java.util.UUID

class UptimeSyncService(
    private val database: NumerisDatabase,
    private val client: UptimeClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.Uptime

    override suspend fun testConnection(): ConnectionTestResult = ConnectionTestResult.Success

    override suspend fun sync(request: SyncRequest): SyncResult {
        val runId = UUID.randomUUID().toString()
        val startedAt = Instant.now(clock).toString()
        val targets =
            request.domain?.let { listOf(SiteIdentity.fromDomainOrUrl(it).homePageUrl) }
                ?: database.performanceDao().enabledUrls().map { it.url }
        database.syncRunDao().upsert(SyncRunEntity(runId, source.name, startedAt, state = "running"))
        return runCatching {
            val rows =
                targets.map { target ->
                    val result = client.probe(target)
                    UptimeCheckEntity(
                        domain = result.domain,
                        checkedAt = Instant.now(clock).toString(),
                        status = result.status,
                        statusCode = result.statusCode,
                        responseMs = result.responseMs,
                        errorMessage = result.safeError,
                    )
                }
            database.writeTransaction {
                healthDao().upsertChecks(rows)
                syncRunDao().upsert(
                    SyncRunEntity(
                        id = runId,
                        source = source.name,
                        startedAt = startedAt,
                        completedAt = Instant.now(clock).toString(),
                        state = "success",
                        rowsWritten = rows.size.toLong(),
                    ),
                )
            }
            rows.size
        }.fold(
            onSuccess = { rowsWritten -> SyncResult.Success(rowsWritten) },
            onFailure = { failure ->
                val safeMessage = failure.message ?: "Uptime sync failed."
                database.syncRunDao().upsert(
                    SyncRunEntity(
                        runId,
                        source.name,
                        startedAt,
                        Instant.now(clock).toString(),
                        "failed",
                        safeError = safeMessage,
                    ),
                )
                SyncResult.Failure(safeMessage, retryable = true)
            },
        )
    }
}
