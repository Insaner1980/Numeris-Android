@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.DatedValue
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.ReportSeries
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import java.time.LocalDate

@Composable
fun DatedChart(
    title: String,
    series: List<ReportSeries>,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    var showData by rememberSaveable(title) { mutableStateOf(false) }
    val selectedDay = remember(series) { mutableStateOf<Long?>(null) }
    val pointListener = rememberPointListener { selectedDay.value = it }
    val segments = remember(series) { chartSegments(series) }
    ReportSection(title, modifier, supporting = supporting) {
        Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
            if (series.size > 1) ChartLegend(series)
            when {
                segments.isEmpty() -> Text("No data for this period", color = NumerisColors.TertiaryText)
                series.all { it.unit in setOf("count", "bytes") && it.points.size <= DENSE_THRESHOLD } ->
                    VolumePlot(series, pointListener)
                else -> ChartPlot(series, segments, pointListener)
            }
            SelectedSamples(series, selectedDay.value)
            if (segments.isNotEmpty()) {
                InlineTextButton(if (showData) "Hide data table" else "Show data table", { showData = !showData })
            }
            if (showData) ChartDataTable(series)
        }
    }
}

private fun chartSegments(series: List<ReportSeries>): List<Pair<Int, List<DatedValue>>> =
    series.flatMapIndexed { index, line ->
        val result = mutableListOf<Pair<Int, List<DatedValue>>>()
        var segment = mutableListOf<DatedValue>()
        line.points.forEach { point ->
            if (point.value == null) {
                if (segment.isNotEmpty()) result += index to segment.toList()
                segment = mutableListOf()
            } else {
                segment += point
            }
        }
        if (segment.isNotEmpty()) result += index to segment.toList()
        result
    }

@Composable
private fun ChartPlot(
    series: List<ReportSeries>,
    segments: List<Pair<Int, List<DatedValue>>>,
    pointListener: CartesianMarkerVisibilityListener,
) {
    val model =
        remember(series, segments) {
            CartesianChartModel(
                LineCartesianLayerModel.build {
                    segments.forEach { (index, points) ->
                        val offset = if (series[index].previous) series[index].points.size else 0
                        series(x = points.map { it.date.toEpochDay() + offset }, y = points.map { it.value!! })
                    }
                },
            )
        }
    val lines =
        segments.map { (index, points) ->
            rememberReportLine(
                seriesColor(index, series[index].previous),
                series[index].previous,
                points.size <= SPARSE_THRESHOLD,
            )
        }
    val layer = rememberLineCartesianLayer(lineProvider = LineCartesianLayer.LineProvider.series(lines))
    ReportPlot(series, model, layer, pointListener)
}

@Composable
private fun VolumePlot(
    series: List<ReportSeries>,
    pointListener: CartesianMarkerVisibilityListener,
) {
    val present = series.withIndex().filter { (_, line) -> line.points.any { it.value != null } }
    val model =
        remember(series) {
            CartesianChartModel(
                ColumnCartesianLayerModel.build {
                    present.forEach { (_, line) ->
                        val points = line.points.filter { it.value != null }
                        val offset = if (line.previous) line.points.size else 0
                        series(x = points.map { it.date.toEpochDay() + offset }, y = points.map { it.value!! })
                    }
                },
            )
        }
    val columns =
        present.map { (index, line) ->
            rememberLineComponent(
                Fill(seriesColor(index, line.previous)),
                COLUMN_THICKNESS,
                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
            )
        }
    val layer = rememberColumnCartesianLayer(columnProvider = ColumnCartesianLayer.ColumnProvider.series(columns))
    ReportPlot(series, model, layer, pointListener)
}

@Composable
private fun ReportPlot(
    series: List<ReportSeries>,
    model: CartesianChartModel,
    layer: CartesianLayer<*>,
    pointListener: CartesianMarkerVisibilityListener,
) {
    CartesianChartHost(
        chart =
            rememberCartesianChart(
                layer,
                startAxis = rememberValueAxis(series),
                bottomAxis = rememberDateAxis(),
                marker = rememberPointMarker(),
                markerController = CartesianMarkerController.rememberToggleOnTap(),
                markerVisibilityListener = pointListener,
            ),
        model = model,
        zoomState = rememberVicoZoomState(initialZoom = Zoom.Content),
        modifier = chartModifier,
    )
}

private val chartModifier =
    Modifier.fillMaxWidth().height(NumerisSizes.ChartHeight).semantics { contentDescription = "Select a chart point" }

private const val DENSE_THRESHOLD = 60
private const val SPARSE_THRESHOLD = 12
private const val SMALL_COUNT_MAX = 5.0
private val COLUMN_THICKNESS = 6.dp

private val seriesColors =
    listOf(
        NumerisColors.Accent,
        NumerisColors.Copper,
        NumerisColors.WarmPlatinum,
        NumerisColors.Error,
    )

internal fun seriesColor(
    index: Int,
    previous: Boolean,
): Color = if (previous) NumerisColors.PreviousSeries else seriesColors[index % seriesColors.size]

@Composable
private fun rememberReportLine(
    color: Color,
    previous: Boolean,
    sparse: Boolean,
) = LineCartesianLayer.rememberLine(
    fill = LineCartesianLayer.LineFill.single(Fill(color)),
    pointProvider =
        if (sparse) {
            LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    rememberShapeComponent(Fill(color), androidx.compose.foundation.shape.CircleShape),
                    6.dp,
                ),
            )
        } else {
            null
        },
    stroke =
        if (previous) {
            LineCartesianLayer.LineStroke.Dashed()
        } else {
            LineCartesianLayer.LineStroke
                .Continuous()
        },
)

/** Pienille kokonaislukumäärille askel 1, jotta akselille ei tule toistuvia pyöristettyjä arvoja. */
@Composable
private fun rememberValueAxis(series: List<ReportSeries>): VerticalAxis<Axis.Position.Vertical.Start> {
    val unit = series.first().unit
    val max = series.flatMap { it.points }.mapNotNull { it.value }.maxOrNull() ?: 0.0
    val integerSteps = unit == "count" && max <= SMALL_COUNT_MAX
    return VerticalAxis.rememberStart(
        label = rememberAxisLabelComponent(style = axisLabelStyle),
        guideline = rememberLineComponent(Fill(NumerisColors.Divider), 1.dp),
        valueFormatter = { _, value, _ -> DisplayFormat.metric(value, unit) },
        itemPlacer =
            if (integerSteps) {
                VerticalAxis.ItemPlacer.step({ 1.0 })
            } else {
                VerticalAxis.ItemPlacer.step()
            },
    )
}

@Composable
private fun rememberDateAxis() =
    HorizontalAxis.rememberBottom(
        label = rememberAxisLabelComponent(style = axisLabelStyle),
        guideline = null,
        valueFormatter = { _, value, _ -> CompactFormat.date(LocalDate.ofEpochDay(value.toLong())) },
    )

private val axisLabelStyle =
    TextStyle(color = NumerisColors.TertiaryText, fontSize = 11.sp, fontFamily = NumerisFontFamily)
