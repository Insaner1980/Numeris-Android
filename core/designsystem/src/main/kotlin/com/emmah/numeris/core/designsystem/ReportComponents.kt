@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.model.Breakdown
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.Period

/** Sivusto- ja jaksovalinta siruina samalla rivillä. Lisäsirut (esim. palveluntarjoaja) tulevat perään. */
@Composable
fun ReportFilters(
    domain: String,
    domains: List<String>,
    period: Period,
    onDomain: (String) -> Unit,
    onPeriod: (Period) -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
        NumerisSiteSelector(domain, domains, onDomain)
        NumerisDropdownSelector(
            period.name,
            Period.entries.map { it.name },
            { onPeriod(Period.valueOf(it)) },
            { Period.valueOf(it).displayLabel },
            detail = { periodRange(Period.valueOf(it)) },
        )
        extra()
    }
}

/** Jakson päivämäärät valikon toisella rivillä, jotta näkymään ei tarvita erillistä päivämääräriviä. */
private fun periodRange(period: Period): String {
    val range = CompactFormat.dateRange(period.toDateRange())
    return if (period == Period.All) "Rolling 365 days, $range" else range
}

@Composable
fun ReportSection(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().hairlineSurface().padding(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                supporting?.let {
                    Text(it, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
            action?.invoke()
        }
        content()
    }
}

/**
 * Rivi, joka avautuu näyttämään yksityiskohdat. Oikealla näkyy arvo tai, jos sävy on annettu, tilapilleri.
 */
@Composable
fun ExpandableRow(
    title: String,
    value: String,
    details: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    valueCaption: String? = null,
    tone: PillTone? = null,
    body: String? = null,
) {
    var expanded by rememberSaveable(title, supporting) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .heightIn(min = NumerisSizes.TouchTarget)
                .padding(vertical = NumerisSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                supporting?.let {
                    Text(it, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (tone != null) StatusPill(value, tone) else TrailingValue(value, caption = valueCaption)
            Icon(
                painterResource(R.drawable.ic_numeris_expand_more),
                contentDescription = null,
                modifier = Modifier.size(NumerisSizes.SmallIcon).rotate(if (expanded) EXPANDED_DEGREES else 0f),
                tint = NumerisColors.TertiaryText,
            )
        }
        if (expanded) {
            Column(
                Modifier.padding(bottom = NumerisSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
            ) {
                DetailPairs(details)
                body?.let { SelectionContainer { Text(it, color = NumerisColors.SecondaryText) } }
            }
        }
        HorizontalDivider(color = NumerisColors.Divider)
    }
}

private const val EXPANDED_DEGREES = 180f

/** Jakauma: nimi ja arvo samalla rivillä, suhteellinen palkki alla. */
@Composable
fun BreakdownRows(
    rows: List<Breakdown>,
    unit: String,
    modifier: Modifier = Modifier,
    visibleCount: Int = DEFAULT_VISIBLE_ROWS,
) {
    var expanded by rememberSaveable(rows.size, unit) { mutableStateOf(false) }
    val showDomain =
        rows
            .map { it.domain }
            .filter { it.isNotBlank() }
            .distinct()
            .size > 1
    val top = rows.maxOfOrNull { it.value } ?: 0L
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
        if (rows.isEmpty()) {
            Text("No data for this period", color = NumerisColors.TertiaryText)
        }
        (if (expanded) rows else rows.take(visibleCount)).forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
                    Column(Modifier.weight(1f)) {
                        SelectionContainer {
                            Text(row.label)
                        }
                        if (showDomain) {
                            Text(
                                row.domain,
                                color = NumerisColors.TertiaryText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    TrailingValue(DisplayFormat.number(row.value), caption = unit)
                }
                if (top > 0) {
                    LinearProgressIndicator(
                        progress = { (row.value.toFloat() / top).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().heightIn(max = NumerisSizes.BarHeight),
                        color = NumerisColors.Accent,
                        trackColor = NumerisColors.Control,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
        if (rows.size > visibleCount) ShowMoreButton(expanded, rows.size, { expanded = !expanded })
    }
}

private const val DEFAULT_VISIBLE_ROWS = 5

@Composable
fun ReportList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(NumerisSpacing.Lg),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
        content = content,
    )
}
