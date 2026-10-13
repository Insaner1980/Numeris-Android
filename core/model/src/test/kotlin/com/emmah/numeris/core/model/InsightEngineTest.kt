package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightEngineTest {
    @Test
    fun `critical HTTP errors sort first`() {
        val result =
            InsightEngine.generate(
                InsightMetrics(
                    httpStatus = StatusCodeSummary(1_000, 0, 20),
                    newSearchQueryCount = 4,
                ),
            )

        assertEquals("Some visitors may be hitting errors", result.first().title)
        assertEquals(InsightSeverity.Critical, result.first().severity)
    }

    @Test
    fun `returns at most four deterministic cards`() {
        val result =
            InsightEngine.generate(
                InsightMetrics(
                    cloudflareVisitors = MetricWindow(200.0, 100.0),
                    googleImpressions = MetricWindow(300.0, 100.0),
                    googleClicks = MetricWindow(10.0, 10.0),
                    bingImpressions = MetricWindow(100.0, 0.0),
                    freshness = SourceFreshnessSummary(2, 1, 0),
                    indexing = IndexingSummary(10, 10, 4),
                    newSearchQueryCount = 2,
                ),
            )

        assertEquals(4, result.size)
        assertTrue(result.zipWithNext().all { (first, second) -> first.severity >= second.severity })
    }

    @Test
    fun `empty state matches connected data contract`() {
        val metrics =
            InsightMetrics(
                cloudflareVisitors = MetricWindow(10.0, 9.0),
                freshness = SourceFreshnessSummary(1, 0, 0),
            )

        assertEquals(
            "No insight rules were triggered by the stored data for this period.",
            InsightEngine.emptyStateText(metrics),
        )
    }
}
