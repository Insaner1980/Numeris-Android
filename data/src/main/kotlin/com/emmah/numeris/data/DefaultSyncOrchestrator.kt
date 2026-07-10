@file:Suppress("MaxLineLength")

package com.emmah.numeris.data

import com.emmah.numeris.core.model.ReportingGroup
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncBatchResult
import com.emmah.numeris.core.model.SyncOrchestrator
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class DefaultSyncOrchestrator(
    services: List<SourceSyncService>,
    private val configuredSources: suspend () -> Set<SourceId> = { services.mapTo(linkedSetOf(), SourceSyncService::source) },
    private val onSourceResult: suspend (SourceId, SyncResult) -> Unit = { _, _ -> },
) : SyncOrchestrator {
    private val servicesBySource = services.associateBy(SourceSyncService::source)
    private val mutex = Mutex()
    private val state = MutableStateFlow<SyncState>(SyncState.Idle)

    override fun observeState(): Flow<SyncState> = state.asStateFlow()

    override suspend fun syncSource(
        source: SourceId,
        request: SyncRequest,
    ): SyncBatchResult = runSources(listOf(source), request)

    override suspend fun syncReportingGroup(
        group: ReportingGroup,
        request: SyncRequest,
    ): SyncBatchResult = runSources(group.sources, request)

    override suspend fun syncAll(request: SyncRequest): SyncBatchResult =
        runSources(
            syncOrder.filter { source -> source in configuredSources() && servicesBySource.containsKey(source) },
            request,
        )

    private suspend fun runSources(
        sources: List<SourceId>,
        request: SyncRequest,
    ): SyncBatchResult =
        mutex.withLock {
            if (sources.isEmpty()) {
                val finalState = SyncState.Success(Instant.now())
                state.value = finalState
                return@withLock SyncBatchResult(finalState)
            }
            val succeeded = linkedSetOf<SourceId>()
            val failed = linkedSetOf<SourceId>()
            var authRequired: SourceId? = null
            sources.forEach { source ->
                val service = servicesBySource[source] ?: return@forEach
                state.value = SyncState.Running(source)
                val result = service.sync(request)
                onSourceResult(source, result)
                when (result) {
                    is SyncResult.Success -> succeeded += source
                    is SyncResult.Failure -> {
                        failed += source
                        if (!result.retryable && result.safeMessage.contains("authorization", ignoreCase = true)) {
                            authRequired = source
                        }
                    }
                }
            }
            val finalState =
                when {
                    authRequired != null -> SyncState.AuthRequired(authRequired)
                    failed.isEmpty() -> SyncState.Success(Instant.now())
                    succeeded.isNotEmpty() -> SyncState.PartialSuccess(succeeded, failed)
                    else ->
                        SyncState.Failed(
                            sources.singleOrNull(),
                            "Sync failed. Previously saved data is still available.",
                        )
                }
            state.value = finalState
            SyncBatchResult(finalState)
        }

    private val ReportingGroup.sources: List<SourceId>
        get() =
            when (this) {
                ReportingGroup.Traffic ->
                    listOf(
                        SourceId.CloudflareZone,
                        SourceId.CloudflareWebAnalytics,
                        SourceId.GoogleAnalytics,
                    )
                ReportingGroup.Search -> listOf(SourceId.SearchConsole, SourceId.Bing, SourceId.Sitemap)
                ReportingGroup.Performance -> listOf(SourceId.Crux, SourceId.PageSpeed)
                ReportingGroup.Health -> listOf(SourceId.Uptime, SourceId.Sitemap)
            }

    private companion object {
        val syncOrder =
            listOf(
                SourceId.Uptime,
                SourceId.Sitemap,
                SourceId.CloudflareZone,
                SourceId.CloudflareWebAnalytics,
                SourceId.Crux,
                SourceId.PageSpeed,
                SourceId.Bing,
                SourceId.SearchConsole,
                SourceId.GoogleAnalytics,
            )
    }
}
