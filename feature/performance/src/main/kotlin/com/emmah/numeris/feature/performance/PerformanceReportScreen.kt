@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.numeris.core.designsystem.DatedChart
import com.emmah.numeris.core.designsystem.ExpandableRow
import com.emmah.numeris.core.designsystem.LabScores
import com.emmah.numeris.core.designsystem.ListCard
import com.emmah.numeris.core.designsystem.ListRow
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisDropdownSelector
import com.emmah.numeris.core.designsystem.NumerisScreenHeader
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.NumerisTabs
import com.emmah.numeris.core.designsystem.PillTone
import com.emmah.numeris.core.designsystem.ReportFilters
import com.emmah.numeris.core.designsystem.ReportList
import com.emmah.numeris.core.designsystem.ReportSection
import com.emmah.numeris.core.designsystem.SectionHeader
import com.emmah.numeris.core.designsystem.StatusPill
import com.emmah.numeris.core.designsystem.labScoreTone
import com.emmah.numeris.core.designsystem.tone
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.DateRange
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.DisplayLabels
import com.emmah.numeris.core.model.FieldMetric
import com.emmah.numeris.core.model.LabRun
import com.emmah.numeris.core.model.PerformanceReport
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.contains
import java.time.LocalDate

@Composable
fun PerformanceReportScreen(
    report: PerformanceReport,
    domain: String,
    domains: List<String>,
    period: Period,
    tab: String,
    syncing: Boolean,
    onDomain: (String) -> Unit,
    onPeriod: (Period) -> Unit,
    onTab: (String) -> Unit,
    onRefresh: () -> Unit,
    onManageUrls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var target by rememberSaveable(tab, domain) { mutableStateOf("") }
    var device by rememberSaveable(tab) { mutableStateOf(if (tab == "CrUX") "PHONE" else "mobile") }
    var metric by rememberSaveable { mutableStateOf("largest_contentful_paint") }
    val targets =
        if (tab == "CrUX") {
            report.field
                .map { it.target }
                .distinct()
                .sorted()
        } else {
            report.runs
                .map { it.url }
                .distinct()
                .sorted()
        }
    val selectedTarget = target.takeIf { it in targets } ?: targets.firstOrNull().orEmpty()
    val range = period.toDateRange()
    val selectedField = report.field.filter { it.target == selectedTarget && it.device == device }
    val runs = report.runs.filter { it.url == selectedTarget && it.device == device }.sortedByDescending { it.time }
    ReportList(modifier) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
                NumerisScreenHeader("Vitals", syncing, onRefresh)
                ReportFilters(domain, domains, period, onDomain, onPeriod)
                NumerisTabs(listOf("Overview", "CrUX", "PageSpeed", "URLs"), tab, onTab)
            }
        }
        if (tab == "CrUX" || tab == "PageSpeed") {
            item {
                PerformanceFilters(
                    tab,
                    selectedTarget,
                    targets,
                    device,
                    metric,
                    report,
                    { target = it },
                    { device = it },
                    { metric = it },
                )
            }
        }
        when (tab) {
            "Overview" -> performanceOverview(report, domains)
            "CrUX" -> cruxReport(selectedField, metric, range)
            "PageSpeed" -> labReport(report, selectedTarget, device, runs, range)
            else -> measuredUrls(report, onManageUrls)
        }
    }
}

internal fun metricUnit(metric: String): String = if (metric == "cumulative_layout_shift") "decimal" else "ms"

private fun LazyListScope.performanceOverview(
    report: PerformanceReport,
    domains: List<String>,
) {
    val sites = siteVitals(report.field, report.runs)
    item { SectionHeader("Your sites") }
    if (sites.isEmpty()) {
        item { Text("No cached performance data", color = NumerisColors.TertiaryText) }
    }
    sites.forEach { site -> item(key = "site-${site.domain}") { SiteVitalsCard(site, domains) } }
}

private fun LazyListScope.cruxReport(
    selectedField: List<FieldMetric>,
    metric: String,
    range: DateRange,
) {
    val points = selectedField.filter { it.metric == metric && it.end in range }.sortedBy { it.end }
    item {
        DatedChart(
            "${DisplayLabels.metricName(metric)} p75",
            listOf(
                ReportMath.series(
                    DisplayLabels.metricLabel(metric),
                    metricUnit(metric),
                    points.mapNotNull { point -> point.p75?.let { point.end to it } }.toMap(),
                    range,
                ),
            ),
        )
    }
    items(points, key = { "${it.targetType}:${it.target}:${it.device}:${it.end}:${it.metric}" }) { point ->
        val rating = point.p75?.let { ReportMath.vitalRating(point.metric, it) }
        ExpandableRow(
            CompactFormat.dateRange(point.start, point.end),
            DisplayFormat.metric(point.p75, metricUnit(metric)),
            listOf(
                "Good" to DisplayFormat.percent(point.good),
                "Needs work" to DisplayFormat.percent(point.needsWork),
                "Poor" to DisplayFormat.percent(point.poor),
            ),
            supporting = "${DisplayFormat.humanize(point.targetType)}: ${CompactFormat.url(point.target)}",
            tone = rating?.tone(),
        )
    }
}

private fun LazyListScope.labReport(
    report: PerformanceReport,
    selectedTarget: String,
    device: String,
    runs: List<LabRun>,
    range: DateRange,
) {
    val latest = runs.firstOrNull()
    item {
        ReportSection("Latest lab run") {
            if (latest == null) {
                Text("No cached run for this URL and device", color = NumerisColors.TertiaryText)
            } else {
                LabScores(latest)
            }
        }
    }
    item {
        DatedChart(
            "Performance score history",
            listOf(
                ReportMath.series(
                    "Performance score",
                    "score",
                    runs
                        .filter { LocalDate.parse(it.time.take(ISO_DAY_LENGTH)) in range }
                        .groupBy { LocalDate.parse(it.time.take(ISO_DAY_LENGTH)) }
                        .mapNotNull { (date, values) -> values.maxBy { it.time }.performance?.let { date to it } }
                        .toMap(),
                    range,
                ),
            ),
            supporting = "${DisplayLabels.device(device)}, ${CompactFormat.url(selectedTarget)}",
        )
    }
    val audits =
        report.audits.filter {
            it.url == selectedTarget &&
                it.device == device &&
                it.time == latest?.time &&
                (it.score ?: 0.0) < AUDIT_ISSUE_THRESHOLD
        }
    item { SectionHeader("Opportunities and diagnostics", supporting = "From the latest selected run") }
    items(audits, key = { "${it.url}:${it.device}:${it.time}:${it.id}" }) { audit ->
        ExpandableRow(
            audit.title,
            audit.displayValue?.takeIf { it.isNotBlank() } ?: "Score ${DisplayFormat.score(audit.score)}",
            listOf("Score" to DisplayFormat.score(audit.score)),
            tone = labScoreTone(audit.score),
            body = audit.details ?: "No additional cached detail",
        )
    }
    if (audits.isEmpty()) {
        item { Text("No cached opportunities for the selected run", color = NumerisColors.TertiaryText) }
    }
}

private fun LazyListScope.measuredUrls(
    report: PerformanceReport,
    onManageUrls: () -> Unit,
) {
    item { FilledTonalButton(onManageUrls) { Text("Manage measured URLs in Sources") } }
    if (report.urls.isEmpty()) {
        item { Text("No measured URLs configured", color = NumerisColors.TertiaryText) }
        return
    }
    item {
        ListCard {
            report.urls.forEachIndexed { index, url ->
                ListRow(
                    CompactFormat.url(url.url),
                    supporting = "Added from ${url.source}",
                    divider = index < report.urls.lastIndex,
                    trailing = {
                        StatusPill(
                            if (url.enabled) "Measured" else "Paused",
                            if (url.enabled) PillTone.Positive else PillTone.Neutral,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PerformanceFilters(
    tab: String,
    selectedTarget: String,
    targets: List<String>,
    device: String,
    metric: String,
    report: PerformanceReport,
    onTarget: (String) -> Unit,
    onDevice: (String) -> Unit,
    onMetric: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
        NumerisDropdownSelector(
            selectedTarget,
            targets,
            onTarget,
            { it.ifEmpty { "No cached targets" }.let(CompactFormat::url) },
        )
        NumerisDropdownSelector(
            device,
            if (tab == "CrUX") listOf("PHONE", "DESKTOP", "TABLET", "ALL") else listOf("mobile", "desktop"),
            onDevice,
            DisplayLabels::device,
        )
        if (tab == "CrUX") {
            NumerisDropdownSelector(
                metric,
                report.field
                    .map { it.metric }
                    .distinct()
                    .sorted(),
                onMetric,
                DisplayLabels::metricLabel,
            )
        }
    }
}

private const val ISO_DAY_LENGTH = 10
private const val AUDIT_ISSUE_THRESHOLD = 0.9
