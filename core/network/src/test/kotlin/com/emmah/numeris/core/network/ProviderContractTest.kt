package com.emmah.numeris.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderContractTest {
    private lateinit var server: MockWebServer
    private lateinit var http: NumerisHttpClient

    @Before fun start() {
        server = MockWebServer().apply { start() }
        http =
            NumerisHttpClient(
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val url =
                            request.url
                                .newBuilder()
                                .scheme("http")
                                .host("localhost")
                                .port(server.port)
                                .build()
                        chain.proceed(request.newBuilder().url(url).build())
                    }.build(),
            )
    }

    @After fun stop() = server.shutdown()

    @Test fun searchAnalyticsPagesUntilAShortPage() =
        runBlocking {
            fun page(count: Int) =
                (1..count).joinToString(",", """{"rows":[""", "]}") {
                    """{"keys":["2026-10-01","q$it"],"clicks":1,"impressions":2,"ctr":0.5,"position":3}"""
                }
            server.enqueue(MockResponse().setBody(page(SEARCH_PAGE_SIZE)))
            server.enqueue(MockResponse().setBody(page(1)))
            val rows =
                SearchConsoleApiClient(http)
                    .query("fixture", "sc-domain:example.com", "2024-10-01", "2026-10-10", listOf("date"))
            assertEquals(SEARCH_PAGE_SIZE + 1, rows.size)
            val starts =
                List(2) {
                    Json
                        .parseToJsonElement(server.takeRequest().body.readUtf8())
                        .jsonObject
                        .getValue("startRow")
                        .jsonPrimitive.content
                }
            assertEquals(listOf("0", SEARCH_PAGE_SIZE.toString()), starts)
        }

    @Test fun cruxNotFoundMeansNoFieldDataInsteadOfFailure() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("""{"error":{"code":404,"message":"chrome ux report data not found"}}"""),
            )
            val points =
                CruxApiClient(http).queryHistory("fixture".toCharArray(), "origin", "https://small.example", null)
            assertTrue(points.isEmpty())
        }

    @Test fun searchPaginationPreservesZeroClickTailBeyondFormerRowCap() =
        runBlocking {
            val fullPage =
                (1..SEARCH_PAGE_SIZE).joinToString(",", """{"rows":[""", "]}") {
                    """{"keys":["q$it"],"clicks":1,"impressions":2}"""
                }
            val offsets = mutableListOf<Int>()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertEquals("web", body.getValue("type").jsonPrimitive.content)
                        val offset =
                            body
                                .getValue("startRow")
                                .jsonPrimitive.content
                                .toInt()
                        offsets += offset
                        return MockResponse().setBody(
                            if (offset < 500_000) {
                                fullPage
                            } else {
                                """{"rows":[{"keys":["zero-click tail"],"clicks":0,"impressions":17}]}"""
                            },
                        )
                    }
                }
            val rows =
                SearchConsoleApiClient(http).query(
                    "fixture",
                    "sc-domain:example.com",
                    "2025-10-01",
                    "2026-10-10",
                    listOf("query"),
                )
            assertEquals(500_001, rows.size)
            assertEquals((0..500_000 step SEARCH_PAGE_SIZE).toList(), offsets)
            assertEquals(0L, rows.last().clicks)
            assertEquals(17L, rows.last().impressions)
            assertEquals(listOf("zero-click tail"), rows.last().keys)
        }

    @Test fun searchQueriesPreserveDatesDimensionsAndPropertyPath() =
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """{"rows":[{"keys":["2026-10-10","Long query"],
                "clicks":7,"impressions":20,"ctr":0.35,"position":2.5}]}""",
                ),
            )
            val rows =
                SearchConsoleApiClient(http).query(
                    "fixture",
                    "https://example.com/path/",
                    "2026-10-01",
                    "2026-10-10",
                    listOf("date", "query"),
                )
            assertEquals(listOf("2026-10-10", "Long query"), rows.single().keys)
            assertEquals(7L, rows.single().clicks)
            val request = server.takeRequest()
            assertEquals("https://example.com/path/", request.requestUrl!!.pathSegments[3])
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("2026-10-01", body.getValue("startDate").jsonPrimitive.content)
            assertEquals("2026-10-10", body.getValue("endDate").jsonPrimitive.content)
            assertEquals(
                listOf("date", "query"),
                body.getValue("dimensions").jsonArray.map { it.jsonPrimitive.content },
            )
        }

    @Test fun webAnalyticsUsesPageViewCountsAndRetainsBreakdownDates() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"data":{"viewer":{"accounts":[{"settings":{}}]}}}"""))
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"errors":null,"data":{"viewer":{"accounts":[{
                    "daily":[{"dimensions":{"date":"2026-10-10"},"sum":{"visits":3},"count":9}],
                    "pages":[{"dimensions":{"date":"2026-10-10","requestPath":"/Path/"},"count":7}],
                    "referrers":[{"dimensions":{"date":"2026-10-10","refererHost":"example.org"},"sum":{"visits":2}}],
                    "countries":[{"dimensions":{"date":"2026-10-10","countryName":"FI"},"sum":{"visits":3}}]
                    }]}}}
                    """.trimIndent(),
                ),
            )
            val report =
                WebAnalyticsApiClient(
                    http,
                ).fetch("fixture".toCharArray(), "account", "tag", "2026-10-01", "2026-10-10")
            assertEquals(3L, report.daily.single().visits)
            assertEquals(9L, report.daily.single().pageViews)
            assertEquals(7L, report.pages.single().value)
            assertEquals("2026-10-10", report.pages.single().date)
            assertEquals("/Path/", report.pages.single().name)
            assertTrue(
                server
                    .takeRequest()
                    .body
                    .readUtf8()
                    .contains("NumerisWebAnalyticsLimits"),
            )
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertTrue(
                body
                    .getValue("query")
                    .jsonPrimitive.content
                    .contains("dimensions { date requestPath } count"),
            )
            assertEquals(
                "tag",
                body
                    .getValue("variables")
                    .jsonObject
                    .getValue("siteTag")
                    .jsonPrimitive.content,
            )
        }

    @Test fun cloudflareSuccessWithNullErrorsRetainsDailyBreakdownsAndWindow() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"data":{"viewer":{"zones":[{"settings":{}}]}}}"""))
            server.enqueue(
                MockResponse().setBody(
                    """{"errors":null,"data":{"viewer":{"zones":[{
                    "httpRequests1dGroups":[{"dimensions":{"date":"2026-10-10"},
                    "sum":{"pageViews":3,"requests":9},"uniq":{"uniques":2}}],
                    "topCountries":[{"dimensions":{"date":"2026-10-09","clientCountryName":"FI"},"count":7,"sum":{"visits":4}}],
                    "topPages":[{"dimensions":{"date":"2026-10-08","clientRequestPath":"/Case/"},"count":8,"sum":{"visits":5}}]
                    }]}}}""",
                ),
            )
            val report =
                CloudflareApiClient(http).fetchTraffic("fixture".toCharArray(), "zone", "2026-08-01", "2026-10-11")
            assertEquals(3L, report.daily.single().pageViews)
            assertEquals(CloudflareBreakdownWire("2026-10-09", "FI", 4), report.countries.single())
            assertEquals(CloudflareBreakdownWire("2026-10-08", "/Case/", 5), report.pages.single())
            assertTrue(
                server
                    .takeRequest()
                    .body
                    .readUtf8()
                    .contains("NumerisZoneLimits"),
            )
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val adaptiveSince =
                body
                    .getValue("variables")
                    .jsonObject
                    .getValue("adaptiveSince")
                    .jsonPrimitive.content
            assertEquals("2026-09-12T00:00:00Z", adaptiveSince)
            val query = body.getValue("query").jsonPrimitive.content
            assertTrue(query.contains("dimensions { date clientRequestPath }"))
            assertFalse(query.contains("limit: 10,"))
        }

    @Test fun discoveryRetainsProviderTargetIdentifiers() =
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """{"success":true,"result":[{"host":"example.com","site_tag":"site-tag"}]}""",
                ),
            )
            val targets = SourceDiscoveryClient(http).cloudflare("fixture".toCharArray(), "account-id")
            assertEquals(DiscoveredTargetWire("example.com", "site-tag"), targets.single())
            assertTrue(server.takeRequest().path!!.startsWith("/client/v4/accounts/account-id/rum/site_info/list?"))
        }

    @Test fun providerFailuresNeverExposeCredentialsOrResponseBodies() =
        runBlocking {
            for (status in listOf(401, 403, 429, 500)) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("fixture-secret-in-error"))
                val failure =
                    runCatching {
                        BingApiClient(
                            http,
                        ).call("fixture-key".toCharArray(), "GetUserSites")
                    }.exceptionOrNull()
                assertTrue("HTTP $status must fail", failure != null)
                assertFalse(failure!!.message.orEmpty().contains("fixture-key"))
                assertFalse(failure.message.orEmpty().contains("fixture-secret-in-error"))
            }
        }

    private companion object {
        const val SEARCH_PAGE_SIZE = 5_000
    }
}
