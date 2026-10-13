package com.emmah.numeris.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CloudflareQueryWindowTest {
    private val now = Instant.parse("2026-10-12T08:00:00Z")
    private val today = LocalDate.parse("2026-10-12")

    @Test fun `daily range is kept when the zone allows it or limits are unknown`() {
        val requested = LocalDate.parse("2026-09-28")
        val generous = CloudflareDatasetLimit(null, 400L * DAY)
        assertEquals(requested, CloudflareQueryWindow.dailyStart(requested, today, now, null))
        assertEquals(requested, CloudflareQueryWindow.dailyStart(requested, today, now, generous))
    }

    @Test fun `daily range is shortened from the start to the plan age and duration limits`() {
        val requested = LocalDate.parse("2025-10-13")
        val oldestAllowed = CloudflareDatasetLimit(null, 30L * DAY)
        val shortWindow = CloudflareDatasetLimit(7L * DAY, null)
        assertEquals(
            LocalDate.parse("2026-09-13"),
            CloudflareQueryWindow.dailyStart(requested, today, now, oldestAllowed),
        )
        assertEquals(
            LocalDate.parse("2026-10-06"),
            CloudflareQueryWindow.dailyStart(requested, today, now, shortWindow),
        )
    }

    @Test fun `adaptive range never exceeds the allowed duration before its end`() {
        val until = Instant.parse("2026-10-13T00:00:00Z")
        val requested = Instant.parse("2026-09-14T00:00:00Z")
        assertEquals(
            Instant.parse("2026-10-12T00:00:00Z"),
            CloudflareQueryWindow.adaptiveStart(requested, until, now, CloudflareDatasetLimit(DAY, null)),
        )
        assertEquals(requested, CloudflareQueryWindow.adaptiveStart(requested, until, now, null))
    }

    @Test fun `zone settings are parsed per dataset and invalid responses are ignored`() {
        val raw =
            """{"data":{"viewer":{"zones":[{"settings":{
            "httpRequests1dGroups":{"maxDuration":31536000,"notOlderThan":31536000},
            "httpRequestsAdaptiveGroups":{"maxDuration":86400,"notOlderThan":2678400}}}]}}}"""
        val datasets = listOf("httpRequests1dGroups", "httpRequestsAdaptiveGroups")
        val limits = CloudflareQueryWindow.parseLimits(raw, "zones", datasets)!!
        assertEquals(CloudflareDatasetLimit(31_536_000, 31_536_000), limits["httpRequests1dGroups"])
        assertEquals(CloudflareDatasetLimit(86_400, 2_678_400), limits["httpRequestsAdaptiveGroups"])
        val error = """{"errors":[{"message":"unknown field"}]}"""
        assertNull(CloudflareQueryWindow.parseLimits(error, "zones", datasets))
    }

    @Test fun `graphql errors become a redacted provider message`() {
        val root =
            kotlinx.serialization.json.Json
                .parseToJsonElement("""{"errors":[{"message":"time range too large, Bearer abc.def"}]}""")
                .let { it as kotlinx.serialization.json.JsonObject }
        val failure = CloudflareQueryWindow.graphqlFailure(root, "Cloudflare")!!
        assertEquals("Cloudflare: time range too large, Bearer [REDACTED]", failure.message)
    }

    private companion object {
        const val DAY = 86_400L
    }
}
