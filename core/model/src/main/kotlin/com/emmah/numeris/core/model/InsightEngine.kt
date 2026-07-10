@file:Suppress(
    "ComplexCondition",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "TooManyFunctions",
    "ktlint:standard:max-line-length",
)

package com.emmah.numeris.core.model

import java.util.Locale
import kotlin.math.abs

object InsightEngine {
    private const val MAX_INSIGHTS = 4
    private const val FLAT_TOLERANCE = 0.05

    fun generate(metrics: InsightMetrics): List<InsightCard> =
        listOfNotNull(
            dataStale(metrics),
            trafficMismatch(metrics),
            googleVisibilityWithoutClicks(metrics),
            decliningPage(metrics),
            newSearchQueries(metrics),
            mobileDemandRisk(metrics),
            indexingIssue(metrics),
            indexingDataMissing(metrics),
            httpErrors(metrics),
            bingVisibilityWithoutClicks(metrics),
            engagementGap(metrics),
            cacheEfficiency(metrics),
            threatSpike(metrics),
        ).sortedWith(compareByDescending<InsightCard> { it.severity }.thenBy { it.priority }).take(MAX_INSIGHTS)

    fun emptyStateText(metrics: InsightMetrics): String =
        if (metrics.freshness.hasConnectedSources && hasAnyData(metrics)) {
            "All connected sources look consistent for this period."
        } else {
            "Connect or refresh sources to generate insights."
        }

    private fun dataStale(metrics: InsightMetrics): InsightCard? {
        if (!metrics.freshness.hasConnectedSources || !metrics.freshness.hasStaleData) return null
        return card(
            "Some data may be out of date",
            "One or more connected sources have not synced recently, so the insights may not reflect the latest traffic.",
            "${metrics.freshness.missingLastSyncSources} connected sources have no sync time and " +
                "${metrics.freshness.staleSources} last synced more than 48 hours ago.",
            "Open Sources and check connected source status.",
            InsightSeverity.Warning,
            10,
        )
    }

    private fun trafficMismatch(metrics: InsightMetrics): InsightCard? {
        if (!hasWindow(metrics.cloudflareVisitors) ||
            !hasWindow(metrics.ga4Users) ||
            metrics.cloudflareVisitors.current < 50 ||
            !isUp(metrics.cloudflareVisitors, 0.25) ||
            !isFlat(metrics.ga4Users)
        ) {
            return null
        }
        return card(
            "Traffic looks inconsistent",
            "Cloudflare recorded more visitors, but Analytics users stayed almost the same. " +
                "This can happen with bots, cached or static requests, or tracking gaps.",
            "Cloudflare visitors increased by ${positiveRatio(metrics.cloudflareVisitors)}, while Analytics users " +
                "changed by ${changedRatio(metrics.ga4Users)}.",
            "Open Cloudflare and review Traffic, Cache, or Status Codes.",
            InsightSeverity.Warning,
            30,
        )
    }

    private fun googleVisibilityWithoutClicks(metrics: InsightMetrics): InsightCard? {
        if (!hasWindow(metrics.googleImpressions) ||
            !hasWindow(metrics.googleClicks) ||
            metrics.googleImpressions.current < 100 ||
            !isUp(metrics.googleImpressions, 0.25) ||
            !isFlat(metrics.googleClicks)
        ) {
            return null
        }
        return card(
            "People see your pages, but do not click",
            "Google impressions are rising, but clicks are not. Your result may need a better title, snippet, or ranking position.",
            "Google impressions increased by ${positiveRatio(metrics.googleImpressions)}, while clicks changed by " +
                "${changedRatio(metrics.googleClicks)}.",
            "Open Google Search and review Queries, Pages, and Indexing.",
            InsightSeverity.Warning,
            40,
        )
    }

    private fun decliningPage(metrics: InsightMetrics): InsightCard? {
        val page = metrics.worstDecliningPage ?: return null
        if (page.previousClicks < 5 || page.currentClicks >= page.previousClicks) return null
        val lost = page.previousClicks - page.currentClicks
        if (lost < 5 && lost.toDouble() / page.previousClicks < 0.25) return null
        return card(
            "A previously useful page is losing search traffic",
            "One page that used to bring Google clicks is now bringing fewer clicks.",
            "${page.page.take(77).let { if (page.page.length > 80) "$it..." else it }} changed from " +
                "${page.previousClicks} to ${page.currentClicks} Google clicks.",
            "Open Google Search and review Queries, Pages, and Indexing.",
            InsightSeverity.Warning,
            45,
        )
    }

    private fun newSearchQueries(metrics: InsightMetrics): InsightCard? {
        if (!metrics.isSingleDomain || metrics.newSearchQueryCount <= 0) return null
        return card(
            "New search queries are bringing traffic",
            "Google is sending clicks from queries that were not visible in the previous period.",
            "${metrics.newSearchQueryCount} new queries brought Google clicks in this period.",
            "Open Google Search and review Queries, Pages, and Indexing.",
            InsightSeverity.Info,
            120,
        )
    }

    private fun mobileDemandRisk(metrics: InsightMetrics): InsightCard? {
        val demand = isUp(metrics.googleMobileClicks, 0.20) || isUp(metrics.googleMobileImpressions, 0.20)
        val weak =
            isDown(metrics.pageSpeedMobileScore) ||
                metrics.pageSpeedMobileScore.current in Double.MIN_VALUE..<0.70
        if (!demand || !weak) return null
        return card(
            "Mobile traffic may need attention",
            "Mobile search demand is growing while mobile performance looks weak or is getting worse.",
            "Mobile impressions changed by ${changedRatio(metrics.googleMobileImpressions)}, mobile clicks changed by " +
                "${changedRatio(metrics.googleMobileClicks)}, and mobile score is ${score(metrics.pageSpeedMobileScore.current)}.",
            "Open Performance and review PageSpeed and Core Web Vitals.",
            InsightSeverity.Warning,
            50,
        )
    }

    private fun indexingIssue(metrics: InsightMetrics): InsightCard? {
        val value = metrics.indexing
        if (value.activeSitemapUrls <= 0 || value.inspectedUrls <= 0) return null
        if (value.notIndexedUrls < 3 && value.notIndexedRatio < 0.20) return null
        return card(
            "Some sitemap pages may not be indexed",
            "Numeris found sitemap URLs that do not currently have a passing Google URL Inspection result.",
            "${value.indexedUrls} of ${value.inspectedUrls} inspected sitemap URLs are indexed.",
            "Open Google Search and review Queries, Pages, and Indexing.",
            InsightSeverity.Warning,
            25,
        )
    }

    private fun indexingDataMissing(metrics: InsightMetrics): InsightCard? {
        val value = metrics.indexing
        if (value.activeSitemapUrls < 5 || value.inspectedUrls != 0) return null
        return card(
            "Indexing data is missing",
            "Numeris knows about sitemap URLs, but they have not been inspected through Google URL Inspection yet.",
            "${value.activeSitemapUrls} active sitemap URLs have no inspection result yet.",
            "Open Google Search and review Queries, Pages, and Indexing.",
            InsightSeverity.Info,
            90,
        )
    }

    private fun httpErrors(metrics: InsightMetrics): InsightCard? {
        val value = metrics.httpStatus
        if (value.totalRequests < 100) return null
        if (value.serverErrorShare >= 0.01 || value.serverErrorResponses >= 10) {
            return errorCard("Server error responses were ${percent(value.serverErrorShare)} of Cloudflare requests.", true)
        }
        return if (value.clientErrorShare >= 0.05) {
            errorCard("Client error responses were ${percent(value.clientErrorShare)} of Cloudflare requests.", false)
        } else {
            null
        }
    }

    private fun bingVisibilityWithoutClicks(metrics: InsightMetrics): InsightCard? {
        if (metrics.bingImpressions.current < 50 || metrics.bingClicks.current != 0.0) return null
        return card(
            "Bing sees the site, but brings no visitors",
            "Bing has impressions for the site, but no clicks in the selected period.",
            "Bing recorded ${number(metrics.bingImpressions.current)} impressions and ${number(metrics.bingClicks.current)} clicks.",
            "Open Bing and review Queries, Pages, and Crawl.",
            InsightSeverity.Info,
            130,
        )
    }

    private fun engagementGap(metrics: InsightMetrics): InsightCard? {
        val growing = isUp(metrics.ga4Users, 0.25)
        val engagementDown = hasWindow(metrics.ga4EngagementRate) && isDown(metrics.ga4EngagementRate)
        val keyEventsWeak =
            hasWindow(metrics.ga4KeyEvents) &&
                (isDown(metrics.ga4KeyEvents) || isFlat(metrics.ga4KeyEvents))
        if (!growing || (!engagementDown && !keyEventsWeak)) return null
        return card(
            "More visitors are not becoming more engaged",
            "Analytics traffic is growing, but engagement or key events are not improving with it.",
            "Analytics users changed by ${changedRatio(metrics.ga4Users)}, engagement changed by " +
                "${changedRatio(metrics.ga4EngagementRate)}, and key events changed by ${changedRatio(metrics.ga4KeyEvents)}.",
            "Open Analytics and review Pages, Acquisition, Events, and Devices.",
            InsightSeverity.Warning,
            60,
        )
    }

    private fun cacheEfficiency(metrics: InsightMetrics): InsightCard? {
        if (metrics.httpStatus.totalRequests < 100 ||
            metrics.cloudflareCacheHitRatio.current >= 0.50 ||
            !isDown(metrics.cloudflareCacheHitRatio, 0.15)
        ) {
            return null
        }
        return card(
            "Caching may be less effective",
            "Cloudflare is serving fewer requests from cache than before.",
            "Cache hit ratio changed by ${changedRatio(metrics.cloudflareCacheHitRatio)} and is now " +
                "${percent(metrics.cloudflareCacheHitRatio.current)}.",
            "Open Cloudflare and review Traffic, Cache, or Status Codes.",
            InsightSeverity.Warning,
            70,
        )
    }

    private fun threatSpike(metrics: InsightMetrics): InsightCard? {
        val state = Trend.classify(metrics.cloudflareThreats, upThreshold = 0.50)
        if (metrics.cloudflareThreats.current < 10 || state !in setOf(TrendState.NewActivity, TrendState.Up)) return null
        return card(
            "Cloudflare is blocking more suspicious traffic",
            "Threat events increased during this period.",
            "Cloudflare threat events changed from ${number(metrics.cloudflareThreats.previous)} to " +
                "${number(metrics.cloudflareThreats.current)}.",
            "Open Cloudflare and review Traffic, Cache, or Status Codes.",
            InsightSeverity.Warning,
            20,
        )
    }

    private fun errorCard(
        why: String,
        critical: Boolean,
    ) = card(
        "Some visitors may be hitting errors",
        "Cloudflare recorded HTTP error responses during this period.",
        why,
        "Open Cloudflare and review Traffic, Cache, or Status Codes.",
        if (critical) InsightSeverity.Critical else InsightSeverity.Warning,
        if (critical) 5 else 55,
    )

    private fun card(
        title: String,
        message: String,
        why: String,
        next: String,
        severity: InsightSeverity,
        priority: Int,
    ) = InsightCard(title, message, why, next, severity, priority)

    private fun hasAnyData(metrics: InsightMetrics): Boolean =
        listOf(
            metrics.cloudflareVisitors,
            metrics.ga4Users,
            metrics.googleImpressions,
            metrics.googleClicks,
            metrics.googleMobileClicks,
            metrics.googleMobileImpressions,
            metrics.pageSpeedMobileScore,
            metrics.bingImpressions,
            metrics.bingClicks,
            metrics.ga4EngagementRate,
            metrics.ga4KeyEvents,
            metrics.cloudflareCacheHitRatio,
            metrics.cloudflareThreats,
        ).any(::hasWindow) ||
            metrics.httpStatus.totalRequests > 0 ||
            metrics.indexing.activeSitemapUrls > 0

    private fun hasWindow(metric: MetricWindow) = metric.current > 0.0 || metric.previous > 0.0

    private fun isUp(
        metric: MetricWindow,
        threshold: Double,
    ) = hasWindow(metric) && Trend.classify(metric, upThreshold = threshold) == TrendState.Up

    private fun isDown(
        metric: MetricWindow,
        threshold: Double = 0.10,
    ) = hasWindow(metric) && Trend.classify(metric, downThreshold = threshold) == TrendState.Down

    private fun isFlat(metric: MetricWindow) =
        hasWindow(metric) && Trend.classify(metric) == TrendState.Flat && abs(Trend.changeRatio(metric)) <= FLAT_TOLERANCE

    private fun positiveRatio(metric: MetricWindow) = Trend.formatRatio(Trend.changeRatio(metric)).trimStart('+')

    private fun changedRatio(metric: MetricWindow) =
        if (metric.previous == 0.0 && metric.current > 0.0) "new activity" else Trend.formatRatio(Trend.changeRatio(metric))

    private fun percent(value: Double) = String.format(Locale.ROOT, "%.1f%%", value * 100)

    private fun score(value: Double) = if (value <= 0) "not available" else String.format(Locale.ROOT, "%.0f", value * 100)

    private fun number(value: Double) = String.format(Locale.ROOT, "%.0f", value)
}
