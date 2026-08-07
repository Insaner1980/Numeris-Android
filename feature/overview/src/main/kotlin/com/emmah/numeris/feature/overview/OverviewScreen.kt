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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisDropdownSelector
import com.emmah.numeris.core.designsystem.NumerisEqualChoiceRow
import com.emmah.numeris.core.designsystem.NumerisScreenHeader
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
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg)) {
                NumerisScreenHeader(
                    title = "Numeris",
                    subtitle = "Private site intelligence",
                    syncing = syncing,
                    onRefresh = onRefresh,
                )
                NumerisDropdownSelector(
                    selected = domain,
                    options = listOf("all") + domains,
                    onSelect = onSelectDomain,
                    label = { value -> if (value == "all") "All domains" else value },
                )
                NumerisEqualChoiceRow(
                    options = Period.entries.map(Period::shortLabel),
                    selected = period.shortLabel,
                    onSelect = { label -> Period.entries.firstOrNull { it.shortLabel == label }?.let(onSelectPeriod) },
                )
                Text(
                    snapshot.latestSuccessfulSync?.let { "Last successful sync $it" } ?: "No successful sync yet",
                    color = NumerisColors.TertiaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (snapshot.latestSuccessfulSync == null) {
            item {
                GetStartedCard(
                    configuredSources = snapshot.configuredSources,
                    syncing = syncing,
                    onRefresh = onRefresh,
                    onOpenSources = onOpenSources,
                )
            }
        } else {
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns =
                        if (maxWidth >= 700.dp) {
                            3
                        } else if (maxWidth >= 320.dp) {
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
}

@Composable
@Suppress("FunctionNaming")
private fun GetStartedCard(
    configuredSources: Int,
    syncing: Boolean,
    onRefresh: () -> Unit,
    onOpenSources: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NumerisColors.Card),
    ) {
        Column(
            modifier = Modifier.padding(NumerisSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
        ) {
            Text("Build your first dashboard", style = MaterialTheme.typography.titleLarge)
            Text(
                if (configuredSources == 0) {
                    "Connect a data source, then refresh to cache your first private report."
                } else {
                    "$configuredSources sources are ready. Refresh to download your first private report."
                },
                color = NumerisColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRefresh,
                    enabled = !syncing,
                ) {
                    Text(if (syncing) "Refreshing…" else "Refresh now")
                }
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSources,
                ) { Text("Set up sources") }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun KpiCard(
    kpi: KpiValue,
    modifier: Modifier = Modifier,
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
            Text(kpi.label, color = NumerisColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            Text(kpi.value, style = MaterialTheme.typography.headlineSmall)
            Text(kpi.detail, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodyMedium)
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
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = NumerisColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Double?.scoreText(): String = this?.let { "${(it * SCORE_SCALE).toInt()}" } ?: "—"
