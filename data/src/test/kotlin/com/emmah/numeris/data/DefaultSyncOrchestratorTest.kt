package com.emmah.numeris.data

import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.model.SyncState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSyncOrchestratorTest {
    @Test fun freshInstallAndDisconnectedSourcesCompleteWithoutRetry() =
        runBlocking {
            val fresh = DefaultSyncOrchestrator(emptyList())
            assertTrue(fresh.syncEverything().state is SyncState.Success)
            assertTrue(fresh.observeState().first() is SyncState.Success)

            val service =
                object : SourceSyncService {
                    override val source = SourceId.Uptime

                    override suspend fun testConnection(connectionId: ConnectionId?) = ConnectionTestResult.Success

                    override suspend fun sync(request: SyncRequest): SyncResult = error("Unexpected sync")
                }
            val disconnected = DefaultSyncOrchestrator(listOf(service), configuredSources = { emptySet() })
            assertTrue(disconnected.syncEverything().state is SyncState.Success)
            assertTrue(disconnected.observeState().first() is SyncState.Success)
        }

    @Test fun identicalConcurrentRefreshesAreCoalesced() =
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val service =
                object : SourceSyncService {
                    override val source = SourceId.Uptime

                    override suspend fun testConnection(connectionId: ConnectionId?) = ConnectionTestResult.Success

                    override suspend fun sync(request: SyncRequest): SyncResult {
                        calls++
                        entered.complete(Unit)
                        release.await()
                        return SyncResult.Success(1)
                    }
                }
            val orchestrator = DefaultSyncOrchestrator(listOf(service))
            val first = async { orchestrator.syncAll(SyncRequest(Period.Last7Days)) }
            entered.await()
            val second = async { orchestrator.syncAll(SyncRequest(Period.Last7Days)) }
            yield()
            release.complete(Unit)
            assertEquals(first.await(), second.await())
            assertEquals(1, calls)
        }

    @Test fun syncSkipsUnconfiguredAndRetiredProvidersAndRetainsPartialFailure() =
        runBlocking {
            val calls = mutableListOf<SourceId>()

            fun service(
                id: SourceId,
                fail: Boolean = false,
            ) = object : SourceSyncService {
                override val source = id

                override suspend fun testConnection(connectionId: ConnectionId?) = ConnectionTestResult.Success

                override suspend fun sync(request: SyncRequest): SyncResult {
                    calls += id
                    return if (fail) SyncResult.Failure("Fixture failure", true) else SyncResult.Success(1)
                }
            }
            val orchestrator =
                DefaultSyncOrchestrator(
                    listOf(
                        service(SourceId.CloudflareZone),
                        service(SourceId.Bing, true),
                        service(SourceId.GoogleAnalytics),
                    ),
                    configuredSources = { setOf(SourceId.CloudflareZone, SourceId.Bing, SourceId.GoogleAnalytics) },
                )
            val state = orchestrator.syncAll(SyncRequest(Period.Last7Days)).state
            assertEquals(listOf(SourceId.CloudflareZone, SourceId.Bing), calls)
            assertTrue(state is SyncState.PartialSuccess)
            assertEquals(SyncState.Idle, DefaultSyncOrchestrator(emptyList()).observeState().first())
        }

    @Test fun syncEverythingFetchesAllSitesForFullHistoryAndInspectsEveryDomain() =
        runBlocking {
            val requests = mutableListOf<SyncRequest>()
            val inspected = mutableListOf<String>()
            val service =
                object : SourceSyncService {
                    override val source = SourceId.SearchConsole

                    override suspend fun testConnection(connectionId: ConnectionId?) = ConnectionTestResult.Success

                    override suspend fun sync(request: SyncRequest): SyncResult {
                        requests += request
                        return SyncResult.Success(1)
                    }
                }
            val recorded = mutableListOf<SyncResult>()
            val orchestrator =
                DefaultSyncOrchestrator(
                    listOf(service),
                    onSourceResult = { _, _, result -> recorded += result },
                    inspectIndexing = { domain ->
                        inspected += domain
                        if (domain == "b.example") SyncResult.Failure("Quota", true) else SyncResult.Success(1)
                    },
                    indexingDomains = { listOf("a.example", "b.example") },
                )
            val state = orchestrator.syncEverything().state
            assertEquals(listOf(SyncRequest(Period.All)), requests)
            assertEquals(listOf("a.example", "b.example"), inspected)
            assertEquals(listOf(SyncResult.Success(1), SyncResult.Failure("Quota", true)), recorded)
            assertEquals(SyncState.PartialSuccess(emptySet(), setOf(SourceId.SearchConsole)), state)
            assertEquals(state, orchestrator.observeState().first())
        }

    @Test fun importAndSyncShareOneWriteGate() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            val orchestrator = DefaultSyncOrchestrator(emptyList())
            var secondEntered = false
            val first =
                launch {
                    orchestrator.exclusive {
                        entered.complete(Unit)
                        gate.await()
                    }
                }
            entered.await()
            val second = launch { orchestrator.exclusive { secondEntered = true } }
            yield()
            assertFalse(secondEntered)
            gate.complete(Unit)
            joinAll(first, second)
            assertTrue(secondEntered)
        }

    @Test fun cancellationDoesNotBecomeSuccessOrFailure() =
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val service =
                object : SourceSyncService {
                    override val source = SourceId.Uptime

                    override suspend fun testConnection(connectionId: ConnectionId?) = ConnectionTestResult.Success

                    override suspend fun sync(request: SyncRequest): SyncResult {
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                }
            val orchestrator = DefaultSyncOrchestrator(listOf(service))
            val job = launch { orchestrator.syncAll(SyncRequest(Period.Last7Days)) }
            entered.await()
            job.cancelAndJoin()
            assertEquals(SyncState.Idle, orchestrator.observeState().first())
        }
}
