package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopInsightTest {
    @Test fun completeDecisionsAndDestinationsMatchCurrentDesktopEngine() {
        val expected =
            javaClass.getResourceAsStream("/insight-reference.csv")!!.bufferedReader().use {
                it.readLines().map { line -> line.split('|') }.groupBy { cells -> cells.first() }
            }
        assertEquals(18, expected.size)
        for ((name, rows) in expected) {
            val actual =
                InsightEngine
                    .generate(inputs.getValue(name))
                    .map { card ->
                        listOf(
                            name,
                            card.title,
                            card.message,
                            card.whyShown,
                            card.nextStep,
                            card.severity.name,
                            card.priority.toString(),
                            card.target.page,
                            desktopTab(card.target.tab),
                            card.target.label,
                        )
                    }.ifEmpty { listOf(listOf(name, "none")) }
            assertEquals(name, rows, actual)
        }
    }

    private fun desktopTab(tab: String) =
        when (tab) {
            "Status codes" -> "status"
            else -> tab.lowercase()
        }

    private fun visibility() =
        InsightMetrics(
            cloudflareVisitors = MetricWindow(100.0, 100.0),
            googleImpressions = MetricWindow(150.0, 100.0),
            googleClicks = MetricWindow(100.0, 100.0),
        )

    private val inputs =
        mapOf(
            "stale" to InsightMetrics(freshness = SourceFreshnessSummary(5, 2, 3)),
            "visibility" to visibility(),
            "visibility-below" to visibility().copy(googleImpressions = MetricWindow(124.0, 100.0)),
            "decline" to InsightMetrics(worstDecliningPage = SearchPageTrend("/article/", 15, 20)),
            "decline-below" to InsightMetrics(worstDecliningPage = SearchPageTrend("/article/", 16, 20)),
            "new-queries" to InsightMetrics(newSearchQueryCount = 3),
            "new-queries-all" to InsightMetrics(newSearchQueryCount = 3, isSingleDomain = false),
            "mobile" to
                InsightMetrics(
                    googleMobileImpressions = MetricWindow(150.0, 100.0),
                    pageSpeedMobileScore = MetricWindow(0.60, 0.90),
                ),
            "indexing" to InsightMetrics(indexing = IndexingSummary(10, 10, 7)),
            "indexing-missing" to InsightMetrics(indexing = IndexingSummary(5, 0, 0)),
            "http-critical" to InsightMetrics(httpStatus = StatusCodeSummary(1000, 0, 10)),
            "http-warning" to InsightMetrics(httpStatus = StatusCodeSummary(10000, 0, 10)),
            "http-client" to InsightMetrics(httpStatus = StatusCodeSummary(100, 5, 0)),
            "bing" to
                InsightMetrics(
                    cloudflareVisitors = MetricWindow(100.0, 100.0),
                    bingImpressions = MetricWindow(200.0, 100.0),
                    bingClicks = MetricWindow(0.0, 0.0),
                ),
            "cache" to
                InsightMetrics(
                    httpStatus = StatusCodeSummary(200, 0, 0),
                    cloudflareCacheHitRatio = MetricWindow(0.35, 0.60),
                ),
            "threats" to InsightMetrics(cloudflareThreats = MetricWindow(20.0, 10.0)),
            "incomplete" to visibility().copy(googleImpressions = MetricWindow(150.0, 100.0, hasComparison = false)),
            "combined" to
                visibility().copy(
                    freshness = SourceFreshnessSummary(5, 2, 3),
                    indexing = IndexingSummary(10, 10, 7),
                    httpStatus = StatusCodeSummary(1000, 0, 10),
                    cloudflareThreats = MetricWindow(20.0, 10.0),
                ),
        )
}
