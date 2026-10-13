@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.performance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.emmah.numeris.core.designsystem.LabScores
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.PillTone
import com.emmah.numeris.core.designsystem.SiteAvatar
import com.emmah.numeris.core.designsystem.StatusPill
import com.emmah.numeris.core.designsystem.contentColor
import com.emmah.numeris.core.designsystem.hairlineSurface
import com.emmah.numeris.core.designsystem.labScoreTone
import com.emmah.numeris.core.designsystem.tone
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.DisplayLabels
import com.emmah.numeris.core.model.FieldMetric
import com.emmah.numeris.core.model.LabRun
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.SiteIdentity

/** Yhden sivuston kenttä- ja laboratoriodata Vitals-yhteenvetoa varten. */
internal data class SiteVitals(
    val domain: String,
    val field: List<FieldMetric>,
    val runs: List<LabRun>,
)

private fun siteOf(target: String): String? = runCatching { SiteIdentity.normalizeDomain(target) }.getOrNull()

/** Ryhmittelee kenttädatan ja lab-ajot sivustoittain. Kenttädatassa suositaan koko sivuston (origin) arvoja. */
internal fun siteVitals(
    field: List<FieldMetric>,
    runs: List<LabRun>,
): List<SiteVitals> {
    val fieldBySite = field.groupBy { siteOf(it.target) }
    val runsBySite = runs.groupBy { siteOf(it.url) }
    return (fieldBySite.keys + runsBySite.keys)
        .filterNotNull()
        .distinct()
        .sorted()
        .map { site ->
            val siteField = fieldBySite[site].orEmpty()
            val preferred = siteField.filter { it.targetType.equals("origin", true) }.ifEmpty { siteField }
            val latest =
                preferred
                    .sortedBy { devicePriority(it.device) }
                    .groupBy { it.metric }
                    .map { (_, points) -> points.filter { it.device == points.first().device }.maxBy { it.end } }
            SiteVitals(site, latest, runsBySite[site].orEmpty())
        }
}

private fun devicePriority(device: String): Int =
    when (device.uppercase()) {
        "ALL" -> 0
        "PHONE" -> 1
        else -> 2
    }

private val headlineMetrics =
    listOf(
        "largest_contentful_paint" to "Loading",
        "interaction_to_next_paint" to "Response",
        "cumulative_layout_shift" to "Stability",
    )

private fun summaryPill(summary: String): Pair<String, PillTone> =
    when (summary) {
        "Pass" -> "Passes" to PillTone.Positive
        "Needs work" -> "Needs work" to PillTone.Warning
        "Fail" -> "Fails" to PillTone.Negative
        else -> "Not enough users" to PillTone.Neutral
    }

@Composable
internal fun SiteVitalsCard(
    site: SiteVitals,
    domains: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(site.domain) { mutableStateOf(false) }
    val (pillText, pillTone) = summaryPill(ReportMath.vitals(site.field))
    Column(
        modifier
            .fillMaxWidth()
            .hairlineSurface()
            .clickable { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
        ) {
            SiteAvatar(site.domain, domains)
            Text(site.domain, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            StatusPill(pillText, pillTone)
        }
        if (site.field.any { it.metric in headlineMetrics.map { metric -> metric.first } }) HeadlineVitals(site.field)
        LabSummaryRow(site.runs)
        if (expanded) VitalsDetails(site)
    }
}

@Composable
private fun HeadlineVitals(field: List<FieldMetric>) {
    Row(Modifier.fillMaxWidth()) {
        headlineMetrics.forEach { (metric, label) ->
            val point = field.firstOrNull { it.metric == metric }
            val rating = point?.p75?.let { ReportMath.vitalRating(metric, it) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs)) {
                Text(label, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
                Text(
                    DisplayFormat.metric(point?.p75, metricUnit(metric)),
                    color = rating?.tone()?.contentColor() ?: NumerisColors.SecondaryText,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}

/** Uusin suorituskykypiste mobiilille ja desktopille samalla rivillä. */
@Composable
private fun LabSummaryRow(runs: List<LabRun>) {
    if (runs.isEmpty()) return
    Column {
        HorizontalDivider(color = NumerisColors.Divider)
        Row(
            Modifier.fillMaxWidth().padding(top = NumerisSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Lab score",
                Modifier.weight(1f),
                color = NumerisColors.TertiaryText,
                style = MaterialTheme.typography.labelMedium,
            )
            listOf("mobile", "desktop").forEach { device ->
                val latest = runs.filter { it.device == device }.maxByOrNull { it.time }
                Text(
                    "${DisplayLabels.device(device)} ",
                    color = NumerisColors.TertiaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    DisplayFormat.score(latest?.performance),
                    Modifier.padding(end = NumerisSpacing.Md),
                    color = labScoreTone(latest?.performance).contentColor(),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun VitalsDetails(site: SiteVitals) {
    Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
        site.field.firstOrNull()?.let {
            Text(
                "Field data from ${DisplayLabels.device(it.device).lowercase()}, " +
                    "${CompactFormat.dateRange(it.start, it.end)}",
                color = NumerisColors.TertiaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        site.field
            .filter { it.p75 != null && it.metric !in headlineMetrics.map { metric -> metric.first } }
            .forEach { point ->
                val rating = ReportMath.vitalRating(point.metric, point.p75!!)
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        DisplayLabels.metricName(point.metric),
                        Modifier.weight(1f),
                        color = NumerisColors.SecondaryText,
                    )
                    Text(
                        DisplayFormat.metric(point.p75, metricUnit(point.metric)),
                        color = rating?.tone()?.contentColor() ?: NumerisColors.PrimaryText,
                    )
                }
            }
        site.runs
            .groupBy { it.url to it.device }
            .values
            .map { runs -> runs.maxBy { it.time } }
            .forEach { LabScores(it) }
    }
}
