package com.emmah.numeris.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.URI

class SitemapClientTest {
    private var responder: (Request) -> Pair<Int, String> = { 200 to "<urlset/>" }
    private var location: String? = null
    private val paths = mutableListOf<String>()
    private val http =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                paths += chain.request().url.encodedPath
                val (code, body) = responder(chain.request())
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Fixture")
                    .body(body.toResponseBody())
                    .apply {
                        location?.let { header("Location", it) }
                    }.build()
            }.build()
    private val client = SitemapClient(NumerisHttpClient(http))

    @After fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    @Test fun `follows same origin redirects and returns sorted distinct pages`() =
        runBlocking {
            responder = { request ->
                when (request.url.encodedPath) {
                    "/sitemap-index.xml" -> {
                        location = "/index.xml"
                        302 to ""
                    }
                    "/index.xml" ->
                        200 to
                            "<sitemapindex><sitemap><loc>https://example.com/pages.xml</loc></sitemap></sitemapindex>"
                    else ->
                        200 to
                            (
                                "<urlset><loc>https://example.com/z</loc><loc>https://example.com/a</loc>" +
                                    "<loc>https://example.com/z</loc><loc> </loc></urlset>"
                            )
                }
            }
            assertEquals(listOf("https://example.com/a", "https://example.com/z"), client.discoverUrls("example.com"))
            assertEquals(listOf("/sitemap-index.xml", "/index.xml", "/pages.xml"), paths)
        }

    @Test fun `rejects empty index and redirect without location`() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.discoverUrls("example.com") } }
        responder = { 302 to "" }
        assertThrows(IllegalStateException::class.java) { runBlocking { client.discoverUrls("example.com") } }
    }

    @Test fun `rejects redirect loops and redirects leaving the origin`() {
        responder = { 302 to "" }
        location = "/loop.xml"
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.discoverUrls("example.com") } }
        assertEquals(6, paths.size)
        location = "https://other.example/index.xml"
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.discoverUrls("example.com") } }
    }

    @Test fun `rejects unsafe sitemap addresses and accepts equivalent HTTPS ports`() {
        val origin = URI("https://example.com")
        listOf(
            "/relative.xml",
            "http://example.com/a",
            "https://user@example.com/a",
            "https://example.com:8443/a",
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { client.validateUri(origin, URI(it), "Sitemap") }
        }
        listOf(
            "0.0.0.0",
            "127.0.0.1",
            "10.0.0.1",
            "169.254.1.1",
            "224.0.0.1",
            "240.0.0.1",
            "[::1]",
            "[fc00::1]",
        ).forEach {
            val local = URI("https://$it/a")
            assertThrows(IllegalArgumentException::class.java) { client.validateUri(local, local, "Sitemap") }
        }
        val explicit = URI("https://example.com:443/a")
        assertEquals(explicit, client.validateUri(origin, explicit, "Sitemap"))
        val publicIp = URI("https://8.8.8.8/a")
        assertEquals(publicIp, client.validateUri(publicIp, publicIp, "Sitemap"))
    }
}
