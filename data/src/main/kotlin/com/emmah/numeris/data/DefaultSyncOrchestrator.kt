@file:Suppress("MaxLineLength")

package com.emmah.numeris.data

import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncBatchResult
import com.emmah.numeris.core.model.SyncOrchestrator
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.model.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class DefaultSyncOrchestrator(
    services: List<SourceSyncService>,
    private val configuredSources: suspend () -> Set<SourceId> = {
        services.mapTo(linkedSetOf(), SourceSyncService::source)
    },
    private val onSourceResult: suspend (SourceId, SyncRequest, SyncResult) -> Unit = { _, _, _ -> },
    private val inspectIndexing: suspend (
        String,
    ) -> SyncResult = { SyncResult.Failure("URL Inspection is unavailable.", false) },
    private val indexingDomains: suspend () -> List<String> = { emptyList() },
) : SyncOrchestrator {
    private val servicesBySource = services.associateBy(SourceSyncService::source)
    private val mutex = Mutex()
    private val pending =
        mutableMapOf<Pair<List<SourceId>, SyncRequest>, kotlinx.coroutines.CompletableDeferred<SyncBatchResult>>()
    private val state = MutableStateFlow<SyncState>(SyncState.Idle)

    override fun observeState(): Flow<SyncState> = state.asStateFlow()

    override suspend fun syncAll(request: SyncRequest): SyncBatchResult =
        runSources(
            syncOrder.filter { source -> source in configuredSources() && servicesBySource.containsKey(source) },
            request,
        )

    /**
     * Yksi päivitys kaikelle: kaikki lähteet kaikille sivustoille koko historian jaksolta ja sen jälkeen
     * URL Inspection jokaiselle sivustolle. Sama pyyntö kuin taustasynkillä, joten samanaikaiset ajot yhdistyvät.
     */
    suspend fun syncEverything(): SyncBatchResult {
        val batch = syncAll(SyncRequest(Period.All))
        if (batch.state is SyncState.AuthRequired || SourceId.SearchConsole !in configuredSources()) return batch
        return exclusive {
            state.value = SyncState.Running(SourceId.SearchConsole)
            val failure =
                indexingDomains()
                    .map { inspectIndexing(it) }
                    .filterIsInstance<SyncResult.Failure>()
                    .firstOrNull()
            // Virhe tallennetaan yhteydelle, jotta Sources kertoo syyn eikä pelkkää "failed"-tilaa.
            // Jos jo varsinainen Search Console -haku epäonnistui, sen viesti on tärkeämpi eikä sitä korvata.
            val searchSyncFailed = (batch.state as? SyncState.PartialSuccess)?.failed?.contains(SourceId.SearchConsole) == true
            if (!searchSyncFailed) failure?.let { onSourceResult(SourceId.SearchConsole, SyncRequest(Period.All), it) }
            val finalState = if (failure != null) batch.state.withFailed(SourceId.SearchConsole) else batch.state
            state.value = finalState
            SyncBatchResult(finalState)
        }
    }

    private fun SyncState.withFailed(source: SourceId): SyncState =
        when (this) {
            is SyncState.Success -> SyncState.PartialSuccess(emptySet(), setOf(source))
            is SyncState.PartialSuccess -> copy(succeeded = succeeded - source, failed = failed + source)
            else -> this
        }

    suspend fun <T> exclusive(block: suspend () -> T): T =
        mutex.withLock {
            try {
                block()
            } catch (cancelled: CancellationException) {
                state.value = SyncState.Idle
                throw cancelled
            }
        }

    private suspend fun runSources(
        sources: List<SourceId>,
        request: SyncRequest,
    ): SyncBatchResult {
        val key = sources to request
        val created = kotlinx.coroutines.CompletableDeferred<SyncBatchResult>()
        val operation = synchronized(pending) { pending.getOrPut(key) { created } }
        if (operation !== created) return operation.await()
        try {
            return runCatching { runSourcesSerialized(sources, request) }.fold(
                onSuccess = {
                    created.complete(it)
                    it
                },
                onFailure = {
                    created.completeExceptionally(it)
                    throw it
                },
            )
        } finally {
            synchronized(pending) { pending.remove(key) }
        }
    }

    private suspend fun runSourcesSerialized(
        sources: List<SourceId>,
        request: SyncRequest,
    ): SyncBatchResult =
        exclusive {
            val eligible = configuredSources()
            val requested = sources.filter { it in eligible && it != SourceId.GoogleAnalytics }
            if (requested.isEmpty()) {
                val finalState = SyncState.Failed(null, "No configured source is available for this operation.")
                state.value = finalState
                return@exclusive SyncBatchResult(finalState)
            }
            val succeeded = linkedSetOf<SourceId>()
            val failed = linkedSetOf<SourceId>()
            var authRequired: SourceId? = null
            requested.forEach { source ->
                val service = servicesBySource[source] ?: return@forEach
                state.value = SyncState.Running(source)
                val result = syncService(service, request)
                onSourceResult(source, request, result)
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

    private suspend fun syncService(
        service: SourceSyncService,
        request: SyncRequest,
    ): SyncResult =
        runCatching { service.sync(request) }.getOrElse {
            if (it is CancellationException) throw it
            SyncResult.Failure("Sync failed. Previously saved data is still available.", true)
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
            )
    }
}
