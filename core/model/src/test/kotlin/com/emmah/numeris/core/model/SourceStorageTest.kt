package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceStorageTest {
    @Test fun persistedNamesRemainCompatibleWithExistingConnections() {
        val names =
            listOf(
                "cloudflare",
                "web_analytics",
                "search_console",
                "crux",
                "pagespeed",
                "bing_webmaster",
                "uptime",
                "sitemap",
            )
        assertEquals(names, SourceId.entries.map { it.storageName })
        assertEquals(SourceId.entries.toList(), names.map(::sourceIdFromStorage))
        assertNull(sourceIdFromStorage("unknown"))
        assertNull(sourceIdFromStorage("Cloudflare"))
    }
}
