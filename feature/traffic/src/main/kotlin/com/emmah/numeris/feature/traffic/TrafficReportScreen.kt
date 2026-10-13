@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.traffic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.emmah.numeris.core.designsystem.BreakdownRows
import com.emmah.numeris.core.designsystem.CompactKpiRow
import com.emmah.numeris.core.designsystem.FreshnessStatus
import com.emmah.numeris.core.designsystem.HeroMetric
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisScreenHeader
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.NumerisTabs
import com.emmah.numeris.core.designsystem.ReportFilters
import com.emmah.numeris.core.designsystem.ReportList
import com.emmah.numeris.core.designsystem.ReportSection
import com.emmah.numeris.core.designsystem.SectionHeader
import com.emmah.numeris.core.model.Breakdown
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.Metric
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.ReportSeries
import com.emmah.numeris.core.model.SearchObservation
import com.emmah.numeris.core.model.TrafficDay
import com.emmah.numeris.core.model.TrafficReport
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.model.values
import com.emmah.numeris.data.cacheWindow
import com.emmah.numeris.data.comparisonSeries
import com.emmah.numeris.data.trafficWindow

val trafficViews = listOf("Traffic", "Web Analytics")

@Composable
fun TrafficReportScreen(
    report: TrafficReport,
    domain: String,
    domains: List<String>,
    period: Period,
    tab: String,
    syncing: Boolean,
    onDomain: (String) -> Unit,
    onPeriod: (Period) -> Unit,
    onTab: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = tab.takeIf { it in trafficViews } ?: trafficViews.first()
    ReportList(modifier) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
                NumerisScreenHeader(
                    "Traffic",
                    syncing,
                    onRefresh,
                    status = { FreshnessStatus(report.freshness) },
                )
                ReportFilters(domain, domains, period, onDomain, onPeriod)
                NumerisTabs(trafficViews, selected, onTab)
            }
        }
        if (selected == "Web Analytics") webAnalyticsReport(report) else trafficOverview(report, period)
    }
}

/** Cloudflaren omat palvelupolut (esim. /cdn-cgi/trace) eivät ole sivuston sisältöä. */
private fun List<Breakdown>.contentPages() = filterNot { it.label.startsWith(CLOUDFLARE_INTERNAL_PATH) }

private fun TrafficReport.metric(
    label: String,
    unit: String = "count",
    higherIsBetter: Boolean = true,
    value: (TrafficDay) -> Number,
): Metric {
    val window = trafficWindow(days, range, expectedSites, value = value)
    return Metric(
        label,
        window.current.takeIf { window.hasCurrent },
        unit,
        ReportMath.change(window),
        higherIsBetter = higherIsBetter,
    )
}

private fun TrafficReport.visitorSeries(): List<ReportSeries> =
    comparisonSeries(
        "Visitors",
        days.map { SearchObservation(it.domain, it.date, "daily", "", it.visitors, null) },
        range,
    )

private fun LazyListScope.trafficOverview(
    report: TrafficReport,
    period: Period,
) {
    val days = report.days.filter { it.date in report.range }
    val coveredDays = days.map { it.date }.distinct().size
    item { TrafficHero(report, days) }
    if (coveredDays < period.days) {
        item {
            Text(
                "Data for $coveredDays of ${period.days} days. Changes need complete data for every site.",
                color = NumerisColors.TertiaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    item { ResponsesSection(report) }
    item { ReportSection("Top countries") { BreakdownRows(report.countries, "visitors") } }
    item {
        ReportSection("Top pages") {
            BreakdownRows(report.pages.contentPages(), "requests")
        }
    }
    item { CacheAndSecurity(report) }
}

@Composable
private fun TrafficHero(
    report: TrafficReport,
    days: List<TrafficDay>,
) {
    val visitors = report.metric("Visitors") { it.visitors }
    val series = report.visitorSeries()
    Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
        HeroMetric(
            "Visitors",
            DisplayFormat.metric(visitors.value, visitors.unit),
            change = visitors.change,
            current = series.values(previous = false),
            previous = series.values(previous = true),
        )
        CompactKpiRow(
            listOf(
                report.metric("Page views") { it.pageViews },
                Metric(
                    "Pages per visit",
                    ReportMath.ratio(days.sumOf { it.pageViews }, days.sumOf { it.visitors }),
                    "decimal",
                ),
                report.metric("Requests") { it.requests },
            ),
        )
    }
}

@Composable
private fun CacheAndSecurity(report: TrafficReport) {
    val cache = cacheWindow(report.days, report.range, report.expectedSites)
    Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
        SectionHeader("Cache and security")
        CompactKpiRow(
            listOf(
                Metric("Cache hits", cache.current.takeIf { cache.hasCurrent }, "percent", ReportMath.change(cache)),
                report.metric("Data saved", "bytes") { it.cachedBytes },
                report.metric("Threats", higherIsBetter = false) { it.threats },
            ),
        )
    }
}

private fun LazyListScope.webAnalyticsReport(report: TrafficReport) {
    val visits = ReportMath.window(report.webVisits, report.range, report.expectedWebSites) { it.clicks }
    val views = ReportMath.window(report.webVisits, report.range, report.expectedWebSites) { it.impressions }
    item {
        val series = comparisonSeries("Visits", report.webVisits, report.range)
        HeroMetric(
            "Visits",
            DisplayFormat.number(visits.current.takeIf { visits.hasCurrent }),
            change = ReportMath.change(visits),
            current = series.values(previous = false),
            previous = series.values(previous = true),
        )
    }
    item {
        CompactKpiRow(
            listOf(
                Metric(
                    "Page views",
                    views.current.takeIf { views.hasCurrent },
                    change = ReportMath.change(views),
                ),
            ),
        )
    }
    item { ReportSection("Top referrers") { BreakdownRows(report.referrers, "visits") } }
    item { ReportSection("Top pages") { BreakdownRows(report.webPages, "page views") } }
    item { ReportSection("Top countries") { BreakdownRows(report.webCountries, "visits") } }
}

private const val CLOUDFLARE_INTERNAL_PATH = "/cdn-cgi/"
