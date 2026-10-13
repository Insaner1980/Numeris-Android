package com.emmah.numeris.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.URI

class NetworkPolicyTest {
    @Test fun `provider errors expose only enum-like codes, never free text`() {
        val body =
            """{"error":{"code":403,"message":"private-detail user@example.com","status":"PERMISSION_DENIED",
            "details":[{"reason":"SERVICE_DISABLED"}]}}"""
        assertEquals("PERMISSION_DENIED, SERVICE_DISABLED", ApiErrors.errorCode(body))
        assertEquals(null, ApiErrors.errorCode("""{"error":{"status":"not an enum value"}}"""))
        assertEquals(null, ApiErrors.errorCode("plain private text"))
        val failure = ApiErrors.fromStatus("Google URL Inspection", 500, errorCode = "INTERNAL")
        assertEquals("Google URL Inspection is temporarily unavailable (HTTP 500, INTERNAL).", failure.message)
    }

    @Test fun `redacts credentials before limiting raw json`() {
        val raw = """{"api_key":"secret","authorization":"Bearer abc.def","url":"https://example.com/?token=value"}"""
        val stored = RawJsonStoragePolicy.redactAndLimit(raw)
        assertFalse(stored.contains("secret"))
        assertFalse(stored.contains("abc.def"))
        assertFalse(stored.contains("token=value"))
    }

    @Test fun `sitemap parser supports namespace and removes whitespace`() {
        val xml =
            """
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
              <url><loc> https://example.com/a </loc></url>
            </urlset>
            """.trimIndent()
        assertEquals(
            listOf("https://example.com/a"),
            SitemapClient(NumerisHttpClient()).parseLocations(xml),
        )
    }

    @Test fun `sitemap blocks cross origin and local IP literals`() {
        val client = SitemapClient(NumerisHttpClient())
        val origin = URI("https://example.com")
        assertThrows(IllegalArgumentException::class.java) {
            client.validateUri(origin, URI("https://other.example/sitemap.xml"), "Child sitemap URL")
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.validateUri(URI("https://127.0.0.1"), URI("https://127.0.0.1/sitemap.xml"), "Sitemap URL")
        }
    }
}
