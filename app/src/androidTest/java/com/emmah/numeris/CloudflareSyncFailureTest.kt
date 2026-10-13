package com.emmah.numeris

import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.CloudflareApiClient
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.data.CloudflareSyncService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CloudflareSyncFailureTest {
    @Test fun networkFailurePreservesCachedDataAndRedactsFeedback() = checkFailure(false)

    @Test fun malformedResponsePreservesCachedDataAndRedactsFeedback() = checkFailure(true)

    private fun checkFailure(malformedResponse: Boolean) =
        runBlocking {
            val database = testDatabase()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        if (!malformedResponse) throw IOException("synthetic-private-response")
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("synthetic-private-response".toResponseBody())
                            .build()
                    }.build()
            val secrets = FixedSecrets("synthetic-private-response")
            try {
                val cached = CloudflareTrafficEntity("one.example", "2026-10-11", uniqueVisitors = 17, fetchedAt = "old")
                database.cloudflareDao().upsertTraffic(listOf(cached))
                database.connectionDao().upsert(
                    listOf(
                        ConnectionEntity(
                            "cloudflare:one.example",
                            "cloudflare",
                            "connected",
                            "{\"domain\":\"one.example\",\"zoneId\":\"fixture\"}",
                            lastSync = "old",
                        ),
                    ),
                )
                val service = CloudflareSyncService(database, secrets, CloudflareApiClient(NumerisHttpClient(client)))
                val result = service.sync(SyncRequest(Period.Last7Days, "one.example"))
                assertTrue(result is SyncResult.Failure)
                val connection = requireNotNull(database.connectionDao().find("cloudflare:one.example"))
                assertEquals("error", connection.status)
                assertEquals("old", connection.lastSync)
                assertEquals((result as SyncResult.Failure).safeMessage, connection.lastError)
                assertFalse(result.safeMessage.contains("synthetic-private-response"))
                assertEquals(
                    listOf(cached),
                    database.cloudflareDao().observeTraffic("one.example", "2026-10-11", "2026-10-11").first(),
                )
            } finally {
                database.close()
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
}
