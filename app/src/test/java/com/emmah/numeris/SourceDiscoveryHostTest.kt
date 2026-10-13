package com.emmah.numeris

import androidx.test.core.app.ApplicationProvider
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.network.BingApiClient
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.core.network.SourceDiscoveryClient
import com.emmah.numeris.data.DiscoveredTarget
import com.emmah.numeris.data.GoogleAuthorizationManager
import com.emmah.numeris.data.SourceDiscovery
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceDiscoveryHostTest {
    private lateinit var client: OkHttpClient
    private lateinit var discovery: SourceDiscovery
    private val secrets = MemorySecrets()
    private var payload = ""
    private val paths = mutableListOf<String>()

    @Before fun setUp() {
        client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    paths += chain.request().url.encodedPath
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("Fixture")
                        .body(payload.toResponseBody())
                        .build()
                }.build()
        val http = NumerisHttpClient(client)
        discovery =
            SourceDiscovery(
                secrets,
                GoogleAuthorizationManager(ApplicationProvider.getApplicationContext()),
                SourceDiscoveryClient(http),
                BingApiClient(http),
            )
    }

    @After fun tearDown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun zoneDiscoveryNormalizesDeduplicatesAndSortsTargetsAndClearsEnteredToken() =
        runBlocking {
            payload =
                """{"success":true,"result":[{"id":"z","name":"z.example"},{"id":"a","name":"www.a.example"},{"id":"a","name":"www.a.example"},{"name":"missing.example"},{"id":"invalid","name":""}]}"""
            val token = "synthetic-entered-token".toCharArray()
            assertEquals(
                listOf(DiscoveredTarget("www.a.example", "a"), DiscoveredTarget("z.example", "z")),
                discovery.discover(SourceId.CloudflareZone, null, emptyMap(), token),
            )
            assertTrue(token.all { it == '\u0000' })
            assertEquals(listOf("/client/v4/zones"), paths)
        }

    @Test fun webDiscoveryUsesConfiguredAccountAndStoredTokenWithoutDeletingStoredSecret() =
        runBlocking {
            payload = """{"success":true,"result":[{"site_tag":"tag","host":"one.example"}]}"""
            val key = SecretKey("web_analytics:web:test:api_token")
            secrets.put(key, "synthetic-stored-token".toCharArray())
            assertEquals(
                listOf(DiscoveredTarget("one.example", "tag")),
                discovery.discover(
                    SourceId.CloudflareWebAnalytics,
                    ConnectionId("web:test"),
                    mapOf(
                        "accountId" to "account-one",
                    ),
                    charArrayOf(),
                ),
            )
            assertEquals("/client/v4/accounts/account-one/rum/site_info/list", paths.single())
            assertEquals("synthetic-stored-token", secrets.get(key)!!.concatToString())
        }

    @Test fun bingDiscoveryAcceptsBothWireFieldNamesAndDropsRowsWithoutSite() =
        runBlocking {
            payload = """{"d":[{"Url":"https://one.example/"},{"SiteUrl":"https://two.example/"},{}]}"""
            secrets.put(SecretKey("bing:bing:api_key"), "synthetic-bing-key".toCharArray())
            val targets = discovery.discover(SourceId.Bing, null, emptyMap(), charArrayOf())
            assertEquals(listOf("one.example", "two.example"), targets.map { it.domain })
            assertTrue(paths.single().endsWith("GetUserSites"))
        }

    @Test fun rejectedDiscoveryClearsTokenAndMissingCredentialsNeverMakeARequest() =
        runBlocking {
            payload = """{"success":false,"result":[]}"""
            val token = "synthetic-entered-token".toCharArray()
            assertTrue(runCatching { discovery.discover(SourceId.CloudflareZone, null, emptyMap(), token) }.isFailure)
            assertTrue(token.all { it == '\u0000' })
            paths.clear()
            val missingToken =
                runCatching {
                    discovery.discover(SourceId.CloudflareZone, null, emptyMap(), charArrayOf())
                }
            assertTrue(missingToken.isFailure)
            assertTrue(paths.isEmpty())
        }
}
