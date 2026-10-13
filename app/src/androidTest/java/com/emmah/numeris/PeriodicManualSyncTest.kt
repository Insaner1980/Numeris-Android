package com.emmah.numeris

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.model.SyncState
import com.emmah.numeris.data.DefaultSyncOrchestrator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

open class PeriodicManualSyncTest {
    @Test fun actualPeriodicWorkerCoalescesMatchingManualAndSerializesDifferentPeriod() =
        runBlocking {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NumerisApplication
            app.initialization.await()
            val container = app.container
            val field = AppContainer::class.java.getDeclaredField("syncOrchestrator").apply { isAccessible = true }
            val original = field.get(container)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val active = AtomicInteger()
            val maximumActive = AtomicInteger()
            val service =
                object : SourceSyncService {
                    override val source = SourceId.CloudflareZone

                    override suspend fun testConnection(connectionId: ConnectionId?) = ConnectionTestResult.Success

                    override suspend fun sync(request: SyncRequest): SyncResult {
                        maximumActive.updateAndGet { maxOf(it, active.incrementAndGet()) }
                        calls.incrementAndGet()
                        entered.complete(Unit)
                        try {
                            release.await()
                            return SyncResult.Success(1)
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                }
            val sync = DefaultSyncOrchestrator(listOf(service))
            val manager = WorkManager.getInstance(app)
            val name = "numeris.parity.periodic.${UUID.randomUUID()}"
            val request = PeriodicWorkRequest.Builder(NumerisSyncWorker::class.java, Duration.ofMinutes(15)).build()
            try {
                field.set(container, sync)
                manager
                    .enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
                    .result
                    .get(20, TimeUnit.SECONDS)
                withTimeout(30000) { entered.await() }
                assertEquals(WorkInfo.State.RUNNING, manager.getWorkInfoById(request.id).get(20, TimeUnit.SECONDS)?.state)
                val matching = async(start = CoroutineStart.UNDISPATCHED) { sync.syncAll(SyncRequest(Period.All)) }
                val different = async(start = CoroutineStart.UNDISPATCHED) { sync.syncAll(SyncRequest(Period.Last7Days)) }
                assertTrue(matching.isActive)
                assertTrue(different.isActive)
                assertEquals(1, calls.get())
                release.complete(Unit)
                assertTrue(withTimeout(30000) { matching.await() }.state is SyncState.Success)
                assertTrue(withTimeout(30000) { different.await() }.state is SyncState.Success)
                withTimeout(30000) {
                    manager.getWorkInfoByIdFlow(request.id).first { it?.state == WorkInfo.State.ENQUEUED }
                }
                assertEquals(2, calls.get())
                assertEquals(1, maximumActive.get())
                assertEquals(0, active.get())
            } finally {
                release.complete(Unit)
                manager.cancelUniqueWork(name).result.get(20, TimeUnit.SECONDS)
                field.set(container, original)
            }
        }
}
