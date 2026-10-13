@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.numeris.core.designsystem.CompactKpiRow
import com.emmah.numeris.core.designsystem.DatedChart
import com.emmah.numeris.core.designsystem.DetailPairs
import com.emmah.numeris.core.designsystem.ExpandableRow
import com.emmah.numeris.core.designsystem.FreshnessStatus
import com.emmah.numeris.core.designsystem.HeroMetric
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisDropdownSelector
import com.emmah.numeris.core.designsystem.NumerisScreenHeader
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.NumerisTabs
import com.emmah.numeris.core.designsystem.ReportFilters
import com.emmah.numeris.core.designsystem.ReportList
import com.emmah.numeris.core.designsystem.ReportSection
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.DisplayLabels
import com.emmah.numeris.core.model.Metric
import com.emmah.numeris.core.model.MetricWindow
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.SearchReport
import com.emmah.numeris.core.model.SearchRow
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.model.values
import com.emmah.numeris.data.comparisonSeries

@Composable
fun SearchReportScreen(
    report: SearchReport,
    domain: String,
    domains: List<String>,
    period: Period,
    provider: String,
    tab: String,
    syncing: Boolean,
    onDomain: (String) -> Unit,
    onPeriod: (Period) -> Unit,
    onProvider: (String) -> Unit,
    onTab: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable(provider, tab) { mutableStateOf("") }
    var sort by rememberSaveable(provider, tab) { mutableStateOf("Clicks") }
    var showIndexed by rememberSaveable { mutableStateOf(false) }
    val views =
        if (provider ==
            "Google"
        ) {
            listOf("Overview", "Queries", "Pages", "Devices", "Indexing")
        } else {
            listOf("Overview", "Queries", "Pages", "Crawl")
        }
    val kind = if (tab == "Pages") "page" else "query"
    val rows = remember(report, kind, filter, sort) { filteredRows(report, kind, filter, sort) }
    ReportList(modifier) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
                NumerisScreenHeader(
                    "Search",
                    syncing,
                    onRefresh,
                    status = { FreshnessStatus(report.freshness, source = provider) },
                )
                ReportFilters(domain, domains, period, onDomain, onPeriod) {
                    NumerisDropdownSelector(provider, listOf("Google", "Bing"), onProvider, { it })
                }
                NumerisTabs(views, tab, onTab)
            }
        }
        when (tab) {
            "Overview" -> searchOverview(report, period)
            "Queries", "Pages" ->
                searchRows(report, provider, tab, rows, kind, filter, sort, { filter = it }, {
                    sort =
                        it
                })
            "Devices" -> searchDevices(report)
            "Indexing" -> searchIndexing(report, showIndexed) { showIndexed = !showIndexed }
            else -> bingCrawl(report)
        }
    }
}

private fun filteredRows(
    report: SearchReport,
    kind: String,
    filter: String,
    sort: String,
): List<SearchRow> {
    val values = report.rows(kind).filter { it.identity.contains(filter, true) }
    return when (sort) {
        "Impressions" -> values.sortedWith(compareByDescending<SearchRow> { it.impressions }.thenBy { it.key })
        "CTR" -> values.sortedWith(compareByDescending<SearchRow> { it.ctr }.thenBy { it.key })
        "Position" -> values.sortedWith(compareBy<SearchRow> { it.position ?: Double.MAX_VALUE }.thenBy { it.key })
        else -> values
    }
}

internal fun searchDetails(row: SearchRow): List<Pair<String, String>> =
    listOf(
        "Clicks" to DisplayFormat.number(row.clicks),
        "Impressions" to DisplayFormat.number(row.impressions),
        "CTR" to DisplayFormat.percent(row.ctr),
        "Position" to DisplayFormat.decimal(row.position),
        "Previous clicks" to DisplayFormat.number(row.previousClicks),
    )

@Composable
fun SearchDataRow(
    row: SearchRow,
    modifier: Modifier = Modifier,
) {
    ExpandableRow(
        row.identity,
        DisplayFormat.number(row.clicks),
        searchDetails(row),
        modifier,
        supporting = row.domain,
        valueCaption = "clicks",
    )
}

private fun LazyListScope.searchOverview(
    report: SearchReport,
    period: Period,
) {
    val clicks = ReportMath.window(report.daily, report.range, report.expectedSites) { it.clicks }
    val impressions = ReportMath.window(report.daily, report.range, report.expectedSites) { it.impressions }
    val summary = ReportMath.searchRows(report.daily.map { it.copy(identity = "total") }, report.range, false)
    val weighted = summary.filter { it.position != null && (it.impressions ?: 0) > 0 }
    val coveredDays =
        report.daily
            .filter { it.date in report.range }
            .map { it.date }
            .distinct()
            .size
    item {
        val series = comparisonSeries("Clicks", report.daily, report.range)
        HeroMetric(
            "Clicks",
            DisplayFormat.number(clicks.current.takeIf { clicks.hasCurrent }),
            change = ReportMath.change(clicks),
            current = series.values(previous = false),
            previous = series.values(previous = true),
        )
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
            CompactKpiRow(secondaryMetrics(clicks, impressions, weighted))

            Text(
                "Data for $coveredDays of ${period.days} days. Search data can arrive a few days late.",
                color = NumerisColors.TertiaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    item {
        DatedChart(
            "Impressions per day",
            comparisonSeries("Impressions", report.daily.map { it.copy(clicks = it.impressions) }, report.range),
        )
    }
}

/** Klikkien rinnalla näytettävät luvut: näyttökerrat, klikkiprosentti ja näyttökerroilla painotettu sijoitus. */
private fun secondaryMetrics(
    clicks: MetricWindow,
    impressions: MetricWindow,
    weighted: List<SearchRow>,
): List<Metric> =
    listOf(
        Metric(
            "Impressions",
            impressions.current.takeIf { impressions.hasCurrent },
            change = ReportMath.change(impressions),
        ),
        Metric(
            "CTR",
            ReportMath
                .ratio(clicks.current, impressions.current)
                .takeIf { clicks.hasCurrent && impressions.hasCurrent },
            "percent",
        ),
        Metric(
            "Avg position",
            weighted.takeIf { it.isNotEmpty() }?.let { list ->
                list.sumOf { it.position!! * it.impressions!! } / list.sumOf { it.impressions!! }
            },
            "decimal",
            higherIsBetter = false,
        ),
    )

private fun LazyListScope.searchRows(
    report: SearchReport,
    provider: String,
    tab: String,
    rows: List<SearchRow>,
    kind: String,
    filter: String,
    sort: String,
    onFilter: (String) -> Unit,
    onSort: (String) -> Unit,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
            OutlinedTextField(
                filter,
                onFilter,
                Modifier.fillMaxWidth(),
                label = { Text("Filter ${tab.lowercase()}") },
                singleLine = true,
            )
            NumerisDropdownSelector(
                sort,
                listOf("Clicks", "Impressions", "CTR") + if (tab == "Queries") listOf("Position") else emptyList(),
                onSort,
                { "Sort by $it" },
            )
            Text(
                "${rows.size} matching ${tab.lowercase()}",
                color = NumerisColors.TertiaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    items(rows, key = { it.key }) { SearchDataRow(it) }
    if (rows.isEmpty()) item { Text("No matching cached ${tab.lowercase()}", color = NumerisColors.TertiaryText) }
    legacySnapshots(report, kind)
    if (provider == "Google") searchDiscoveries(report, tab, kind)
}

private fun LazyListScope.searchDevices(report: SearchReport) {
    val devices = ReportMath.searchRows(report.devices, report.range, false)
    val totalClicks = devices.mapNotNull { it.clicks }.sum()
    item {
        ReportSection("Device shares") {
            Column {
                if (devices.isEmpty()) Text("No data for this period", color = NumerisColors.TertiaryText)
                devices.forEach { row ->
                    ExpandableRow(
                        DisplayLabels.device(row.identity),
                        DisplayFormat.percent(ReportMath.ratio(row.clicks, totalClicks)),
                        searchDetails(row),
                        supporting = row.domain,
                        valueCaption = "of clicks",
                    )
                }
            }
        }
    }
    devices.map { it.identity }.distinct().forEach { device ->
        item {
            DatedChart(
                "${DisplayLabels.device(device)} clicks",
                comparisonSeries(
                    DisplayLabels.device(device),
                    report.devices.filter { it.identity == device },
                    report.range,
                ),
            )
        }
    }
}

private fun LazyListScope.bingCrawl(report: SearchReport) {
    item {
        ReportSection("Stored Bing crawl reports") {
            if (report.crawl.isEmpty()) {
                Text("No cached crawl reports", color = NumerisColors.TertiaryText)
            } else {
                DetailPairs(
                    report.crawl.groupBy { it.method }.map { (method, rows) ->
                        crawlLabel(method) to
                            "${rows.size} records, updated ${DisplayFormat.timestamp(rows.maxOf { it.time })}"
                    },
                )
            }
        }
    }
    items(report.crawl, key = { "${it.domain}:${it.method}:${it.key}" }) { row ->
        ExpandableRow(
            crawlLabel(row.method),
            DisplayFormat.timestamp(row.time),
            listOf("Summary" to row.summary),
            supporting = row.domain,
            body = row.detail,
        )
    }
}

private fun crawlLabel(method: String): String = DisplayFormat.humanize(method.removePrefix("Get"))
