@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris.feature.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.data.OverviewSnapshot
import java.text.NumberFormat
import java.util.Locale

private data class KpiValue(
    val label: String,
    val value: String,
    val detail: String,
)

private const val SCORE_SCALE = 100

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun OverviewScreen(
    snapshot: OverviewSnapshot,
    domain: String,
    domains: List<String>,
    period: Period,
    syncing: Boolean,
    onSelectPeriod: (Period) -> Unit,
    onSelectDomain: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val numberFormat = NumberFormat.getIntegerInstance(Locale.US)
    val kpis =
        listOf(
            KpiValue("Cloudflare visitors", numberFormat.format(snapshot.cloudflareVisitors), period.displayLabel),
            KpiValue("Google Search clicks", numberFormat.format(snapshot.googleClicks), period.displayLabel),
            KpiValue("Bing clicks", numberFormat.format(snapshot.bingClicks), period.displayLabel),
            KpiValue("Core Web Vitals", "—", "No cached CrUX status"),
            KpiValue("PageSpeed mobile", snapshot.pageSpeedMobile.scoreText(), "Latest cached run"),
            KpiValue(
                "Source health",
                "${snapshot.configuredSources} / ${snapshot.totalSources}",
                "Configured sources",
            ),
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Numeris", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text("Private site intelligence", color = NumerisColors.SecondaryText)
                    }
                    Button(onClick = onRefresh, enabled = !syncing) {
                        Text(if (syncing) "Refreshing…" else "Refresh")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                    items(domains.size + 1) { index ->
                        val option = if (index == 0) "all" else domains[index - 1]
                        FilterChip(
                            selected = domain == option,
                            onClick = { onSelectDomain(option) },
                            label = { Text(if (option == "all") "All domains" else option) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                    Period.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { onSelectPeriod(option) },
                            label = { Text(option.shortLabel) },
                        )
                    }
                }
                Text(
                    snapshot.latestSuccessfulSync?.let { "Last successful sync $it" } ?: "No successful sync yet",
                    color = NumerisColors.TertiaryText,
                )
            }
        }
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns =
                    if (maxWidth >= 700.dp) {
                        3
                    } else if (maxWidth >= 420.dp) {
                        2
                    } else {
                        1
                    }
                Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
                    kpis.chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
                        ) {
                            rowItems.forEach { kpi -> KpiCard(kpi, Modifier.weight(1f)) }
                            repeat(columns - rowItems.size) { Column(modifier = Modifier.weight(1f)) {} }
                        }
                    }
                }
            }
        }
        item { SummaryCard("Traffic trend", "Trend data appears after a successful provider sync.") }
        item { SummaryCard("Search trend", "Google and Bing history remains available offline.") }
        item { SummaryCard("Insights", "No deterministic issues were found in the cached data.") }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun KpiCard(
    kpi: KpiValue,
    modifier: Modifier,
) {
    Card(
        modifier =
            modifier.semantics {
                contentDescription = "${kpi.label}, ${kpi.value}, ${kpi.detail}"
            },
        colors = CardDefaults.cardColors(containerColor = NumerisColors.Card),
    ) {
        Column(
            modifier = Modifier.padding(NumerisSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
        ) {
            Text(kpi.label, color = NumerisColors.SecondaryText)
            Text(kpi.value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(kpi.detail, color = NumerisColors.TertiaryText)
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun SummaryCard(
    title: String,
    detail: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NumerisColors.Card),
    ) {
        Column(modifier = Modifier.padding(NumerisSpacing.Lg)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = NumerisColors.SecondaryText)
        }
    }
}

private fun Double?.scoreText(): String = this?.let { "${(it * SCORE_SCALE).toInt()}" } ?: "—"
