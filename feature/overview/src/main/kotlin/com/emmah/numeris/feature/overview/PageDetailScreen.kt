@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.overview

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.emmah.numeris.core.designsystem.DatedChart
import com.emmah.numeris.core.designsystem.DetailPairs
import com.emmah.numeris.core.designsystem.ExpandableRow
import com.emmah.numeris.core.designsystem.KpiGrid
import com.emmah.numeris.core.designsystem.LabScores
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.NumerisTopBar
import com.emmah.numeris.core.designsystem.ReportList
import com.emmah.numeris.core.designsystem.ReportSection
import com.emmah.numeris.core.designsystem.StatusPill
import com.emmah.numeris.core.designsystem.indexingLabel
import com.emmah.numeris.core.designsystem.indexingTone
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.PageDetailReport
import com.emmah.numeris.core.designsystem.R as DesignSystemR

@Composable
fun PageDetailScreen(
    report: PageDetailReport,
    onBack: () -> Unit,
    onManageUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val page = report.page
    val url = "https://${page.domain}${page.path}"
    ReportList(
        modifier,
        contentPadding = PaddingValues(start = NumerisSpacing.Lg, end = NumerisSpacing.Lg, bottom = NumerisSpacing.Xl),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
                NumerisTopBar("Page detail", onBack)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(CompactFormat.url(url), style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton({
                        context
                            .getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Page URL", url))
                    }) {
                        Icon(
                            painterResource(DesignSystemR.drawable.ic_numeris_content_copy),
                            contentDescription = "Copy URL",
                            tint = NumerisColors.SecondaryText,
                        )
                    }
                }
                Text(
                    CompactFormat.dateRange(report.range),
                    color = NumerisColors.TertiaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                KpiGrid(report.metrics)
                if (report.coverage.isNotBlank()) {
                    Text(
                        report.coverage,
                        color = NumerisColors.TertiaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        report.series.chunked(2).forEach { lines -> item { DatedChart(lines.first().label, lines) } }
        pageQueries(report)
        item { InspectionSection(report) }
        pageLab(report, url, onManageUrl)
    }
}

@Composable
private fun InspectionSection(report: PageDetailReport) {
    val inspection = report.inspection
    ReportSection(
        "URL Inspection",
        action = { StatusPill(indexingLabel(inspection?.verdict), indexingTone(inspection?.verdict)) },
    ) {
        if (inspection == null) {
            Text("No saved Google URL Inspection result", color = NumerisColors.TertiaryText)
        } else {
            DetailPairs(
                listOf(
                    "Coverage" to (inspection.coverage ?: DisplayFormat.MISSING),
                    "Inspected" to DisplayFormat.timestamp(inspection.inspectedAt),
                ),
            )
        }
    }
}

private fun LazyListScope.pageQueries(report: PageDetailReport) {
    item {
        ReportSection(
            "Top queries",
            supporting =
                report.queries.firstOrNull()?.let { "Latest stored period, ${it.start} – ${it.end}" }
                    ?: "Latest stored page-query period",
        ) {
            Column {
                if (report.queries.isEmpty()) Text("No cached page queries", color = NumerisColors.TertiaryText)
                report.queries.forEach { query ->
                    ExpandableRow(
                        query.row.identity,
                        DisplayFormat.number(query.row.clicks),
                        listOf(
                            "Clicks" to DisplayFormat.number(query.row.clicks),
                            "Impressions" to DisplayFormat.number(query.row.impressions),
                            "CTR" to DisplayFormat.percent(query.row.ctr),
                            "Position" to DisplayFormat.decimal(query.row.position),
                        ),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.pageLab(
    report: PageDetailReport,
    url: String,
    onManageUrl: (String) -> Unit,
) {
    item {
        ReportSection("PageSpeed", supporting = "Latest lab runs for this URL") {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg)) {
                if (report.performance.runs.isEmpty()) {
                    Text("No cached lab runs for this URL", color = NumerisColors.TertiaryText)
                }
                report.performance.runs
                    .distinctBy { it.device }
                    .forEach { LabScores(it) }
                if (report.performance.urls.isEmpty()) {
                    FilledTonalButton({ onManageUrl(url) }) { Text("Add measured URL in Sources") }
                }
            }
        }
    }
}
