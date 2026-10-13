@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.traffic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.emmah.numeris.core.designsystem.ExpandableRow
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.ReportSection
import com.emmah.numeris.core.designsystem.ShareBar
import com.emmah.numeris.core.designsystem.ShareLegend
import com.emmah.numeris.core.designsystem.ShowMoreButton
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.TrafficReport

private val StatusMeanings =
    mapOf(
        200 to "OK",
        204 to "No content",
        206 to "Partial content",
        301 to "Moved permanently",
        302 to "Found",
        304 to "Not modified",
        307 to "Temporary redirect",
        308 to "Permanent redirect",
        400 to "Bad request",
        401 to "Unauthorized",
        403 to "Forbidden",
        404 to "Not found",
        405 to "Method not allowed",
        429 to "Too many requests",
        499 to "Visitor closed the connection",
        500 to "Internal server error",
        502 to "Bad gateway",
        503 to "Service unavailable",
        504 to "Gateway timeout",
        520 to "Unknown origin error",
        521 to "Origin server is down",
        522 to "Origin connection timed out",
        524 to "Origin response timed out",
    )

private fun statusMeaning(code: Int): String = StatusMeanings[code] ?: "Other HTTP status"

/** Vastausryhmä, sen väri ja selkokielinen nimi. */
private data class ResponseGroup(
    val label: String,
    val range: IntRange,
    val color: Color,
)

private val responseGroups =
    listOf(
        ResponseGroup("OK", 200..299, NumerisColors.Positive),
        ResponseGroup("Redirects", 300..399, NumerisColors.Copper),
        ResponseGroup("Not found and other client errors", 400..499, NumerisColors.Warning),
        ResponseGroup("Server errors", 500..599, NumerisColors.Negative),
    )

/** Vastaukset yhtenä värijakopalkkina. Yksittäiset statuskoodit avautuvat "See all" -toiminnolla. */
@Composable
internal fun ResponsesSection(report: TrafficReport) {
    var showCodes by rememberSaveable { mutableStateOf(false) }
    val total = report.statuses.sumOf { it.value }
    val groups =
        responseGroups.map { group ->
            group to report.statuses.filter { it.key.toIntOrNull() in group.range }.sumOf { it.value }
        }
    ReportSection("Responses") {
        Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
            if (total == 0L) {
                Text("No data for this period", color = NumerisColors.TertiaryText)
            } else {
                ShareBar(groups.map { (group, value) -> group.color to value.toDouble() })
                ShareLegend(
                    groups.filter { it.second > 0 }.map { (group, value) ->
                        Triple(group.color, group.label, DisplayFormat.percent(ReportMath.ratio(value, total)))
                    },
                )
                if (showCodes) {
                    Column {
                        report.statuses.forEach { row ->
                            ExpandableRow(
                                row.label,
                                DisplayFormat.number(row.value),
                                listOf(
                                    "Share of requests" to DisplayFormat.percent(ReportMath.ratio(row.value, total)),
                                ),
                                supporting = statusMeaning(row.key.toInt()),
                                valueCaption = "requests",
                            )
                        }
                    }
                }
                ShowMoreButton(showCodes, report.statuses.size, { showCodes = !showCodes })
            }
        }
    }
}
