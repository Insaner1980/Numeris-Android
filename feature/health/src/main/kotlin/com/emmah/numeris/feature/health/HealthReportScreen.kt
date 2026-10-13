@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.emmah.numeris.core.designsystem.DatedChart
import com.emmah.numeris.core.designsystem.ExpandableRow
import com.emmah.numeris.core.designsystem.KpiGrid
import com.emmah.numeris.core.designsystem.ListCard
import com.emmah.numeris.core.designsystem.ListRow
import com.emmah.numeris.core.designsystem.NoticeCard
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.NumerisTabs
import com.emmah.numeris.core.designsystem.NumerisTopBar
import com.emmah.numeris.core.designsystem.PillTone
import com.emmah.numeris.core.designsystem.RefreshButton
import com.emmah.numeris.core.designsystem.ReportFilters
import com.emmah.numeris.core.designsystem.ReportList
import com.emmah.numeris.core.designsystem.SectionHeader
import com.emmah.numeris.core.designsystem.SiteAvatar
import com.emmah.numeris.core.designsystem.StatusPill
import com.emmah.numeris.core.designsystem.TrailingValue
import com.emmah.numeris.core.designsystem.indexingLabel
import com.emmah.numeris.core.designsystem.indexingTone
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.HealthReport
import com.emmah.numeris.core.model.Metric
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.Probe
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.contains
import java.time.LocalDate

@Composable
fun HealthReportScreen(
    report: HealthReport,
    domain: String,
    domains: List<String>,
    period: Period,
    tab: String,
    syncing: Boolean,
    onDomain: (String) -> Unit,
    onPeriod: (Period) -> Unit,
    onTab: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val probes = report.probes.filter { probeDate(it) in report.range }
    var selectedSite by rememberSaveable { mutableStateOf<String?>(null) }
    ReportList(
        modifier,
        contentPadding = PaddingValues(start = NumerisSpacing.Lg, end = NumerisSpacing.Lg, bottom = NumerisSpacing.Xl),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
                NumerisTopBar("Health", onBack, actions = { RefreshButton(syncing, onRefresh) })
                ReportFilters(domain, domains, period, onDomain, onPeriod)
                NumerisTabs(listOf("Uptime", "Sitemap"), tab, onTab)
            }
        }
        if (tab == "Uptime") {
            uptimeReport(report, probes, domains, selectedSite) { site ->
                selectedSite = site.takeUnless { it == selectedSite }
            }
        } else {
            sitemapReport(report)
        }
    }
}

private fun probeDate(probe: Probe): LocalDate = LocalDate.parse(probe.time.take(ISO_DAY_LENGTH))

private fun Probe.isUp(): Boolean = status.equals("up", true)

private fun LazyListScope.uptimeReport(
    report: HealthReport,
    probes: List<Probe>,
    domains: List<String>,
    selectedSite: String?,
    onSite: (String) -> Unit,
) {
    val sites = report.probes.groupBy { it.domain }
    item {
        KpiGrid(
            listOf(
                Metric("Success rate", ReportMath.uptime(report.probes), "percent", detail = "All stored history"),
                Metric(
                    "Failed checks",
                    probes.count { !it.isUp() }.toDouble(),
                    detail = "In the selected period",
                    higherIsBetter = false,
                ),
            ),
        )
    }
    item { SectionHeader("Sites") }
    if (sites.isEmpty()) {
        item { Text("No cached checks", color = NumerisColors.TertiaryText) }
    } else {
        item {
            ListCard {
                sites.entries.forEachIndexed { index, (site, rows) ->
                    SiteHealthRow(site, rows, domains, index < sites.size - 1) { onSite(site) }
                }
            }
        }
    }
    selectedSite?.let { site -> siteCharts(report, site, probes.filter { it.domain == site }) }
    item { SectionHeader("Incidents") }
    val incidents = probes.filter { !it.isUp() }.sortedByDescending { it.time }
    if (incidents.isEmpty()) {
        item { NoticeCard("No incidents", "Every recorded check in this period succeeded.", PillTone.Positive) }
    }
    items(incidents, key = { "${it.domain}:${it.time}" }) { probe ->
        ExpandableRow(
            probe.domain,
            DisplayFormat.humanize(probe.status),
            listOf(
                "Checked" to DisplayFormat.timestamp(probe.time),
                "HTTP status" to (probe.code?.toString() ?: DisplayFormat.MISSING),
            ),
            supporting = DisplayFormat.timestamp(probe.time),
            tone = PillTone.Negative,
            body = probe.error,
        )
    }
}

@Composable
private fun SiteHealthRow(
    site: String,
    rows: List<Probe>,
    domains: List<String>,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val latest = rows.maxBy { it.time }
    ListRow(
        site,
        supporting =
            listOfNotNull(latest.code?.let { "HTTP $it" }, "checked ${CompactFormat.relative(latest.time)}")
                .joinToString(", ")
                .replaceFirstChar { it.uppercase() } +
                "\nSuccess rate ${DisplayFormat.percent(ReportMath.uptime(rows))}",
        divider = divider,
        onClick = onClick,
        leading = { SiteAvatar(site, domains) },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs),
            ) {
                latest.responseMs?.let { TrailingValue(DisplayFormat.metric(it.toDouble(), "ms")) }
                if (latest.isUp()) StatusPill("Up", PillTone.Positive) else StatusPill("Down", PillTone.Negative)
            }
        },
    )
}

private fun LazyListScope.siteCharts(
    report: HealthReport,
    site: String,
    records: List<Probe>,
) {
    item(key = "response-$site") {
        DatedChart(
            "Response time",
            listOf(
                ReportMath.series(
                    "Average response",
                    "ms",
                    records
                        .filter { it.isUp() && it.responseMs != null }
                        .groupBy(::probeDate)
                        .mapValues { (_, values) -> values.map { it.responseMs!!.toDouble() }.average() },
                    report.range,
                ),
            ),
            supporting = "$site, successful checks",
        )
    }
    item(key = "failed-$site") {
        if (records.none { !it.isUp() }) {
            NoticeCard("No failed checks", "$site responded to every check in this period.", PillTone.Positive)
        } else {
            DatedChart(
                "Failed checks",
                listOf(
                    ReportMath.series(
                        "Failed checks",
                        "count",
                        records
                            .groupBy(::probeDate)
                            .mapValues { (_, values) -> values.count { !it.isUp() }.toDouble() },
                        report.range,
                    ),
                ),
                supporting = site,
            )
        }
    }
}

private fun LazyListScope.sitemapReport(report: HealthReport) {
    val active = report.sitemap.count { it.removedAt == null }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
            KpiGrid(
                listOf(
                    Metric("Active sitemap URLs", active.toDouble()),
                    Metric("Removed URLs", (report.sitemap.size - active).toDouble()),
                ),
            )
            Text(
                "Being listed in a sitemap does not prove that a page is indexed.",
                color = NumerisColors.TertiaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    items(report.sitemap, key = { "${it.domain}:${it.url}" }) { page ->
        val removed = page.removedAt != null
        ExpandableRow(
            page.url,
            if (removed) "Removed" else indexingLabel(page.verdict),
            listOf(
                "Last seen" to DisplayFormat.timestamp(page.lastSeen),
                "Removed" to DisplayFormat.timestamp(page.removedAt),
                "Inspected" to DisplayFormat.timestamp(page.inspectedAt),
                "Coverage" to (page.coverage ?: DisplayFormat.MISSING),
            ),
            tone = if (removed) PillTone.Neutral else indexingTone(page.verdict),
        )
    }
}

private const val ISO_DAY_LENGTH = 10
