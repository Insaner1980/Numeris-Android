@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.Metric

/** Sivuston tunnusväri: paikka aakkosjärjestetyssä sivustolistassa, tuntemattomalle nimen tiiviste. */
fun siteColor(
    domain: String,
    allDomains: List<String>,
): Color {
    val palette = NumerisColors.SitePalette
    val index =
        allDomains
            .distinct()
            .sorted()
            .indexOf(domain)
            .takeIf { it >= 0 } ?: domain.hashCode()
    return palette[index.mod(palette.size)]
}

/**
 * Näkymän pääluku: selite, iso luku, värikoodattu muutos edelliseen jaksoon ja hillitty trendialue.
 * Korostus tulee luvun koosta ja väljyydestä, ei pinnan väristä.
 */
@Composable
fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    change: Double? = null,
    current: List<Double?> = emptyList(),
    previous: List<Double?> = emptyList(),
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = NumerisSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
    ) {
        Text(label, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Text(value, style = MaterialTheme.typography.displayMedium)
            change?.let { ChangeText(it, suffix = " vs previous") }
        }
        if (current.any { it != null }) {
            TrendArea(
                current,
                previous,
                Modifier.fillMaxWidth().padding(top = NumerisSpacing.Md).height(HERO_CHART_HEIGHT),
            )
        }
        footer?.invoke()
    }
}

private val HERO_CHART_HEIGHT = 64.dp

/** Ohut trendiviiva: kuluva jakso korostusvärillä, edellinen jakso katkoviivana. Aukot katkaisevat viivan. */
@Composable
fun TrendArea(
    current: List<Double?>,
    previous: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = NumerisColors.Accent,
) {
    val max = (current + previous).filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Canvas(
        modifier.semantics {
            contentDescription = "Trend for ${current.count { it != null }} days compared with the previous period"
        },
    ) {
        if (previous.any { it != null }) {
            drawSeries(previous, max, NumerisColors.PreviousSeries, dashed = true)
        }
        drawSeries(current, max, color, dashed = false)
    }
}

private fun DrawScope.drawSeries(
    values: List<Double?>,
    max: Double,
    color: Color,
    dashed: Boolean,
) {
    val step = size.width / (values.size - 1).coerceAtLeast(1)

    fun point(
        index: Int,
        value: Double,
    ) = Offset(step * index, (size.height * (1 - value / max)).toFloat())
    var segment = mutableListOf<Offset>()
    val segments = mutableListOf<List<Offset>>()
    values.forEachIndexed { index, value ->
        if (value == null) {
            if (segment.isNotEmpty()) segments += segment
            segment = mutableListOf()
        } else {
            segment += point(index, value)
        }
    }
    if (segment.isNotEmpty()) segments += segment
    segments.forEach { points ->
        val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(
            line,
            color,
            style =
                Stroke(
                    width = (if (dashed) 1.dp else 1.5.dp).toPx(),
                    pathEffect = dashEffect.takeIf { dashed },
                ),
        )
    }
}

private const val DASH_LENGTH = 6f
private val dashEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_LENGTH, DASH_LENGTH))

/** Osuudet yhtenä ohuena vaakapalkkina, esim. kävijät sivustoittain tai vastaukset tilaryhmittäin. */
@Composable
fun ShareBar(
    segments: List<Pair<Color, Double>>,
    modifier: Modifier = Modifier,
) {
    val total = segments.sumOf { it.second }.takeIf { it > 0 } ?: return
    Row(
        modifier
            .fillMaxWidth()
            .height(NumerisSizes.ShareBarHeight)
            .clip(RoundedCornerShape(NumerisShapes.PillRadius))
            .background(NumerisColors.Control),
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs),
    ) {
        segments.filter { it.second > 0 }.forEach { (color, value) ->
            Box(Modifier.weight((value / total).toFloat().coerceAtLeast(MIN_SEGMENT)).fillMaxHeight().background(color))
        }
    }
}

private const val MIN_SEGMENT = 0.005f

/** Selite osuuspalkille: väripiste, nimi ja arvo. */
@Composable
fun ShareLegend(
    items: List<Triple<Color, String, String>>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs),
    ) {
        items.forEach { (color, label, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
            ) {
                StatusDot(color)
                Text(label, color = NumerisColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text(value, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Pieni pyöreä värimerkki selitteisiin ja tilariveille. */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(NumerisSizes.StatusDot).clip(CircleShape).background(color))
}

/**
 * Kolme pientä tunnuslukua hiusviivojen välissä ilman laatikoita. Suurella fontilla luvut pinotaan
 * allekkain.
 */
@Composable
fun CompactKpiRow(
    metrics: List<Metric>,
    modifier: Modifier = Modifier,
    tones: List<Pair<String, PillTone>?> = emptyList(),
) {
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val tile: @Composable (Int, Metric, Modifier) -> Unit = { index, metric, tileModifier ->
        Column(
            tileModifier.padding(vertical = NumerisSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs),
        ) {
            Text(metric.label, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
            Text(DisplayFormat.metric(metric.value, metric.unit), style = MaterialTheme.typography.headlineSmall)
            val tone = tones.getOrNull(index)
            val change = metric.change
            when {
                tone != null ->
                    Text(tone.first, color = tone.second.contentColor(), style = MaterialTheme.typography.labelMedium)
                change != null -> ChangeText(change, higherIsBetter = metric.higherIsBetter)
            }
        }
    }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = NumerisColors.Divider)
        if (stacked) {
            metrics.forEachIndexed { index, metric ->
                if (index > 0) HorizontalDivider(color = NumerisColors.Divider)
                tile(index, metric, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                metrics.forEachIndexed { index, metric ->
                    if (index > 0) {
                        VerticalDivider(color = NumerisColors.Divider)
                        Spacer(Modifier.width(NumerisSpacing.Md))
                    }
                    tile(index, metric, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        HorizontalDivider(color = NumerisColors.Divider)
    }
}

/** Yksi hiusviivarajattu rivi "N things to check", joka avautuu näyttämään sisällön. */
@Composable
fun AttentionSummary(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
        Row(
            Modifier
                .fillMaxWidth()
                .hairlineSurface()
                .clickable(onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = NumerisSpacing.Lg, vertical = NumerisSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_numeris_warning),
                contentDescription = null,
                modifier = Modifier.size(NumerisSizes.SmallIcon),
                tint = NumerisColors.Warning,
            )
            Text(
                if (count == 1) "1 thing to check" else "$count things to check",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                painterResource(R.drawable.ic_numeris_expand_more),
                contentDescription = null,
                modifier = Modifier.size(NumerisSizes.SmallIcon).rotate(if (expanded) HALF_TURN else 0f),
                tint = NumerisColors.TertiaryText,
            )
        }
        if (expanded) content()
    }
}

private const val HALF_TURN = 180f
