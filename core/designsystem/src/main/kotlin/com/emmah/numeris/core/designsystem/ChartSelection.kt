@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.ReportSeries
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.time.LocalDate

@Composable
internal fun rememberPointMarker(): CartesianMarker =
    rememberDefaultCartesianMarker(
        label =
            rememberTextComponent(
                style = MaterialTheme.typography.bodySmall.copy(color = NumerisColors.PrimaryText),
                background = rememberShapeComponent(Fill(NumerisColors.Control)),
            ),
        valueFormatter =
            remember {
                DefaultCartesianMarker.ValueFormatter { _, targets ->
                    targets.firstOrNull()?.let { CompactFormat.date(LocalDate.ofEpochDay(it.x.toLong())) }.orEmpty()
                }
            },
    )

@Composable
internal fun rememberPointListener(onDay: (Long) -> Unit): CartesianMarkerVisibilityListener {
    val currentOnDay by rememberUpdatedState(onDay)
    return remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(
                marker: CartesianMarker,
                targets: List<CartesianMarker.Target>,
            ) {
                targets.firstOrNull()?.let { currentOnDay(it.x.toLong()) }
            }

            override fun onUpdated(
                marker: CartesianMarker,
                targets: List<CartesianMarker.Target>,
            ) {
                targets.firstOrNull()?.let { currentOnDay(it.x.toLong()) }
            }
        }
    }
}

/** Valitun päivän arvot sarjoittain. Vertailusarjan päivä siirretään takaisin omalle jaksolleen. */
@Composable
internal fun SelectedSamples(
    series: List<ReportSeries>,
    day: Long?,
) {
    if (day == null) return
    val values =
        series.mapNotNull { line ->
            val date = LocalDate.ofEpochDay(day - if (line.previous) line.points.size else 0)
            line.points.firstOrNull { it.date == date && it.value != null }?.let { point ->
                "${legendLabel(line, series)}, ${CompactFormat.date(date)}" to
                    DisplayFormat.metric(point.value, line.unit)
            }
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NumerisShapes.ControlRadius))
            .background(NumerisColors.Control)
            .padding(NumerisSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
    ) {
        Text("Selected day", style = MaterialTheme.typography.titleSmall)
        DetailPairs(values)
    }
}

/** Selitteen nimi: vertailukaaviossa "This period" ja "Previous period", muuten sarjan oma nimi. */
internal fun legendLabel(
    line: ReportSeries,
    series: List<ReportSeries>,
): String =
    when {
        line.previous -> "Previous period"
        series.any { it.previous } -> "This period"
        else -> line.label
    }

@Composable
internal fun ChartLegend(series: List<ReportSeries>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg)) {
        series.forEachIndexed { index, line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
            ) {
                Box(
                    Modifier
                        .size(LEGEND_SWATCH)
                        .clip(RoundedCornerShape(2.dp))
                        .background(seriesColor(index, line.previous)),
                )
                Text(
                    legendLabel(line, series),
                    color = if (line.previous) NumerisColors.TertiaryText else NumerisColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun ChartDataTable(series: List<ReportSeries>) {
    Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
        series.forEach { line ->
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
                Text(legendLabel(line, series), style = MaterialTheme.typography.titleSmall)
                DetailPairs(
                    line.points.map { CompactFormat.date(it.date) to DisplayFormat.metric(it.value, line.unit) },
                )
            }
        }
    }
}

private val LEGEND_SWATCH = 8.dp
