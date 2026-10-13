package com.emmah.numeris

import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.PerformanceUrlEntity
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.CruxApiClient
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.core.network.PageSpeedApiClient
import com.emmah.numeris.data.CruxSyncService
import com.emmah.numeris.data.PageSpeedSyncService
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

@RunWith(RobolectricTestRunner::class)
class PerformanceSyncHostTest {
    private lateinit var database: NumerisDatabase
    private lateinit var client: OkHttpClient
    private lateinit var crux: CruxSyncService
    private lateinit var pageSpeed: PageSpeedSyncService
    private val secrets = MemorySecrets()
    private val requests = mutableListOf<String>()
    private var fail = false

    @Before fun setUp() {
        database = testDatabase()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val isCrux = request.url.host.startsWith("chromeuxreport")
                    requests +=
                        if (isCrux) {
                            Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                        } else {
                            requireNotNull(request.url.queryParameter("url"))
                        }
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (fail) 403 else 200)
                        .message("Fixture")
                        .body((if (isCrux) CRUX_RESPONSE else PAGESPEED_RESPONSE).toResponseBody())
                        .build()
                }.build()
        val http = NumerisHttpClient(client)
        crux = CruxSyncService(database, secrets, CruxApiClient(http))
        pageSpeed = PageSpeedSyncService(database, secrets, PageSpeedApiClient(http))
    }

    @After fun tearDown() {
        database.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun selectedSiteSyncPersistsDeviceMetricsAndAudits() =
        runBlocking {
            configureKeys()
            database.performanceDao().upsertUrls(
                listOf(
                    PerformanceUrlEntity(
                        url = "https://one.example/",
                        origin = "https://one.example",
                        createdAt = "fixture",
                    ),
                    PerformanceUrlEntity(
                        url = "https://other.example/",
                        origin = "https://other.example",
                        createdAt = "fixture",
                    ),
                ),
            )
            val selected = SyncRequest(Period.Last7Days, "one.example")
            assertEquals(SyncResult.Success(8), crux.sync(selected))
            assertEquals(SyncResult.Success(4), pageSpeed.sync(selected))
            assertEquals(10, requests.size)
            assertTrue(requests.none { it.contains("other.example") })
            val dao = database.reportSnapshotDao()
            val points = dao.readCrux("one.example")
            assertEquals(8, points.size)
            assertTrue(points.all { it.p75 == 1200.0 && it.goodDensity == 0.8 })
            assertEquals(setOf("ALL", "PHONE", "DESKTOP", "TABLET"), points.map { it.formFactor }.toSet())
            assertEquals(2, dao.readPageSpeedRuns("one.example").size)
            assertEquals(2, dao.readPageSpeedAudits("one.example").size)
            assertEquals(ConnectionTestResult.Success, crux.testConnection(null))
            assertEquals(ConnectionTestResult.Success, pageSpeed.testConnection(null))

            fail = true
            assertTrue(crux.sync(selected) is SyncResult.Failure)
            assertTrue(pageSpeed.sync(selected) is SyncResult.Failure)
            assertTrue(crux.testConnection(null) is ConnectionTestResult.Failure)
            assertTrue(pageSpeed.testConnection(null) is ConnectionTestResult.Failure)
            assertEquals(points, dao.readCrux("one.example"))
            assertEquals(2, dao.readPageSpeedRuns("one.example").size)
            assertEquals(2, dao.readPageSpeedAudits("one.example").size)
        }

    @Test fun missingKeysOrUrlsNeverMakeNetworkRequests() =
        runBlocking {
            assertTrue(crux.testConnection(null) is ConnectionTestResult.Failure)
            assertTrue(pageSpeed.testConnection(null) is ConnectionTestResult.Failure)
            assertTrue(crux.sync(SyncRequest(Period.Last7Days)) is SyncResult.Failure)
            assertTrue(pageSpeed.sync(SyncRequest(Period.Last7Days)) is SyncResult.Failure)
            configureKeys()
            assertEquals(ConnectionTestResult.Failure("Add a performance URL first."), crux.testConnection(null))
            assertEquals(ConnectionTestResult.Failure("Add a performance URL first."), pageSpeed.testConnection(null))
            assertEquals(SyncResult.Success(0), crux.sync(SyncRequest(Period.Last7Days)))
            assertEquals(SyncResult.Success(0), pageSpeed.sync(SyncRequest(Period.Last7Days)))
            assertTrue(requests.isEmpty())
        }

    private suspend fun configureKeys() {
        secrets.put(SecretKey("crux:crux:api_key"), "synthetic-crux-key".toCharArray())
        secrets.put(SecretKey("pagespeed:pagespeed:api_key"), "synthetic-pagespeed-key".toCharArray())
    }

    private companion object {
        const val CRUX_RESPONSE = """{"record":{
          "collectionPeriods":[{"firstDate":{"year":2026,"month":9,"day":14},
            "lastDate":{"year":2026,"month":10,"day":11}}],
          "metrics":{"largest_contentful_paint":{"percentilesTimeseries":{"p75s":[1200]},
            "histogramTimeseries":[{"densities":[0.8]},{"densities":[0.15]},{"densities":[0.05]}]}}
        }}"""
        const val PAGESPEED_RESPONSE = """{"analysisUTCTimestamp":"2026-10-11T12:00:00Z","lighthouseResult":{
          "finalUrl":"https://one.example/","lighthouseVersion":"13","runWarnings":[],
          "categories":{"performance":{"score":0.9},"accessibility":{"score":1},
            "best-practices":{"score":1},"seo":{"score":0.95}},
          "audits":{"largest-contentful-paint":{"title":"Largest Contentful Paint","score":1,
            "numericValue":1200,"numericUnit":"millisecond","displayValue":"1.2 s",
            "scoreDisplayMode":"numeric","details":{"type":"table","items":[]}}}
        }}"""
    }
}
