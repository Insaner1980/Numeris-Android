package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.time.LocalDate

class ReportMathTest {
    private val range = DateRange(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11"), 7)

    private fun observations(
        site: String = "example.com",
        value: Long = 10,
    ) = (
        ReportMath.dates(
            range.previous(),
        ) + ReportMath.dates(range)
    ).map { SearchObservation(site, it, "daily", "", value, value * 10, 2.0) }

    @Test fun completePeriodsRequireEveryExpectedSiteAndDay() {
        val rows = observations()
        assertTrue(ReportMath.complete(rows, range))
        assertFalse(ReportMath.complete(rows.drop(1), range))
        assertFalse(ReportMath.complete(rows, range, setOf("example.com", "missing.com")))
        assertNull(ReportMath.change(ReportMath.window(rows.drop(1), range) { it.clicks }))
    }

    @Test fun missingZeroAndNewActivityAreDifferent() {
        val empty = ReportMath.window(emptyList(), range) { it.clicks }
        val zeros = ReportMath.window(observations(value = 0), range) { it.clicks }
        assertFalse(empty.hasCurrent)
        assertTrue(zeros.hasCurrent)
        assertNull(ReportMath.change(zeros))
        assertEquals(TrendState.NewActivity, Trend.classify(MetricWindow(10.0, 0.0)))
        val series = ReportMath.series("Visitors", "count", mapOf(range.start to 0.0), range)
        assertEquals(0.0, series.points.first().value)
        assertNull(series.points[1].value)
        assertEquals(range.end, series.points.last().date)
    }

    @Test fun queryAggregatesWeightPositionAndComputeCtrFromTotals() {
        val rows =
            listOf(
                SearchObservation("a.com", range.start, "query", "same", 1, 10, 1.0),
                SearchObservation("a.com", range.end, "query", "same", 9, 90, 9.0),
                SearchObservation("b.com", range.end, "query", "same", 20, 100, 3.0),
            )
        val result = ReportMath.searchRows(rows, range, false)
        assertEquals(2, result.size)
        assertEquals("b.com", result.first().domain)
        val a = result.last()
        assertEquals(8.2, a.position!!, 0.00001)
        assertEquals(0.1, a.ctr!!, 0.00001)
        assertNull(a.previousClicks)
        assertEquals(2, result.map { it.key }.distinct().size)
    }

    @Test fun ratioUsesTotalsAndUnavailableDenominator() {
        assertEquals(0.18, ReportMath.ratio(18, 100)!!, 0.0)
        assertNull(ReportMath.ratio(0, 0))
        assertNull(ReportMath.ratio(null, 10))
    }

    @Test fun uptimeIsAverageOfSiteHistoryIncludingFailedProbes() {
        val rows =
            listOf(
                Probe("a.com", "old", "up", 200, 100, null),
                Probe("a.com", "new", "timeout", null, null, "Timeout"),
                Probe("b.com", "new", "up", 200, 50, null),
            )
        assertEquals(0.75, ReportMath.uptime(rows)!!, 0.0)
        assertNull(ReportMath.uptime(emptyList()))
    }

    @Test fun removedAndUninspectedPagesDoNotBecomeIndexingFailures() {
        fun page(
            url: String,
            removed: String?,
            inspected: String?,
            verdict: String?,
        ) = SitemapPage("a.com", url, "now", removed, inspected, verdict, null, null, null, null, null, null)
        val value =
            ReportMath.indexing(
                listOf(
                    page("/a", null, "now", "PASS"),
                    page("/b", null, "now", "FAIL"),
                    page("/c", null, null, null),
                    page("/d", "now", "now", "PASS"),
                ),
            )
        assertEquals(IndexingSummary(3, 2, 1), value)
    }

    @Test fun normalizedIdentityPreservesMeasuredPathAndQuery() {
        assertEquals("/Some%20Page/", SiteIdentity.pagePath("https://EXAMPLE.com/Some%20Page?q=1#frag"))
        assertEquals("/Some%20Page/", SiteIdentity.pagePath("/Some Page"))
        assertEquals("/a%2Fb/", SiteIdentity.pagePath("/a%2fb"))
        assertEquals("/a%2Bb/", SiteIdentity.pagePath("/a+b"))
        assertEquals(
            "https://example.com/Some%20Page/?q=1",
            PerformanceUrl.normalizePageUrl("https://EXAMPLE.com/Some%20Page?q=1#frag"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            PerformanceUrl.normalizePageUrl("https://example.com/?access_token=hidden")
        }
        val urlWithCredentials =
            URI("https", "test-user:test-password", "example.com", -1, "/", null, null).toString()
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                PerformanceUrl.normalizePageUrl(urlWithCredentials)
            }
        assertEquals("Target URLs must not contain credentials", error.message)
    }

    @Test fun providerDatesNeverInventASnapshotDate() {
        assertEquals("2026-10-11", ProviderDate.normalize("2026-10-11T12:00:00Z"))
        assertEquals("1970-01-01", ProviderDate.normalize("/Date(0+0300)/"))
        assertNull(ProviderDate.normalize(""))
        assertNull(ProviderDate.normalize("unknown"))
    }

    @Test fun largeValuesAndNonFiniteValuesHaveDeliberateFormatting() {
        assertEquals("9,223,372,036,854,775,807", DisplayFormat.number(Long.MAX_VALUE))
        assertEquals("—", DisplayFormat.number(Double.NaN))
        assertEquals("—", DisplayFormat.bytes(-1.0))
        assertEquals("0.0%", DisplayFormat.percent(0.0))
        assertEquals("-12.5%", DisplayFormat.change(-12.5))
    }
}
