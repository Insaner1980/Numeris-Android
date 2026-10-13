@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.emmah.numeris.core.designsystem.AttentionSummary
import com.emmah.numeris.core.designsystem.ChangeText
import com.emmah.numeris.core.designsystem.CompactKpiRow
import com.emmah.numeris.core.designsystem.FreshnessStatus
import com.emmah.numeris.core.designsystem.HeroMetric
import com.emmah.numeris.core.designsystem.ListCard
import com.emmah.numeris.core.designsystem.ListRow
import com.emmah.numeris.core.designsystem.NoticeCard
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisScreenHeader
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.PillTone
import com.emmah.numeris.core.designsystem.ReportFilters
import com.emmah.numeris.core.designsystem.ReportList
import com.emmah.numeris.core.designsystem.SectionHeader
import com.emmah.numeris.core.designsystem.ShareBar
import com.emmah.numeris.core.designsystem.ShowMoreButton
import com.emmah.numeris.core.designsystem.SiteAvatar
import com.emmah.numeris.core.designsystem.TrailingValue
import com.emmah.numeris.core.designsystem.indexingLabel
import com.emmah.numeris.core.designsystem.siteColor
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.InsightCard
import com.emmah.numeris.core.model.InsightSeverity
import com.emmah.numeris.core.model.InsightTarget
import com.emmah.numeris.core.model.Metric
import com.emmah.numeris.core.model.MetricWindow
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.PortfolioPage
import com.emmah.numeris.core.model.PortfolioReport
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.SearchObservation
import com.emmah.numeris.core.model.SiteSummary
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.model.values
import com.emmah.numeris.data.comparisonSeries
import com.emmah.numeris.data.trafficWindow

@Composable
fun OverviewScreen(
    report: PortfolioReport,
    domain: String,
    domains: List<String>,
    period: Period,
    syncing: Boolean,
    onSelectPeriod: (Period) -> Unit,
    onSelectDomain: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSources: () -> Unit,
    onPage: (PortfolioPage) -> Unit,
    onInsight: (InsightTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var attentionOpen by rememberSaveable { mutableStateOf(false) }
    var allPages by rememberSaveable { mutableStateOf(false) }
    ReportList(modifier) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
                NumerisScreenHeader(
                    "Overview",
                    syncing,
                    onRefresh,
                    status = { FreshnessStatus(report.traffic.freshness) },
                )
                ReportFilters(domain, domains, period, onSelectDomain, onSelectPeriod)
            }
        }
        if (!report.hasCache) {
            item {
                NoticeCard(
                    "Build your first dashboard",
                    "Connect a source, then refresh to cache your private reports. " +
                        "Imported reports are available offline.",
                    PillTone.Neutral,
                    actionLabel = "Set up sources",
                    onAction = onOpenSources,
                )
            }
        }
        item { VisitorsHero(report, domains, period) }
        item { SummaryTiles(report) }
        if (report.insights.isNotEmpty()) {
            item {
                AttentionSummary(report.insights.size, attentionOpen, { attentionOpen = !attentionOpen }) {
                    Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                        report.insights.forEach { InsightNotice(it, onInsight) }
                    }
                }
            }
        }
        portfolioSites(report, domain, domains, onSelectDomain)
        portfolioPages(report, domains, allPages, { allPages = !allPages }, onPage)
    }
}

private const val TOP_PAGE_PREVIEW = 5
private const val TOP_PAGE_LIMIT = 10

@Composable
private fun VisitorsHero(
    report: PortfolioReport,
    domains: List<String>,
    period: Period,
) {
    val visitors = trafficWindow(report.traffic.days, report.range, report.traffic.expectedSites) { it.visitors }
    val series =
        comparisonSeries(
            "Visitors",
            report.traffic.days.map { SearchObservation(it.domain, it.date, "daily", "", it.visitors, null) },
            report.range,
        )
    val sites = report.sites.filter { (it.visitors ?: 0.0) > 0 }
    HeroMetric(
        "Visitors, ${period.displayLabel.lowercase()}",
        DisplayFormat.number(visitors.current.takeIf { visitors.hasCurrent }),
        change = ReportMath.change(visitors),
        current = series.values(previous = false),
        previous = series.values(previous = true),
        footer =
            if (sites.size > 1) {
                { ShareBar(sites.map { siteColor(it.domain, domains) to (it.visitors ?: 0.0) }) }
            } else {
                null
            },
    )
}

@Composable
private fun SummaryTiles(report: PortfolioReport) {
    val google = ReportMath.window(report.google.daily, report.range, report.google.expectedSites) { it.clicks }
    val bing = ReportMath.window(report.bing.daily, report.range, report.bing.expectedSites) { it.clicks }
    val indexing = ReportMath.indexing(report.health.sitemap)
    val toCheck = indexing.inspectedUrls - indexing.indexedUrls
    val down =
        report.health.probes
            .groupBy { it.domain }
            .values
            .count { rows -> !rows.maxBy { it.time }.status.equals("up", true) }
    CompactKpiRow(
        listOf(
            Metric("Search clicks", searchClicks(google, bing)),
            Metric(
                "Indexed",
                ReportMath.ratio(indexing.indexedUrls, indexing.inspectedUrls),
                "percent",
            ),
            Metric("Uptime", ReportMath.uptime(report.health.probes), "percent"),
        ),
        tones =
            listOf(
                ("Google ${DisplayFormat.number(google.current.takeIf { google.hasCurrent })}" to PillTone.Neutral),
                if (toCheck > 0) "$toCheck to check" to PillTone.Warning else "All indexed" to PillTone.Positive,
                if (down > 0) "$down down" to PillTone.Negative else "All up" to PillTone.Positive,
            ),
    )
}

private fun searchClicks(
    google: MetricWindow,
    bing: MetricWindow,
): Double? =
    if (google.hasCurrent || bing.hasCurrent) {
        (if (google.hasCurrent) google.current else 0.0) + (if (bing.hasCurrent) bing.current else 0.0)
    } else {
        null
    }

private fun InsightSeverity.tone(): PillTone =
    when (this) {
        InsightSeverity.Critical -> PillTone.Negative
        InsightSeverity.Warning -> PillTone.Warning
        InsightSeverity.Info -> PillTone.Neutral
    }

@Composable
private fun InsightNotice(
    insight: InsightCard,
    onInsight: (InsightTarget) -> Unit,
) {
    NoticeCard(
        insight.title,
        "${insight.message} ${insight.whyShown}",
        insight.severity.tone(),
        actionLabel = "Open ${insight.target.label}",
        onAction = { onInsight(insight.target) },
    )
}

private fun LazyListScope.portfolioSites(
    report: PortfolioReport,
    domain: String,
    domains: List<String>,
    onSelectDomain: (String) -> Unit,
) {
    if (report.sites.isEmpty()) return
    item { SectionHeader("Your sites") }
    item {
        ListCard {
            report.sites.forEachIndexed { index, site ->
                SiteRow(site, domains, site.domain == domain, index < report.sites.lastIndex) {
                    onSelectDomain(if (domain == site.domain) "all" else site.domain)
                }
            }
        }
    }
}

@Composable
private fun SiteRow(
    site: SiteSummary,
    domains: List<String>,
    selected: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
) {
    ListRow(
        site.domain,
        supporting = if (selected) "Showing only this site" else null,
        divider = divider,
        onClick = onClick,
        leading = { SiteAvatar(site.domain, domains) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                TrailingValue(DisplayFormat.number(site.visitors), caption = "visitors")
                site.change?.let { ChangeText(it) }
            }
        },
    )
}

private fun LazyListScope.portfolioPages(
    report: PortfolioReport,
    domains: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPage: (PortfolioPage) -> Unit,
) {
    item { SectionHeader("Top pages") }
    val pages = report.pages.take(TOP_PAGE_LIMIT)
    if (pages.isEmpty()) {
        item { Text("No cached page reports", color = NumerisColors.TertiaryText) }
        return
    }
    val visible = if (expanded) pages else pages.take(TOP_PAGE_PREVIEW)
    item {
        ListCard {
            visible.forEachIndexed { index, page ->
                ListRow(
                    page.path.ifBlank { "/" },
                    supporting = pageSupporting(page),
                    divider = index < visible.lastIndex,
                    onClick = { onPage(page) },
                    leading = { SiteAvatar(page.domain, domains) },
                    trailing = { TrailingValue(DisplayFormat.number(page.pageViews), caption = "views") },
                )
            }
            if (pages.size > TOP_PAGE_PREVIEW) ShowMoreButton(expanded, pages.size, onToggle)
        }
    }
}

/** Vain toteutuneet hakuklikkaukset ja indeksointiongelma; tyhjät arvot jätetään pois. */
private fun pageSupporting(page: PortfolioPage): String? =
    listOfNotNull(
        page.googleClicks?.takeIf { it > 0 }?.let { "Google ${DisplayFormat.number(it)} clicks" },
        page.bingClicks?.takeIf { it > 0 }?.let { "Bing ${DisplayFormat.number(it)} clicks" },
        indexingLabel(page.indexed).takeIf { page.indexed == "FAIL" },
    ).joinToString(", ").ifEmpty { null }
