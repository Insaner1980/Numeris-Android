@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import com.emmah.numeris.core.designsystem.ExpandableRow
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.ReportSection
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.SearchReport

internal fun LazyListScope.legacySnapshots(
    report: SearchReport,
    kind: String,
) {
    val legacy = report.observations.filter { it.kind == "legacy_$kind" }
    if (legacy.isNotEmpty()) {
        item {
            ReportSection(
                "Legacy stored snapshots",
                supporting =
                    "These records predate daily query and page storage. Their period is unavailable, so they " +
                        "are excluded from daily totals and comparisons.",
            ) {
                Column {
                    legacy.groupBy { it.domain }.forEach { (_, values) ->
                        val latest = values.maxOf { it.date }
                        values.filter { it.date == latest }.forEach { row ->
                            ExpandableRow(
                                row.identity,
                                DisplayFormat.number(row.clicks),
                                listOf(
                                    "Stored" to DisplayFormat.date(latest),
                                    "Impressions" to DisplayFormat.number(row.impressions),
                                ),
                                supporting = row.domain,
                                valueCaption = "clicks",
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.searchDiscoveries(
    report: SearchReport,
    tab: String,
    kind: String,
) {
    item {
        ReportSection(
            if (tab == "Queries") "New queries" else "Declining pages",
            supporting = "Compared with the previous period",
        ) {
            Column {
                if (!report.comparisonReady) {
                    Text("A complete current and previous period is required.", color = NumerisColors.TertiaryText)
                } else {
                    val discoveries =
                        report.rows(kind).filter { row ->
                            if (tab == "Queries") {
                                row.previousClicks == 0L && (row.clicks ?: 0) > 0
                            } else {
                                (row.previousClicks ?: 0) > (row.clicks ?: 0)
                            }
                        }
                    if (discoveries.isEmpty()) {
                        Text(
                            "No ${if (tab == "Queries") "new queries" else "declining pages"} in the stored comparison",
                            color = NumerisColors.TertiaryText,
                        )
                    }
                    discoveries.take(DISCOVERY_LIMIT).forEach { SearchDataRow(it) }
                }
            }
        }
    }
}

private const val DISCOVERY_LIMIT = 10
