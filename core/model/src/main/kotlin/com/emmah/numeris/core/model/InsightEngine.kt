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
            googleVisibilityWithoutClicks(metrics),
            decliningPage(metrics),
            newSearchQueries(metrics),
            mobileDemandRisk(metrics),
            indexingIssue(metrics),
            indexingDataMissing(metrics),
            httpErrors(metrics),
            bingVisibilityWithoutClicks(metrics),
            cacheEfficiency(metrics),
            threatSpike(metrics),
        ).sortedWith(compareByDescending<InsightCard> { it.severity }.thenBy { it.priority }).take(MAX_INSIGHTS)

    fun emptyStateText(metrics: InsightMetrics): String =
        if (metrics.freshness.hasConnectedSources && hasAnyData(metrics)) {
            "No insight rules were triggered by the stored data for this period."
        } else {
            "Connect or refresh sources to generate insights."
        }

    private fun dataStale(metrics: InsightMetrics): InsightCard? {
        if (!metrics.freshness.hasConnectedSources || !metrics.freshness.hasStaleData) return null
        return card(
            "Some data may be out of date",
            "One or more connected sources have not synced recently, so the insights may not reflect the " +
                "latest traffic.",
            freshnessDetail(metrics.freshness),
            "Open Sources and check connected source status.",
            InsightSeverity.Warning,
            10,
        )
    }

    private fun freshnessDetail(freshness: SourceFreshnessSummary): String {
        fun sources(count: Int) = if (count == 1) "1 connected source" else "$count connected sources"
        return listOfNotNull(
            "${sources(freshness.missingLastSyncSources)} never synced".takeIf { freshness.missingLastSyncSources > 0 },
            "${sources(
                freshness.staleSources,
            )} last synced more than 48 hours ago".takeIf { freshness.staleSources > 0 },
        ).joinToString(", and ") + "."
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
            "Google impressions are rising, but clicks are not. Your result may need a better title, " +
                "snippet, or ranking position.",
            "Google impressions increased by ${positiveRatio(metrics.googleImpressions)}, while clicks changed by " +
                "${changedRatio(metrics.googleClicks)}.",
            REVIEW_SEARCH,
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
            "${if (page.page.length > 80) page.page.take(77) + "..." else page.page} changed from " +
                "${page.previousClicks} to ${page.currentClicks} Google clicks.",
            REVIEW_SEARCH,
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
            REVIEW_SEARCH,
            InsightSeverity.Info,
            120,
        )
    }

    private fun mobileDemandRisk(metrics: InsightMetrics): InsightCard? {
        val demand = isUp(metrics.googleMobileClicks, 0.20) || isUp(metrics.googleMobileImpressions, 0.20)
        val weak =
            isDown(metrics.pageSpeedMobileScore) ||
                (
                    metrics.pageSpeedMobileScore.hasCurrent &&
                        metrics.pageSpeedMobileScore.current in Double.MIN_VALUE..<0.70
                )
        if (!demand || !weak) return null
        return card(
            "Mobile traffic may need attention",
            "Mobile search demand is growing while mobile performance looks weak or is getting worse.",
            "Mobile impressions changed by ${changedRatio(
                metrics.googleMobileImpressions,
            )}, mobile clicks changed by " +
                "${changedRatio(
                    metrics.googleMobileClicks,
                )}, and mobile score is ${score(metrics.pageSpeedMobileScore.current)}.",
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
            REVIEW_SEARCH,
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
            REVIEW_SEARCH,
            InsightSeverity.Info,
            90,
        )
    }

    private fun httpErrors(metrics: InsightMetrics): InsightCard? {
        val value = metrics.httpStatus
        if (value.totalRequests < 100) return null
        if (value.serverErrorResponses >= 10) {
            val critical = value.serverErrorShare >= 0.01
            return card(
                VISITOR_ERRORS,
                "Cloudflare recorded HTTP error responses during this period.",
                "${number(
                    value.serverErrorResponses.toDouble(),
                )} server error responses, ${percent(value.serverErrorShare)} of Cloudflare requests.",
                REVIEW_CLOUDFLARE,
                if (critical) InsightSeverity.Critical else InsightSeverity.Warning,
                if (critical) 5 else 30,
            )
        }
        return if (value.clientErrorShare >= 0.05) {
            errorCard("Client error responses were ${percent(value.clientErrorShare)} of Cloudflare requests.", false)
        } else {
            null
        }
    }

    private fun bingVisibilityWithoutClicks(metrics: InsightMetrics): InsightCard? {
        if (!metrics.bingImpressions.hasCurrent ||
            !metrics.bingClicks.hasCurrent ||
            metrics.bingImpressions.current < 50 ||
            metrics.bingClicks.current != 0.0
        ) {
            return null
        }
        return card(
            "Bing sees the site, but brings no visitors",
            "Bing has impressions for the site, but no clicks in the selected period.",
            "Bing recorded ${number(
                metrics.bingImpressions.current,
            )} impressions and ${number(metrics.bingClicks.current)} clicks.",
            "Open Bing and review Queries, Pages, and Crawl.",
            InsightSeverity.Info,
            130,
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
            REVIEW_CLOUDFLARE,
            InsightSeverity.Warning,
            70,
        )
    }

    private fun threatSpike(metrics: InsightMetrics): InsightCard? {
        val state = Trend.classify(metrics.cloudflareThreats, upThreshold = 0.50)
        if (metrics.cloudflareThreats.current < 10 ||
            state !in setOf(TrendState.NewActivity, TrendState.Up)
        ) {
            return null
        }
        return card(
            "Cloudflare is blocking more suspicious traffic",
            "Threat events increased during this period.",
            "Cloudflare threat events changed from ${number(metrics.cloudflareThreats.previous)} to " +
                "${number(metrics.cloudflareThreats.current)}.",
            REVIEW_CLOUDFLARE,
            InsightSeverity.Warning,
            20,
        )
    }

    private fun errorCard(
        why: String,
        critical: Boolean,
    ) = card(
        VISITOR_ERRORS,
        "Cloudflare recorded HTTP error responses during this period.",
        why,
        REVIEW_CLOUDFLARE,
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
    ) = InsightCard(title, message, why, next, severity, priority, target(title))

    private fun target(title: String): InsightTarget =
        when (title) {
            "Some data may be out of date" -> InsightTarget("sources", "", "Sources")
            "A previously useful page is losing search traffic" ->
                InsightTarget(
                    "search",
                    "Pages",
                    "Google Search, Pages",
                )
            "Some sitemap pages may not be indexed", "Indexing data is missing" ->
                InsightTarget(
                    "search",
                    "Indexing",
                    "Google Search, Indexing",
                )
            "Mobile traffic may need attention" -> InsightTarget("performance", "PageSpeed", "Performance, PageSpeed")
            VISITOR_ERRORS ->
                InsightTarget(
                    "cloudflare",
                    "Status codes",
                    "Cloudflare, Status codes",
                )
            "Caching may be less effective" -> InsightTarget("cloudflare", "Cache", "Cloudflare, Cache")
            "Cloudflare is blocking more suspicious traffic" ->
                InsightTarget(
                    "cloudflare",
                    "Security",
                    "Cloudflare, Security",
                )
            "Bing sees the site, but brings no visitors" -> InsightTarget("bing", "Queries", "Bing, Queries")
            else -> InsightTarget("search", "Queries", "Google Search, Queries")
        }

    private fun hasAnyData(metrics: InsightMetrics): Boolean =
        listOf(
            metrics.cloudflareVisitors,
            metrics.googleImpressions,
            metrics.googleClicks,
            metrics.googleMobileClicks,
            metrics.googleMobileImpressions,
            metrics.pageSpeedMobileScore,
            metrics.bingImpressions,
            metrics.bingClicks,
            metrics.cloudflareCacheHitRatio,
            metrics.cloudflareThreats,
        ).any { it.hasCurrent || it.hasPrevious } ||
            metrics.httpStatus.totalRequests > 0 ||
            metrics.indexing.activeSitemapUrls > 0

    private fun hasWindow(metric: MetricWindow) =
        metric.hasCurrent &&
            metric.hasPrevious &&
            metric.hasComparison &&
            (metric.current > 0.0 || metric.previous > 0.0)

    private fun isUp(
        metric: MetricWindow,
        threshold: Double,
    ) = hasWindow(metric) && Trend.classify(metric, upThreshold = threshold) == TrendState.Up

    private fun isDown(
        metric: MetricWindow,
        threshold: Double = 0.10,
    ) = hasWindow(metric) && Trend.classify(metric, downThreshold = threshold) == TrendState.Down

    private fun isFlat(metric: MetricWindow) =
        hasWindow(metric) &&
            Trend.classify(metric) == TrendState.Flat &&
            abs(Trend.changeRatio(metric)) <= FLAT_TOLERANCE

    private fun positiveRatio(metric: MetricWindow) = Trend.formatRatio(Trend.changeRatio(metric)).trimStart('+')

    private fun changedRatio(metric: MetricWindow) =
        if (!metric.hasCurrent || !metric.hasPrevious || !metric.hasComparison) {
            "not available"
        } else if (metric.previous == 0.0 && metric.current > 0.0) {
            "new activity"
        } else {
            Trend.formatRatio(Trend.changeRatio(metric))
        }

    private fun percent(value: Double) = String.format(Locale.ROOT, "%.1f%%", value * 100)

    private fun score(value: Double) =
        if (value <=
            0
        ) {
            "not available"
        } else {
            String.format(Locale.ROOT, "%.0f", value * 100)
        }

    private fun number(value: Double) = String.format(Locale.ROOT, "%.0f", value)
}

private const val VISITOR_ERRORS = "Some visitors may be hitting errors"

private const val REVIEW_SEARCH = "Open Google Search and review Queries, Pages, and Indexing."

private const val REVIEW_CLOUDFLARE = "Open Cloudflare and review Traffic, Cache, or Status Codes."
