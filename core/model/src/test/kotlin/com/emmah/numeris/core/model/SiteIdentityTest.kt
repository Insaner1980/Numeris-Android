package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SiteIdentityTest {
    @Test fun `normalizes domain and URLs like Windows Numeris`() {
        assertEquals("knittoolsapp.com", SiteIdentity.normalizeDomain(" HTTPS://KnitToolsApp.com/path "))
        assertEquals("https://knittoolsapp.com", SiteIdentity.normalizeOriginUrl("knittoolsapp.com"))
        assertEquals("https://knittoolsapp.com/", SiteIdentity.normalizeHomePageUrl("knittoolsapp.com"))
        assertEquals(
            "https://knittoolsapp.com/docs/",
            SiteIdentity.normalizeHttpsPageUrl("https://knittoolsapp.com/docs#intro"),
        )
    }

    @Test fun `rejects cleartext page URL`() {
        assertThrows(IllegalArgumentException::class.java) {
            SiteIdentity.normalizeHttpsPageUrl("http://knittoolsapp.com/")
        }
    }
}
