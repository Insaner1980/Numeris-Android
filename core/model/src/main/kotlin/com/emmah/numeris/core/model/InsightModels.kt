package com.emmah.numeris.core.model

import kotlin.math.max

enum class InsightSeverity { Info, Warning, Critical }

data class InsightTarget(
    val page: String,
    val tab: String,
    val label: String,
)

data class InsightCard(
    val title: String,
    val message: String,
    val whyShown: String,
    val nextStep: String,
    val severity: InsightSeverity,
    val priority: Int,
    val target: InsightTarget,
)

data class StatusCodeSummary(
    val totalRequests: Long,
    val clientErrorResponses: Long,
    val serverErrorResponses: Long,
) {
    val clientErrorShare: Double get() = ratio(clientErrorResponses, totalRequests)
    val serverErrorShare: Double get() = ratio(serverErrorResponses, totalRequests)

    private fun ratio(
        part: Long,
        total: Long,
    ): Double = if (total > 0) part.toDouble() / total else 0.0
}

data class IndexingSummary(
    val activeSitemapUrls: Int,
    val inspectedUrls: Int,
    val indexedUrls: Int,
) {
    val notIndexedUrls: Int get() = max(0, inspectedUrls - indexedUrls)
    val notIndexedRatio: Double get() = if (inspectedUrls > 0) notIndexedUrls.toDouble() / inspectedUrls else 0.0
}

data class SourceFreshnessSummary(
    val connectedSources: Int,
    val missingLastSyncSources: Int,
    val staleSources: Int,
) {
    val hasConnectedSources: Boolean get() = connectedSources > 0
    val hasStaleData: Boolean get() = missingLastSyncSources > 0 || staleSources > 0
}

data class SearchPageTrend(
    val page: String,
    val currentClicks: Long,
    val previousClicks: Long,
) {
    val clickDelta: Long get() = currentClicks - previousClicks
}

data class InsightMetrics(
    val cloudflareVisitors: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val googleImpressions: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val googleClicks: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val googleMobileClicks: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val googleMobileImpressions: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val pageSpeedMobileScore: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val bingImpressions: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val bingClicks: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val cloudflareCacheHitRatio: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val cloudflareThreats: MetricWindow = MetricWindow(0.0, 0.0, false, false, false),
    val httpStatus: StatusCodeSummary = StatusCodeSummary(0, 0, 0),
    val indexing: IndexingSummary = IndexingSummary(0, 0, 0),
    val freshness: SourceFreshnessSummary = SourceFreshnessSummary(0, 0, 0),
    val worstDecliningPage: SearchPageTrend? = null,
    val newSearchQueryCount: Int = 0,
    val isSingleDomain: Boolean = true,
)
