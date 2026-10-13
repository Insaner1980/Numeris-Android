package com.emmah.numeris

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.WebAnalyticsDailyEntity
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.core.network.WebAnalyticsApiClient
import com.emmah.numeris.data.WebAnalyticsSyncService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WebAnalyticsSyncHostTest {
    private lateinit var database: NumerisDatabase
    private lateinit var client: OkHttpClient
    private lateinit var service: WebAnalyticsSyncService
    private val secrets = MemorySecrets()
    private val requests = mutableListOf<String>()
    private var fail = false
    private val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)
    private val connection =
        ConnectionEntity(
            "web:test",
            "web_analytics",
            "configured",
            """{"accountId":"account-one","siteMappings":"one.com=tag-one\ntwo.com=tag-two"}""",
            lastSync = "2026-10-01T00:00:00Z",
        )

    @Before fun setUp() {
        database = testDatabase()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val body = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
                    requests += body
                    val payload =
                        if (body.contains("NumerisWebAnalyticsLimits")) {
                            """{"data":{"viewer":{"accounts":[{"settings":{"rumPageloadEventsAdaptiveGroups":{"maxDuration":31536000,"notOlderThan":31536000}}}]}}}"""
                        } else {
                            """{"data":{"viewer":{"accounts":[{
                  "daily":[{"dimensions":{"date":"2026-10-10"},"sum":{"visits":12},"count":20}],
                  "referrers":[{"dimensions":{"date":"2026-10-10","refererHost":"search.example"},"sum":{"visits":5}}],
                  "pages":[{"dimensions":{"date":"2026-10-10","requestPath":"/article"},"count":8}],
                  "countries":[{"dimensions":{"date":"2026-10-10","countryName":"FI"},"sum":{"visits":7}}]
                }]}}}"""
                        }
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (fail) 403 else 200)
                        .message("Fixture")
                        .body(payload.toResponseBody())
                        .build()
                }.build()
        service =
            WebAnalyticsSyncService(database, secrets, WebAnalyticsApiClient(NumerisHttpClient(client), clock), clock)
    }

    @After fun tearDown() {
        database.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun selectedSiteRefreshWritesAllBreakdownsWithoutRefreshingOtherSitesOrConnectionTimestamp() =
        runBlocking {
            configure()
            database.webAnalyticsDao().upsertWebAnalytics(
                listOf(WebAnalyticsDailyEntity("two.com", "2026-10-10", 99, 100, "old")),
            )
            assertEquals(SyncResult.Success(4), service.sync(SyncRequest(Period.Last7Days, "one.com")))
            assertEquals(2, requests.size)
            assertTrue(requests.last().contains("tag-one"))
            assertTrue(requests.none { it.contains("tag-two") })
            val dao = database.reportSnapshotDao()
            assertEquals(12L, dao.readWebAnalytics("one.com", "2026-10-01", "2026-10-11").single().visits)
            assertEquals(99L, dao.readWebAnalytics("two.com", "2026-10-01", "2026-10-11").single().visits)
            assertEquals(1, dao.readWebPages("2026-10-01", "2026-10-11").size)
            assertEquals(1, dao.readWebReferrers("2026-10-01", "2026-10-11").size)
            assertEquals(1, dao.readWebCountries("2026-10-01", "2026-10-11").size)
            assertEquals(connection.lastSync, database.connectionDao().find(connection.id)!!.lastSync)
        }

    @Test fun allSitesRefreshAndConnectionTestSucceedThenNetworkFailurePreservesCachedData() =
        runBlocking {
            configure()
            assertEquals(ConnectionTestResult.Success, service.testConnection(ConnectionId(connection.id)))
            assertEquals(SyncResult.Success(8), service.sync(SyncRequest(Period.Last7Days)))
            assertEquals(clock.instant().toString(), database.connectionDao().find(connection.id)!!.lastSync)
            fail = true
            assertTrue(service.sync(SyncRequest(Period.Last7Days)) is SyncResult.Failure)
            assertTrue(service.testConnection(null) is ConnectionTestResult.Failure)
            assertEquals("error", database.connectionDao().find(connection.id)!!.status)
            assertEquals("Web Analytics sync failed.", database.connectionDao().find(connection.id)!!.lastError)
            assertEquals(
                12L,
                database
                    .reportSnapshotDao()
                    .readWebAnalytics("one.com", "2026-10-01", "2026-10-11")
                    .single()
                    .visits,
            )
        }

    @Test fun missingConfigurationAndTokenFailWithoutNetworkAndUnselectedSiteIsSkipped() =
        runBlocking {
            assertTrue(service.sync(SyncRequest(Period.Last7Days)) is SyncResult.Failure)
            assertEquals(
                ConnectionTestResult.Failure("No Web Analytics connection is configured."),
                service.testConnection(null),
            )
            database.connectionDao().upsert(listOf(connection.copy(config = "{}")))
            assertEquals(
                ConnectionTestResult.Failure("Web Analytics account ID is missing."),
                service.testConnection(null),
            )
            database.connectionDao().upsert(listOf(connection.copy(config = """{"accountId":"account-one"}""")))
            assertEquals(ConnectionTestResult.Failure("Add a Web Analytics site first."), service.testConnection(null))
            database.connectionDao().upsert(listOf(connection))
            assertTrue(service.testConnection(null) is ConnectionTestResult.Failure)
            assertTrue(service.sync(SyncRequest(Period.Last7Days)) is SyncResult.Failure)
            assertEquals(SyncResult.Success(0), service.sync(SyncRequest(Period.Last7Days, "unconfigured.com")))
            assertTrue(requests.isEmpty())
        }

    private suspend fun configure() {
        database.connectionDao().upsert(listOf(connection))
        secrets.put(SecretKey("web_analytics:${connection.id}:api_token"), "synthetic-web-token".toCharArray())
    }
}
