package com.emmah.numeris

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.BingApiClient
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.data.BingSyncService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BingScopedSyncTest {
    @Test fun oneSiteRefreshNeverListsOrFetchesOtherSites() =
        runBlocking {
            val database = testDatabase()
            val calls = mutableListOf<String>()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val method = request.url.pathSegments.last()
                        assertEquals("https://one.example/", request.url.queryParameter("siteUrl"))
                        calls += method
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("{\"d\":[]}".toResponseBody())
                            .build()
                    }.build()
            val secrets = FixedSecrets("synthetic-bing-key")
            try {
                database.connectionDao().upsert(
                    listOf(
                        ConnectionEntity(
                            "bing:fixture",
                            "bing_webmaster",
                            "configured",
                            "{\"sites\":\"https://one.example/,https://two.example/\"}",
                        ),
                    ),
                )
                val service = BingSyncService(database, secrets, BingApiClient(NumerisHttpClient(client)))
                assertTrue(service.sync(SyncRequest(Period.Last7Days, "one.example")) is SyncResult.Success)
                assertEquals(
                    listOf("GetCrawlStats", "GetCrawlIssues", "GetRankAndTrafficStats", "GetQueryStats", "GetPageStats"),
                    calls,
                )
            } finally {
                database.close()
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
}
