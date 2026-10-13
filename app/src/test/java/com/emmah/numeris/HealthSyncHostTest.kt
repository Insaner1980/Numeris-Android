package com.emmah.numeris

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.SitemapUrlEntity
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.core.network.SitemapClient
import com.emmah.numeris.core.network.UptimeClient
import com.emmah.numeris.data.SitemapSyncService
import com.emmah.numeris.data.UptimeSyncService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class HealthSyncHostTest {
    private lateinit var database: NumerisDatabase
    private lateinit var client: OkHttpClient
    private lateinit var http: NumerisHttpClient
    private val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)
    private var responder: (Request) -> Pair<Int, String> = { 200 to "ok" }

    @Before fun setUp() {
        database = testDatabase()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val (code, body) = responder(chain.request())
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("Fixture")
                        .body(body.toResponseBody())
                        .build()
                }.build()
        http = NumerisHttpClient(client)
    }

    @After fun tearDown() {
        database.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun uptimePersistsReachableAndUnreachableResultsForSelectedAndAllSites() =
        runBlocking {
            database.connectionDao().upsert(
                listOf(
                    ConnectionEntity(
                        "search",
                        "search_console",
                        "configured",
                        """{"sites":["sc-domain:example.com"]}""",
                    ),
                ),
            )
            val service = UptimeSyncService(database, UptimeClient(http), clock)
            assertEquals(ConnectionTestResult.Success, service.testConnection(null))
            assertEquals(SyncResult.Success(1), service.sync(SyncRequest(Period.Last7Days, "example.com")))
            val up =
                database
                    .healthDao()
                    .observeUptime("example.com", 10)
                    .first()
                    .single()
            assertEquals("up", up.status)
            assertEquals(200, up.statusCode)
            assertNotNull(up.responseMs)
            responder = { 503 to "{}" }
            assertEquals(SyncResult.Success(1), service.sync(SyncRequest(Period.Last7Days)))
            val down =
                database
                    .healthDao()
                    .observeUptime("example.com", 10)
                    .first()
                    .single()
            assertEquals("down", down.status)
            assertTrue(down.errorMessage!!.contains("503"))
        }

    @Test fun sitemapRefreshAddsReactivatesAndMarksMissingPagesWithoutLosingInspection() =
        runBlocking {
            val old = "2026-10-01T00:00:00Z"
            database.searchDao().upsertSitemapUrls(
                listOf(
                    SitemapUrlEntity("example.com", "https://example.com/retained", old, old, verdict = "PASS"),
                    SitemapUrlEntity("example.com", "https://example.com/reactivated", old, old, removedAt = old),
                    SitemapUrlEntity("example.com", "https://example.com/removed", old, old),
                    SitemapUrlEntity(
                        "example.com",
                        "https://example.com/previously-removed",
                        old,
                        old,
                        removedAt = old,
                    ),
                ),
            )
            responder = { request ->
                val locations =
                    if (request.url.encodedPath == "/sitemap-index.xml") {
                        listOf("https://example.com/pages.xml")
                    } else {
                        listOf(
                            "https://example.com/retained",
                            "https://example.com/reactivated",
                            "https://example.com/new",
                        )
                    }
                200 to "<urlset>${locations.joinToString("") { "<url><loc>$it</loc></url>" }}</urlset>"
            }
            val service = SitemapSyncService(database, SitemapClient(http), clock)
            assertEquals(ConnectionTestResult.Success, service.testConnection(null))
            assertEquals(SyncResult.Success(3), service.sync(SyncRequest(Period.Last7Days, "example.com")))
            val rows = database.searchDao().sitemapUrls("example.com").associateBy { it.url.substringAfterLast('/') }
            assertEquals("PASS", rows.getValue("retained").verdict)
            assertEquals(null, rows.getValue("reactivated").removedAt)
            assertEquals(clock.instant().toString(), rows.getValue("removed").removedAt)
            assertEquals(old, rows.getValue("previously-removed").removedAt)
            assertEquals(clock.instant().toString(), rows.getValue("new").discoveredAt)
            responder = { 500 to "{}" }
            assertTrue(service.sync(SyncRequest(Period.Last7Days)) is SyncResult.Failure)
            assertEquals(rows.values.toSet(), database.searchDao().sitemapUrls("example.com").toSet())
        }
}
