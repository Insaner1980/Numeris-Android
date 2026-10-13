package com.emmah.numeris

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.SitemapUrlEntity
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.core.network.SearchConsoleApiClient
import com.emmah.numeris.data.GoogleAuthorizationState
import com.emmah.numeris.data.SearchConsoleSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class GoogleSyncHostTest {
    private lateinit var database: NumerisDatabase
    private lateinit var client: OkHttpClient
    private lateinit var search: SearchConsoleSyncService
    private var auth: GoogleAuthorizationState = GoogleAuthorizationState.Authorized("synthetic-token")
    private val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)
    private val requests = mutableListOf<String>()
    private var responder: (Request) -> Pair<Int, String> = { 200 to response(it) }
    private val request = SyncRequest(Period.Last7Days, "example.com")

    @Before fun setUp() {
        database = testDatabase()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request.url.toString()
                    assertEquals("Bearer synthetic-token", request.header("Authorization"))
                    val (code, body) = responder(request)
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("Fixture")
                        .body(body.toResponseBody())
                        .build()
                }.build()
        val http = NumerisHttpClient(client)
        search = SearchConsoleSyncService(database, { auth }, SearchConsoleApiClient(http), clock)
    }

    @After fun tearDown() {
        database.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun searchRefreshPersistsEveryBreakdownAndFailureKeepsCachedRows() =
        runBlocking {
            configure()
            assertEquals(ConnectionTestResult.Success, search.testConnection(null))
            assertEquals(SyncResult.Success(5), search.sync(request))
            val rows = database.reportSnapshotDao().readSearchConsole("example.com", "2026-10-01", "2026-10-11")
            assertEquals(setOf("daily", "query_daily", "page_daily"), rows.map { it.kind }.toSet())
            assertTrue(rows.all { it.clicks == 12L && it.impressions == 60L })
            assertEquals(
                "MOBILE",
                database
                    .reportSnapshotDao()
                    .readSearchDevices("example.com", "2026-10-01", "2026-10-11")
                    .single()
                    .device,
            )
            responder = { 403 to "{}" }
            assertTrue(search.sync(request) is SyncResult.Failure)
            assertTrue(search.testConnection(null) is ConnectionTestResult.Failure)
            assertEquals(
                rows,
                database.reportSnapshotDao().readSearchConsole("example.com", "2026-10-01", "2026-10-11"),
            )
        }

    @Test fun missingMalformedAndOutOfScopeConfigurationNeverMakesDataRequests() =
        runBlocking {
            assertTrue(search.sync(request) is SyncResult.Failure)
            assertTrue(search.testConnection(null) is ConnectionTestResult.Failure)
            database.connectionDao().upsert(
                listOf(
                    ConnectionEntity("bad-search", "search_console", "configured", "not-json"),
                ),
            )
            assertTrue(search.sync(request) is SyncResult.Failure)
            configure()
            assertTrue(search.sync(request.copy(domain = "other.example")) is SyncResult.Failure)
            assertTrue(requests.isEmpty())
            database.connectionDao().upsert(
                listOf(
                    ConnectionEntity("search", "search_console", "configured", """{"sites":"sc-domain:example.com"}"""),
                ),
            )
            assertEquals(SyncResult.Success(5), search.sync(request.copy(domain = null)))
            responder = { 200 to "{}" }
            assertTrue(search.testConnection(null) is ConnectionTestResult.Failure)
            assertEquals(SyncResult.Success(0), search.sync(request))
        }

    @Test fun authorizationFailureAndResolutionPreventNetworkAccess() =
        runBlocking {
            configure()
            auth = GoogleAuthorizationState.Failure("Synthetic authorization failure.")
            assertEquals(SyncResult.Failure("Synthetic authorization failure.", false), search.sync(request))
            assertTrue(search.testConnection(null) is ConnectionTestResult.Failure)
            auth =
                GoogleAuthorizationState.UserActionRequired(
                    PendingIntent.getActivity(
                        ApplicationProvider.getApplicationContext(),
                        0,
                        Intent("synthetic.AUTH"),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            assertTrue(search.sync(request) is SyncResult.Failure)
            assertTrue(search.testConnection(null) is ConnectionTestResult.Failure)
            assertTrue(requests.isEmpty())
        }

    @Test fun inspectionSelectsOnlyActiveStaleUrlsAndStoresReturnedFields() =
        runBlocking {
            configure()
            database.searchDao().upsertSitemapUrls(
                listOf(
                    sitemap("new"),
                    sitemap("stale").copy(lastInspectedAt = "invalid"),
                    sitemap("fresh").copy(lastInspectedAt = clock.instant().toString()),
                    sitemap("removed").copy(removedAt = clock.instant().toString()),
                ),
            )
            assertEquals(SyncResult.Success(0), search.inspectStaleSitemapUrls("other.example"))
            assertEquals(SyncResult.Success(2), search.inspectStaleSitemapUrls("example.com"))
            val rows = database.searchDao().sitemapUrls("example.com").associateBy { it.url.substringAfterLast('/') }
            assertEquals("PASS", rows.getValue("new").verdict)
            assertEquals("Indexed", rows.getValue("stale").coverageState)
            assertEquals("MOBILE", rows.getValue("new").crawledAs)
            assertEquals(null, rows.getValue("removed").lastInspectedAt)
            assertEquals(SyncResult.Success(0), search.inspectStaleSitemapUrls("example.com"))
        }

    @Test fun inspectionRetainsSuccessBeforeQuotaFailureAndMarksMissingStatus() =
        runBlocking {
            configure()
            database.searchDao().upsertSitemapUrls(listOf(sitemap("a"), sitemap("b")))
            var calls = 0
            responder = { if (++calls == 1) 200 to "{}" else 429 to "{}" }
            assertTrue(search.inspectStaleSitemapUrls("example.com") is SyncResult.Failure)
            val rows = database.searchDao().sitemapUrls("example.com")
            assertEquals(1, rows.count { it.verdict == "ERROR" && it.lastInspectedAt != null })
            assertEquals(1, rows.count { it.lastInspectedAt == null })
        }

    @Test fun transientInspectionFailureRetriesAndLaterUrlsStillSucceed() =
        runBlocking {
            configure()
            database.searchDao().upsertSitemapUrls(listOf(sitemap("a"), sitemap("b")))
            var calls = 0
            responder = { if (++calls <= 2) 503 to "{}" else 200 to response(it) }
            assertEquals(SyncResult.Success(1), search.inspectStaleSitemapUrls("example.com"))
            assertEquals(3, calls)
            responder = { 503 to "{}" }
            assertTrue(search.inspectStaleSitemapUrls("example.com") is SyncResult.Failure)
        }

    @Test fun cancellationPropagatesWithoutReplacingStoredSearchData() =
        runBlocking {
            configure()
            responder = { throw CancellationException("fixture cancellation") }
            assertThrows(CancellationException::class.java) { runBlocking { search.sync(request) } }
            assertThrows(CancellationException::class.java) { runBlocking { search.testConnection(null) } }
            database.searchDao().upsertSitemapUrls(listOf(sitemap("a")))
            assertThrows(CancellationException::class.java) {
                runBlocking { search.inspectStaleSitemapUrls("example.com") }
            }
            assertFalse(
                database
                    .searchDao()
                    .sitemapUrls("example.com")
                    .single()
                    .lastInspectedAt != null,
            )
        }

    private suspend fun configure() {
        database.connectionDao().upsert(
            listOf(
                ConnectionEntity("search", "search_console", "configured", """{"sites":["sc-domain:example.com"]}"""),
            ),
        )
    }

    private fun sitemap(path: String) =
        SitemapUrlEntity(
            domain = "example.com",
            url = "https://example.com/$path",
            discoveredAt = "2026-10-01",
            lastSeenAt = "2026-10-01",
        )

    private fun response(request: Request): String =
        when {
            request.method == "GET" -> """{"siteEntry":[{"siteUrl":"sc-domain:example.com"},{}]}"""
            request.url.host == "searchconsole.googleapis.com" ->
                """{"inspectionResult":{"indexStatusResult":{
                    "verdict":"PASS","coverageState":"Indexed","indexingState":"INDEXING_ALLOWED",
                    "robotsTxtState":"ALLOWED","pageFetchState":"SUCCESSFUL","crawledAs":"MOBILE",
                    "lastCrawlTime":"2026-10-10T00:00:00Z"}}}"""
            else -> searchResponse(request)
        }

    private fun searchResponse(request: Request): String {
        val body = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
        val keys =
            body.getValue("dimensions").jsonArray.map {
                when (it.jsonPrimitive.content) {
                    "date" -> "2026-10-10"
                    "page" -> "https://example.com/article"
                    "device" -> "MOBILE"
                    else -> "fixture query"
                }
            }
        val keyJson = keys.joinToString { "\"$it\"" }
        return """{"rows":[{"keys":[$keyJson],"clicks":12,"impressions":60,"ctr":0.2,"position":3.5}]}"""
    }
}
