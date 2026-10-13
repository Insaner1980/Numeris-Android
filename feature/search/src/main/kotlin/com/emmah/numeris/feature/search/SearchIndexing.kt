@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.numeris.core.designsystem.ExpandableRow
import com.emmah.numeris.core.designsystem.InlineTextButton
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.ReportSection
import com.emmah.numeris.core.designsystem.SectionHeader
import com.emmah.numeris.core.designsystem.ShareBar
import com.emmah.numeris.core.designsystem.ShareLegend
import com.emmah.numeris.core.designsystem.indexingLabel
import com.emmah.numeris.core.designsystem.indexingTone
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.SearchReport
import com.emmah.numeris.core.model.SitemapPage
import com.emmah.numeris.core.model.contains

@Composable
fun InspectionRow(
    row: SitemapPage,
    modifier: Modifier = Modifier,
) {
    ExpandableRow(
        row.url,
        indexingLabel(row.verdict),
        listOf(
            "Coverage" to (row.coverage ?: DisplayFormat.MISSING),
            "Inspected" to DisplayFormat.timestamp(row.inspectedAt),
            "Last seen" to DisplayFormat.timestamp(row.lastSeen),
            "Indexing" to (row.indexing ?: DisplayFormat.MISSING),
            "Robots" to (row.robots ?: DisplayFormat.MISSING),
            "Fetch" to (row.fetch ?: DisplayFormat.MISSING),
            "Crawled as" to (row.crawledAs ?: DisplayFormat.MISSING),
            "Last crawl" to DisplayFormat.timestamp(row.lastCrawl),
        ),
        modifier = modifier,
        tone = indexingTone(row.verdict),
    )
}

internal fun LazyListScope.searchIndexing(
    report: SearchReport,
    showIndexed: Boolean,
    onToggleIndexed: () -> Unit,
) {
    val active = report.sitemap.filter { it.removedAt == null }
    val indexing = ReportMath.indexing(report.sitemap)
    val notIndexed = indexing.inspectedUrls - indexing.indexedUrls
    val notInspected = indexing.activeSitemapUrls - indexing.inspectedUrls
    item {
        ReportSection(
            "${DisplayFormat.percent(ReportMath.ratio(indexing.indexedUrls, indexing.inspectedUrls))} indexed",
            supporting = "${indexing.indexedUrls} of ${indexing.inspectedUrls} inspected sitemap URLs",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                ShareBar(
                    listOf(
                        NumerisColors.Positive to indexing.indexedUrls.toDouble(),
                        NumerisColors.Warning to notIndexed.toDouble(),
                        NumerisColors.PreviousSeries to notInspected.toDouble(),
                    ),
                )
                ShareLegend(
                    listOf(
                        Triple(NumerisColors.Positive, "Indexed", indexing.indexedUrls),
                        Triple(NumerisColors.Warning, "Not indexed", notIndexed),
                        Triple(NumerisColors.PreviousSeries, "Not inspected yet", notInspected),
                    ).filter { it.third > 0 }
                        .map { (color, label, count) -> Triple(color, label, DisplayFormat.number(count)) },
                )
            }
        }
    }
    val visible = if (showIndexed) active else active.filter { it.verdict != "PASS" }
    item {
        SectionHeader(
            if (showIndexed) "All sitemap URLs" else "URLs to check",
            supporting = "${visible.size} URLs",
            action = {
                InlineTextButton(if (showIndexed) "Hide indexed" else "Show indexed too", onToggleIndexed)
            },
        )
    }
    items(visible, key = { "${it.domain}:${it.url}" }) { InspectionRow(it) }
}
