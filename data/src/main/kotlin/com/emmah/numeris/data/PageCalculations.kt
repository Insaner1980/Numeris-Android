package com.emmah.numeris.data

import com.emmah.numeris.core.database.SearchPageQueryEntity
import com.emmah.numeris.core.model.DateRange
import com.emmah.numeris.core.model.Metric
import com.emmah.numeris.core.model.MetricWindow
import com.emmah.numeris.core.model.PageQueryWindow
import com.emmah.numeris.core.model.PerformanceReport
import com.emmah.numeris.core.model.PortfolioPage
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.ReportSeries
import com.emmah.numeris.core.model.SearchRow
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.contains

internal fun pageQueries(
    rows: List<SearchPageQueryEntity>,
    domain: String,
    path: String,
): List<PageQueryWindow> {
    val queries = rows.filter { matches(it.siteUrl, domain) && SiteIdentity.pagePath(it.page) == path }
    val latest = queries.maxOfOrNull { it.periodEnd }
    val selected = queries.filter { it.periodEnd == latest }
    val start = selected.maxOfOrNull { it.periodStart }
    return selected
        .filter { it.periodStart == start }
        .map {
            PageQueryWindow(
                domain,
                path,
                it.periodStart,
                it.periodEnd,
                SearchRow(
                    domain,
                    it.query,
                    it.clicks,
                    it.impressions,
                    ReportMath.ratio(it.clicks, it.impressions),
                    it.position,
                ),
            )
        }.sortedWith(compareByDescending<PageQueryWindow> { it.row.clicks }.thenBy { it.row.identity })
        .take(PAGE_QUERY_LIMIT)
}

internal fun pageMetrics(
    history: List<ReportSeries>,
    range: DateRange,
    page: PortfolioPage,
): List<Metric> =
    history.chunked(2).map { lines ->
        val current = lines.first().points.mapNotNull { it.value }
        val prior = lines.last().points.mapNotNull { it.value }
        val complete = current.size == range.days && prior.size == range.days
        val window =
            MetricWindow(current.sum(), prior.sum(), current.isNotEmpty(), prior.isNotEmpty(), complete)
        Metric(
            lines.first().label,
            window.current.takeIf { window.hasCurrent },
            change = ReportMath.change(window),
        )
    } + Metric("Google position", page.position, "decimal")

internal fun matchingPerformance(
    perf: PerformanceReport,
    url: String,
) = PerformanceReport(
    perf.urls.filter { it.url == url },
    perf.field.filter { it.target == url },
    perf.runs.filter { it.url == url },
    perf.audits.filter { it.url == url },
)

private const val PAGE_QUERY_LIMIT = 8
